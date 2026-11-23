package com.khalinux.android.red

import java.util.UUID

enum class RedOperationState { NOT_STARTED, PREPARING, WAITING_PARTICIPANT, ACTIVE, ENDING, COMPLETED, INTERRUPTED }
enum class RedNetworkState { IDLE, REQUESTING_HOTSPOT, HOTSPOT_READY, STARTING_SERVER, WAITING_PARTICIPANT, PARTICIPANT_ACTIVE, STOPPING, ERROR }
enum class RedPortalMode { LOCAL_LOGIN, OPEN_ACCESS }
enum class RedEventType {
    SESSION_STARTED,
    HOTSPOT_REQUESTED, HOTSPOT_READY, HOTSPOT_STOPPED, HOTSPOT_FAILED,
    PORTAL_STARTING, PORTAL_STARTED, PORTAL_STOPPED, PORTAL_OPENED, PORTAL_ERROR,
    HTTP_REQUEST_RECEIVED, HTTP_RESPONSE_SENT, NAVIGATION, REDIRECT,
    FORM_SUBMITTED, FORM_REJECTED, DEMO_FORM_COMPLETED,
    PERMISSION_REQUESTED, PERMISSION_GRANTED, PERMISSION_DENIED, PERMISSION_CANCELLED,
    PERMISSION_DEMO, RESOURCE_PREVIEW_STARTED, RESOURCE_PREVIEW_STOPPED,
    FILE_PICKER_OPENED, FILE_SELECTED, STEP_OPENED, STEP_COMPLETED,
    ERROR, SESSION_ENDED
}
enum class RedCategory { NETWORKS, WEB, SOCIAL_ENGINEERING, LOCAL_ANALYSIS }
enum class RedDifficulty { BEGINNER, INTERMEDIATE, ADVANCED }

/** Observação de uma requisição local. Valores de formulário nunca pertencem a este modelo persistente. */
data class RedHotspotSnapshot(
    val state: RedNetworkState,
    val message: String,
    val ssid: String? = null,
    val passphrase: String? = null,
    val interfaceName: String? = null,
    val ipv4Address: String? = null,
    val androidFailureCode: Int? = null
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
    val remoteAddress: String? = null
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
    val bytesReceived: Int? = null
)

data class RedSession(
    val id: String = UUID.randomUUID().toString(),
    val operationId: String,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val state: RedOperationState = RedOperationState.PREPARING,
    val completedSteps: Set<String> = emptySet(),
    val events: List<RedEvent> = emptyList()
)

object RedCatalog {
    private val builtInOperations = listOf(
        RedOperation(
            id = "red-wifi-public-01",
            number = 1,
            title = "Wi-Fi público: conexão, portal e navegação",
            category = RedCategory.NETWORKS,
            difficulty = RedDifficulty.BEGINNER,
            summary = "Laboratório de dois aparelhos com hotspot próprio, portal HTTP local e observação do que o servidor realmente recebe.",
            objectives = listOf(
                "Preparar uma rede de laboratório no aparelho operador",
                "Conectar um segundo aparelho e abrir o portal local",
                "Comparar autenticação local com acesso direto sem login",
                "Observar navegação, requisições e resultados de permissões"
            ),
            prerequisites = listOf(
                "Dois aparelhos Android dedicados ou explicitamente autorizados",
                "Wi-Fi habilitado nos dois aparelhos",
                "Dados de teste fictícios preparados para a aula"
            ),
            steps = listOf(
                RedTutorialStep("prepare", "Preparar o operador", "Confirme que os dois aparelhos pertencem ao laboratório e escolha o cenário da aula."),
                RedTutorialStep("hotspot", "Ativar a rede de laboratório", "Solicite ao Android a criação do hotspot local. O sistema informa os parâmetros que o aplicativo pode exibir."),
                RedTutorialStep("connect", "Conectar o participante", "No segundo aparelho, abra as configurações Wi-Fi e conecte-se à rede exibida pelo operador."),
                RedTutorialStep("portal", "Abrir o portal local", "Use o endereço HTTP e o QR Code mostrados pelo operador. A abertura automática depende do Android."),
                RedTutorialStep("access", "Comparar os modos de acesso", "Percorra o portal com autenticação local fictícia e o modo de acesso direto sem login."),
                RedTutorialStep("navigation", "Explorar navegação", "Abra os links locais e observe no painel as rotas e respostas efetivamente recebidas."),
                RedTutorialStep("permissions", "Observar permissões", "No aparelho participante, execute as demonstrações nativas e observe os diálogos reais do Android."),
                RedTutorialStep("report", "Encerrar e revisar", "Encerre a sessão e revise a linha do tempo e o relatório local.")
            ),
            completionCriteria = listOf(
                "Hotspot solicitado e estado real apresentado",
                "Segundo aparelho conectado e portal local acessível",
                "Modo com login local e modo sem login demonstrados",
                "Requisições locais observadas no painel",
                "Relatório local disponível"
            ),
            environment = "Dois aparelhos Android em rede de laboratório própria"
        )
    )

    @Volatile private var loadedOperations: List<RedOperation> = builtInOperations
    val operations: List<RedOperation> get() = loadedOperations
    fun replace(operations: List<RedOperation>) { loadedOperations = operations.toList() }
    fun find(id: String): RedOperation? = loadedOperations.firstOrNull { it.id == id }
}
