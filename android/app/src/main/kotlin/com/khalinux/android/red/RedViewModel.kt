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
        if (message.startsWith("O Android encerrou")) {
            portal.stop()
            mutableState.update { it.copy(networkRunning = false, portalAddress = null, hotspotInfo = null) }
        }
        if (message.startsWith("Hotspot local ativado")) {
            mutableState.update { it.copy(networkRunning = true) }
            try {
                val address = portal.start()
                mutableState.update { it.copy(networkRunning = true, portalAddress = address) }
                val current = mutableState.value.session
                if (current != null) {
                    val updated = current.withEvent(RedEventType.PORTAL_STARTED, "Portal local vinculado à interface Wi-Fi do laboratório.")
                    store.save(updated)
                    mutableState.update { it.copy(session = updated) }
                }
            } catch (error: Exception) {
                mutableState.update { it.copy(message = "Hotspot ativo, mas o portal não pôde ser vinculado à rede do laboratório: " + (error.message ?: "erro desconhecido")) }
                recordRuntimeEvent("Portal não iniciado: interface Wi-Fi indisponível.")
            }
        }
    }
    private val mutableState = MutableStateFlow(RedUiState())
    val uiState: StateFlow<RedUiState> = mutableState.asStateFlow()

    init {
        runCatching {
            val json = application.assets.open("operations.json").bufferedReader().use { it.readText() }
            RedCatalog.replace(RedCatalogJson.parse(json))
        }.onFailure { error ->
            mutableState.update { it.copy(message = "Catálogo RED embarcado inválido; usando catálogo interno de contingência: " + (error.message ?: "erro de leitura")) }
        }
    }

    fun open() {
        mutableState.update { it.copy(visible = true, selectedOperationId = null, session = null, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val previous = store.all()
            previous.filter { it.endedAt == null && it.state != RedOperationState.COMPLETED }.forEach { old ->
                val interrupted = old.copy(state = RedOperationState.INTERRUPTED, endedAt = System.currentTimeMillis(),
                    events = (old.events + RedEvent(sessionId = old.id, type = RedEventType.SESSION_ENDED, detail = "Sessão marcada como interrompida na recuperação local.")).takeLast(300))
                store.save(interrupted)
            }
            mutableState.update { it.copy(history = store.all()) }
        }
    }

    fun close() {
        stopNetworkLab()
        mutableState.update { it.copy(visible = false) }
    }
    fun dismissMessage() = mutableState.update { it.copy(message = null) }
    fun filter(category: RedCategory?, difficulty: RedDifficulty?) = mutableState.update { it.copy(category = category, difficulty = difficulty) }
    fun select(id: String) = mutableState.update { it.copy(selectedOperationId = id, message = null) }
    fun backToCatalog() {
        stopNetworkLab()
        mutableState.update { it.copy(selectedOperationId = null, session = null, message = null) }
    }

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
        mutableState.update { it.copy(message = "Solicitando hotspot local ao Android...") }
        recordRuntimeEvent("Operador solicitou hotspot local.")
        hotspot.start()
    }

    fun stopNetworkLab() {
        hotspot.stop()
        portal.stop()
        mutableState.update { it.copy(networkRunning = false, portalAddress = null, hotspotInfo = null) }
    }

    private fun recordRuntimeEvent(detail: String) {
        val current = mutableState.value.session ?: return
        val type = when {
            detail.startsWith("Operador solicitou hotspot") -> RedEventType.HOTSPOT_REQUESTED
            detail.startsWith("Hotspot local ativado") -> RedEventType.HOTSPOT_READY
            detail.startsWith("Servidor local iniciado") -> RedEventType.PORTAL_STARTED
            detail.startsWith("Portal de treinamento acessado") -> RedEventType.PORTAL_OPENED
            detail.startsWith("Participante concluiu") -> RedEventType.DEMO_FORM_COMPLETED
            detail.startsWith("Permissão simulada") -> RedEventType.PERMISSION_DEMO
            detail.startsWith("Participante abriu") -> RedEventType.STEP_OPENED
            detail.startsWith("Sessão") || detail.startsWith("Hotspot local liberado") -> RedEventType.SESSION_ENDED
            else -> RedEventType.ERROR
        }
        val nextState = when (type) {
            RedEventType.HOTSPOT_READY, RedEventType.PORTAL_STARTED -> RedOperationState.WAITING_PARTICIPANT
            RedEventType.PORTAL_OPENED, RedEventType.DEMO_FORM_COMPLETED -> RedOperationState.ACTIVE
            else -> current.state
        }
        val updated = current.copy(state = nextState).withEvent(type, detail)
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
