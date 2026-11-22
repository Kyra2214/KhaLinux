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
import com.khalinux.android.domain.RootfsInstallObserver
import com.khalinux.android.domain.ProotInstallObserver
import com.khalinux.android.domain.TerminalRuntimeGateway
import com.khalinux.android.domain.TerminalUseCases
import com.khalinux.runtime.RuntimeEvent
import com.khalinux.runtime.TerminalEmulator
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update

private const val DEFAULT_COMMAND = "printf 'KhaLinux\\n'"

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
    val isInstallingRootfs: Boolean = false,
    val rootfsInstallProgress: String? = null,
    val rootfsInstallMessage: String? = null,
    val rootfsInstalled: Boolean = false,
    val prootInstalled: Boolean = false,
    val isInstallingProot: Boolean = false,
    val prootInstallProgress: String? = null,
    val prootInstallMessage: String? = null,
    val terminalSnapshot: TerminalSnapshot = TerminalSnapshot.empty(),
    val commandHistory: List<CommandHistoryEntry> = emptyList(),
    val fontSizeSp: Float = 12f,
    val terminalRows: Int = 24,
    val terminalColumns: Int = 80
) {
    val isRunning: Boolean get() = isStarting || sessionId != null
}

class TerminalViewModel(application: Application) : AndroidViewModel(application) {
    private val lock = Any()
    private val terminalEmulator = TerminalEmulator(rows = 24, columns = 80)
    private val database = CommandHistoryDatabase.getInstance(application)
    private val historyRepository = CommandHistoryRepository(database.commandHistoryDao())
    private val settingsRepository = TerminalSettingsRepository(database.terminalSettingsDao())
    private val mutableUiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = mutableUiState.asStateFlow()

    private var runtimeGateway: TerminalRuntimeGateway? = null
    private var runtimeUseCases: TerminalUseCases? = null
    private var startJob: Job? = null
    private var inputJob: Job? = null
    private val completedSessionsBeforeStartReturn = mutableSetOf<String>()

