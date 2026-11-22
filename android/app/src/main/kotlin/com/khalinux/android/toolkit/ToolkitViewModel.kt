package com.khalinux.android.toolkit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.khalinux.android.domain.TerminalRuntimeGateway
import com.khalinux.android.data.FavoritesProvider
import com.khalinux.runtime.favorites.FavoriteKind
import com.khalinux.runtime.toolkit.ToolStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ToolkitUiState(
    val visible: Boolean = false,
    val available: Boolean = false,
    val tools: List<ToolStatus> = emptyList(),
    /** Ferramenta cujo escopo está sendo confirmado (diálogo aberto). */
    val scopeDialogFor: String? = null,
    val scopeText: String = "",
    val busy: String? = null,
    val message: String? = null,
    val favoriteToolIds: Set<String> = emptySet()
)

/** Estado da tela do Cyber Toolkit: lista por grupo, confirmação de escopo e verificação no aparelho. */
class ToolkitViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(ToolkitUiState())
    val uiState: StateFlow<ToolkitUiState> = mutableState.asStateFlow()

    private val favoritesStore = FavoritesProvider.get(application)
    @Volatile private var gateway: TerminalRuntimeGateway? = null

    fun attach(gateway: TerminalRuntimeGateway) {
        this.gateway = gateway
        if (mutableState.value.visible) refresh()
    }

    fun open() {
        mutableState.update { it.copy(visible = true) }
        refresh()
    }

    fun close() = mutableState.update { it.copy(visible = false, message = null, scopeDialogFor = null) }
    fun dismissMessage() = mutableState.update { it.copy(message = null) }
    fun askScope(toolId: String) = mutableState.update { it.copy(scopeDialogFor = toolId, scopeText = "") }
    fun cancelScope() = mutableState.update { it.copy(scopeDialogFor = null, scopeText = "") }
    fun setScopeText(text: String) = mutableState.update { it.copy(scopeText = text.take(200)) }

    fun toggleFavorite(toolId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { favoritesStore.toggle(FavoriteKind.TOOL, toolId) }
            val favorites = runCatching { favoritesStore.list(FavoriteKind.TOOL).map { it.value }.toSet() }.getOrDefault(emptySet())
            mutableState.update { it.copy(favoriteToolIds = favorites) }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val toolkit = gateway?.toolkit()
            if (toolkit == null) {
                mutableState.update { it.copy(available = false, tools = emptyList()) }
                return@launch
            }
            val tools = toolkit.status()
            val favorites = runCatching { favoritesStore.list(FavoriteKind.TOOL).map { it.value }.toSet() }.getOrDefault(emptySet())
            mutableState.update { it.copy(available = true, tools = tools, favoriteToolIds = favorites) }
        }
    }

    fun confirmScope() {
        val state = mutableState.value
        val id = state.scopeDialogFor ?: return
        val toolkit = gateway?.toolkit() ?: return
        val message = try {
            toolkit.acknowledge(id, state.scopeText)
            null
        } catch (e: IllegalArgumentException) {
            e.message
        }
        mutableState.update { it.copy(scopeDialogFor = if (message == null) null else id, message = message) }
        refresh()
    }

    fun revoke(toolId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            gateway?.toolkit()?.revoke(toolId)
            refresh()
        }
    }

    fun verify(toolId: String) {
        if (mutableState.value.busy != null) return
        val toolkit = gateway?.toolkit() ?: return
        mutableState.update { it.copy(busy = toolId, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val message = try {
                val result = toolkit.verify(toolId)
                if (result.success) null else "Verificação falhou: ${result.output}"
            } catch (e: Exception) {
                e.message ?: e.javaClass.simpleName
            }
            mutableState.update { it.copy(busy = null, message = message) }
            refresh()
        }
    }
}
