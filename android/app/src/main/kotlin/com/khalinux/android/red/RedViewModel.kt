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
    val hotspotSnapshot: RedHotspotSnapshot? = null,
    val networkRunning: Boolean = false,
    val networkState: RedNetworkState = RedNetworkState.IDLE,
    val portalMode: RedPortalMode = RedPortalMode.LOCAL_LOGIN,
    val requests: List<RedRequestObservation> = emptyList()
)

class RedViewModel(application: Application) : AndroidViewModel(application) {
    private val store = RedSessionStore(application)
    private val portal = RedPortalServer(
        onEvent = { detail -> recordRuntimeEvent(detail) },
        onAddress = { address -> mutableState.update { it.copy(portalAddress = address) } }
    )
    private val hotspot = RedHotspotController(application) { snapshot ->
        val info = buildList {
            snapshot.ssid?.let { add("SSID: $it") }
            snapshot.passphrase?.let { add("Senha: $it") }
            snapshot.interfaceName?.let { add("Interface: $it") }
            snapshot.ipv4Address?.let { add("IPv4: $it") }
            snapshot.androidFailureCode?.let { add("Código Android: $it") }
        }.joinToString("\n").ifBlank { "O Android não disponibilizou detalhes adicionais da rede." }
        mutableState.update {
            it.copy(
                message = snapshot.message,
                hotspotInfo = info,
                hotspotSnapshot = snapshot,
                networkState = snapshot.state,
                networkRunning = snapshot.state == RedNetworkState.HOTSPOT_READY ||
                    snapshot.state == RedNetworkState.STARTING_SERVER ||
                    snapshot.state == RedNetworkState.WAITING_PARTICIPANT ||
                    snapshot.state == RedNetworkState.PARTICIPANT_ACTIVE
            )
        }
        val eventType = when (snapshot.state) {
            RedNetworkState.REQUESTING_HOTSPOT -> RedEventType.HOTSPOT_REQUESTED
            RedNetworkState.HOTSPOT_READY -> RedEventType.HOTSPOT_READY
            RedNetworkState.IDLE -> RedEventType.HOTSPOT_STOPPED
            RedNetworkState.ERROR -> RedEventType.HOTSPOT_FAILED
            else -> RedEventType.ERROR
        }
        recordStructuredEvent(
            eventType,
            snapshot.message,
            source = "android",
            route = snapshot.interfaceName,
            httpStatus = snapshot.androidFailureCode
        )
        if (snapshot.state == RedNetworkState.IDLE) {
            portal.stop()
            mutableState.update {
                it.copy(networkRunning = false, portalAddress = null, hotspotInfo = null, hotspotSnapshot = null)
            }
        }
        if (snapshot.state == RedNetworkState.HOTSPOT_READY) {
            mutableState.update { it.copy(networkState = RedNetworkState.STARTING_SERVER) }
            try {
                val address = portal.start()
                mutableState.update {
                    it.copy(networkRunning = true, portalAddress = address, networkState = RedNetworkState.WAITING_PARTICIPANT)
                }
                recordStructuredEvent(
                    RedEventType.PORTAL_STARTED,
                    "Servidor local preparado após a criação do hotspot.",
                    source = "portal"
                )
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(
                        message = "Hotspot ativo, mas o portal não pôde ser vinculado à rede do laboratório: " +
                            (error.message ?: "erro desconhecido"),
                        networkState = RedNetworkState.ERROR
                    )
                }
                recordStructuredEvent(
                    RedEventType.PORTAL_ERROR,
                    "Portal não iniciado: interface Wi-Fi indisponível.",
                    source = "portal"
                )
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
        mutableState.update {
            it.copy(
                visible = true,
                selectedOperationId = null,
                session = null,
                message = null,
                networkState = RedNetworkState.IDLE,
                hotspotSnapshot = null,
                requests = emptyList()
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val previous = store.all()
            previous.filter { it.endedAt == null && it.state != RedOperationState.COMPLETED }.forEach { old ->
                val interrupted = old.copy(
                    state = RedOperationState.INTERRUPTED,
                    endedAt = System.currentTimeMillis(),
                    events = (old.events + RedEvent(
                        sessionId = old.id,
                        type = RedEventType.SESSION_ENDED,
                        detail = "Sessão marcada como interrompida na recuperação local.",
                        sequence = (old.events.maxOfOrNull { it.sequence } ?: 0) + 1,
                        source = "recovery"
                    )).takeLast(300)
                )
                store.save(interrupted)
            }
            mutableState.update { it.copy(history = store.all()) }
        }
    }

    fun close() {
        interruptCurrentSession()
        mutableState.update { it.copy(visible = false) }
    }
    fun dismissMessage() = mutableState.update { it.copy(message = null) }
    fun filter(category: RedCategory?, difficulty: RedDifficulty?) =
        mutableState.update { it.copy(category = category, difficulty = difficulty) }
    fun select(id: String) = mutableState.update { it.copy(selectedOperationId = id, message = null) }
    fun setPortalMode(mode: RedPortalMode) = mutableState.update { it.copy(portalMode = mode) }
    fun backToCatalog() {
        interruptCurrentSession()
        mutableState.update { it.copy(selectedOperationId = null, session = null, message = null) }
    }

    private fun interruptCurrentSession() {
        stopNetworkLab()
        val current = mutableState.value.session ?: return
        if (current.endedAt == null) {
            val interrupted = current.copy(state = RedOperationState.INTERRUPTED, endedAt = System.currentTimeMillis())
                .withEvent(RedEventType.SESSION_ENDED, "Sessão interrompida antes do encerramento normal.", source = "session")
            store.save(interrupted)
            mutableState.update { it.copy(session = interrupted, history = store.all()) }
        }
    }

    fun startSession() {
        val operationId = mutableState.value.selectedOperationId ?: return
        if (RedCatalog.find(operationId) == null) return
        val session = RedSession(operationId = operationId, state = RedOperationState.PREPARING)
            .withEvent(RedEventType.SESSION_STARTED, "Sessão de laboratório iniciada.", source = "session")
        store.save(session)
        mutableState.update {
            it.copy(
                session = session,
                requests = emptyList(),
                networkState = RedNetworkState.IDLE,
                hotspotSnapshot = null,
                message = "Sessão local criada. A rede só será iniciada após a ação explícita do operador."
            )
        }
        refreshHistory()
    }

    fun markStep(stepId: String) {
        val current = mutableState.value.session ?: return
        val operation = RedCatalog.find(current.operationId) ?: return
        if (operation.steps.none { it.id == stepId }) return
        val updated = current.copy(completedSteps = current.completedSteps + stepId)
            .withEvent(RedEventType.STEP_COMPLETED, "Etapa concluída: $stepId", stepId, source = "tutorial")
        store.save(updated)
        mutableState.update { it.copy(session = updated) }
        refreshHistory()
    }

    /** Mantido para compatibilidade com a primeira UI; a submissão HTTP real será ligada ao portal no RED-5. */
    fun recordDemoForm() {
        recordStructuredEvent(
            RedEventType.DEMO_FORM_COMPLETED,
            "Formulário demonstrativo concluído; esta ação manual não representa recebimento de campos pelo servidor.",
            source = "manual"
        )
    }

    fun startNetworkLab() {
        if (mutableState.value.session == null) {
            mutableState.update { it.copy(message = "Inicie uma sessão antes de ativar recursos de rede.") }
            return
        }
        mutableState.update {
            it.copy(message = "Solicitando hotspot local ao Android...", networkState = RedNetworkState.REQUESTING_HOTSPOT, requests = emptyList())
        }
        recordStructuredEvent(RedEventType.HOTSPOT_REQUESTED, "Operador solicitou hotspot local.", source = "android")
        hotspot.start()
    }

    fun stopNetworkLab() {
        mutableState.update { it.copy(networkState = RedNetworkState.STOPPING) }
        hotspot.stop()
        portal.stop()
        mutableState.update {
            it.copy(
                networkRunning = false,
                portalAddress = null,
                hotspotInfo = null,
                networkState = RedNetworkState.IDLE
            )
        }
    }

    private fun recordRuntimeEvent(detail: String) {
        val type = when {
            detail.startsWith("Operador solicitou hotspot") -> RedEventType.HOTSPOT_REQUESTED
            detail.startsWith("Hotspot local ativado") -> RedEventType.HOTSPOT_READY
            detail.startsWith("Hotspot local liberado") || detail.startsWith("O Android encerrou") -> RedEventType.HOTSPOT_STOPPED
            detail.startsWith("Servidor local iniciado") -> RedEventType.PORTAL_STARTED
            detail.startsWith("Servidor do portal local encerrado") -> RedEventType.PORTAL_STOPPED
            detail.startsWith("Portal de treinamento acessado") -> RedEventType.PORTAL_OPENED
            detail.startsWith("Participante concluiu") -> RedEventType.DEMO_FORM_COMPLETED
            detail.startsWith("Permissão simulada") -> RedEventType.PERMISSION_DEMO
            detail.startsWith("Participante abriu") -> RedEventType.STEP_OPENED
            detail.startsWith("Falha") || detail.startsWith("Não foi possível") -> RedEventType.ERROR
            else -> RedEventType.ERROR
        }
        recordStructuredEvent(type, detail, source = "runtime")
        when (type) {
            RedEventType.HOTSPOT_READY -> mutableState.update { it.copy(networkState = RedNetworkState.HOTSPOT_READY) }
            RedEventType.HOTSPOT_STOPPED -> mutableState.update { it.copy(networkState = RedNetworkState.IDLE) }
            RedEventType.PORTAL_OPENED -> mutableState.update { it.copy(networkState = RedNetworkState.PARTICIPANT_ACTIVE) }
            RedEventType.ERROR -> mutableState.update { it.copy(networkState = RedNetworkState.ERROR) }
            else -> Unit
        }
    }

    fun recordStructuredEvent(
        type: RedEventType,
        detail: String,
        stepId: String? = null,
        source: String? = null,
        httpMethod: String? = null,
        route: String? = null,
        httpStatus: Int? = null,
        bytesReceived: Int? = null
    ) {
        val current = mutableState.value.session ?: return
        val nextState = when (type) {
            RedEventType.HOTSPOT_READY, RedEventType.PORTAL_STARTED -> RedOperationState.WAITING_PARTICIPANT
            RedEventType.PORTAL_OPENED, RedEventType.HTTP_REQUEST_RECEIVED, RedEventType.FORM_SUBMITTED,
            RedEventType.DEMO_FORM_COMPLETED, RedEventType.NAVIGATION -> RedOperationState.ACTIVE
            else -> current.state
        }
        val updated = current.copy(state = nextState).withEvent(
            type = type,
            detail = detail,
            stepId = stepId,
            source = source,
            httpMethod = httpMethod,
            route = route,
            httpStatus = httpStatus,
            bytesReceived = bytesReceived
        )
        store.save(updated)
        mutableState.update { it.copy(session = updated) }
        if (type == RedEventType.PORTAL_OPENED || type == RedEventType.HTTP_REQUEST_RECEIVED) {
            mutableState.update { it.copy(networkState = RedNetworkState.PARTICIPANT_ACTIVE) }
        }
        refreshHistory()
    }

    fun recordRequest(observation: RedRequestObservation) {
        mutableState.update { it.copy(requests = (it.requests + observation).takeLast(100)) }
        recordStructuredEvent(
            type = RedEventType.HTTP_REQUEST_RECEIVED,
            detail = "${observation.method} ${observation.path} → HTTP ${observation.statusCode} · ${observation.bodyBytes} bytes",
            source = "http",
            httpMethod = observation.method,
            route = observation.path,
            httpStatus = observation.statusCode,
            bytesReceived = observation.bodyBytes
        )
    }

    fun finishSession() {
        stopNetworkLab()
        val current = mutableState.value.session ?: return
        val updated = current.copy(state = RedOperationState.COMPLETED, endedAt = System.currentTimeMillis())
            .withEvent(RedEventType.SESSION_ENDED, "Sessão encerrada pelo operador.", source = "session")
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
            session.events.sortedBy { it.sequence }.forEach { event ->
                appendLine("- #${event.sequence} ${event.timestamp}: ${event.detail}")
                if (event.httpMethod != null || event.route != null) {
                    appendLine("  - HTTP: ${event.httpMethod ?: "?"} ${event.route ?: "?"} · status ${event.httpStatus ?: "?"} · bytes ${event.bytesReceived ?: "?"}")
                }
            }
            appendLine()
            appendLine("Relatório local. Não inclui valores de formulários, credenciais, tokens, cookies, arquivos ou localização precisa.")
        }
    }

    private fun RedSession.withEvent(
        type: RedEventType,
        detail: String,
        stepId: String? = null,
        source: String? = null,
        httpMethod: String? = null,
        route: String? = null,
        httpStatus: Int? = null,
        bytesReceived: Int? = null
    ) = copy(events = (events + RedEvent(
        sessionId = id,
        type = type,
        detail = detail,
        stepId = stepId,
        sequence = (events.maxOfOrNull { it.sequence } ?: 0) + 1,
        source = source,
        httpMethod = httpMethod,
        route = route,
        httpStatus = httpStatus,
        bytesReceived = bytesReceived
    )).takeLast(300))
}
