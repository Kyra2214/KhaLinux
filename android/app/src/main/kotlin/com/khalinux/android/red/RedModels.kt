package com.khalinux.android.red

import java.util.UUID

enum class RedOperationState { NOT_STARTED, PREPARING, WAITING_PARTICIPANT, ACTIVE, ENDING, COMPLETED, INTERRUPTED }
enum class RedNetworkState { IDLE, REQUESTING_HOTSPOT, HOTSPOT_READY, STARTING_SERVER, DISCOVERING_INTERFACE, WAITING_PARTICIPANT, PARTICIPANT_ACTIVE, STOPPING, ERROR }
enum class RedPortalMode { LOCAL_LOGIN, OPEN_ACCESS }
enum class RedCookieChoice { ACCEPT_ALL, REJECT_OPTIONAL, CUSTOM }
enum class RedWebPermissionResource { CAMERA, MICROPHONE, LOCATION }
enum class RedWebPermissionState { REQUESTED, GRANTED, DENIED, CANCELLED, UNAVAILABLE, REVOKED }
enum class RedAndroidPermissionState { UNKNOWN, GRANTED, DENIED, PERMANENTLY_DENIED, REVOKED }

data class RedUpstreamSnapshot(
    val internetAvailable: Boolean,
    val validated: Boolean,
    val transport: String?,
    val message: String
)

data class RedWebPermissionObservation(
    val resource: RedWebPermissionResource,
    val state: RedWebPermissionState
)

data class RedCookieConsent(
    val choice: RedCookieChoice,
    val analytics: Boolean,
    val preferences: Boolean,
    val personalization: Boolean,
    val recordedAt: Long = System.currentTimeMillis()
) {
    fun summary(): String =
        "Escolha=${choice.name.lowercase()}; análise=${if (analytics) "aceito" else "recusado"}; " +
            "preferências=${if (preferences) "aceito" else "recusado"}; " +
            "personalização=${if (personalization) "aceito" else "recusado"}"
}
data class RedPortalConfiguration(
    val mode: RedPortalMode = RedPortalMode.LOCAL_LOGIN,
    val requestedPort: Int = 8443
)

data class RedPortalEndpoint(
    val url: String,
    val interfaceName: String?,
    val port: Int
)

data class RedParticipantObservation(
    val remoteAddress: String?,
    val firstRequestAt: Long,
    val lastRequestAt: Long,
    val requestCount: Int
)

enum class RedEventType {
    SESSION_STARTED,
    COOKIE_CONSENT_RECORDED,
    ANDROID_PERMISSION_REQUESTED, ANDROID_PERMISSION_GRANTED, ANDROID_PERMISSION_DENIED, ANDROID_PERMISSION_REVOKED,
    HOTSPOT_REQUESTED, HOTSPOT_INTERFACE_DISCOVERY, HOTSPOT_READY, HOTSPOT_STOPPED, HOTSPOT_FAILED,
    PORTAL_STARTING, PORTAL_STARTED, PORTAL_STOPPED, PORTAL_OPENED, PORTAL_ERROR,
    HTTP_REQUEST_RECEIVED, HTTP_REQUEST_REJECTED, HTTP_RESPONSE_SENT, HTTP_SERVER_FAILURE, NAVIGATION, REDIRECT,
    FORM_SUBMITTED, FORM_REJECTED, DEMO_FORM_COMPLETED,
    PERMISSION_REQUESTED, PERMISSION_GRANTED, PERMISSION_DENIED, PERMISSION_CANCELLED,
    WEB_PERMISSION_REQUESTED, WEB_PERMISSION_GRANTED, WEB_PERMISSION_DENIED, WEB_PERMISSION_CANCELLED, WEB_PERMISSION_UNAVAILABLE, WEB_PERMISSION_REVOKED,
    TLS_CA_CERTIFICATE_DOWNLOADED, TLS_HANDSHAKE_OBSERVED, UPSTREAM_STATUS_CHANGED, PARTICIPANT_HEARTBEAT,
    PERMISSION_DEMO, RESOURCE_PREVIEW_STARTED, RESOURCE_PREVIEW_STOPPED,
    FILE_PICKER_OPENED, FILE_SELECTED, STEP_OPENED, STEP_COMPLETED,
    ERROR, SESSION_ENDED, SESSION_INTERRUPTED
}
enum class RedCategory { NETWORKS, WEB, SOCIAL_ENGINEERING, LOCAL_ANALYSIS }
enum class RedDifficulty { BEGINNER, INTERMEDIATE, ADVANCED }

/** Observação de uma requisição local. Valores de formulário nunca pertencem a este modelo persistente. */
data class RedHotspotSnapshot(
    val state: RedNetworkState,
    val message: String,
    val ssid: String? = null,
    val passphrase: String? = null,
    val security: String? = null,
    val band: String? = null,
    val interfaceName: String? = null,
    val ipv4Address: String? = null,
    val androidFailureCode: Int? = null
)

/** Resultado minimizado do formulário de laboratório; nunca contém senha ou credenciais em claro. */
data class RedSyntheticSubmission(
    val timestamp: Long = System.currentTimeMillis(),
    val fields: Map<String, String>,
    val accepted: Boolean,
    val message: String
)

/** Observação de uma requisição local. Valores de formulário nunca pertencem a este modelo persistente. */
data class RedRequestObservation(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val method: String,
    val path: String,
    val statusCode: Int,
    val contentType: String? = null,
    val bodyBytes: Int = 0,
    val remoteAddress: String? = null,
    val safeHeaders: Map<String, String> = emptyMap(),
    val responseSent: Boolean = false,
    val rejectionReason: String? = null,
    val serverFailure: Boolean = false
)

