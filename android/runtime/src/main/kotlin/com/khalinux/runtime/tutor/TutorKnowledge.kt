package com.khalinux.runtime.tutor

import com.khalinux.runtime.packages.PackageManifestException
import java.text.Normalizer

/** Tópico da base do tutor: conceito ou comando explicado em português, com palavras-chave de busca. */
data class TutorTopic(
    val id: String,
    val title: String,
    /** Palavras-chave já normalizadas (ver [TutorText.normalize]); podem ser frases de várias palavras. */
    val keywords: List<String>,
    val answer: String,
    val related: List<String>,
    /** Caminho relativo da documentação do projeto (ex.: `docs/labs/linux-basico.md`) ou nulo. */
    val doc: String?
) {
    init {
        if (!ID.matches(id)) throw PackageManifestException("Id de tópico inválido: '$id'")
        if (title.isBlank() || title.length > 80) throw PackageManifestException("Título inválido no tópico $id")
        if (keywords.isEmpty() || keywords.size > 16) throw PackageManifestException("Palavras-chave inválidas no tópico $id")
        if (keywords.any { it.length !in 2..40 }) throw PackageManifestException("Palavra-chave inválida no tópico $id")
        if (answer.isBlank() || answer.length > 1500) throw PackageManifestException("Resposta inválida no tópico $id")
        if (doc != null && !DOC.matches(doc)) throw PackageManifestException("Documentação inválida no tópico $id: '$doc'")
    }

    companion object {
        internal val ID = Regex("[a-z0-9][a-z0-9-]{0,39}")
        // Segmentos não podem começar com "." (recusa "..") e o caminho fica sob docs/.
        internal val DOC = Regex("docs/(?:[A-Za-z0-9_-][A-Za-z0-9_.-]{0,60}/){0,4}[A-Za-z0-9_-][A-Za-z0-9_.-]{0,60}\\.md")
    }
}

class TutorKnowledge(val topics: List<TutorTopic>) {
    init {
        val ids = topics.map { it.id }
        if (ids.toSet().size != ids.size) throw PackageManifestException("Ids de tópico duplicados")
        topics.forEach { topic ->
            topic.related.forEach { r ->
                if (r !in ids) throw PackageManifestException("Tópico '${topic.id}' referencia tópico inexistente '$r'")
                if (r == topic.id) throw PackageManifestException("Tópico '${topic.id}' referencia a si mesmo")
            }
        }
    }

    fun find(id: String): TutorTopic? = topics.firstOrNull { it.id == id }
}

/**
 * Formato TSV (uma linha por tópico, `#` inicia comentário):
 * `id<TAB>título<TAB>palavras,chave<TAB>relacionados,por,id<TAB>doc|-<TAB>resposta` — `\n` literal na resposta vira quebra de linha.
 */
object TutorKnowledgeParser {
    fun parse(text: String): TutorKnowledge {
        val topics = text.lineSequence().withIndex()
            .filter { (_, line) -> line.isNotBlank() && !line.trimStart().startsWith("#") }
            .map { (index, line) ->
                val cols = line.split('\t')
                if (cols.size != 6) throw PackageManifestException("Linha ${index + 1}: esperadas 6 colunas, encontradas ${cols.size}")
                TutorTopic(
                    id = cols[0].trim(),
                    title = cols[1].trim(),
                    keywords = cols[2].split(',').map { TutorText.normalize(it) }.filter { it.isNotEmpty() },
                    related = cols[3].split(',').map { it.trim() }.filter { it.isNotEmpty() },
                    doc = cols[4].trim().takeUnless { it == "-" || it.isEmpty() },
                    answer = cols[5].trim().replace("\\n", "\n")
                )
            }.toList()
        if (topics.isEmpty()) throw PackageManifestException("Base do tutor vazia")
        return TutorKnowledge(topics)
    }
}

internal object TutorText {
    private val COMBINING = Regex("\\p{M}+")
    private val NON_ALNUM = Regex("[^a-z0-9]+")

    /** Minúsculas, sem acentos, só `a-z0-9` separados por um espaço. */
    fun normalize(raw: String): String =
        Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
            .replace(COMBINING, "")
            .replace(NON_ALNUM, " ")
            .trim()

    fun tokens(raw: String): List<String> = normalize(raw).split(' ').filter { it.isNotEmpty() }
}
