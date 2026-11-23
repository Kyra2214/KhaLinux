package com.khalinux.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.khalinux.android.data.CommandHistoryEntry
import com.khalinux.android.data.CommandHistoryDatabase
import com.khalinux.android.data.CommandHistoryRepository
import com.khalinux.android.data.TerminalSettingsEntity
import com.khalinux.android.data.TerminalSettingsRepository
import com.khalinux.android.domain.RuntimeEventObserver
import com.khalinux.android.domain.EnvironmentInstallObserver
import com.khalinux.android.domain.TerminalRuntimeGateway
import com.khalinux.android.domain.TerminalUseCases
import com.khalinux.runtime.RuntimeEvent
import com.khalinux.runtime.diagnostics.DiagnosticId
import com.khalinux.runtime.diagnostics.DiagnosticReport
import com.khalinux.runtime.diagnostics.DiagnosticStatus
import com.khalinux.runtime.install.InstallPhase
import com.khalinux.runtime.install.InstallProgress
import com.khalinux.android.data.FavoritesProvider
import com.khalinux.runtime.favorites.FavoriteKind
import com.khalinux.runtime.files.HomeFileManager
import com.khalinux.runtime.reports.OutputNote
import com.khalinux.runtime.reports.ReportWriter
import com.khalinux.runtime.scripts.ScriptStore
import com.khalinux.runtime.settings.TerminalThemes
import com.khalinux.runtime.tabs.TerminalTab
import com.khalinux.runtime.tabs.TerminalTabInfo
import com.khalinux.runtime.tabs.TerminalTabs
import com.khalinux.runtime.TerminalKey
import com.khalinux.runtime.TerminalKeys
import kotlinx.coroutines.delay
import com.khalinux.runtime.TerminalSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import java.time.Duration
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update

private const val PUBLISH_INTERVAL_MILLIS = 33L
private const val DEFAULT_COMMAND = "printf 'KhaLinux\\n'"

/** Editor de script aberto na UI (nome e conteúdo em edição). */
data class ScriptEditorState(val name: String, val content: String, val isNew: Boolean)

data class TerminalUiState(
    val serviceConnected: Boolean = false,
    val runtimeInstalled: Boolean = false,
    val connectionMessage: String? = null,
    val command: String = DEFAULT_COMMAND,
    val output: String = "",
    val sessionId: String? = null,
    val isInteractive: Boolean = false,
    val isStarting: Boolean = false,
    val isSendingInput: Boolean = false,
    val isInstalling: Boolean = false,
    val installPhase: InstallPhase? = null,
    val installPercent: Int = 0,
    val installDetail: String? = null,
    val installError: String? = null,
    val rootfsInstalled: Boolean = false,
    val prootInstalled: Boolean = false,
    val terminalSnapshot: TerminalSnapshot = TerminalSnapshot.empty(),
    val commandHistory: List<CommandHistoryEntry> = emptyList(),
    val fontSizeSp: Float = 12f,
    val terminalRows: Int = 24,
    val terminalColumns: Int = 80,
    val showDiagnostics: Boolean = false,
    val diagnosticsRunning: Boolean = false,
    val ctrlArmed: Boolean = false,
    val altArmed: Boolean = false,
    val diagnostics: DiagnosticReport = DiagnosticReport.initial(),
    val tabs: List<TerminalTabInfo> = emptyList(),
    val activeTabId: String = "",
    val canAddTab: Boolean = true,
    val themeIndex: Int = 0,
    val favoriteCommands: List<String> = emptyList(),
    val scripts: List<String> = emptyList(),
    val scriptEditor: ScriptEditorState? = null,
    val scriptMessage: String? = null,
    val noteMessage: String? = null
) {
    val isRunning: Boolean get() = isStarting || sessionId != null
}

class TerminalViewModel(application: Application) : AndroidViewModel(application) {
    private val lock = Any()
    private val tabs = TerminalTabs(rows = 24, columns = 80)
    private val database = CommandHistoryDatabase.getInstance(application)
    private val historyRepository = CommandHistoryRepository(database.commandHistoryDao())
    private val settingsRepository = TerminalSettingsRepository(database.terminalSettingsDao())
    private val favoritesStore = FavoritesProvider.get(application)
    // O diretório do host aparece no RootFS como /home/khalinux/scripts (bind do home persistente).
    private val scriptStore = ScriptStore(java.io.File(application.filesDir, "home/scripts"))
    private val mutableUiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = mutableUiState.asStateFlow()

    private var runtimeGateway: TerminalRuntimeGateway? = null
    private var runtimeUseCases: TerminalUseCases? = null
    private val startJobs = mutableMapOf<String, Job>()
    private val inputJobs = mutableMapOf<String, Job>()
    private var diagnosticsJob: Job? = null
    private var publishJob: Job? = null
    private var lastPublishMillis = 0L
    private val completedSessionsBeforeStartReturn = mutableSetOf<String>()

