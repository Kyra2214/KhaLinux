package com.khalinux.runtime.toolkit

import java.io.File
import java.time.Duration

/** Confirmação de escopo registrada pelo usuário para uma ferramenta. */
data class ScopeAcknowledgement(val toolId: String, val scope: String, val acknowledgedAtEpochMillis: Long)

sealed class GuardDecision {
    object Allowed : GuardDecision()
    data class NeedsAcknowledgement(val reason: String) : GuardDecision()
    data class UnknownTool(val toolId: String) : GuardDecision()
}

/** Registro (TAB, gravação atômica) das confirmações de escopo; o texto de escopo nunca contém TAB/quebra de linha. */
class AuthorizationStore(private val file: File) {
    @Synchronized
    fun get(toolId: String): ScopeAcknowledgement? {
        if (!file.isFile) return null
        return file.readLines(Charsets.UTF_8).mapNotNull { line ->
            val f = line.split('\t', limit = 3)
            if (f.size != 3) return@mapNotNull null
            val at = f[1].toLongOrNull() ?: return@mapNotNull null
            ScopeAcknowledgement(f[0], f[2], at)
        }.lastOrNull { it.toolId == toolId }
    }

    @Synchronized
    fun put(ack: ScopeAcknowledgement) {
        file.parentFile?.mkdirs()
        val others = if (file.isFile) file.readLines(Charsets.UTF_8).filter { it.substringBefore('\t') != ack.toolId } else emptyList()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText((others + "${ack.toolId}\t${ack.acknowledgedAtEpochMillis}\t${ack.scope}").joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        if (!temporary.renameTo(file)) {
            file.delete()
            check(temporary.renameTo(file)) { "Não foi possível gravar ${file.name}" }
        }
    }

    @Synchronized
    fun clear(toolId: String) {
        if (!file.isFile) return
        val kept = file.readLines(Charsets.UTF_8).filter { it.substringBefore('\t') != toolId }
        file.writeText(kept.joinToString("\n", postfix = if (kept.isEmpty()) "" else "\n"), Charsets.UTF_8)
    }
}

/**
 * Política de uso autorizado: ferramentas com `requiresAuthorization` só são liberadas depois de o
 * usuário descrever o escopo (alvo que possui ou tem permissão explícita para avaliar). A confirmação
 * expira após [validity]. O guarda não executa nada: a camada que lança comandos deve consultá-lo.
 */
class ToolkitGuard(
    private val registry: ToolkitRegistry,
    private val store: AuthorizationStore,
    private val validity: Duration = Duration.ofHours(24),
    private val clock: () -> Long = System::currentTimeMillis
) {
    init { require(!validity.isNegative && !validity.isZero) { "validade deve ser positiva" } }

    fun check(toolId: String): GuardDecision {
        val tool = registry.find(toolId) ?: return GuardDecision.UnknownTool(toolId)
        if (!tool.requiresAuthorization) return GuardDecision.Allowed
        val ack = store.get(tool.id)
            ?: return GuardDecision.NeedsAcknowledgement("Informe o escopo autorizado antes de usar ${tool.title}.")
        val age = clock() - ack.acknowledgedAtEpochMillis
        return if (age in 0 until validity.toMillis()) GuardDecision.Allowed
        else GuardDecision.NeedsAcknowledgement("A confirmação de escopo de ${tool.title} expirou.")
    }

    /** Registra a confirmação. O escopo deve descrever o alvo (mínimo 3 caracteres úteis). */
    fun acknowledge(toolId: String, scope: String): ScopeAcknowledgement {
        val tool = registry.find(toolId) ?: throw IllegalArgumentException("Ferramenta desconhecida: $toolId")
        val clean = scope.replace(Regex("[\\t\\r\\n]+"), " ").trim().take(200)
        require(clean.length >= 3) { "Descreva o escopo autorizado (alvo)" }
        return ScopeAcknowledgement(tool.id, clean, clock()).also(store::put)
    }

    fun revoke(toolId: String) = store.clear(toolId)
}
