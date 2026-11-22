package com.khalinux.android.packages

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.khalinux.android.domain.TerminalRuntimeGateway
import com.khalinux.runtime.install.InstallCancelledException
import com.khalinux.runtime.packages.PackageCategory
import com.khalinux.runtime.packages.PackageHistoryEntry
import com.khalinux.runtime.packages.PackageInstallEvent
import com.khalinux.runtime.packages.PackageInstallStep
import com.khalinux.runtime.packages.PackageManifest
import com.khalinux.runtime.packages.PackageQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.khalinux.runtime.packages.PackageManager as KhaPackageManager

data class PackageRow(val manifest: PackageManifest, val installedVersion: String?, val updateTo: String?)

data class PackagesUiState(
    val visible: Boolean = false,
    val available: Boolean = false,
    val query: String = "",
    val category: PackageCategory? = null,
    val categories: List<Pair<PackageCategory, Int>> = emptyList(),
    val rows: List<PackageRow> = emptyList(),
    val updateCount: Int = 0,
    val showHistory: Boolean = false,
    val history: List<PackageHistoryEntry> = emptyList(),
    /** Descrição da operação em andamento (nulo se ociosa). */
    val busy: String? = null,
    val message: String? = null
)

/**
 * Estado da tela de pacotes. As operações do [KhaPackageManager] bloqueiam e rodam em Dispatchers.IO;
 * uma única operação por vez; cancelamento cooperativo.
 */
class PackagesViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(PackagesUiState())
    val uiState: StateFlow<PackagesUiState> = mutableState.asStateFlow()

    @Volatile private var gateway: TerminalRuntimeGateway? = null
    @Volatile private var cancelRequested = false

    fun attach(gateway: TerminalRuntimeGateway) {
        this.gateway = gateway
        if (mutableState.value.visible) refresh()
    }

    fun open() {
        mutableState.update { it.copy(visible = true) }
        refresh()
    }

    fun close() = mutableState.update { it.copy(visible = false, message = null) }
    fun setQuery(text: String) { mutableState.update { it.copy(query = text) }; refresh() }
    fun setCategory(category: PackageCategory?) { mutableState.update { it.copy(category = category) }; refresh() }
    fun toggleHistory() { mutableState.update { it.copy(showHistory = !it.showHistory) }; refresh() }
    fun dismissMessage() = mutableState.update { it.copy(message = null) }
    fun cancel() { cancelRequested = true }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val manager = gateway?.packageManager()
            if (manager == null) {
                mutableState.update { it.copy(available = false, rows = emptyList(), categories = emptyList()) }
                return@launch
            }
            val state = mutableState.value
            val installed = manager.installed().associate { it.name to it.version }
            val updates = manager.availableUpdates().associate { it.name to it.candidateVersion }
            val rows = manager.search(PackageQuery(text = state.query, category = state.category))
                .map { PackageRow(it, installed[it.name], updates[it.name]) }
            mutableState.update {
                it.copy(
                    available = true,
                    categories = manager.categories(),
                    rows = rows,
                    updateCount = updates.size,
                    history = if (it.showHistory) manager.history(50) else emptyList()
                )
            }
        }
    }

    fun install(name: String) = runOperation("instalar $name") { it.install(listOf(name), ::isCancelled, ::publish) }
    fun update(name: String?) = runOperation("atualizar ${name ?: "tudo"}") {
        it.update(name?.let { n -> listOf(n) }, ::isCancelled, ::publish)
    }
    fun remove(name: String) = runOperation("remover $name") { it.remove(name, ::publish) }

    private fun isCancelled() = cancelRequested

    private fun publish(event: PackageInstallEvent) {
        val label = buildString {
            append(event.packageName ?: "")
            if (event.total > 0) append(" (${event.index}/${event.total})")
            append(" · ")
            append(
                when (event.step) {
                    PackageInstallStep.RESOLVING -> "resolvendo dependências"
                    PackageInstallStep.UPDATING_INDEX -> "atualizando índice APT"
                    PackageInstallStep.DOWNLOADING -> "baixando" + (event.totalBytes?.let { " ${event.bytes * 100 / it}%" } ?: "")
                    PackageInstallStep.VERIFYING -> "verificando integridade"
                    PackageInstallStep.EXTRACTING -> "extraindo"
                    PackageInstallStep.INSTALLING -> "instalando"
                    PackageInstallStep.RECORDED -> "concluído"
                }
            )
        }
        mutableState.update { it.copy(busy = label) }
    }

    private fun runOperation(description: String, block: (KhaPackageManager) -> Unit) {
        if (mutableState.value.busy != null) return
        val manager = gateway?.packageManager() ?: return
        cancelRequested = false
        mutableState.update { it.copy(busy = description, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val message = try {
                block(manager)
                null
            } catch (e: InstallCancelledException) {
                "Operação cancelada."
            } catch (e: Exception) {
                e.message ?: e.javaClass.simpleName
            }
            mutableState.update { it.copy(busy = null, message = message) }
            refresh()
        }
    }
}
