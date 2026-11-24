package com.khalinux.android.red

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
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
    val portalEndpoint: RedPortalEndpoint? = null,
    val tlsPreparationUrl: String? = null,
    val tlsCaFingerprint: String? = null,
    val tlsBrowserTrustObserved: Boolean = false,
    val systemHotspotMode: Boolean = false,
    val systemSettingsLaunchPending: Boolean = false,
    val upstreamInternetAvailable: Boolean = false,
    val upstreamInternetValidated: Boolean = false,
    val upstreamTransport: String? = null,
    val upstreamMessage: String? = null,
    val portalConfiguration: RedPortalConfiguration = RedPortalConfiguration(),
    val participantObservation: RedParticipantObservation? = null,
    val hotspotInfo: String? = null,
    val hotspotSnapshot: RedHotspotSnapshot? = null,
    val networkRunning: Boolean = false,
    val networkState: RedNetworkState = RedNetworkState.IDLE,
    val portalMode: RedPortalMode = RedPortalMode.LOCAL_LOGIN,
    val requests: List<RedRequestObservation> = emptyList(),
    val latestSubmission: RedSyntheticSubmission? = null,
)

class RedViewModel(application: Application) : AndroidViewModel(application) {
    private val store = RedSessionStore(application)
    private val appLifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_STOP -> {
                if (!mutableState.value.systemSettingsLaunchPending) {
                    interruptActiveSession("Aplicativo foi para segundo plano ou a tela foi bloqueada; rede e portal foram encerrados.")
                }
            }
            Lifecycle.Event.ON_START -> {
                if (mutableState.value.systemSettingsLaunchPending) {
                    mutableState.update {
                        it.copy(
                            systemSettingsLaunchPending = false,
                            message = "Retorno das Configurações do Android. Ative o hotspot do sistema (se ainda não estiver ativo) e toque em Detectar hotspot."
                        )
                    }
                }
            }
            else -> Unit
        }
    }
    private val tlsManager = RedLocalTlsManager(application)
    private val tlsPreparation = RedTlsPreparationServer(
        onEvent = { detail -> recordRuntimeEvent(detail) },
        port = RedPortalServer.PREPARATION_PORT,
        onCertificateDownloaded = { remote ->
            recordStructuredEvent(
                RedEventType.TLS_CA_CERTIFICATE_DOWNLOADED,
                "O navegador do participante solicitou o certificado público da CA de laboratório.",
                source = "tls-preparation",
                remoteAddress = remote.takeIf { it.isNotBlank() }
            )
        }
    )
    private val portal = RedPortalServer(
        onEvent = { detail -> recordRuntimeEvent(detail) },
        onAddress = { address ->
            val previousState = mutableState.value
            val portalWasActive = previousState.networkState in setOf(
                RedNetworkState.HOTSPOT_READY,
                RedNetworkState.STARTING_SERVER,
                RedNetworkState.WAITING_PARTICIPANT,
                RedNetworkState.PARTICIPANT_ACTIVE
            )
            mutableState.update { current ->
                current.copy(
                    portalEndpoint = address?.let {
                        RedPortalEndpoint(it, null, current.portalConfiguration.requestedPort)
                    },
                    networkState = if (address == null && portalWasActive) RedNetworkState.ERROR else current.networkState,
                    networkRunning = if (address == null && portalWasActive) true else current.networkRunning
                )
            }
            if (address == null && portalWasActive) {
                tlsPreparation.stop()
                runCatching { tlsManager.clearEphemeralServerIdentity() }
                if (previousState.systemHotspotMode) {
                    systemHotspot.stopMonitoring()
                } else {
                    hotspot.stop()
                }
                mutableState.update {
                    it.copy(
                        networkState = RedNetworkState.ERROR,
                        networkRunning = false,
                        portalEndpoint = null,
                        tlsPreparationUrl = null,
                        tlsCaFingerprint = null,
                        tlsBrowserTrustObserved = false,
                        message = if (previousState.systemHotspotMode) {
                            "Portal encerrado inesperadamente. O hotspot do Android permanece ativo; tente detectar novamente ou desligue-o nas Configurações."
                        } else {
                            "Portal encerrado inesperadamente; hotspot local liberado. Inicie uma nova sessão para continuar."
                        }
                    )
                }
            }
        },
        onRequest = { observation -> recordRequest(observation) },
        onSyntheticSubmission = { submission -> recordSyntheticSubmission(submission) },
        onCookieConsent = { consent -> recordStructuredEvent(RedEventType.COOKIE_CONSENT_RECORDED, consent.summary(), source = "browser-cookie-consent", cookieConsent = consent) },
        onWebPermission = { observation ->
            val eventType = when (observation.state) {
                RedWebPermissionState.REQUESTED -> RedEventType.WEB_PERMISSION_REQUESTED
                RedWebPermissionState.GRANTED -> RedEventType.WEB_PERMISSION_GRANTED
                RedWebPermissionState.DENIED -> RedEventType.WEB_PERMISSION_DENIED
                RedWebPermissionState.CANCELLED -> RedEventType.WEB_PERMISSION_CANCELLED
                RedWebPermissionState.UNAVAILABLE -> RedEventType.WEB_PERMISSION_UNAVAILABLE
                RedWebPermissionState.REVOKED -> RedEventType.WEB_PERMISSION_REVOKED
            }
            val resource = observation.resource.name.lowercase()
            val detail = when (observation.state) {
                RedWebPermissionState.REQUESTED -> "Navegador iniciou solicitação de $resource."
                RedWebPermissionState.GRANTED -> "Navegador concedeu $resource; nenhum conteúdo foi enviado ao laboratório."
                RedWebPermissionState.DENIED -> "Navegador negou $resource."
                RedWebPermissionState.CANCELLED -> "Solicitação de $resource foi cancelada."
                RedWebPermissionState.UNAVAILABLE -> "$resource indisponível neste contexto/origem do navegador."
                RedWebPermissionState.REVOKED -> "Permissão de $resource foi revogada no navegador."
            }
            recordStructuredEvent(
                eventType,
                detail,
                source = "browser-permission",
                webPermissionResource = observation.resource,
                webPermissionState = observation.state
            )
        },
        configuredPort = RedPortalConfiguration().requestedPort,
        onTlsHandshake = { remote ->
            mutableState.update { it.copy(tlsBrowserTrustObserved = true) }
            recordStructuredEvent(
                RedEventType.TLS_HANDSHAKE_OBSERVED,
                "Handshake TLS concluído pelo participante após o vínculo da sessão; a origem HTTPS foi aceita nesta conexão.",
                source = "tls-server",
                remoteAddress = remote.takeIf { it.isNotBlank() }
            )
        }
    )
    private val hotspot = RedHotspotController(application) { snapshot -> handleHotspotSnapshot(snapshot) }
    private val systemHotspot = RedSystemHotspotController(application) { upstream ->
        mutableState.update {
            it.copy(
                upstreamInternetAvailable = upstream.internetAvailable,
                upstreamInternetValidated = upstream.validated,
                upstreamTransport = upstream.transport,
                upstreamMessage = upstream.message
            )
        }
        recordStructuredEvent(
            RedEventType.UPSTREAM_STATUS_CHANGED,
            upstream.message,
            source = "system-tethering"
        )
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
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
        viewModelScope.launch(Dispatchers.IO) { recoverInterruptedSessions() }
    }

    private fun handleHotspotSnapshot(snapshot: RedHotspotSnapshot) {
        val info = buildList {
            snapshot.ssid?.let { add("SSID: $it") }
            snapshot.passphrase?.let { add("Senha: $it") }
            snapshot.security?.let { add("Segurança: $it") }
            snapshot.band?.let { add("Banda: $it") }
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
                    snapshot.state == RedNetworkState.DISCOVERING_INTERFACE ||
                    snapshot.state == RedNetworkState.STARTING_SERVER ||
                    snapshot.state == RedNetworkState.WAITING_PARTICIPANT ||
                    snapshot.state == RedNetworkState.PARTICIPANT_ACTIVE
            )
        }
        val eventType = when (snapshot.state) {
            RedNetworkState.REQUESTING_HOTSPOT -> RedEventType.HOTSPOT_REQUESTED
            RedNetworkState.DISCOVERING_INTERFACE -> RedEventType.HOTSPOT_INTERFACE_DISCOVERY
            RedNetworkState.HOTSPOT_READY -> RedEventType.HOTSPOT_READY
            RedNetworkState.IDLE -> RedEventType.HOTSPOT_STOPPED
            RedNetworkState.ERROR -> RedEventType.HOTSPOT_FAILED
            else -> RedEventType.ERROR
        }
        recordStructuredEvent(
            eventType,
            snapshot.message,
            source = if (mutableState.value.systemHotspotMode) "android-system-tethering" else "android-local-only",
            route = snapshot.interfaceName,
            httpStatus = snapshot.androidFailureCode
        )
        if (snapshot.state == RedNetworkState.IDLE) {
            portal.stop()
            tlsPreparation.stop()
            if (mutableState.value.systemHotspotMode) systemHotspot.stopMonitoring()
            runCatching { tlsManager.clearEphemeralServerIdentity() }
            mutableState.update {
                it.copy(
                    networkRunning = false, portalEndpoint = null, tlsPreparationUrl = null,
                    tlsCaFingerprint = null, tlsBrowserTrustObserved = false, hotspotInfo = null,
                    hotspotSnapshot = null, systemHotspotMode = false, systemSettingsLaunchPending = false,
                    upstreamInternetAvailable = false, upstreamInternetValidated = false,
                    upstreamTransport = null, upstreamMessage = null
                )
            }
        }
        if (snapshot.state == RedNetworkState.HOTSPOT_READY) {
            mutableState.update { it.copy(networkState = RedNetworkState.STARTING_SERVER) }
            try {
                val hotspotAddress = snapshot.ipv4Address
                    ?: error("O Android não disponibilizou o IPv4 do hotspot para emitir o certificado TLS.")
                val tlsMaterial = tlsManager.prepare(hotspotAddress)
                val caUrl = tlsPreparation.start(hotspotAddress, tlsMaterial.caCertificateDer, tlsMaterial.caFingerprintSha256)
                mutableState.update { it.copy(tlsPreparationUrl = caUrl, tlsCaFingerprint = tlsMaterial.caFingerprintSha256, tlsBrowserTrustObserved = false) }
                val address = portal.startHttps(hotspotAddress, mutableState.value.portalConfiguration.mode, tlsMaterial.sslContext)
                mutableState.update {
                    it.copy(
                        networkRunning = true,
                        portalEndpoint = RedPortalEndpoint(address, portal.boundInterface, portal.boundPort ?: mutableState.value.portalConfiguration.requestedPort),
                        networkState = RedNetworkState.WAITING_PARTICIPANT,
                        message = "Portal HTTPS local iniciado. Instale a CA pública no aparelho B e confira a impressão digital antes de abrir o endereço HTTPS."
                    )
                }
            } catch (error: Exception) {
                portal.stop()
                tlsPreparation.stop()
                runCatching { tlsManager.clearEphemeralServerIdentity() }
                if (mutableState.value.systemHotspotMode) {
                    systemHotspot.stopMonitoring()
                } else {
                    hotspot.stop()
                }
                mutableState.update {
                    it.copy(
                        message = if (mutableState.value.systemHotspotMode) {
                            "O portal não pôde ser vinculado. O hotspot do Android permanece ativo: " + (error.message ?: "erro desconhecido")
                        } else {
                            "O hotspot local foi liberado porque o portal não pôde ser vinculado: " + (error.message ?: "erro desconhecido")
                        },
                        networkRunning = false,
                        portalEndpoint = null,
                        tlsPreparationUrl = null,
                        tlsCaFingerprint = null,
                        tlsBrowserTrustObserved = false,
                        networkState = RedNetworkState.ERROR
                    )
                }
                recordStructuredEvent(
                    RedEventType.PORTAL_ERROR,
                    "Portal não iniciado: " + (error.message ?: "erro desconhecido"),
                    source = "portal"
                )
            }
        }
    }

    fun open() {
        val currentSession = mutableState.value.session
        if (currentSession != null && currentSession.endedAt == null &&
            currentSession.state != RedOperationState.COMPLETED &&
            currentSession.state != RedOperationState.INTERRUPTED
        ) {
            mutableState.update { it.copy(visible = true, message = null) }
            refreshHistory()
            return
        }
        mutableState.update {
            it.copy(
                visible = true,
                selectedOperationId = null,
                session = null,
                message = null,
                networkState = RedNetworkState.IDLE,
                hotspotSnapshot = null,
                requests = emptyList(),
                latestSubmission = null
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

    fun hideForBlue() {
        mutableState.update { it.copy(visible = false) }
    }

    fun close() {
        interruptCurrentSession()
        mutableState.update { it.copy(visible = false) }
    }
    fun dismissMessage() = mutableState.update { it.copy(message = null) }
    fun filter(category: RedCategory?, difficulty: RedDifficulty?) =
        mutableState.update { it.copy(category = category, difficulty = difficulty) }
    fun select(id: String) = mutableState.update { it.copy(selectedOperationId = id, message = null) }
    fun setPortalMode(mode: RedPortalMode) {
        mutableState.update { it.copy(portalConfiguration = it.portalConfiguration.copy(mode = mode)) }
        portal.setMode(mode)
    }
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
            RedLiveEvidenceStore.updateSession(interrupted)
            mutableState.update { it.copy(session = interrupted, history = store.all()) }
        }
    }

    fun startSession() {
        val operationId = mutableState.value.selectedOperationId ?: return
        if (RedCatalog.find(operationId) == null) return
        val session = RedSession(
            operationId = operationId,
            state = RedOperationState.PREPARING,
            portalMode = mutableState.value.portalConfiguration.mode
        ).withEvent(RedEventType.SESSION_STARTED, "Sessão de laboratório iniciada.", source = "session")
        store.save(session)
        RedLiveEvidenceStore.begin(session)
        mutableState.update {
            it.copy(
                session = session,
                requests = emptyList(),
                latestSubmission = null,
                networkState = RedNetworkState.IDLE,
                hotspotSnapshot = null,
                participantObservation = null,
                portalEndpoint = null,
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
        if (mutableState.value.systemHotspotMode) {
            mutableState.update { it.copy(message = "O hotspot do sistema permanece selecionado. Desligue-o nas Configurações e escolha Voltar para hotspot local isolado antes de usar LocalOnlyHotspot.") }
            return
        }
        if (mutableState.value.session == null) {
            mutableState.update { it.copy(message = "Inicie uma sessão antes de ativar recursos de rede.") }
            return
        }
        mutableState.update {
            it.copy(
                systemHotspotMode = false,
                systemSettingsLaunchPending = false,
                message = "Solicitando hotspot local isolado ao Android...",
                networkState = RedNetworkState.REQUESTING_HOTSPOT,
                requests = emptyList()
            )
        }
        hotspot.start()
    }

    fun startSystemHotspotLab() {
        if (mutableState.value.session == null) {
            mutableState.update { it.copy(message = "Inicie uma sessão antes de ativar recursos de rede.") }
            return
        }
        if (mutableState.value.networkRunning || (mutableState.value.networkState != RedNetworkState.IDLE && !(mutableState.value.systemHotspotMode && mutableState.value.networkState == RedNetworkState.ERROR))) {
            mutableState.update { it.copy(message = "Encerre a rede atual antes de configurar o hotspot do sistema.") }
            return
        }
        mutableState.update {
            it.copy(
                systemHotspotMode = true,
                systemSettingsLaunchPending = true,
                networkState = RedNetworkState.REQUESTING_HOTSPOT,
                networkRunning = false,
                message = "Desative os dados móveis e mantenha o hotspot do sistema desligado. Abra as Configurações, ligue o hotspot, retorne e detecte a interface; depois de B conectar, ative os dados móveis sem desligar o hotspot."
            )
        }
        if (!systemHotspot.openSystemHotspotSettings()) {
            systemHotspot.stopMonitoring()
            mutableState.update {
                it.copy(
                    systemHotspotMode = false,
                    systemSettingsLaunchPending = false,
                    networkState = RedNetworkState.ERROR,
                    message = "Não foi possível abrir as Configurações de hotspot do Android."
                )
            }
            recordStructuredEvent(RedEventType.ERROR, "Falha ao abrir Configurações de tethering do Android.", source = "system-tethering")
        } else {
            recordStructuredEvent(
                RedEventType.HOTSPOT_REQUESTED,
                "Operador abriu as Configurações do hotspot do sistema; ativação será feita pelo Android.",
                source = "system-tethering"
            )
        }
    }

    fun detectSystemHotspot() {
        if (!mutableState.value.systemHotspotMode) {
            mutableState.update { it.copy(message = "Abra primeiro as Configurações do hotspot do sistema.") }
            return
        }
        handleHotspotSnapshot(systemHotspot.discoverSystemHotspot())
    }

    fun stopNetworkLab() {
        val systemMode = mutableState.value.systemHotspotMode
        mutableState.update { it.copy(networkState = RedNetworkState.STOPPING) }
        hotspot.stop()
        if (systemMode) systemHotspot.stopMonitoring()
        portal.stop()
        tlsPreparation.stop()
        runCatching { tlsManager.clearEphemeralServerIdentity() }
        mutableState.update {
            it.copy(
                networkRunning = false,
                portalEndpoint = null,
                tlsPreparationUrl = null,
                tlsCaFingerprint = null,
                tlsBrowserTrustObserved = false,
                hotspotInfo = null,
                networkState = RedNetworkState.IDLE,
                systemHotspotMode = systemMode,
                systemSettingsLaunchPending = false,
                upstreamInternetAvailable = false,
                upstreamInternetValidated = false,
                upstreamTransport = null,
                upstreamMessage = null,
                message = if (systemMode) {
                    "Portal encerrado. O hotspot do Android permanece ligado; desligue-o nas Configurações quando terminar."
                } else {
                    "Hotspot local isolado e portal encerrados."
                }
            )
        }
    }

    fun resetSystemHotspotMode() {
        if (mutableState.value.networkRunning) {
            mutableState.update { it.copy(message = "Encerre primeiro o portal antes de trocar o modo de hotspot.") }
            return
        }
        systemHotspot.stopMonitoring()
        mutableState.update {
            it.copy(
                systemHotspotMode = false,
                systemSettingsLaunchPending = false,
                upstreamInternetAvailable = false,
                upstreamInternetValidated = false,
                upstreamTransport = null,
                upstreamMessage = null,
                networkState = RedNetworkState.IDLE,
                message = "Modo local isolado selecionado. Confirme que o hotspot do sistema foi desligado nas Configurações antes de iniciar LocalOnlyHotspot."
            )
        }
    }

    private fun recordRuntimeEvent(detail: String) {
        val type = when {
            detail.startsWith("Operador solicitou hotspot") -> RedEventType.HOTSPOT_REQUESTED
            detail.startsWith("Hotspot local ativado") -> RedEventType.HOTSPOT_READY
            detail.startsWith("Hotspot local liberado") || detail.startsWith("O Android encerrou") -> RedEventType.HOTSPOT_STOPPED
            detail.startsWith("Servidor local iniciado") || detail.startsWith("Servidor HTTP local iniciado") || detail.startsWith("Servidor HTTPS local iniciado") || detail.startsWith("Endpoint HTTP de preparação TLS iniciado") -> RedEventType.PORTAL_STARTED
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
        bytesReceived: Int? = null,
        cookieConsent: RedCookieConsent? = null,
        webPermissionResource: RedWebPermissionResource? = null,
        webPermissionState: RedWebPermissionState? = null,
        responseSent: Boolean? = null,
        rejectionReason: String? = null,
        serverFailure: Boolean? = null,
        eventTimestamp: Long? = null,
        safeHeaders: Map<String, String> = emptyMap(),
        remoteAddress: String? = null
    ) {
        val current = mutableState.value.session ?: return
        val nextState = when (type) {
            RedEventType.HOTSPOT_READY, RedEventType.PORTAL_STARTED -> RedOperationState.WAITING_PARTICIPANT
            RedEventType.PORTAL_OPENED, RedEventType.HTTP_REQUEST_RECEIVED, RedEventType.HTTP_REQUEST_REJECTED,
            RedEventType.HTTP_RESPONSE_SENT, RedEventType.FORM_SUBMITTED,
            RedEventType.DEMO_FORM_COMPLETED, RedEventType.FORM_REJECTED,
            RedEventType.NAVIGATION, RedEventType.REDIRECT, RedEventType.COOKIE_CONSENT_RECORDED,
            RedEventType.WEB_PERMISSION_REQUESTED, RedEventType.WEB_PERMISSION_GRANTED,
            RedEventType.WEB_PERMISSION_DENIED, RedEventType.WEB_PERMISSION_CANCELLED,
            RedEventType.WEB_PERMISSION_UNAVAILABLE, RedEventType.WEB_PERMISSION_REVOKED,
            RedEventType.ANDROID_PERMISSION_REQUESTED, RedEventType.ANDROID_PERMISSION_GRANTED,
            RedEventType.ANDROID_PERMISSION_DENIED, RedEventType.ANDROID_PERMISSION_REVOKED -> RedOperationState.ACTIVE
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
            bytesReceived = bytesReceived,
            cookieConsent = cookieConsent,
            webPermissionResource = webPermissionResource,
            webPermissionState = webPermissionState,
            responseSent = responseSent,
            rejectionReason = rejectionReason,
            serverFailure = serverFailure,
            eventTimestamp = eventTimestamp,
            safeHeaders = safeHeaders,
            remoteAddress = remoteAddress
        )
        store.save(updated)
        RedLiveEvidenceStore.updateSession(updated)
        mutableState.update { it.copy(session = updated) }
        if (type == RedEventType.PORTAL_OPENED || type == RedEventType.HTTP_REQUEST_RECEIVED) {
            mutableState.update { it.copy(networkState = RedNetworkState.PARTICIPANT_ACTIVE) }
        }
        refreshHistory()
    }

    private fun recordSyntheticSubmission(submission: RedSyntheticSubmission) {
        // Os valores só existem no estado de UI da sessão atual; nunca são gravados no SessionStore.
        mutableState.update { it.copy(latestSubmission = submission) }
        RedLiveEvidenceStore.updateSubmission(submission)
        recordStructuredEvent(
            type = if (submission.accepted) RedEventType.FORM_SUBMITTED else RedEventType.FORM_REJECTED,
            detail = submission.message,
            source = "local-form"
        )
    }

    fun recordRequest(observation: RedRequestObservation) {
        val current = mutableState.value
        val updatedRequests = (current.requests + observation).takeLast(100)
        val previousPeer = current.participantObservation
        val peer = if (previousPeer != null && previousPeer.remoteAddress == observation.remoteAddress) {
            previousPeer.copy(lastRequestAt = observation.timestamp, requestCount = previousPeer.requestCount + 1)
        } else {
            RedParticipantObservation(
                remoteAddress = observation.remoteAddress,
                firstRequestAt = observation.timestamp,
                lastRequestAt = observation.timestamp,
                requestCount = 1
            )
        }
        mutableState.update { it.copy(requests = updatedRequests, participantObservation = peer) }
        RedLiveEvidenceStore.updateRequests(updatedRequests)

        val detail = "${observation.method} ${observation.path} → HTTP ${observation.statusCode} · ${observation.bodyBytes} bytes"
        if (observation.method != "UNKNOWN" && observation.rejectionReason != "invalid_request_line") {
            recordStructuredEvent(
                RedEventType.HTTP_REQUEST_RECEIVED,
                "Requisição recebida: $detail",
                source = "http",
                httpMethod = observation.method,
                route = observation.path,
                httpStatus = observation.statusCode,
                bytesReceived = observation.bodyBytes,
                responseSent = observation.responseSent,
                rejectionReason = observation.rejectionReason,
                serverFailure = observation.serverFailure,
                eventTimestamp = observation.timestamp,
                safeHeaders = observation.safeHeaders,
                remoteAddress = observation.remoteAddress
            )
        }
        observation.rejectionReason?.let { reason ->
            recordStructuredEvent(
                RedEventType.HTTP_REQUEST_REJECTED,
                "Requisição rejeitada ($reason): $detail",
                source = "http",
                httpMethod = observation.method,
                route = observation.path,
                httpStatus = observation.statusCode,
                bytesReceived = observation.bodyBytes,
                responseSent = observation.responseSent,
                rejectionReason = reason,
                serverFailure = observation.serverFailure,
                eventTimestamp = observation.timestamp,
                remoteAddress = observation.remoteAddress
            )
        }
        if (observation.serverFailure) {
            recordStructuredEvent(
                RedEventType.HTTP_SERVER_FAILURE,
                "Falha interna do servidor: $detail",
                source = "http",
                httpMethod = observation.method,
                route = observation.path,
                httpStatus = observation.statusCode,
                bytesReceived = observation.bodyBytes,
                responseSent = observation.responseSent,
                serverFailure = true,
                eventTimestamp = observation.timestamp,
                remoteAddress = observation.remoteAddress
            )
        }
        if (observation.responseSent) {
            recordStructuredEvent(
                RedEventType.HTTP_RESPONSE_SENT,
                "Resposta enviada: HTTP ${observation.statusCode} para ${observation.method} ${observation.path}.",
                source = "http",
                httpMethod = observation.method,
                route = observation.path,
                httpStatus = observation.statusCode,
                responseSent = true,
                rejectionReason = observation.rejectionReason,
                serverFailure = observation.serverFailure,
                eventTimestamp = observation.timestamp,
                remoteAddress = observation.remoteAddress
            )
        }
        if (observation.path.startsWith("/go/") && observation.statusCode in 300..399) {
            recordStructuredEvent(RedEventType.REDIRECT, "Redirecionamento local executado: ${observation.path}.", source = "http", route = observation.path, httpStatus = observation.statusCode, eventTimestamp = observation.timestamp)
        } else if (observation.method == "GET" && observation.path != "/" && observation.statusCode in 200..299) {
            recordStructuredEvent(RedEventType.NAVIGATION, "Navegação local: ${observation.path}.", source = "http", route = observation.path, httpStatus = observation.statusCode, eventTimestamp = observation.timestamp)
        }
    }

    fun finishSession() {
        stopNetworkLab()
        val current = mutableState.value.session ?: return
        val updated = current.copy(state = RedOperationState.COMPLETED, endedAt = System.currentTimeMillis())
            .withEvent(RedEventType.SESSION_ENDED, "Sessão encerrada pelo operador.", source = "session")
        store.save(updated)
        RedLiveEvidenceStore.clear()
        mutableState.update { it.copy(session = updated, latestSubmission = null, requests = emptyList(), participantObservation = null, message = "Sessão encerrada e relatório local atualizado.") }
        refreshHistory()
    }

    fun refreshHistory() {
        viewModelScope.launch(Dispatchers.IO) { mutableState.update { it.copy(history = store.all()) } }
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(appLifecycleObserver)
        interruptActiveSession("Área RED encerrada; sessão marcada como interrompida.")
        RedLiveEvidenceStore.clear()
        super.onCleared()
    }

    private fun recoverInterruptedSessions() {
        val now = System.currentTimeMillis()
        store.all().filter { session ->
            session.endedAt == null &&
                session.state !in setOf(RedOperationState.COMPLETED, RedOperationState.INTERRUPTED)
        }.forEach { session ->
            val sequence = (session.events.maxOfOrNull { it.sequence } ?: 0) + 1
            val event = RedEvent(
                sessionId = session.id,
                type = RedEventType.SESSION_INTERRUPTED,
                timestamp = now,
                detail = "Processo anterior encerrou sem finalizar a sessão; recuperada como interrompida.",
                sequence = sequence,
                source = "recovery"
            )
            store.save(
                session.copy(
                    state = RedOperationState.INTERRUPTED,
                    endedAt = now,
                    events = (session.events + event).takeLast(300)
                )
            )
        }
        mutableState.update { it.copy(history = store.all()) }
    }

    private fun interruptActiveSession(reason: String) {
        val current = mutableState.value.session
        stopNetworkLab()
        if (current == null || current.endedAt != null ||
            current.state in setOf(RedOperationState.COMPLETED, RedOperationState.INTERRUPTED)
        ) return
        val now = System.currentTimeMillis()
        val sequence = (current.events.maxOfOrNull { it.sequence } ?: 0) + 1
        val event = RedEvent(
            sessionId = current.id,
            type = RedEventType.SESSION_INTERRUPTED,
            timestamp = now,
            detail = reason,
            sequence = sequence,
            source = "lifecycle"
        )
        val updated = current.copy(
            state = RedOperationState.INTERRUPTED,
            endedAt = now,
            events = (current.events + event).takeLast(300)
        )
        store.save(updated)
        mutableState.update {
            it.copy(
                session = updated,
                latestSubmission = null,
                requests = emptyList(),
                participantObservation = null,
                message = reason
            )
        }
        RedLiveEvidenceStore.clear()
        refreshHistory()
    }

    fun clearSensitivePanelData() {
        mutableState.update { it.copy(latestSubmission = null) }
        RedLiveEvidenceStore.updateSubmission(null)
        mutableState.update { it.copy(message = "Valores sintéticos temporários removidos do painel.") }
    }

    fun deleteSession(sessionId: String) {
        val current = mutableState.value.session
        if (current?.id == sessionId && current.endedAt == null) {
            mutableState.update { it.copy(message = "Encerre a sessão antes de excluí-la do histórico.") }
            return
        }
        store.delete(sessionId)
        mutableState.update {
            it.copy(
                session = if (it.session?.id == sessionId) null else it.session,
                message = "Sessão e relatório local removidos do histórico."
            )
        }
        refreshHistory()
    }

    fun deleteHistory() {
        stopNetworkLab()
        store.deleteAll()
        RedLiveEvidenceStore.clear()
        mutableState.update { it.copy(history = emptyList(), session = null, latestSubmission = null, requests = emptyList(), participantObservation = null, message = "Histórico RED apagado.") }
    }

    private fun markdownText(value: String): String = value
        .replace("\\", "\\\\")
        .replace("`", "\\`")
        .replace("*", "\\*")
        .replace("_", "\\_")
        .replace("[", "\\[")
        .replace("]", "\\]")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\r", " ")
        .replace("\n", " ")

    private fun markdownCode(value: String): String =
        "`" + value.replace("`", "\\`").replace("\r", " ").replace("\n", " ") + "`"
    fun report(session: RedSession): String {
        val app = getApplication<Application>()
        val appVersion = runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0).let { info ->
                "${info.versionName ?: "versão não informada"} (${info.longVersionCode})"
            }
        }.getOrDefault("não disponível")

        val operation = RedCatalog.find(session.operationId)
        val orderedEvents = session.events.sortedBy { it.sequence }
        val requestEvents = orderedEvents.filter { it.type == RedEventType.HTTP_REQUEST_RECEIVED || (it.type == RedEventType.HTTP_REQUEST_REJECTED && it.rejectionReason == "invalid_request_line") }
        val permissionEvents = orderedEvents.filter {
            it.type in setOf(
                RedEventType.WEB_PERMISSION_REQUESTED,
                RedEventType.WEB_PERMISSION_GRANTED,
                RedEventType.WEB_PERMISSION_DENIED,
                RedEventType.WEB_PERMISSION_CANCELLED,
                RedEventType.WEB_PERMISSION_UNAVAILABLE,
                RedEventType.WEB_PERMISSION_REVOKED,
                RedEventType.ANDROID_PERMISSION_REQUESTED,
                RedEventType.ANDROID_PERMISSION_GRANTED,
                RedEventType.ANDROID_PERMISSION_DENIED,
                RedEventType.ANDROID_PERMISSION_REVOKED
            )
        }
        return buildString {
            appendLine("# Relatório RED")
            appendLine()
            appendLine("- Aplicativo: KhaLinux $appVersion")
            appendLine("- Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("- Gerado em: ${System.currentTimeMillis()}")
            appendLine("- Operação: ${operation?.title ?: session.operationId}")
            appendLine("- Sessão: ${session.id}")
            appendLine("- Início: ${session.startedAt}")
            appendLine("- Fim: ${session.endedAt ?: "em andamento"}")
            appendLine("- Estado: ${session.state}")
            appendLine("- Modo do portal: ${if (session.portalMode == RedPortalMode.LOCAL_LOGIN) "login local" else "acesso direto"}")
            appendLine("- Etapas concluídas: ${session.completedSteps.size}/${operation?.steps?.size ?: 0}")
            appendLine("- Requisições HTTP observadas: ${requestEvents.size}")
            appendLine("- Requisições rejeitadas: ${orderedEvents.count { it.type == RedEventType.HTTP_REQUEST_REJECTED }}")
            appendLine("- Respostas HTTP registradas: ${orderedEvents.count { it.type == RedEventType.HTTP_RESPONSE_SENT }}")
            appendLine("- Falhas internas registradas: ${orderedEvents.count { it.type == RedEventType.HTTP_SERVER_FAILURE }}")
            appendLine("- Eventos de permissões/recursos: ${permissionEvents.size}")
            appendLine("- Escolhas de cookies registradas: ${orderedEvents.count { it.type == RedEventType.COOKIE_CONSENT_RECORDED }}")
            appendLine()
            appendLine("## Linha do tempo")
            orderedEvents.forEach { event ->
                appendLine("- #${event.sequence} ${event.timestamp} [${event.type}] ${markdownText(event.detail)}")
                if (event.httpMethod != null || event.route != null) {
                    appendLine("  - HTTP: ${event.httpMethod?.let(::markdownCode) ?: markdownCode("?")} ${event.route?.let(::markdownCode) ?: markdownCode("?")} · status ${event.httpStatus ?: "?"} · bytes ${event.bytesReceived ?: "?"}")
                }
                event.responseSent?.let { appendLine("  - Resposta HTTP enviada: $it") }
                event.rejectionReason?.let { appendLine("  - Motivo da rejeição: $it") }
                if (event.serverFailure == true) appendLine("  - Falha interna do servidor: sim")
                event.remoteAddress?.let { appendLine("  - Origem remota: ${markdownCode(it)}") }
                event.safeHeaders.forEach { (name, value) -> appendLine("  - Header permitido ${markdownCode(name)}: ${markdownCode(value)}") }
                event.cookieChoice?.let { choice ->
                    appendLine("  - Consentimento de cookies: $choice")
                    appendLine("  - Categorias: análise=${event.cookieAnalytics}, preferências=${event.cookiePreferences}, personalização=${event.cookiePersonalization}")
                }
                event.webPermissionResource?.let { resource ->
                    appendLine("  - Permissão web: $resource · estado ${event.webPermissionState ?: "não informado"}")
                }
                event.source?.let { appendLine("  - Origem do evento: ${markdownCode(it)}") }
            }
            appendLine()
            appendLine("## Limitações e validação")
            val caDownloadObserved = orderedEvents.any { it.type == RedEventType.TLS_CA_CERTIFICATE_DOWNLOADED }
            val tlsHandshakeObserved = orderedEvents.any { it.type == RedEventType.TLS_HANDSHAKE_OBSERVED }
            appendLine("- O portal RED utiliza HTTPS local; download da CA pública observado: ${if (caDownloadObserved) "sim" else "não observado"}.")
            appendLine("- Handshake TLS concluído pelo navegador: ${if (tlsHandshakeObserved) "observado nesta sessão" else "não observado nesta sessão"}. A ausência do evento não prova que a CA não foi instalada.")
            val upstreamEvent = orderedEvents.lastOrNull { it.type == RedEventType.UPSTREAM_STATUS_CHANGED }
            appendLine("- Upstream do aparelho A: ${upstreamEvent?.detail ?: "não observado nesta sessão"}. Isso não confirma, isoladamente, acesso externo do B.")
            appendLine("- Permissões web dependem de contexto seguro e suporte do navegador; indisponibilidade não equivale a recusa.")
            appendLine("- Testes automatizados, compilação e testes físicos são acumulados para a etapa final do roadmap e não são declarados aprovados neste relatório.")
            appendLine("- A ausência de evento significa somente que o servidor não recebeu esse evento durante esta sessão.")

            appendLine("## Privacidade do relatório")
            appendLine("- Não inclui campos de formulários, códigos de acesso, cookies, tokens, arquivos, áudio, vídeo ou coordenadas.")
            appendLine("- Valores sintéticos do formulário existiram somente em memória durante a sessão.")
            appendLine("- Requisições listadas são as recebidas pelo servidor local; não representam captura de tráfego externo.")
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
        bytesReceived: Int? = null,
        cookieConsent: RedCookieConsent? = null,
        webPermissionResource: RedWebPermissionResource? = null,
        webPermissionState: RedWebPermissionState? = null,
        responseSent: Boolean? = null,
        rejectionReason: String? = null,
        serverFailure: Boolean? = null,
        eventTimestamp: Long? = null,
        safeHeaders: Map<String, String> = emptyMap(),
        remoteAddress: String? = null
    ) = copy(events = (events + RedEvent(
        sessionId = id,
        type = type,
        detail = detail,
        timestamp = eventTimestamp ?: System.currentTimeMillis(),
        stepId = stepId,
        sequence = (events.maxOfOrNull { it.sequence } ?: 0) + 1,
        source = source,
        httpMethod = httpMethod,
        route = route,
        httpStatus = httpStatus,
        bytesReceived = bytesReceived,
        cookieChoice = cookieConsent?.choice,
        cookieAnalytics = cookieConsent?.analytics,
        cookiePreferences = cookieConsent?.preferences,
        cookiePersonalization = cookieConsent?.personalization,
        webPermissionResource = webPermissionResource,
        webPermissionState = webPermissionState,
        responseSent = responseSent,
        rejectionReason = rejectionReason,
        serverFailure = serverFailure,
        safeHeaders = safeHeaders,
        remoteAddress = remoteAddress
    )).takeLast(300))
}
