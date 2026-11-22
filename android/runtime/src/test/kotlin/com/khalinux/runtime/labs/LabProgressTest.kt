package com.khalinux.runtime.labs

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LabProgressTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun lab(id: String, prerequisites: List<String> = emptyList()) = LabDescriptor(
        id = id, track = LabTrack.LINUX, title = "Título", summary = "Resumo", difficulty = LabDifficulty.BEGINNER,
        requiredTools = emptyList(), prerequisites = prerequisites, services = emptyList(),
        exercises = listOf(
            LabExercise("e1", "Ex", "Enunciado", LabDifficulty.BEGINNER, AnswerHasher.sha256("42", false), false, listOf(LabHint("dica", 2)))
        ),
        doc = "docs/labs/$id.md"
    )

    private fun service(file: File) =
        LabProgressService(LabEngine(LabCatalog("t", listOf(lab("a"), lab("b", listOf("a"))))), LabProgressStore(file))

    @Test fun persistsPointsCompletionAndUnlocksDependents() {
        val file = File(tmp.root, "labs/progress.tsv")
        val first = service(file)
        assertFailsWith<LabLockedException> { first.open("b") }
        val session = first.open("a")
        first.revealNextHint(session, "e1")
        assertEquals(SubmitResult.Incorrect(1), first.submit(session, "e1", "0"))
        val result = first.submit(session, "e1", "42")
        assertTrue(result is SubmitResult.Correct && result.pointsAwarded == 8 && result.labCompleted)

        val second = service(file) // nova instância: lê do arquivo
        assertEquals(8, second.progress().totalPoints)
        assertTrue("a" in second.progress().completedLabs)
        assertTrue(second.open("a").state("e1").solved)
        second.open("b") // desbloqueado
        assertEquals(listOf("b"), second.available().map { it.id })
    }

    @Test fun resetLabAndResetAll() {
        val file = File(tmp.root, "progress.tsv")
        val s = service(file)
        s.submit(s.open("a"), "e1", "42")
        s.resetLab("a")
        assertEquals(0, s.progress().totalPoints)
        assertTrue(s.progress().completedLabs.isEmpty())
        s.submit(s.open("a"), "e1", "42")
        s.resetAll()
        assertTrue(!file.exists())
        assertEquals(LabProgress(), s.progress())
    }

    @Test fun ignoresMalformedLines() {
        val file = File(tmp.root, "progress.tsv")
        file.writeText("lixo\nE\ta\te1\tx\t0\t1\t5\nE\ta\te1\t2\t1\t1\t8\nC\t\nC\ta\n", Charsets.UTF_8)
        val progress = LabProgressStore(file).load()
        assertEquals(ExerciseState(2, 1, true, 8), progress.exercises.getValue("a").getValue("e1"))
        assertEquals(setOf("a"), progress.completedLabs)
    }
}
