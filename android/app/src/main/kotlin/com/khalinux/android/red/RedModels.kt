package com.khalinux.android.red

import java.util.UUID

enum class RedOperationState { NOT_STARTED, PREPARING, WAITING_PARTICIPANT, ACTIVE, ENDING, COMPLETED, INTERRUPTED }
enum class RedEventType { SESSION_STARTED, HOTSPOT_REQUESTED, HOTSPOT_READY, HOTSPOT_STOPPED, PORTAL_STARTED, PORTAL_STOPPED, PORTAL_OPENED, STEP_OPENED, STEP_COMPLETED, DEMO_FORM_COMPLETED, PERMISSION_DEMO, ERROR, SESSION_ENDED }
enum class RedCategory { NETWORKS, WEB, SOCIAL_ENGINEERING, LOCAL_ANALYSIS }
enum class RedDifficulty { BEGINNER, INTERMEDIATE, ADVANCED }

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
    val detail: String
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
            title = "Nunca Conecte à Wi-Fi Pública",
            category = RedCategory.NETWORKS,
            difficulty = RedDifficulty.BEGINNER,
            summary = "Laboratório de dois aparelhos com hotspot próprio e portal local de treinamento.",
            objectives = listOf("Preparar uma rede de laboratório", "Abrir um portal local", "Executar interações com dados sintéticos", "Analisar os eventos gerados"),
            prerequisites = listOf("Dois aparelhos Android sob controle ou autorização explícita", "Wi-Fi habilitado", "Participante informado de que está em um laboratório"),
            steps = listOf(
                RedTutorialStep("prepare", "Preparar o operador", "Confirme que os dois aparelhos pertencem ao laboratório. Escolha um nome identificável para a rede e leia as limitações do Android."),
                RedTutorialStep("hotspot", "Ativar a rede de laboratório", "Solicite a ativação do hotspot local pelo Android. A rede e a senha são fornecidas pelo sistema."),
                RedTutorialStep("connect", "Conectar o participante", "No segundo aparelho, abra as configurações Wi-Fi e conecte-se à rede exibida pelo operador."),
                RedTutorialStep("portal", "Abrir o portal de treinamento", "Use o endereço local mostrado pelo operador. A abertura automática não é garantida pelo Android."),
                RedTutorialStep("traps", "Percorrer as simulações", "Abra cada cenário identificado como SIMULAÇÃO. Use somente os dados fictícios fornecidos na própria aula."),
                RedTutorialStep("report", "Encerrar e revisar", "Finalize a sessão, confira a linha do tempo de eventos e gere o relatório local.")
            ),
            completionCriteria = listOf("Sessão iniciada e encerrada", "Etapas do tutorial concluídas", "Formulário sintético demonstrado", "Relatório local disponível"),
            environment = "Dois aparelhos Android em rede de laboratório própria"
        )
    )

    @Volatile private var loadedOperations: List<RedOperation> = builtInOperations
    val operations: List<RedOperation> get() = loadedOperations
    fun replace(operations: List<RedOperation>) { loadedOperations = operations.toList() }
    fun find(id: String): RedOperation? = loadedOperations.firstOrNull { it.id == id }
}
