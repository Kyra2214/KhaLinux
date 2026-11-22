package com.khalinux.runtime.labs

import java.io.File

/** Progresso persistido de todos os laboratórios: estado por exercício e laboratórios concluídos. */
data class LabProgress(
    val exercises: Map<String, Map<String, ExerciseState>> = emptyMap(),
    val completedLabs: Set<String> = emptySet()
) {
    /** Total de pontos concedidos em todos os laboratórios. */
    val totalPoints: Int get() = exercises.values.sumOf { lab -> lab.values.sumOf { it.pointsAwarded } }

    fun pointsOf(labId: String): Int = exercises[labId]?.values?.sumOf { it.pointsAwarded } ?: 0
}

/**
 * Registro (TAB, gravação atômica, como [com.khalinux.runtime.toolkit.AuthorizationStore]).
 * Linhas: `E<TAB>lab<TAB>exercicio<TAB>erradas<TAB>dicas<TAB>resolvido(0/1)<TAB>pontos` e `C<TAB>lab`.
 * Linhas inválidas são ignoradas; valores negativos são normalizados.
 */
class LabProgressStore(private val file: File) {
    @Synchronized
    fun load(): LabProgress {
        if (!file.isFile) return LabProgress()
        val exercises = LinkedHashMap<String, MutableMap<String, ExerciseState>>()
        val completed = LinkedHashSet<String>()
        for (line in file.readLines(Charsets.UTF_8)) {
            val f = line.split('\t')
            when {
                f.size == 7 && f[0] == "E" -> {
                    val wrong = f[3].toIntOrNull() ?: continue
                    val hints = f[4].toIntOrNull() ?: continue
                    val points = f[6].toIntOrNull() ?: continue
                    val solved = when (f[5]) { "1" -> true; "0" -> false; else -> continue }
                    exercises.getOrPut(f[1]) { LinkedHashMap() }[f[2]] = ExerciseState(
                        wrongAttempts = maxOf(0, wrong),
                        hintsRevealed = maxOf(0, hints),
                        solved = solved,
                        pointsAwarded = if (solved) maxOf(0, points) else 0
                    )
                }
                f.size == 2 && f[0] == "C" && f[1].isNotBlank() -> completed += f[1]
            }
        }
        return LabProgress(exercises, completed)
    }

    /** Grava o snapshot de uma sessão e marca o laboratório como concluído quando todos os exercícios foram resolvidos. */
    @Synchronized
    fun save(session: LabSession) {
        val snapshot = session.snapshot()
        val current = load()
        val exercises = current.exercises + (session.lab.id to snapshot)
        val completed = if (session.isCompleted()) current.completedLabs + session.lab.id else current.completedLabs
        write(LabProgress(exercises, completed))
    }

    /** Apaga o progresso de um laboratório; os dependentes que exigiam sua conclusão continuam registrados. */
    @Synchronized
    fun resetLab(labId: String) {
        val current = load()
        write(LabProgress(current.exercises - labId, current.completedLabs - labId))
    }

    @Synchronized
    fun resetAll() {
        if (file.isFile) check(file.delete()) { "Não foi possível apagar ${file.name}" }
    }

    private fun write(progress: LabProgress) {
        file.parentFile?.mkdirs()
        val lines = ArrayList<String>()
        for ((lab, states) in progress.exercises) {
            require(lab.none { it == '\t' || it == '\n' || it == '\r' })
            for ((ex, s) in states) {
                require(ex.none { it == '\t' || it == '\n' || it == '\r' })
                lines += "E\t$lab\t$ex\t${s.wrongAttempts}\t${s.hintsRevealed}\t${if (s.solved) 1 else 0}\t${s.pointsAwarded}"
            }
        }
        progress.completedLabs.forEach { lines += "C\t$it" }
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(lines.joinToString("\n", postfix = if (lines.isEmpty()) "" else "\n"), Charsets.UTF_8)
        if (!temporary.renameTo(file)) {
            file.delete()
            check(temporary.renameTo(file)) { "Não foi possível gravar ${file.name}" }
        }
    }
}

/** Liga motor e persistência: abre sessões com o estado restaurado e grava após cada ação. */
class LabProgressService(val engine: LabEngine, private val store: LabProgressStore) {
    fun progress(): LabProgress = store.load()

    fun available(): List<LabDescriptor> = engine.available(store.load().completedLabs)

    fun open(labId: String): LabSession {
        val progress = store.load()
        return engine.open(labId, progress.completedLabs, progress.exercises[labId].orEmpty())
    }

    fun submit(session: LabSession, exerciseId: String, answer: String): SubmitResult =
        session.submit(exerciseId, answer).also { store.save(session) }

    fun revealNextHint(session: LabSession, exerciseId: String): LabHint? =
        session.revealNextHint(exerciseId).also { if (it != null) store.save(session) }

    fun resetLab(labId: String) = store.resetLab(labId)

    fun resetAll() = store.resetAll()
}
