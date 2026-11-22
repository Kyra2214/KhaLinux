package com.khalinux.android.files

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.khalinux.android.domain.TerminalRuntimeGateway
import com.khalinux.runtime.files.FileEntry
import com.khalinux.runtime.labs.LabProgress
import com.khalinux.runtime.reports.ReportBuilder
import com.khalinux.runtime.reports.ReportWriter
import com.khalinux.runtime.files.HomeFileManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Arquivo aberto no editor de texto (caminho relativo ao home; vazio em `newName` = arquivo existente). */
data class OpenFile(val path: String, val content: String, val isNew: Boolean)

data class FilesUiState(
    val visible: Boolean = false,
    val path: String = "",
    val entries: List<FileEntry> = emptyList(),
    val openFile: OpenFile? = null,
    val newFolderDialog: Boolean = false,
    val newName: String = "",
    val renaming: String? = null,
    val confirmDelete: FileEntry? = null,
    val message: String? = null,
    val notice: String? = null,
    /** Resumo de espaço (8.1): total do app e quanto é resíduo removível; nulo se o runtime não está disponível. */
    val storage: String? = null,
    val reclaimableBytes: Long = 0L
)

/** Gerenciador de arquivos do home persistente (`/home/khalinux`), com edição de texto simples. */
class FilesViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = HomeFileManager(File(application.filesDir, "home").apply { mkdirs() })
    private val mutableState = MutableStateFlow(FilesUiState())
    @Volatile private var gateway: TerminalRuntimeGateway? = null
    val uiState: StateFlow<FilesUiState> = mutableState.asStateFlow()

    fun attach(gateway: TerminalRuntimeGateway) {
        this.gateway = gateway
    }

    /** Gera o relatório de progresso (laboratórios e ferramentas) em `reports/` do home. */
    fun generateReport() {
        val activeGateway = gateway
        viewModelScope.launch(Dispatchers.IO) {
            val labsService = runCatching { activeGateway?.labs() }.getOrNull()
            val tools = runCatching { activeGateway?.toolkit()?.status() }.getOrNull()
            if (labsService == null && tools == null) {
                mutableState.update { it.copy(notice = null, message = "Runtime Linux ainda não instalado: nada a reportar") }
                return@launch
            }
            val now = System.currentTimeMillis()
            val content = ReportBuilder.build(
                generatedAtMillis = now,
                labs = labsService?.engine?.catalog?.labs ?: emptyList(),
                progress = runCatching { labsService?.progress() }.getOrNull() ?: LabProgress(),
                tools = tools ?: emptyList()
            )
            val path = ReportWriter(manager).write(now, content)
            mutableState.update {
                if (path == null) it.copy(notice = null, message = "Não foi possível gravar o relatório")
                else it.copy(notice = "Relatório salvo em $path", message = null)
            }
            refresh()
        }
    }

    fun open() {
        mutableState.update { it.copy(visible = true, message = null) }
        refresh()
        refreshStorage()
    }

    fun refreshStorage() {
        val hygiene = gateway?.storage() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val report = runCatching { hygiene.report() }.getOrNull() ?: return@launch
            mutableState.update {
                it.copy(
                    storage = "Espaço usado: ${formatMb(report.totalBytes)} · resíduos removíveis: ${formatMb(report.reclaimableBytes)}",
                    reclaimableBytes = report.reclaimableBytes
                )
            }
        }
    }

    /** Remove só resíduos de instalação/temporários antigos (nunca RootFS, home, notas ou relatórios). */
    fun cleanStorage() {
        val activeGateway = gateway ?: return
        val hygiene = activeGateway.storage() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { hygiene.cleanup(installRunning = activeGateway.isInstallRunning()) }.getOrNull()
            mutableState.update {
                when {
                    result == null -> it.copy(notice = null, message = "Não foi possível limpar o armazenamento")
                    result.removed.isEmpty() && result.skipped.isNotEmpty() -> it.copy(notice = null, message = "Limpeza adiada: ${result.skipped.first()}")
                    else -> it.copy(notice = "Liberado: ${formatMb(result.freedBytes)} (${result.removed.size} itens)", message = null)
                }
            }
            refreshStorage()
        }
    }

    private fun formatMb(bytes: Long): String = String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))

    fun close() = mutableState.update { it.copy(visible = false, openFile = null, newFolderDialog = false, renaming = null, confirmDelete = null, message = null) }

    fun refresh() {
        val path = mutableState.value.path
        viewModelScope.launch(Dispatchers.IO) {
            val entries = manager.list(path)
            mutableState.update {
                if (entries == null) it.copy(path = "", entries = manager.list("") ?: emptyList(), message = "Pasta indisponível")
                else it.copy(entries = entries)
            }
        }
    }

    fun enter(entry: FileEntry) {
        if (entry.directory) {
            mutableState.update { it.copy(path = entry.relativePath, message = null) }
            refresh()
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                val content = manager.readText(entry.relativePath)
                mutableState.update {
                    if (content == null) it.copy(message = "Não é possível abrir: binário, grande demais ou inacessível")
                    else it.copy(openFile = OpenFile(entry.relativePath, content, isNew = false), message = null)
                }
            }
        }
    }

    fun up() {
        val path = mutableState.value.path
        if (path.isEmpty()) return
        mutableState.update { it.copy(path = path.substringBeforeLast('/', ""), message = null) }
        refresh()
    }

    fun newFile() = mutableState.update {
        it.copy(openFile = OpenFile(if (it.path.isEmpty()) "" else it.path + "/", "", isNew = true), message = null)
    }

    fun editOpenFile(path: String, content: String) = mutableState.update { state ->
        state.openFile?.let { state.copy(openFile = it.copy(path = if (it.isNew) path else it.path, content = content)) } ?: state
    }

    fun closeOpenFile() = mutableState.update { it.copy(openFile = null) }

    fun saveOpenFile() {
        val file = mutableState.value.openFile ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val ok = manager.writeText(file.path, file.content)
            mutableState.update {
                if (ok) it.copy(openFile = null, message = null) else it.copy(message = "Não foi possível salvar (nome inválido, pasta inexistente ou arquivo grande)")
            }
            if (ok) refresh()
        }
    }

    fun askNewFolder() = mutableState.update { it.copy(newFolderDialog = true, newName = "") }
    fun cancelNewFolder() = mutableState.update { it.copy(newFolderDialog = false) }
    fun setNewName(name: String) = mutableState.update { it.copy(newName = name.take(255)) }

    fun createFolder() {
        val state = mutableState.value
        val target = if (state.path.isEmpty()) state.newName.trim() else state.path + "/" + state.newName.trim()
        viewModelScope.launch(Dispatchers.IO) {
            val ok = manager.mkdir(target)
            mutableState.update { it.copy(newFolderDialog = false, message = if (ok) null else "Não foi possível criar a pasta") }
            refresh()
        }
    }

    fun askRename(entry: FileEntry) = mutableState.update { it.copy(renaming = entry.relativePath, newName = entry.name) }
    fun cancelRename() = mutableState.update { it.copy(renaming = null) }

    fun confirmRename() {
        val state = mutableState.value
        val source = state.renaming ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val ok = manager.rename(source, state.newName.trim())
            mutableState.update { it.copy(renaming = null, message = if (ok) null else "Não foi possível renomear") }
            refresh()
        }
    }

    fun askDelete(entry: FileEntry) = mutableState.update { it.copy(confirmDelete = entry) }
    fun cancelDelete() = mutableState.update { it.copy(confirmDelete = null) }

    fun confirmDelete() {
        val entry = mutableState.value.confirmDelete ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val ok = manager.delete(entry.relativePath, recursive = entry.directory)
            mutableState.update { it.copy(confirmDelete = null, message = if (ok) null else "Não foi possível apagar") }
            refresh()
        }
    }
}