data class RedOperation(
    val id: String,
    val number: Int,
    val title: String,
    val category: RedCategory,
    val difficulty: RedDifficulty,
    val summary: String,
    val objectives: List<String>,
    val prerequisites: List<String>,
    val steps: List<RedTutorialStep>,
    val completionCriteria: List<String>,
    val environment: String
)

data class RedTutorialStep(
    val id: String,
    val title: String,
    val instructions: String
)

data class RedEvent(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val type: RedEventType,
    val timestamp: Long = System.currentTimeMillis(),
    val stepId: String? = null,
    val detail: String,
    val sequence: Int = 0,
    val source: String? = null,
    val httpMethod: String? = null,
    val route: String? = null,
    val httpStatus: Int? = null,
    val bytesReceived: Int? = null,
    val cookieChoice: RedCookieChoice? = null,
    val cookieAnalytics: Boolean? = null,
    val cookiePreferences: Boolean? = null,
    val cookiePersonalization: Boolean? = null,
    val webPermissionResource: RedWebPermissionResource? = null,
    val webPermissionState: RedWebPermissionState? = null,
    val responseSent: Boolean? = null,
    val rejectionReason: String? = null,
    val serverFailure: Boolean? = null,
    val safeHeaders: Map<String, String> = emptyMap(),
    val remoteAddress: String? = null
)

data class RedSession(
    val id: String = UUID.randomUUID().toString(),
    val operationId: String,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val state: RedOperationState = RedOperationState.PREPARING,
    val completedSteps: Set<String> = emptySet(),
    val events: List<RedEvent> = emptyList(),
    val portalMode: RedPortalMode = RedPortalMode.LOCAL_LOGIN
)

object RedCatalog {
    private val builtInOperations = listOf(
        RedOperation(
            id = "red-wifi-public-01",
            number = 1,
            title = "Wi-Fi público: conexão, portal e navegação",
            category = RedCategory.NETWORKS,
            difficulty = RedDifficulty.BEGINNER,
            summary = "Laboratório de dois aparelhos com hotspot próprio, portal HTTPS local, preparação offline de CA e observação do que o servidor realmente recebe.",
            objectives = listOf(
                "Preparar uma rede de laboratório no aparelho operador",
                "Conectar um segundo aparelho, preparar a confiança TLS e abrir o portal HTTPS local",
                "Comparar autenticação local com acesso direto sem login",
                "Observar navegação, requisições e resultados de permissões",
                "Distinguir hotspot local-only de hotspot do sistema para ativação posterior de internet"
            ),
            prerequisites = listOf(
                "Dois aparelhos Android dedicados ou explicitamente autorizados",
                "Wi-Fi habilitado nos dois aparelhos",
                "Dados de teste fictícios preparados para a aula"
            ),
            steps = listOf(
                RedTutorialStep("prepare", "Preparar o operador", "Confirme que os dois aparelhos pertencem ao laboratório e escolha o cenário da aula."),
                RedTutorialStep("hotspot", "Ativar a rede de laboratório", "Solicite ao Android a criação do hotspot local. O sistema informa os parâmetros que o aplicativo pode exibir."),
                RedTutorialStep("system-hotspot", "Preparar internet futura sem trocar a rede", "Se B precisar continuar conectado quando a internet for ativada depois, escolha o hotspot do sistema do Android desde o início. Desative os dados móveis, mantenha o hotspot desligado ao iniciar o modo, ligue-o nas Configurações, conecte B e depois ative os dados móveis no A sem desligar o hotspot. LocalOnlyHotspot é isolado e não recebe internet posteriormente."),
                RedTutorialStep("connect", "Conectar o participante", "No segundo aparelho, abra as configurações Wi-Fi e conecte-se à rede exibida pelo operador."),
                RedTutorialStep("portal", "Abrir o portal local", "Primeiro abra o endereço HTTP de preparação, baixe a CA pública e confira a impressão digital no aparelho A. Depois instale a CA pelas configurações do Android e abra o endereço HTTPS/QR do portal. A confiança depende do navegador."),
                RedTutorialStep("access", "Comparar os modos de acesso", "Percorra o portal com autenticação local fictícia e o modo de acesso direto sem login."),
                RedTutorialStep("navigation", "Explorar navegação", "Abra os links locais e observe no painel as rotas e respostas efetivamente recebidas."),
                RedTutorialStep("permissions", "Observar permissões web", "No navegador do aparelho B, abra Permissões do navegador e escolha separadamente câmera, microfone ou localização. Cada pedido exige ação explícita e contexto HTTPS confiável; se o navegador rejeitar a CA, o recurso fica indisponível."),
                RedTutorialStep("report", "Encerrar e revisar", "Encerre a sessão e revise a linha do tempo e o relatório local.")
            ),
            completionCriteria = listOf(
                "Hotspot solicitado e estado real apresentado",
                "Segundo aparelho conectado e portal HTTPS local acessível após confiança TLS",
                "Modo com login local e modo sem login demonstrados",
                "Requisições locais observadas no painel",
                "Relatório local disponível",
                "Upstream do aparelho A observado sem presumir acesso externo do B"
            ),
            environment = "Dois aparelhos Android em rede de laboratório própria"
        )
    )

    @Volatile private var loadedOperations: List<RedOperation> = builtInOperations
    val operations: List<RedOperation> get() = loadedOperations
    fun replace(operations: List<RedOperation>) { loadedOperations = operations.toList() }
    fun find(id: String): RedOperation? = loadedOperations.firstOrNull { it.id == id }
}
