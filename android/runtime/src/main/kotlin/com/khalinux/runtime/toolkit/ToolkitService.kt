package com.khalinux.runtime.toolkit

import com.khalinux.runtime.packages.InstalledPackageStore
import com.khalinux.runtime.packages.PackageCommandOutcome
import com.khalinux.runtime.packages.PackageCommandRunner
import java.io.File
import java.time.Duration

/** Resultado do último comando de verificação de uma ferramenta, executado no RootFS do aparelho. */
data class ToolVerification(val toolId: String, val epochMillis: Long, val success: Boolean, val output: String)

/** Registro (TAB, gravação atômica) das verificações; saída limitada e sem TAB/quebra de linha. */
class VerificationStore(private val file: File) {
    @Synchronized
    fun all(): Map<String, ToolVerification> {
        if (!file.isFile) return emptyMap()
        return file.readLines(Charsets.UTF_8).mapNotNull { line ->
            val f = line.split('\t', limit = 4)
            if (f.size != 4) return@mapNotNull null
            val at = f[1].toLongOrNull() ?: return@mapNotNull null
            ToolVerification(f[0], at, f[2] == "1", f[3])
        }.associateBy { it.toolId }
    }

    @Synchronized
    fun put(v: ToolVerification) {
        file.parentFile?.mkdirs()
        val current = all().toMutableMap()
        current[v.toolId] = v
        val text = current.values.sortedBy { it.toolId }.joinToString("\n", postfix = "\n") {
            "${it.toolId}\t${it.epochMillis}\t${if (it.success) "1" else "0"}\t${it.output}"
        }
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(text, Charsets.UTF_8)
        if (!temporary.renameTo(file)) {
            file.delete()
            check(temporary.renameTo(file)) { "Não foi possível gravar ${file.name}" }
        }
    }
}

/** Situação de uma ferramenta para a UI. */
data class ToolStatus(
    val tool: ToolDescriptor,
    /** Versão do pacote registrada pelo gerenciador (nulo = não instalado). */
    val installedVersion: String?,
    /** Verdadeiro se a política de uso autorizado já libera a ferramenta agora. */
    val allowed: Boolean,
    val lastVerification: ToolVerification?
)

class ToolkitBlockedException(message: String) : IllegalStateException(message)

/**
 * Camada que lança comandos do toolkit: consulta o [ToolkitGuard], aceita apenas comandos da própria
 * ferramenta e sem metacaracteres de shell, e registra a verificação (evidência de execução no aparelho).
 * O comando de verificação (`check_command`) não tem alvo e por isso não exige confirmação de escopo.
 */
class ToolkitService(
    val registry: ToolkitRegistry,
    private val guard: ToolkitGuard,
    private val runner: PackageCommandRunner,
    private val installed: InstalledPackageStore,
    private val verifications: VerificationStore,
    private val clock: () -> Long = System::currentTimeMillis
) {
    fun status(): List<ToolStatus> {
        val versions = installed.versions()
        val checks = verifications.all()
        return registry.tools.sortedWith(compareBy({ it.group.ordinal }, { it.id })).map {
            ToolStatus(it, versions[it.packageName], guard.check(it.id) == GuardDecision.Allowed, checks[it.id])
        }
    }

    fun acknowledge(toolId: String, scope: String): ScopeAcknowledgement = guard.acknowledge(toolId, scope)
    fun revoke(toolId: String) = guard.revoke(toolId)

    /** Executa o `check_command` da ferramenta (exige o pacote instalado) e registra o resultado. */
    fun verify(toolId: String, timeout: Duration = Duration.ofSeconds(30)): ToolVerification {
        val tool = registry.find(toolId) ?: throw IllegalArgumentException("Ferramenta desconhecida: $toolId")
        if (installed.versions()[tool.packageName] == null) {
            throw ToolkitBlockedException("Instale o pacote ${tool.packageName} antes de verificar ${tool.title}.")
        }
        val outcome = runner.run(tool.checkCommand, timeout)
        return record(tool, outcome)
    }

    /**
     * Executa um comando da ferramenta no RootFS, depois de liberado pelo guarda. O comando deve começar
     * por um dos comandos do descritor e não pode conter metacaracteres de shell.
     */
    fun run(toolId: String, commandLine: String, timeout: Duration = Duration.ofMinutes(2)): PackageCommandOutcome {
        val tool = registry.find(toolId) ?: throw IllegalArgumentException("Ferramenta desconhecida: $toolId")
        when (val decision = guard.check(tool.id)) {
            GuardDecision.Allowed -> Unit
            is GuardDecision.NeedsAcknowledgement -> throw ToolkitBlockedException(decision.reason)
            is GuardDecision.UnknownTool -> throw IllegalArgumentException("Ferramenta desconhecida: $toolId")
        }
        val line = commandLine.trim()
        val first = line.split(' ', limit = 2).first()
        if (first !in tool.commands) throw ToolkitBlockedException("Comando '$first' não pertence a ${tool.title}.")
        if (line.any { it in FORBIDDEN } || line.length > 500) throw ToolkitBlockedException("Comando contém caracteres não permitidos.")
        if (installed.versions()[tool.packageName] == null) {
            throw ToolkitBlockedException("Instale o pacote ${tool.packageName} antes de usar ${tool.title}.")
        }
        return runner.run(line, timeout)
    }

    private fun record(tool: ToolDescriptor, outcome: PackageCommandOutcome): ToolVerification {
        val clean = outcome.output.replace(Regex("[\\t\\r\\n]+"), " ").trim().take(300)
        return ToolVerification(tool.id, clock(), outcome.exitCode == 0, clean).also(verifications::put)
    }

    private companion object {
        val FORBIDDEN = setOf(';', '&', '|', '<', '>', '$', '`', '\n', '\r', '\\', '"', '\'', '(', ')', '{', '}', '*', '?', '!', '#')
    }
}
