package com.khalinux.runtime.reports

import com.khalinux.runtime.files.HomeFileManager
import com.khalinux.runtime.labs.LabDescriptor
import com.khalinux.runtime.labs.LabProgress
import com.khalinux.runtime.toolkit.ToolStatus
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Gera o relatório em Markdown de progresso do usuário: laboratórios (estado, pontos, exercícios) e ferramentas
 * (instalação e última verificação). Não inclui respostas, hashes, dicas, escopos autorizados nem a saída bruta
 * das verificações (pode conter dados do ambiente).
 */
object ReportBuilder {
    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC)
    private val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

    fun build(
        generatedAtMillis: Long,
        labs: List<LabDescriptor>,
        progress: LabProgress,
        tools: List<ToolStatus>
    ): String = buildString {
        val completed = labs.count { it.id in progress.completedLabs }
        append("# Relatório KhaLinux\n\n")
        append("Gerado em ${STAMP.format(Instant.ofEpochMilli(generatedAtMillis))}.\n\n")
        append("## Resumo\n\n")
        append("- Pontos totais: ${progress.totalPoints}\n")
        append("- Laboratórios concluídos: $completed de ${labs.size}\n")
        append("- Ferramentas instaladas: ${tools.count { it.installedVersion != null }} de ${tools.size}\n")
        append("- Ferramentas com verificação bem-sucedida: ${tools.count { it.lastVerification?.success == true }}\n\n")

        append("## Laboratórios\n\n")
        append("| Laboratório | Trilha | Dificuldade | Estado | Exercícios | Pontos |\n|---|---|---|---|---|---|\n")
        labs.forEach { lab ->
            val states = progress.exercises[lab.id].orEmpty()
            val solved = states.values.count { it.solved }
            val state = when {
                lab.id in progress.completedLabs -> "concluído"
                solved > 0 -> "em andamento"
                else -> "não iniciado"
            }
            append("| ${cell(lab.title)} | ${lab.track.id} | ${lab.difficulty.id} | $state | $solved/${lab.exercises.size} | ${progress.pointsOf(lab.id)} |\n")
        }

        append("\n## Ferramentas\n\n")
        append("| Ferramenta | Grupo | Instalada | Verificação |\n|---|---|---|---|\n")
        tools.forEach { status ->
            val check = status.lastVerification
            val verification = when {
                check == null -> "não verificada"
                check.success -> "ok em " + STAMP.format(Instant.ofEpochMilli(check.epochMillis))
                else -> "falhou em " + STAMP.format(Instant.ofEpochMilli(check.epochMillis))
            }
            append("| ${cell(status.tool.title)} | ${status.tool.group.id} | ${status.installedVersion?.let { cell(it) } ?: "não"} | $verification |\n")
        }

        append("\n---\n\nUso autorizado: use as ferramentas somente em alvos próprios ou com autorização e escopo explícitos. ")
        append("Este relatório não contém respostas dos exercícios nem saídas brutas de ferramentas.\n")
    }

    fun fileName(generatedAtMillis: Long, prefix: String = "relatorio"): String =
        "$prefix-" + FILE_STAMP.format(Instant.ofEpochMilli(generatedAtMillis)) + ".md"

    /** Evita quebrar a tabela Markdown. */
    private fun cell(value: String): String =
        value.replace("|", "\\|").replace('\n', ' ').replace('\r', ' ')
}

/** Grava relatórios em `reports/` dentro do home persistente (visível em `/home/khalinux/reports`). */
class ReportWriter(private val files: HomeFileManager) {
    /** Devolve o caminho relativo gravado (`reports/relatorio-....md`) ou nulo se falhou. */
    fun write(generatedAtMillis: Long, content: String, prefix: String = "relatorio"): String? {
        files.mkdir(REPORTS_DIRECTORY)
        val base = ReportBuilder.fileName(generatedAtMillis, prefix)
        var path = "$REPORTS_DIRECTORY/$base"
        var suffix = 1
        // Dois relatórios no mesmo segundo não se sobrescrevem.
        while (files.resolve(path)?.exists() == true && suffix < 100) {
            path = "$REPORTS_DIRECTORY/${base.removeSuffix(".md")}-$suffix.md"
            suffix++
        }
        return if (files.writeText(path, content)) path else null
    }

    companion object {
        const val REPORTS_DIRECTORY = "reports"
    }
}