    init {
        synchronized(lock) { refreshLocked() }
        reloadFavorites()
        reloadScripts()
        viewModelScope.launch {
            historyRepository.observeRecent().collect { entries ->
                mutableUiState.update { it.copy(commandHistory = entries) }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.observeSettings().collect { settings ->
                if (settings == null) {
                    settingsRepository.save(TerminalSettingsEntity())
                } else {
                    synchronized(lock) {
                        val rows = settings.rows.coerceIn(12, 60)
                        val columns = settings.columns.coerceIn(40, 160)
                        tabs.resizeAll(rows, columns)
                        mutableUiState.update {
                            it.copy(
                                fontSizeSp = settings.fontSizeSp.coerceIn(10f, 22f),
                                themeIndex = TerminalThemes.byIndex(settings.themeIndex).index,
                                terminalRows = rows,
                                terminalColumns = columns
                            )
                        }
                        refreshLocked()
                    }
                }
            }
        }
    }

    fun attachRuntime(gateway: TerminalRuntimeGateway) {
        synchronized(lock) {
            val sameService = runtimeGateway == gateway
            if (!sameService && tabs.anyRunning()) {
                dropAllSessionsLocked("\n[sessão interrompida após reconexão do Service]\n")
            }
            if (!sameService) {
                runtimeGateway = gateway
                runtimeUseCases = TerminalUseCases(gateway)
            }
            mutableUiState.update {
                it.copy(
                    serviceConnected = true,
                    runtimeInstalled = runtimeUseCases?.isRuntimeInstalled?.invoke() == true,
                    rootfsInstalled = gateway.isRootfsInstalled(),
                    prootInstalled = gateway.isProotInstalled(),
                    connectionMessage = null
                )
            }
        }

        // Instalação automática na primeira abertura: PRoot + Debian, com progresso global.
        // Em reconexões o Service apenas reenvia o último progresso ao novo ouvinte.
        if (!mutableUiState.value.runtimeInstalled) startInstall()
    }

    fun serviceDisconnected() = synchronized(lock) {
        dropAllSessionsLocked("\n[Service desconectado; sessão não está mais vinculada]\n")
        diagnosticsJob?.cancel()
        runtimeGateway = null
        runtimeUseCases = null
        mutableUiState.update {
            it.copy(
                serviceConnected = false,
                runtimeInstalled = false,
                connectionMessage = "Service local desconectado",
                isInstalling = false,
                diagnosticsRunning = false
            )
        }
    }

    fun bindingUnavailable() = synchronized(lock) {
        dropAllSessionsLocked(null)
        diagnosticsJob?.cancel()
        runtimeGateway = null
        runtimeUseCases = null
        mutableUiState.update {
            it.copy(
                serviceConnected = false,
                runtimeInstalled = false,
                connectionMessage = "Não foi possível conectar ao Service local",
                isInstalling = false,
                diagnosticsRunning = false
            )
        }
    }

    /** Inicia (ou retoma/reconecta) a instalação automática; também serve de "Tentar novamente". */
    fun startInstall() {
        val gateway = synchronized(lock) {
            val activeGateway = runtimeGateway ?: return
            val current = mutableUiState.value
            if (!current.serviceConnected || current.runtimeInstalled) return
            mutableUiState.update {
                it.copy(
                    isInstalling = true,
                    installError = null,
                    installPhase = it.installPhase ?: InstallPhase.CHECKING
                )
            }
            activeGateway
        }
        try {
            gateway.installEnvironment(object : EnvironmentInstallObserver {
                override fun onProgress(progress: InstallProgress) {
                    mutableUiState.update {
                        it.copy(
                            isInstalling = true,
                            installPhase = progress.phase,
                            installPercent = progress.percent,
                            installDetail = describeProgress(progress)
                        )
                    }
                }

                override fun onFinished(runtimeReady: Boolean) {
                    mutableUiState.update {
                        it.copy(
                            isInstalling = false,
                            installPhase = InstallPhase.READY,
                            installPercent = 100,
                            installDetail = null,
                            installError = if (runtimeReady) null else "Ambiente instalado, mas o runtime não iniciou",
                            runtimeInstalled = runtimeReady,
                            rootfsInstalled = true,
                            prootInstalled = true
                        )
                    }
                }

                override fun onError(message: String) {
                    mutableUiState.update {
                        it.copy(
                            isInstalling = false,
                            installPhase = InstallPhase.FAILED,
                            installError = message
                        )
                    }
                }
            })
        } catch (error: Exception) {
            mutableUiState.update {
                it.copy(
                    isInstalling = false,
                    installPhase = InstallPhase.FAILED,
                    installError = "Falha ao iniciar instalação: ${error.message ?: error.javaClass.simpleName}"
                )
            }
        }
    }

    fun cancelInstall() {
        runtimeGateway?.cancelInstall()
    }

    /** Abre a tela de Diagnóstico e executa os testes automáticos sozinha. */
    fun openDiagnostics() {
        mutableUiState.update { it.copy(showDiagnostics = true) }
        runDiagnostics()
    }

    fun closeDiagnostics() {
        mutableUiState.update { it.copy(showDiagnostics = false) }
    }

    fun runDiagnostics() {
        val gateway = synchronized(lock) {
            if (mutableUiState.value.diagnosticsRunning) return
            val activeGateway = runtimeGateway ?: return
            mutableUiState.update {
                it.copy(diagnosticsRunning = true, diagnostics = DiagnosticReport.initial())
            }
            activeGateway
        }
        diagnosticsJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val finalReport = gateway.runDiagnostics { report ->
                    mutableUiState.update { it.copy(diagnostics = report) }
                }
                mutableUiState.update { it.copy(diagnostics = finalReport) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableUiState.update {
                    it.copy(
                        diagnostics = it.diagnostics.with(
                            DiagnosticId.PROOT_EXEC,
                            DiagnosticStatus.FAILED,
                            "Falha ao executar o diagnóstico: ${error.message ?: error.javaClass.simpleName}"
                        )
                    )
                }
            } finally {
                mutableUiState.update { it.copy(diagnosticsRunning = false) }
            }
        }
    }

