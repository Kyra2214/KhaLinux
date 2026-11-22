package com.khalinux.runtime.tutor

import com.khalinux.runtime.toolkit.ToolDescriptor

/** Exercício aberto no momento da pergunta (só texto público do enunciado; nunca a resposta nem o hash). */
data class TutorContext(val labId: String? = null, val exerciseTitle: String? = null, val exerciseInstructions: String? = null) {
    val hasExercise: Boolean get() = !exerciseTitle.isNullOrBlank()
}

enum class TutorReplyKind {
    /** Explicação de um tópico da base. */
    TOPIC,
    /** Ficha de uma ferramenta do toolkit. */
    TOOL,
    /** Pedido de resposta/gabarito de exercício: recusado, com orientação. */
    ANSWER_WITHHELD,
    /** Pedido que envolve ataque a terceiros ou uso sem autorização: recusado. */
    OUT_OF_SCOPE,
    /** Nada na base corresponde à pergunta. */
    NOT_FOUND,
    EMPTY
}

data class TutorReply(
    val kind: TutorReplyKind,
    val text: String,
    /** Ids dos tópicos usados/sugeridos, do mais relevante ao menos. */
    val topicIds: List<String> = emptyList(),
    val doc: String? = null
)

/**
 * Tutor didático local: sem modelo embarcado e sem rede (ADR-0006). Responde apenas com o conteúdo
 * versionado (`distribution/tutor/knowledge.tsv`) e com os descritores do toolkit; quando não sabe, diz que
 * não sabe. Nunca revela a resposta de exercícios e recusa pedidos de ataque a terceiros.
 */
