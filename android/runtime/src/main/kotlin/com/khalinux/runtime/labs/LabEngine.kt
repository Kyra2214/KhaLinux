package com.khalinux.runtime.labs

import java.security.MessageDigest

/** Estado de um exercício dentro de uma sessão (sem persistência: ver submódulo 6.3). */
data class ExerciseState(
    val wrongAttempts: Int = 0,
    /** Quantidade de dicas reveladas, sempre na ordem do descritor. */
    val hintsRevealed: Int = 0,
    val solved: Boolean = false,
    /** Pontos concedidos; só diferente de zero quando [solved]. */
    val pointsAwarded: Int = 0
)

sealed class SubmitResult {
    data class Correct(val pointsAwarded: Int, val labCompleted: Boolean) : SubmitResult()
    data class Incorrect(val wrongAttempts: Int) : SubmitResult()
    object AlreadySolved : SubmitResult()
    object EmptyAnswer : SubmitResult()
}

class LabLockedException(message: String) : IllegalStateException(message)

/**
 * Regra de pontuação: pontos base da dificuldade do exercício, menos o custo das dicas reveladas e menos
 * 1 ponto por tentativa errada além da [FREE_ATTEMPTS]; nunca abaixo de 25% do valor base.
 */
object LabScoring {
    const val FREE_ATTEMPTS = 3

    fun points(difficulty: LabDifficulty, hintCost: Int, wrongAttempts: Int): Int {
        val base = difficulty.basePoints
        val floor = base / 4
        val penalty = hintCost + maxOf(0, wrongAttempts - FREE_ATTEMPTS)
        return maxOf(floor, base - penalty)
    }
}

/**
 * Sessão de um laboratório: confere respostas pelo hash, revela dicas em ordem e calcula pontos.
 * Thread-safe. O estado inicial pode ser restaurado (usado pela persistência em 6.3).
 */
class LabSession(
    val lab: LabDescriptor,
    initial: Map<String, ExerciseState> = emptyMap()
) {
    private val states = HashMap<String, ExerciseState>()

    init {
        for ((id, state) in initial) {
            val ex = lab.exercises.firstOrNull { it.id == id } ?: continue
            states[id] = state.copy(hintsRevealed = state.hintsRevealed.coerceIn(0, ex.hints.size))
        }
    }

    @Synchronized
    fun state(exerciseId: String): ExerciseState {
        requireExercise(exerciseId)
        return states[exerciseId] ?: ExerciseState()
    }

    @Synchronized
    fun snapshot(): Map<String, ExerciseState> = lab.exercises.associate { it.id to (states[it.id] ?: ExerciseState()) }

    @Synchronized
    fun isCompleted(): Boolean = lab.exercises.all { states[it.id]?.solved == true }

    @Synchronized
    fun totalPoints(): Int = states.values.sumOf { it.pointsAwarded }

    /** Próxima dica ainda oculta do exercício (e registra a revelação), ou nulo se não houver mais. */
    @Synchronized
    fun revealNextHint(exerciseId: String): LabHint? {
        val ex = requireExercise(exerciseId)
        val current = states[exerciseId] ?: ExerciseState()
        if (current.solved || current.hintsRevealed >= ex.hints.size) return null
        states[exerciseId] = current.copy(hintsRevealed = current.hintsRevealed + 1)
        return ex.hints[current.hintsRevealed]
    }

    /** Dicas já reveladas, em ordem. */
    @Synchronized
    fun revealedHints(exerciseId: String): List<LabHint> {
        val ex = requireExercise(exerciseId)
        return ex.hints.take((states[exerciseId] ?: ExerciseState()).hintsRevealed)
    }

    @Synchronized
    fun submit(exerciseId: String, answer: String): SubmitResult {
        val ex = requireExercise(exerciseId)
        val current = states[exerciseId] ?: ExerciseState()
        if (current.solved) return SubmitResult.AlreadySolved
        if (answer.isBlank()) return SubmitResult.EmptyAnswer
        val given = AnswerHasher.sha256(answer, ex.ignoreCase)
        // Comparação em tempo constante dos hashes.
        val correct = MessageDigest.isEqual(given.toByteArray(Charsets.US_ASCII), ex.answerSha256.toByteArray(Charsets.US_ASCII))
        if (!correct) {
            val wrong = current.wrongAttempts + 1
            states[exerciseId] = current.copy(wrongAttempts = wrong)
            return SubmitResult.Incorrect(wrong)
        }
        val hintCost = ex.hints.take(current.hintsRevealed).sumOf { it.cost }
        val points = LabScoring.points(ex.difficulty, hintCost, current.wrongAttempts)
        states[exerciseId] = current.copy(solved = true, pointsAwarded = points)
        return SubmitResult.Correct(points, lab.exercises.all { states[it.id]?.solved == true })
    }

    private fun requireExercise(id: String): LabExercise =
        lab.exercises.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Exercício desconhecido: $id")
}

/** Abre sessões respeitando os pré-requisitos do catálogo. */
class LabEngine(val catalog: LabCatalog) {
    fun available(completed: Set<String>): List<LabDescriptor> = catalog.available(completed)

    fun open(labId: String, completed: Set<String>, restored: Map<String, ExerciseState> = emptyMap()): LabSession {
        val lab = catalog.find(labId) ?: throw IllegalArgumentException("Laboratório desconhecido: $labId")
        val missing = lab.prerequisites.filter { it !in completed }
        if (missing.isNotEmpty()) {
            throw LabLockedException("Conclua antes: ${missing.joinToString(", ")}")
        }
        return LabSession(lab, restored)
    }
}
