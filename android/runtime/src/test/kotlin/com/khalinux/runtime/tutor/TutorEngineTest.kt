package com.khalinux.runtime.tutor

import com.khalinux.runtime.packages.PackageManifestException
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Escrito em 7.8; **não executado** (sem `kotlinc`/rede): entra na validação final da Fase 7. */
class TutorEngineTest {
    private fun repoRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "distribution/tutor/knowledge.tsv").isFile) dir = dir.parentFile
        return requireNotNull(dir) { "raiz do repositório não encontrada" }
    }

    private fun realKnowledge() = TutorKnowledgeParser.parse(File(repoRoot(), "distribution/tutor/knowledge.tsv").readText(Charsets.UTF_8))
    private fun engine() = TutorEngine(realKnowledge())

    @Test fun realKnowledgeIsValidAndDocsExist() {
        val root = repoRoot()
        val knowledge = realKnowledge()
        assertTrue(knowledge.topics.size >= 25)
        knowledge.topics.forEach { t -> t.doc?.let { assertTrue(File(root, it).isFile, "doc ausente: $it") } }
    }

    @Test fun explainsKnownCommand() {
        val reply = engine().ask("O que faz o chmod?")
        assertEquals(TutorReplyKind.TOPIC, reply.kind)
        assertEquals("chmod", reply.topicIds.first())
    }

    @Test fun ignoresAccentsAndCase() {
        assertEquals("permissoes", engine().ask("Quais são as PERMISSÕES de um arquivo?").topicIds.first())
    }

    @Test fun genericWordsDoNotMatchTopics() {
        assertEquals(TutorReplyKind.NOT_FOUND, engine().ask("me ajuda por favor").kind)
    }

    @Test fun unknownQuestionAdmitsItDoesNotKnow() {
        val reply = engine().ask("qual a capital da França?")
        assertEquals(TutorReplyKind.NOT_FOUND, reply.kind)
        assertTrue(reply.text.contains("Não encontrei"))
    }

    @Test fun emptyQuestion() {
        assertEquals(TutorReplyKind.EMPTY, engine().ask("   ").kind)
    }

    @Test fun refusesAttacksOnThirdParties() {
        assertEquals(TutorReplyKind.OUT_OF_SCOPE, engine().ask("como invadir o wifi do meu vizinho").kind)
        assertEquals(TutorReplyKind.OUT_OF_SCOPE, engine().ask("quero derrubar o site de um concorrente com ddos").kind)
    }

    @Test fun withholdsExerciseAnswers() {
        val context = TutorContext("linux-permissoes", "Umask padrão", "Qual a permissão de um arquivo novo com umask 022?")
        val reply = engine().ask("me dá a resposta", context)
        assertEquals(TutorReplyKind.ANSWER_WITHHELD, reply.kind)
        assertTrue(!reply.text.contains("644"))
    }

    @Test fun answerWordWithoutExerciseIsNotBlocked() {
        assertEquals(TutorReplyKind.NOT_FOUND, engine().ask("resposta").kind)
    }

    @Test fun relatedTopicsForExercise() {
        val context = TutorContext("linux-permissoes", "Umask padrão", "Qual a permissão de um arquivo novo com umask 022?")
        assertTrue(engine().topicsFor(context).any { it.id == "umask" })
        assertTrue(engine().topicsFor(TutorContext()).isEmpty())
    }

    @Test fun questionIsTruncated() {
        val reply = engine().ask("chmod " + "x".repeat(10_000))
        assertTrue(reply.kind == TutorReplyKind.TOPIC)
    }

    @Test fun parserRejectsBadData() {
        assertFailsWith<PackageManifestException> { TutorKnowledgeParser.parse("a\tb\tc") }
        assertFailsWith<PackageManifestException> { TutorKnowledgeParser.parse("# só comentário") }
        val dup = "x\tT\tk1\t\t-\tresp\nx\tT\tk2\t\t-\tresp"
        assertFailsWith<PackageManifestException> { TutorKnowledgeParser.parse(dup) }
        val badRef = "x\tT\tk1\tnao-existe\t-\tresp"
        assertFailsWith<PackageManifestException> { TutorKnowledgeParser.parse(badRef) }
    }

    @Test fun docReferenceCannotEscapeDocsDirectory() {
        assertTrue(TutorTopic.DOC.matches("docs/decisions/ADR-0002-proot-session-runtime.md"))
        assertTrue(TutorTopic.DOC.matches("docs/labs/linux-basico.md"))
        for (bad in listOf("docs/../SECURITY.md", "docs/a/../b.md", "/docs/x.md", "docs/.oculto.md", "docs/x.txt", "../docs/x.md")) {
            assertTrue(!TutorTopic.DOC.matches(bad), "deveria recusar: $bad")
        }
    }
}