class TutorEngine(
    private val knowledge: TutorKnowledge,
    private val tools: List<ToolDescriptor> = emptyList()
) {
    fun ask(question: String, context: TutorContext = TutorContext()): TutorReply {
        val raw = question.trim().take(MAX_QUESTION)
        val normalized = TutorText.normalize(raw)
        if (normalized.isEmpty()) {
            return TutorReply(TutorReplyKind.EMPTY, "Escreva uma pergunta, por exemplo: \"o que faz o chmod?\" ou \"como listar arquivos ocultos?\".")
        }
        val padded = " $normalized "
        if (SCOPE_PHRASES.any { padded.contains(" $it ") }) return outOfScope()
        if (context.hasExercise && ANSWER_PHRASES.any { padded.contains(" $it ") }) return answerWithheld(context)

        val tokens = normalized.split(' ').toSet()
        tools.firstOrNull { tool -> tool.id in tokens || tool.commands.any { it.length >= 3 && it.lowercase() in tokens } }?.let { return toolReply(it) }

        val ranked = rank(tokens, padded)
        if (ranked.isEmpty()) return notFound()
        val best = ranked.first()
        val others = ranked.drop(1).take(2)
        val text = buildString {
            append(best.title).append('\n').append(best.answer)
            if (others.isNotEmpty()) append("\n\nVeja também: ").append(others.joinToString("; ") { it.title })
        }
        return TutorReply(TutorReplyKind.TOPIC, text, (listOf(best) + others).map { it.id }, best.doc)
    }

    /** Tópicos relacionados ao enunciado do exercício aberto (conceitos, sem dar a resposta). */
    fun topicsFor(context: TutorContext, limit: Int = 3): List<TutorTopic> {
        if (!context.hasExercise) return emptyList()
        val source = listOfNotNull(context.exerciseTitle, context.exerciseInstructions).joinToString(" ")
        val normalized = TutorText.normalize(source)
        return rank(normalized.split(' ').toSet(), " $normalized ").take(limit)
    }

    private fun rank(tokens: Set<String>, padded: String): List<TutorTopic> =
        knowledge.topics.map { it to score(it, tokens, padded) }
            .filter { it.second >= MIN_SCORE }
            .sortedByDescending { it.second } // sortedBy é estável: empate mantém a ordem do arquivo
            .map { it.first }

    private fun score(topic: TutorTopic, tokens: Set<String>, padded: String): Int {
        var total = 0
        for (kw in topic.keywords) {
            total += when {
                kw.contains(' ') -> if (padded.contains(" $kw ")) PHRASE else 0
                kw in tokens -> EXACT
                kw.length >= STEM_MIN && tokens.any { sharesStem(it, kw) } -> STEM
                else -> 0
            }
        }
        return total
    }

    private fun sharesStem(token: String, keyword: String): Boolean {
        if (token.length < STEM_MIN) return false
        val common = token.commonPrefixWith(keyword).length
        return common >= STEM_MIN && common >= minOf(token.length, keyword.length) - 3
    }

    private fun toolReply(tool: ToolDescriptor): TutorReply {
        val text = buildString {
            append(tool.title).append(" (").append(tool.group.id).append(")\n").append(tool.summary)
            append("\nComandos: ").append(tool.commands.joinToString(", ")).append('.')
            if (tool.requiresAuthorization) {
                append("\nUse somente em alvos seus ou com autorização e escopo explícitos; o app pede confirmação de escopo antes de executar.")
            }
            if (tool.rootLimitations.isNotEmpty()) {
                append("\nLimitações sem root: ").append(tool.rootLimitations.joinToString("; ")).append('.')
            }
            append(if (tool.verified) "\nVerificada em aparelho: sim." else "\nVerificada em aparelho: ainda não.")
        }
        return TutorReply(TutorReplyKind.TOOL, text, doc = tool.doc)
    }

    private fun answerWithheld(context: TutorContext): TutorReply {
        val related = topicsFor(context)
        val text = buildString {
            append("Não entrego a resposta do exercício: o objetivo é você chegar nela. ")
            append("Use as dicas do próprio exercício (elas custam pontos, mas são graduais)")
            if (related.isNotEmpty()) {
                append(" e revise: ").append(related.joinToString("; ") { it.title }).append('.')
            } else {
                append(". Pergunte sobre o conceito ou o comando que você não entendeu.")
            }
        }
        return TutorReply(TutorReplyKind.ANSWER_WITHHELD, text, related.map { it.id })
    }

    private fun outOfScope() = TutorReply(
        TutorReplyKind.OUT_OF_SCOPE,
        "Não ajudo com ataques, invasão ou testes em sistemas de terceiros ou sem autorização. " +
            "O KhaLinux ensina em laboratórios locais (só em 127.0.0.1) e em ambientes seus. " +
            "Posso explicar como funciona a defesa, os conceitos ou um laboratório da trilha Web ou Rede."
    )

    private fun notFound(): TutorReply {
        val sample = knowledge.topics.take(5)
        return TutorReply(
            TutorReplyKind.NOT_FOUND,
            "Não encontrei isso na minha base (sou um tutor offline e só respondo o que está documentado). " +
                "Tente outras palavras ou pergunte sobre: " + sample.joinToString("; ") { it.title } + ".",
            sample.map { it.id }
        )
    }

    companion object {
        const val MAX_QUESTION = 300
        private const val PHRASE = 3
        private const val EXACT = 2
        private const val STEM = 1
        private const val STEM_MIN = 5
        private const val MIN_SCORE = 2

        /** Frases (já normalizadas) de pedido de resposta pronta. */
        internal val ANSWER_PHRASES = listOf(
            "resposta", "respostas", "gabarito", "solucao", "me da a resposta", "qual e a resposta", "resolve o exercicio", "resolva o exercicio"
        )

        /** Frases (já normalizadas) de alvo de ataque/uso sem autorização. Lista conservadora; não substitui o gate do toolkit. */
        internal val SCOPE_PHRASES = listOf(
            "invadir", "invadi", "hackear", "hackeia", "hackeie", "derrubar site", "derrubar o site", "ddos", "dos attack",
            "sem autorizacao", "sem permissao do dono", "do meu vizinho", "da minha ex", "do meu ex", "do meu chefe",
            "roubar senha", "roubar senhas", "quebrar senha de", "invasao", "clonar whatsapp", "espionar"
        )
    }
}