    init {
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
                        terminalEmulator.resize(rows, columns)
                        mutableUiState.update {
                            it.copy(
                                fontSizeSp = settings.fontSizeSp.coerceIn(10f, 22f),
                                terminalRows = rows,
                                terminalColumns = columns,
                                terminalSnapshot = terminalEmulator.snapshot(),
                                output = terminalEmulator.plainText()
                            )
                        }
                    }
                }
            }
        }
    }

    fun attachRuntime(gateway: TerminalRuntimeGateway) = synchronized(lock) {
        if (runtimeGateway !== gateway && mutableUiState.value.isRunning) {
            appendOutputLocked("\n[sessão interrompida após reconexão do Service]\n")
            startJob?.cancel()
            inputJob?.cancel()
            completedSessionsBeforeStartReturn.clear()
            mutableUiState.update { it.copy(sessionId = null, isInteractive = false, isStarting = false, isSendingInput = false) }
        }
        runtimeGateway = gateway
        runtimeUseCases = TerminalUseCases(gateway)
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

    fun serviceDisconnected() = synchronized(lock) {
        if (mutableUiState.value.sessionId != null || mutableUiState.value.isStarting) {
            appendOutputLocked("\n[Service desconectado; sessão não está mais vinculada]\n")
        }
        startJob?.cancel()
        inputJob?.cancel()
        completedSessionsBeforeStartReturn.clear()
        runtimeGateway = null
        runtimeUseCases = null
        mutableUiState.update {
            it.copy(
                serviceConnected = false,
                runtimeInstalled = false,
                connectionMessage = "Service local desconectado",
                sessionId = null,
                isInteractive = false,
                isStarting = false,
                isSendingInput = false,
                isInstallingRootfs = false,
                rootfsInstallProgress = null,
                isInstallingProot = false,
                prootInstallProgress = null
            )
        }
    }

    fun bindingUnavailable() = synchronized(lock) {
        startJob?.cancel()
        inputJob?.cancel()
        completedSessionsBeforeStartReturn.clear()
        runtimeGateway = null
        runtimeUseCases = null
        mutableUiState.update {
            it.copy(
                serviceConnected = false,
                runtimeInstalled = false,
                connectionMessage = "Não foi possível conectar ao Service local",
                sessionId = null,
                isInteractive = false,
                isStarting = false,
                isSendingInput = false,
                isInstallingRootfs = false,
                rootfsInstallProgress = null,
                isInstallingProot = false,
                prootInstallProgress = null
            )
        }
    }

    fun installRootfs() {
        val gateway = synchronized(lock) {
            val activeGateway = runtimeGateway ?: return
            val current = mutableUiState.value
            if (!current.serviceConnected || current.isInstallingRootfs || current.runtimeInstalled) return
            mutableUiState.update { it.copy(isInstallingRootfs = true, rootfsInstallProgress = "Preparando download…", rootfsInstallMessage = null) }
            activeGateway
        }
        try {
            gateway.installRootfs(object : RootfsInstallObserver {
                override fun onProgress(downloadedBytes: Long, totalBytes: Long?) {
                    val downloadedMb = downloadedBytes / (1024 * 1024)
                    val progress = if (totalBytes != null && totalBytes > 0) {
                        "$downloadedMb / ${totalBytes / (1024 * 1024)} MB"
                    } else {
                        "$downloadedMb MB baixados"
                    }
                    mutableUiState.update { it.copy(rootfsInstallProgress = progress) }
                }

                override fun onInstalled(runtimeReady: Boolean) {
                    mutableUiState.update {
                        it.copy(
                            isInstallingRootfs = false,
                            runtimeInstalled = runtimeReady,
                            rootfsInstalled = true,
                            rootfsInstallProgress = null,
                            rootfsInstallMessage = if (runtimeReady) {
                                "Ambiente Linux instalado e pronto."
                            } else {
                                "RootFS instalado. O binário PRoot ainda precisa ser instalado."
                            }
                        )
                    }
                }

                override fun onError(message: String) {
                    mutableUiState.update {
                        it.copy(
                            isInstallingRootfs = false,
                            rootfsInstallProgress = null,
                            rootfsInstallMessage = "Falha na instalação: $message"
                        )
                    }
                }
            })
        } catch (error: Exception) {
            mutableUiState.update {
                it.copy(
                    isInstallingRootfs = false,
                    rootfsInstallProgress = null,
                    rootfsInstallMessage = "Falha ao iniciar instalação: ${error.message ?: error.javaClass.simpleName}"
                )
            }
        }
    }

    fun installProot() {
        val gateway = synchronized(lock) {
            val activeGateway = runtimeGateway ?: return
            val current = mutableUiState.value
            if (!current.serviceConnected || !current.rootfsInstalled ||
                current.isInstallingProot || current.prootInstalled
            ) return
            mutableUiState.update {
                it.copy(
                    isInstallingProot = true,
                    prootInstallProgress = "Preparando download do PRoot…",
                    prootInstallMessage = null
                )
            }
            activeGateway
        }
        try {
            gateway.installProot(object : ProotInstallObserver {
                override fun onProgress(downloadedBytes: Long, totalBytes: Long?) {
                    val downloadedKb = downloadedBytes / 1024
                    val progress = if (totalBytes != null && totalBytes > 0) {
                        "$downloadedKb / ${totalBytes / 1024} KB"
                    } else {
                        "$downloadedKb KB baixados"
                    }
                    mutableUiState.update { it.copy(prootInstallProgress = progress) }
                }

                override fun onInstalled(runtimeReady: Boolean) {
                    mutableUiState.update {
                        it.copy(
                            isInstallingProot = false,
                            prootInstalled = true,
                            runtimeInstalled = runtimeReady,
                            prootInstallProgress = null,
                            prootInstallMessage = if (runtimeReady) {
                                "PRoot instalado. Ambiente Linux pronto."
                            } else {
                                "PRoot instalado, mas o runtime não iniciou. Verifique o RootFS e os pontos de montagem."
                            }
                        )
                    }
                }

                override fun onError(message: String) {
                    mutableUiState.update {
                        it.copy(
                            isInstallingProot = false,
                            prootInstallProgress = null,
                            prootInstallMessage = "Falha na instalação do PRoot: $message"
                        )
                    }
                }
            })
        } catch (error: Exception) {
            mutableUiState.update {
                it.copy(
                    isInstallingProot = false,
                    prootInstallProgress = null,
                    prootInstallMessage = "Falha ao iniciar instalação do PRoot: ${error.message ?: error.javaClass.simpleName}"
                )
            }
        }
    }

    fun updateCommand(command: String) {
        mutableUiState.update { it.copy(command = command) }
    }

    fun runCommand() {
        val start = synchronized(lock) {
            val activeUseCases = runtimeUseCases ?: return
            val current = mutableUiState.value
            if (!current.runtimeInstalled || current.isRunning || current.isInteractive) return
            val command = current.command.trim()
            if (command.isEmpty()) return
            appendOutputLocked("\n$ $command\n")
            mutableUiState.update { it.copy(isStarting = true) }
            activeUseCases to command
        }
        persistCommand(start.second)
        launchSession(interactive = false, useCases = start.first, command = start.second)
    }

    fun openInteractiveShell() {
        val start = synchronized(lock) {
            val current = mutableUiState.value
            val useCases = runtimeUseCases ?: return
            if (!current.runtimeInstalled || current.isRunning) return
            appendOutputLocked("\n[shell interativo PTY iniciando; TERM=ansi]\n")
            mutableUiState.update { it.copy(isStarting = true) }
            Triple(useCases, current.terminalRows, current.terminalColumns)
        }
        launchSession(
            interactive = true,
            useCases = start.first,
            rows = start.second,
            columns = start.third
        )
    }

    private fun launchSession(
        interactive: Boolean,
        useCases: TerminalUseCases,
        command: String? = null,
        rows: Int = 24,
        columns: Int = 80
    ) {
        startJob = viewModelScope.launch {
            val createdSessionId = AtomicReference<String?>(null)
            try {
                val sessionId = withContext(Dispatchers.IO) {
                    val id = if (interactive) {
                        useCases.startInteractive(rows, columns, observerFor(useCases))
                    } else {
                        useCases.startCommand(requireNotNull(command), observerFor(useCases))
                    }
                    createdSessionId.set(id)
                    id
                }
                synchronized(lock) {
                    val alreadyFinished = completedSessionsBeforeStartReturn.remove(sessionId)
                    mutableUiState.update { state ->
                        state.copy(
                            sessionId = if (alreadyFinished) null else sessionId,
                            isStarting = false,
                            isInteractive = interactive && !alreadyFinished,
                            command = if (interactive) "" else state.command
                        )
                    }
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
                    mutableUiState.update { it.copy(isStarting = false, sessionId = null, isInteractive = false) }
                    appendOutputLocked("erro ao iniciar sessão: ${error.message ?: error.javaClass.simpleName}\n")
                }
            }
        }
    }

    fun sendInput() {
        val payload = synchronized(lock) {
            val activeUseCases = runtimeUseCases ?: return
            val current = mutableUiState.value
            val sessionId = current.sessionId ?: return
            if (!current.isInteractive || current.isStarting || current.isSendingInput) return
            val input = current.command
            if (input.isEmpty()) return
            mutableUiState.update { it.copy(isSendingInput = true) }
            Triple(activeUseCases, sessionId, input)
        }
        inputJob = viewModelScope.launch {
            try {
                val sent = withContext(Dispatchers.IO) {
                    payload.first.writeInput(payload.second, "${payload.third}\n")
                }
                if (sent) persistCommand(payload.third)
                synchronized(lock) {
                    mutableUiState.update { state ->
                        state.copy(
                            command = if (sent && state.command == payload.third) "" else state.command,
                            isSendingInput = false
                        )
                    }
                    if (!sent) appendOutputLocked("\n[entrada não enviada: sessão PTY indisponível]\n")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                synchronized(lock) {
                    mutableUiState.update { it.copy(isSendingInput = false) }
                    appendOutputLocked("\n[erro de entrada: ${error.message ?: error.javaClass.simpleName}]\n")
                }
            }
        }
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
                        columns = state.terminalColumns
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
            terminalEmulator.resize(rows, columns)
            mutableUiState.update {
                it.copy(
                    terminalRows = rows,
                    terminalColumns = columns,
                    terminalSnapshot = terminalEmulator.snapshot(),
                    output = terminalEmulator.plainText()
                )
            }
            val state = mutableUiState.value
            val gateway = runtimeGateway
            val sessionId = state.sessionId
            if (gateway != null && sessionId != null && state.isInteractive) {
                gateway.resize(sessionId, rows, columns)
            }
        }
        saveTerminalSettings()
    }

    fun resizeTerminal(rows: Int, columns: Int) = synchronized(lock) {
        terminalEmulator.resize(rows, columns)
        publishTerminalSnapshot()
        val activeUseCases = runtimeUseCases ?: return
        val sessionId = mutableUiState.value.sessionId ?: return
        if (mutableUiState.value.isInteractive) activeUseCases.resize(sessionId, rows, columns)
    }

    fun cancelCommand() {
        val (activeUseCases, sessionId) = synchronized(lock) {
            val useCases = runtimeUseCases ?: return
            val id = mutableUiState.value.sessionId ?: return
            useCases to id
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { activeUseCases.cancelSession(sessionId) }
        }
    }

    private fun observerFor(activeUseCases: TerminalUseCases) = RuntimeEventObserver { event ->
        synchronized(lock) {
            if (runtimeUseCases === activeUseCases) handleEventLocked(event)
        }
    }

    private fun handleEventLocked(event: RuntimeEvent) {
        val activeSessionId = mutableUiState.value.sessionId
        when (event) {
            is RuntimeEvent.Started -> {
                if (activeSessionId == null || activeSessionId == event.sessionId) {
                    mutableUiState.update { it.copy(sessionId = event.sessionId) }
                }
            }
            is RuntimeEvent.Stdout -> {
                if (event.sessionId == activeSessionId) appendOutputBytesLocked(event.bytes)
            }
            is RuntimeEvent.Stderr -> {
                if (event.sessionId == activeSessionId) appendOutputBytesLocked(event.bytes)
            }
            is RuntimeEvent.Exited -> {
                if (event.sessionId == activeSessionId) {
                    inputJob?.cancel()
                    val result = if (event.timedOut) "timeout" else "exit=${event.exitCode}"
                    appendOutputLocked("\n[$result]\n")
                    if (mutableUiState.value.isStarting) {
                        completedSessionsBeforeStartReturn.add(event.sessionId)
                    }
                    mutableUiState.update { it.copy(sessionId = null, isInteractive = false, isSendingInput = false) }
                }
            }
            is RuntimeEvent.Failed -> {
                if (event.sessionId == activeSessionId) {
                    inputJob?.cancel()
                    appendOutputLocked("\n[falha=${event.message}]\n")
                    if (mutableUiState.value.isStarting) {
                        completedSessionsBeforeStartReturn.add(event.sessionId)
                    }
                    mutableUiState.update { it.copy(sessionId = null, isInteractive = false, isSendingInput = false) }
                }
            }
        }
    }

    fun restoreCommandFromHistory(command: String) {
        updateCommand(command)
    }

    private fun persistCommand(command: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { historyRepository.record(command) }
        }
    }

    private fun appendOutputLocked(text: String) {
        terminalEmulator.appendText(text)
        publishTerminalSnapshot()
    }

    private fun appendOutputBytesLocked(bytes: ByteArray) {
        terminalEmulator.append(bytes)
        publishTerminalSnapshot()
    }

    private fun publishTerminalSnapshot() {
        val snapshot = terminalEmulator.snapshot()
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
            val sessionId = mutableUiState.value.sessionId
            if (activeUseCases != null && sessionId != null) {
                try {
                    activeUseCases.cancelSession(sessionId)
                } catch (_: Exception) {
                    // The service may already have been destroyed with the Activity.
                }
            }
            startJob?.cancel()
            inputJob?.cancel()
            completedSessionsBeforeStartReturn.clear()
            runtimeGateway = null
            runtimeUseCases = null
        }
        super.onCleared()
    }
}