    /** Itens de rotação e segundo plano só o usuário consegue confirmar. */
    fun confirmManualDiagnostic(id: DiagnosticId, ok: Boolean) {
        mutableUiState.update { state ->
            val item = state.diagnostics.item(id)
            if (item.status != DiagnosticStatus.MANUAL &&
                item.status != DiagnosticStatus.OK &&
                item.status != DiagnosticStatus.FAILED
            ) return@update state
            if (id != DiagnosticId.ROTATION && id != DiagnosticId.BACKGROUND) return@update state
            state.copy(
                diagnostics = state.diagnostics.with(
                    id,
                    if (ok) DiagnosticStatus.OK else DiagnosticStatus.FAILED,
                    if (ok) "confirmado pelo usuário" else "reprovado pelo usuário"
                )
            )
        }
    }

    private fun describeProgress(progress: InstallProgress): String? {
        val total = progress.totalBytes ?: return null
        if (total <= 0L) return null
        val megabyte = 1024L * 1024L
        return when (progress.phase) {
            InstallPhase.DOWNLOADING_PROOT ->
                "${progress.doneBytes / 1024} / ${total / 1024} KB"
            InstallPhase.DOWNLOADING_ROOTFS, InstallPhase.EXTRACTING ->
                "${progress.doneBytes / megabyte} / ${total / megabyte} MB"
            else -> null
        }
    }

    // ---- Integração entre ferramentas (7.7): saída do terminal -> nota em reports/ ----

