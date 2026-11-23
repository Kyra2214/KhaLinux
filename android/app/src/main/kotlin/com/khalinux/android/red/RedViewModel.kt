package com.khalinux.android.red

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RedUiState(
    val visible: Boolean = false,
    val selectedOperationId: String? = null,
    val session: RedSession? = null,
    val history: List<RedSession> = emptyList(),
    val category: RedCategory? = null,
    val difficulty: RedDifficulty? = null,
    val message: String? = null,
    val portalAddress: String? = null,
    val hotspotInfo: String? = null,
    val networkRunning: Boolean = false
)

class RedViewModel(application: Application) : AndroidViewModel(application) {
    private val store = RedSessionStore(application)
    private val portal = RedPortalServer(
        onEvent = { detail -> recordRuntimeEvent(detail) },
        onAddress = { address -> mutableState.update { it.copy(portalAddress = address) } }
    )
    private val hotspot = RedHotspotController(application) { message, info ->
        mutableState.update { it.copy(message = message, hotspotInfo = info) }
        recordRuntimeEvent(message)
    }
    private val mutableState = MutableStateFlow(RedUiState())
    val uiState: StateFlow<RedUiState> = mutableState.asStateFlow()

    fun open() {
        mutableState.update { it.copy(visible = true, selectedOperationId = null, session = null, message = null) }
        refreshHistory()
    }

    fun close() = mutableState.update { it.copy(visible = false) }
    fun dismissMessage() = mutableState.update { it.copy(message = null) }
    fun filter(category: RedCategory?, difficulty: RedDifficulty?) = mutableState.update { it.copy(category = category, difficulty = difficulty) }
    fun select(id: String) = mutableState.update { it.copy(selectedOperationId = id, message = null) }
    fun backToCatalog() = mutableState.update { it.copy(selectedOperationId = null, session = null, message = null) }

    fun startSession() {
        val operationId = mutableState.value.selectedOperationId ?: return
        if (RedCatalog.find(operationId) == null) return
        val session = RedSession(operationId = operationId, state = RedOperationState.PREPARING)
            .withEvent(RedEventType.SESSION_STARTED, "Sessão de laboratório iniciada.")
        store.save(session)
        mutableState.update { it.copy(session = session, message = "Sessão local criada. A rede só será iniciada após a ação explícita do operador.") }
        refreshHistory()
    }

    fun markStep(stepId: String) {
        val current = mutableState.value.session ?: return
        val operation = RedCatalog.find(current.operationId) ?: return
        if (operation.steps.none { it.id == stepId }) return
        val updated = current.copy(completedSteps = current.completedSteps + stepId)
            .withEvent(RedEventType.STEP_COMPLETED, "Etapa concluída: $stepId", stepId)
        store.save(updated)
        mutableState.update { it.copy(session = updated) }
        refreshHistory()
    }

    fun recordDemoForm() {
        val current = mutableState.value.session ?: return
        val updated = current.withEvent(RedEventType.DEMO_FORM_COMPLETED, "Formulário sintético concluído; nenhum valor digitado foi armazenado.")
        store.save(updated)
        mutableState.update { it.copy(session = updated, message = "Demonstração registrada. Nenhuma senha foi salva ou transmitida.") }
        refreshHistory()
    }

    fun startNetworkLab() {
        if (mutableState.value.session == null) {
            mutableState.update { it.copy(message = "Inicie uma sessão antes de ativar recursos de rede.") }
            return
        }
        try {
            val address = portal.start()
            mutableState.update { it.copy(networkRunning = true, portalAddress = address) }
            val current = mutableState.value.session
            if (current != null) {
                val updated = current.withEvent(RedEventType.PORTAL_STARTED, "Portal HTTP local iniciado.")
                store.save(updated)
                mutableState.update { it.copy(session = updated) }
            }
            hotspot.start()
        } catch (error: Exception) {
            mutableState.update { it.copy(message = "Não foi possível iniciar o portal: " + (error.message ?: "erro desconhecido")) }
            recordRuntimeEvent("Falha ao iniciar o portal local.")
        }
    }

    fun stopNetworkLab() {
        hotspot.stop()
        portal.stop()
        mutableState.update { it.copy(networkRunning = false, portalAddress = null, hotspotInfo = null) }
    }

    private fun recordRuntimeEvent(detail: String) {
        val current = mutableState.value.session ?: return
        val updated = current.withEvent(RedEventType.PORTAL_OPENED, detail)
        store.save(updated)
        mutableState.update { it.copy(session = updated) }
    }

    fun finishSession() {
        stopNetworkLab()
        val current = mutableState.value.session ?: return
        val updated = current.copy(state = RedOperationState.COMPLETED, endedAt = System.currentTimeMillis())
            .withEvent(RedEventType.SESSION_ENDED, "Sessão encerrada pelo operador.")
        store.save(updated)
        mutableState.update { it.copy(session = updated, message = "Sessão encerrada e relatório local atualizado.") }
        refreshHistory()
    }

    fun refreshHistory() {
        viewModelScope.launch(Dispatchers.IO) { mutableState.update { it.copy(history = store.all()) } }
    }

    override fun onCleared() {
        stopNetworkLab()
        super.onCleared()
    }

    fun deleteHistory() {
        store.deleteAll()
        mutableState.update { it.copy(history = emptyList(), session = null, message = "Histórico RED apagado.") }
    }

    fun report(session: RedSession): String {
        val operation = RedCatalog.find(session.operationId)
        return buildString {
            appendLine("# Relatório RED")
            appendLine()
            appendLine("- Operação: ${operation?.title ?: session.operationId}")
            appendLine("- Sessão: ${session.id}")
            appendLine("- Início: ${session.startedAt}")
            appendLine("- Fim: ${session.endedAt ?: "em andamento"}")
            appendLine("- Estado: ${session.state}")
            appendLine("- Etapas concluídas: ${session.completedSteps.size}/${operation?.steps?.size ?: 0}")
            appendLine()
            appendLine("## Eventos")
            session.events.forEach { appendLine("- ${it.timestamp}: ${it.detail}") }
            appendLine()
            appendLine("Relatório local. Nenhuma credencial ou dado pessoal foi incluído.")
        }
    }

    private fun RedSession.withEvent(type: RedEventType, detail: String, stepId: String? = null) =
        copy(events = (events + RedEvent(sessionId = id, type = type, detail = detail, stepId = stepId)).takeLast(300))
}