    /** Salva a saída da aba ativa (e o último comando digitado) como nota Markdown em `reports/`. */
    fun saveOutputNote() {
        val state = mutableUiState.value
        val title = state.tabs.firstOrNull { it.id == state.activeTabId }?.title ?: "Saída do terminal"
        val output = state.output
        val command = state.command.takeIf { it.isNotBlank() && !state.isInteractive }
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val note = OutputNote.build(now, title, command, output)
            val path = if (note == null) null else runCatching {
                ReportWriter(HomeFileManager(java.io.File(getApplication<Application>().filesDir, "home").apply { mkdirs() }))
                    .write(now, note, OutputNote.PREFIX)
            }.getOrNull()
            mutableUiState.update {
                it.copy(noteMessage = when {
                    note == null -> "Sem saída para salvar nesta aba"
                    path == null -> "Não foi possível salvar a nota"
                    else -> "Nota salva em $path"
                })
            }
        }
    }

    fun dismissNoteMessage() = mutableUiState.update { it.copy(noteMessage = null) }

    // ---- Scripts (7.4) ----

    private fun reloadScripts() {
        viewModelScope.launch(Dispatchers.IO) {
            val names = runCatching { scriptStore.list() }.getOrDefault(emptyList())
            mutableUiState.update { it.copy(scripts = names) }
        }
    }

    fun newScript() = mutableUiState.update {
        it.copy(scriptEditor = ScriptEditorState("", "#!/usr/bin/env bash\n", isNew = true), scriptMessage = null)
    }

    fun editScript(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val content = runCatching { scriptStore.read(name) }.getOrNull()
            mutableUiState.update {
                if (content == null) it.copy(scriptMessage = "Não foi possível abrir o script")
                else it.copy(scriptEditor = ScriptEditorState(name, content, isNew = false), scriptMessage = null)
            }
        }
    }

    fun updateScriptEditor(name: String, content: String) = mutableUiState.update { state ->
        state.scriptEditor?.let { state.copy(scriptEditor = it.copy(name = name.take(64), content = content)) } ?: state
    }

    fun cancelScriptEditor() = mutableUiState.update { it.copy(scriptEditor = null) }

    fun saveScript() {
        val editor = mutableUiState.value.scriptEditor ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val saved = runCatching { scriptStore.save(editor.name, editor.content) }.getOrNull()
            mutableUiState.update {
                if (saved == null) it.copy(scriptMessage = "Nome inválido (use letras, números, ponto, hífen) ou conteúdo grande demais")
                else it.copy(scriptEditor = null, scriptMessage = null)
            }
            if (saved != null) reloadScripts()
        }
    }

    fun deleteScript(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { scriptStore.delete(name) }
            reloadScripts()
        }
    }

    /** Executa o script na aba ativa (o comando entra no histórico como qualquer comando). */
    fun runScript(name: String) {
        val command = scriptStore.command(name) ?: return
        updateCommand(command)
        runCommand()
    }

    // ---- Favoritos (7.3) ----

    private fun reloadFavorites() {
        viewModelScope.launch(Dispatchers.IO) {
            val commands = runCatching { favoritesStore.list(FavoriteKind.COMMAND).map { it.value } }.getOrDefault(emptyList())
            mutableUiState.update { it.copy(favoriteCommands = commands) }
        }
    }

    /** Favorita (ou desfavorita) o comando digitado; ignora entrada vazia e sessões interativas (podem conter senhas). */
    fun toggleFavoriteCommand() {
        val command = mutableUiState.value.command
        if (mutableUiState.value.isInteractive) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { favoritesStore.toggle(FavoriteKind.COMMAND, command) }
            reloadFavorites()
        }
    }

    fun removeFavoriteCommand(command: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { favoritesStore.remove(FavoriteKind.COMMAND, command) }
            reloadFavorites()
        }
    }

    fun updateCommand(command: String) {
        mutableUiState.update { it.copy(command = command) }
    }

    // ---- Múltiplas sessões (7.1): cada aba tem emulador, sessão do runtime e rascunho próprios ----

    /** Cria e ativa uma nova aba (até o limite de abas). */
    fun addTab() {
        synchronized(lock) {
            if (!tabs.canCreate()) return
            tabs.active().draft = mutableUiState.value.command
            val tab = tabs.create() ?: return
            tab.draft = DEFAULT_COMMAND
            mutableUiState.update { it.copy(command = tab.draft) }
            refreshLocked()
        }
    }

    fun selectTab(id: String) {
        synchronized(lock) {
            if (id == tabs.activeId()) return
            val previous = tabs.active()
            if (!tabs.select(id)) return
            previous.draft = mutableUiState.value.command
            mutableUiState.update { it.copy(command = tabs.active().draft, ctrlArmed = false, altArmed = false) }
            refreshLocked()
        }
    }

    fun renameTab(id: String, title: String) {
        synchronized(lock) {
            if (tabs.rename(id, title)) refreshLocked()
        }
    }

    /** Fecha a aba (a última não fecha) e cancela a sessão que ela mantinha. */
    fun closeTab(id: String) {
        val cancel = synchronized(lock) {
            val wasActive = tabs.activeId() == id
            val result = tabs.close(id)
            if (!result.closed) return
            startJobs.remove(id)?.cancel()
            inputJobs.remove(id)?.cancel()
            if (wasActive) mutableUiState.update { it.copy(command = tabs.active().draft) }
            refreshLocked()
            runtimeUseCases to result.sessionToCancel
        }
        val useCases = cancel.first
        val sessionId = cancel.second
        if (useCases != null && sessionId != null) {
            viewModelScope.launch(Dispatchers.IO) { runCatching { useCases.cancelSession(sessionId) } }
        }
    }

    fun runCommand() {
        val start = synchronized(lock) {
            val activeUseCases = runtimeUseCases ?: return
            val tab = tabs.active()
            val current = mutableUiState.value
            if (!current.runtimeInstalled || tab.isRunning || tab.interactive) return
            val command = current.command.trim()
            if (command.isEmpty()) return
            appendOutputLocked(tab, "\n$ $command\n")
            tab.starting = true
            refreshLocked()
            Triple(activeUseCases, command, tab.id)
        }
        persistCommand(start.second)
        if (start.second == "khalab" || start.second.startsWith("khalab ")) {
            launchLabCommand(start.third, start.second)
        } else {
            launchSession(tabId = start.third, interactive = false, useCases = start.first, command = start.second)
        }
    }

    /** CLI operacional do terminal; usa o mesmo motor, persistência e evidências das futuras telas RED/BLUE. */
    private fun launchLabCommand(tabId: String, command: String) {
        val engineRef = AtomicReference<com.khalinux.runtime.operations.LabOperationEngine?>()
        val sessionRef = AtomicReference<String?>()
        val executionRef = AtomicReference<String?>()
        val job = viewModelScope.launch {
            try {
                val output = withContext(Dispatchers.IO) {
                    val gateway = synchronized(lock) { runtimeGateway }
                        ?: throw IllegalStateException("Service local desconectado")
                    val engine = gateway.operationEngine()
                        ?: throw IllegalStateException("Motor operacional indisponível")
                    engineRef.set(engine)
                    val args = command.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                    when {
                        args.size == 1 || args.getOrNull(1) == "help" ->
                            "KhaLinux Lab CLI\n  khalab list\n  khalab sessions\n  khalab run <operation-id>\n"
                        args.size == 2 && args[1] == "list" ->
                            engine.operations().joinToString("\n") { "${it.id}  |  ${it.title}  |  ${it.executor}" } + "\n"
                        args.size == 2 && args[1] == "sessions" ->
                            engine.sessions().joinToString("\n") { "${it.id}  |  ${it.state}  |  ${it.label}" }
                                .ifBlank { "Nenhuma sessão operacional registrada." } + "\n"
                        args.size == 3 && args[1] == "run" -> {
                            val operation = engine.operation(args[2])
                                ?: throw IllegalArgumentException("Operação não encontrada: ${args[2]}")
                            val labSession = engine.createSession("Terminal: ${operation.id}")
                            sessionRef.set(labSession.id)
                            val queued = engine.submit(labSession.id, operation.id)
                            executionRef.set(queued.id)
                            var result = engine.awaitExecution(
                                queued.id, Duration.ofSeconds(operation.timeoutSeconds.toLong() + 15L)
                            ) ?: throw IllegalStateException("Execução não encontrada")
                            if (result.state == com.khalinux.runtime.operations.LabExecutionState.QUEUED ||
                                result.state == com.khalinux.runtime.operations.LabExecutionState.RUNNING
                            ) {
                                engine.cancel(queued.id)
                                result = engine.awaitExecution(queued.id, Duration.ofSeconds(3)) ?: result
                            }
                            val evidence = engine.evidenceForExecution(queued.id)
                            val stdout = evidence.firstOrNull { it.kind == "process.stdout" }
                                ?.let { engine.evidenceBytes(it.id) }?.let { String(it, Charsets.UTF_8) }.orEmpty()
                            val stderr = evidence.firstOrNull { it.kind == "process.stderr" }
                                ?.let { engine.evidenceBytes(it.id) }?.let { String(it, Charsets.UTF_8) }.orEmpty()
                            buildString {
                                if (stdout.isNotEmpty()) append(stdout)
                                if (stderr.isNotEmpty()) {
                                    if (isNotEmpty() && last() != '\n') append("\n")
                                    append("[stderr]\n").append(stderr)
                                }
                                append("\\n[").append(result.state).append("]")
                                result.exitCode?.let { append(" exit=").append(it) }
                                result.durationMillis?.let { append(" duração=").append(it).append("ms") }
                                result.errorSummary?.let { append("\n").append(it) }
                                if (isEmpty()) append("Operação concluída sem saída textual.")
                                append("\n")
                            }
                        }
                        else -> "Sintaxe inválida. Use: khalab help\n"
                    }
                }
                synchronized(lock) {
                    tabs.get(tabId)?.let { tab ->
                        tab.starting = false
                        appendOutputLocked(tab, output)
                        refreshLocked()
                    }
                }
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable + Dispatchers.IO) {
                    executionRef.get()?.let { engineRef.get()?.cancel(it) }
                }
                throw cancelled
            } catch (error: Exception) {
                synchronized(lock) {
                    tabs.get(tabId)?.let { tab ->
                        tab.starting = false
                        appendOutputLocked(tab, "khalab: ${error.message ?: error.javaClass.simpleName}\n")
                        refreshLocked()
                    }
                }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    sessionRef.get()?.let { engineRef.get()?.closeSession(it) }
                }
            }
        }
        synchronized(lock) { startJobs[tabId] = job }
    }

    fun openInteractiveShell() {
        val start = synchronized(lock) {
            val current = mutableUiState.value
            val useCases = runtimeUseCases ?: return
            val tab = tabs.active()
            if (!current.runtimeInstalled || tab.isRunning) return
            appendOutputLocked(tab, "\n[shell interativo PTY iniciando; TERM=ansi]\n")
            tab.starting = true
            refreshLocked()
            Triple(useCases, current.terminalRows, current.terminalColumns) to tab.id
        }
        launchSession(
            tabId = start.second,
            interactive = true,
            useCases = start.first.first,
            rows = start.first.second,
            columns = start.first.third
        )
    }

    private fun launchSession(
        tabId: String,
        interactive: Boolean,
        useCases: TerminalUseCases,
        command: String? = null,
        rows: Int = 24,
        columns: Int = 80
    ) {
        val job = viewModelScope.launch {
            val createdSessionId = AtomicReference<String?>(null)
            try {
                val sessionId = withContext(Dispatchers.IO) {
                    val id = if (interactive) {
                        useCases.startInteractive(rows, columns, observerFor(useCases, tabId))
                    } else {
                        useCases.startCommand(requireNotNull(command), observerFor(useCases, tabId))
                    }
                    createdSessionId.set(id)
                    id
                }
                synchronized(lock) {
                    val alreadyFinished = completedSessionsBeforeStartReturn.remove(sessionId)
                    val tab = tabs.get(tabId)
                    if (tab == null) {
                        // A aba foi fechada durante a inicialização: a sessão recém-criada não tem dono.
                        viewModelScope.launch(Dispatchers.IO) { runCatching { useCases.cancelSession(sessionId) } }
                        return@synchronized
                    }
                    tab.sessionId = if (alreadyFinished) null else sessionId
                    tab.starting = false
                    tab.interactive = interactive && !alreadyFinished
                    if (interactive) {
                        if (tab.id == tabs.activeId()) mutableUiState.update { it.copy(command = "") } else tab.draft = ""
                    }
                    refreshLocked()
                }
            } catch (cancelled: CancellationException) {
                createdSessionId.get()?.let { sessionId ->
                    withContext(NonCancellable + Dispatchers.IO) {
                        runCatching { useCases.cancelSession(sessionId) }
                    }
                }
                throw cancelled
            } catch (error: Exception) {
                synchronized(lock) {
                    val tab = tabs.get(tabId) ?: return@synchronized
                    tab.starting = false
                    tab.sessionId = null
                    tab.interactive = false
                    appendOutputLocked(tab, "erro ao iniciar sessão: ${error.message ?: error.javaClass.simpleName}\n")
                    refreshLocked()
                }
            }
        }
        synchronized(lock) { startJobs[tabId] = job }
    }

    fun sendInput() {
        val payload = synchronized(lock) {
            val activeUseCases = runtimeUseCases ?: return
            val tab = tabs.active()
            val current = mutableUiState.value
            val sessionId = tab.sessionId ?: return
            if (!tab.interactive || tab.starting || tab.sendingInput) return
            val input = current.command
            if (input.isEmpty()) return
            val ctrl = current.ctrlArmed
            val alt = current.altArmed
            tab.sendingInput = true
            mutableUiState.update { it.copy(ctrlArmed = false, altArmed = false) }
            refreshLocked()
            SendRequest(activeUseCases, sessionId, input, ctrl, alt, tab.id)
        }
        val job = viewModelScope.launch {
            try {
                val sent = withContext(Dispatchers.IO) {
                    // Com Ctrl/Alt armado o texto vira uma tecla modificada: sem Enter automático.
                    val modifierUsed = payload.ctrl || payload.alt
                    val text = TerminalKeys.applyModifiers(payload.text, payload.ctrl, payload.alt)
                    payload.useCases.writeInput(payload.sessionId, if (modifierUsed) text else "$text\n")
                }
                // Entrada interativa pode conter senhas em prompts sem eco: nunca é gravada no histórico.
                synchronized(lock) {
                    val tab = tabs.get(payload.tabId) ?: return@synchronized
                    tab.sendingInput = false
                    if (sent) {
                        if (tab.id == tabs.activeId()) {
                            mutableUiState.update { state ->
                                state.copy(command = if (state.command == payload.text) "" else state.command)
                            }
                        } else if (tab.draft == payload.text) {
                            tab.draft = ""
                        }
                    } else {
                        appendOutputLocked(tab, "\n[entrada não enviada: sessão PTY indisponível]\n")
                    }
                    refreshLocked()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                synchronized(lock) {
                    val tab = tabs.get(payload.tabId) ?: return@synchronized
                    tab.sendingInput = false
                    appendOutputLocked(tab, "\n[erro de entrada: ${error.message ?: error.javaClass.simpleName}]\n")
                    refreshLocked()
                }
            }
        }
        synchronized(lock) { inputJobs[payload.tabId] = job }
    }

    fun toggleCtrl() = mutableUiState.update { it.copy(ctrlArmed = !it.ctrlArmed) }

    fun toggleAlt() = mutableUiState.update { it.copy(altArmed = !it.altArmed) }

    /** Envia uma tecla da barra extra como bytes brutos ao PTY; Ctrl/Alt armados valem para uma única tecla. */
    fun sendKey(key: TerminalKey) {
        val request = synchronized(lock) {
            val activeUseCases = runtimeUseCases ?: return
            val tab = tabs.active()
            val current = mutableUiState.value
            val sessionId = tab.sessionId ?: return
            if (!tab.interactive || tab.starting) return
            mutableUiState.update { it.copy(ctrlArmed = false, altArmed = false) }
            SendRequest(activeUseCases, sessionId, TerminalKeys.encode(key, current.ctrlArmed, current.altArmed), false, false, tab.id)
        }
        viewModelScope.launch(Dispatchers.IO) {
            val sent = runCatching { request.useCases.writeInput(request.sessionId, request.text) }.getOrDefault(false)
            if (!sent) synchronized(lock) {
                tabs.get(request.tabId)?.let { appendOutputLocked(it, "\n[tecla não enviada: sessão PTY indisponível]\n") }
            }
        }
    }

    private class SendRequest(
        val useCases: TerminalUseCases,
        val sessionId: String,
        val text: String,
        val ctrl: Boolean,
        val alt: Boolean,
        val tabId: String
    )

    fun selectTheme(index: Int) {
        mutableUiState.update { it.copy(themeIndex = TerminalThemes.byIndex(index).index) }
        saveTerminalSettings()
    }

    fun updateFontSize(fontSizeSp: Float) {
        mutableUiState.update { it.copy(fontSizeSp = fontSizeSp.coerceIn(10f, 22f)) }
    }

    fun saveTerminalSettings() {
        val state = mutableUiState.value
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                settingsRepository.save(
                    TerminalSettingsEntity(
                        fontSizeSp = state.fontSizeSp,
                        rows = state.terminalRows,
                        columns = state.terminalColumns,
                        themeIndex = state.themeIndex
                    )
                )
            }
        }
    }

    fun adjustTerminalRows(delta: Int) {
        val state = mutableUiState.value
        updateTerminalDimensions((state.terminalRows + delta).coerceIn(12, 60), state.terminalColumns)
    }

    fun adjustTerminalColumns(delta: Int) {
        val state = mutableUiState.value
        updateTerminalDimensions(state.terminalRows, (state.terminalColumns + delta).coerceIn(40, 160))
    }

    private fun updateTerminalDimensions(rows: Int, columns: Int) {
        synchronized(lock) {
            tabs.resizeAll(rows, columns)
            mutableUiState.update { it.copy(terminalRows = rows, terminalColumns = columns) }
            refreshLocked()
            resizeInteractiveTabsLocked(rows, columns)
        }
        saveTerminalSettings()
    }

    /** Pressão de memória do sistema (8.4): só níveis críticos reduzem o histórico das abas em segundo plano. */
    fun onTrimMemory(level: Int) {
        if (com.khalinux.runtime.memory.MemoryPressure.shouldTrimBackgroundTabs(level)) {
            synchronized(lock) { tabs.trimBackground() }
        }
    }

    fun resizeTerminal(rows: Int, columns: Int) = synchronized(lock) {
        tabs.resizeAll(rows, columns)
        publishTerminalSnapshot()
        resizeInteractiveTabsLocked(rows, columns)
    }

    /** O tamanho é global: todas as sessões PTY em execução recebem o novo tamanho. */
    private fun resizeInteractiveTabsLocked(rows: Int, columns: Int) {
        val activeUseCases = runtimeUseCases ?: return
        tabs.all().forEach { tab ->
            val sessionId = tab.sessionId
            if (sessionId != null && tab.interactive) activeUseCases.resize(sessionId, rows, columns)
        }
    }

    /** Cancela a sessão da aba ativa. */
    fun cancelCommand() {
        val (activeUseCases, sessionId) = synchronized(lock) {
            val useCases = runtimeUseCases ?: return
            val id = tabs.active().sessionId ?: return
            useCases to id
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { activeUseCases.cancelSession(sessionId) }
        }
    }

    private fun observerFor(activeUseCases: TerminalUseCases, tabId: String) = RuntimeEventObserver { event ->
        synchronized(lock) {
            if (runtimeUseCases === activeUseCases) handleEventLocked(tabId, event)
        }
    }

    private fun handleEventLocked(tabId: String, event: RuntimeEvent) {
        val tab = tabs.get(tabId) ?: return
        val activeSessionId = tab.sessionId
        when (event) {
            is RuntimeEvent.Started -> {
                if (activeSessionId == null || activeSessionId == event.sessionId) {
                    tab.sessionId = event.sessionId
                    refreshLocked()
                }
            }
            is RuntimeEvent.Stdout -> {
                if (event.sessionId == activeSessionId) appendOutputBytesLocked(tab, event.bytes)
            }
            is RuntimeEvent.Stderr -> {
                if (event.sessionId == activeSessionId) appendOutputBytesLocked(tab, event.bytes)
            }
            is RuntimeEvent.Exited -> {
                if (event.sessionId == activeSessionId) {
                    val result = if (event.timedOut) "timeout" else "exit=${event.exitCode}"
                    finishSessionLocked(tab, event.sessionId, "\n[$result]\n")
                }
            }
            is RuntimeEvent.Failed -> {
                if (event.sessionId == activeSessionId) {
                    finishSessionLocked(tab, event.sessionId, "\n[falha=${event.message}]\n")
                }
            }
        }
    }

    private fun finishSessionLocked(tab: TerminalTab, sessionId: String, message: String) {
        inputJobs.remove(tab.id)?.cancel()
        appendOutputLocked(tab, message)
        if (tab.starting) completedSessionsBeforeStartReturn.add(sessionId)
        tab.sessionId = null
        tab.interactive = false
        tab.sendingInput = false
        refreshLocked()
    }

    fun restoreCommandFromHistory(command: String) {
        updateCommand(command)
    }

    private fun persistCommand(command: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { historyRepository.record(command) }
        }
    }

    /** Desvincula todas as sessões das abas (Service reconectado/perdido) e cancela as tarefas pendentes. */
    private fun dropAllSessionsLocked(message: String?) {
        val running = tabs.clearSessions()
        if (message != null) running.forEach { it.emulator.appendText(message) }
        startJobs.values.forEach { it.cancel() }
        startJobs.clear()
        inputJobs.values.forEach { it.cancel() }
        inputJobs.clear()
        completedSessionsBeforeStartReturn.clear()
        refreshLocked()
    }

    /** Reflete a aba ativa (e a lista de abas) no estado da UI; os campos de sessão derivam sempre da aba. */
    private fun refreshLocked() {
        val tab = tabs.active()
        lastPublishMillis = System.nanoTime() / 1_000_000L
        val snapshot = tab.emulator.snapshot()
        val infos = tabs.infos()
        val canAdd = tabs.canCreate()
        mutableUiState.update {
            it.copy(
                tabs = infos,
                activeTabId = tab.id,
                canAddTab = canAdd,
                sessionId = tab.sessionId,
                isInteractive = tab.interactive,
                isStarting = tab.starting,
                isSendingInput = tab.sendingInput,
                output = snapshot.plainText(),
                terminalSnapshot = snapshot
            )
        }
    }

    /** Mensagens do app: publicadas na hora se a aba é a ativa. */
    private fun appendOutputLocked(tab: TerminalTab, text: String) {
        tab.emulator.appendText(text)
        if (tab.id == tabs.activeId()) publishTerminalSnapshot()
    }

    /** Saída do PTY: o emulador da aba recebe os bytes na hora; o snapshot de UI (só da aba ativa) sai no máximo a cada ~33 ms. */
    private fun appendOutputBytesLocked(tab: TerminalTab, bytes: ByteArray) {
        tab.emulator.append(bytes)
        if (tab.id != tabs.activeId()) return
        val wait = PUBLISH_INTERVAL_MILLIS - (System.nanoTime() / 1_000_000L - lastPublishMillis)
        if (wait <= 0L) {
            publishTerminalSnapshot()
        } else if (publishJob?.isActive != true) {
            publishJob = viewModelScope.launch {
                delay(wait)
                synchronized(lock) { publishTerminalSnapshot() }
            }
        }
    }

    private fun publishTerminalSnapshot() {
        lastPublishMillis = System.nanoTime() / 1_000_000L
        val snapshot = tabs.active().emulator.snapshot()
        mutableUiState.update {
            it.copy(
                output = snapshot.plainText(),
                terminalSnapshot = snapshot
            )
        }
    }

    override fun onCleared() {
        synchronized(lock) {
            val activeUseCases = runtimeUseCases
            val sessionIds = tabs.all().mapNotNull { it.sessionId }
            if (activeUseCases != null && sessionIds.isNotEmpty()) {
                Thread {
                    sessionIds.forEach { sessionId ->
                        try {
                            activeUseCases.cancelSession(sessionId)
                        } catch (_: Exception) {
                            // The service may already have been destroyed with the Activity.
                        }
                    }
                }.apply { isDaemon = true; name = "khalinux-cancel-on-clear" }.start()
            }
            startJobs.values.forEach { it.cancel() }
            startJobs.clear()
            inputJobs.values.forEach { it.cancel() }
            inputJobs.clear()
            completedSessionsBeforeStartReturn.clear()
            runtimeGateway = null
            runtimeUseCases = null
        }
        super.onCleared()
    }
}
