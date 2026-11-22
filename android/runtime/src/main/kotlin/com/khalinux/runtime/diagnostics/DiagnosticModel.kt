package com.khalinux.runtime.diagnostics

enum class DiagnosticId(val title: String) {
    PROOT_EXEC("Executar o PRoot (--version)"),
    ROOTFS_SHELL("Shell no RootFS (uname -m, id)"),
    DNS("DNS (getent hosts debian.org)"),
    NETWORK("Rede (curl)"),
    PTY("PTY (resize e Ctrl+C)"),
    ROTATION("Sessão após rotação de tela"),
    BACKGROUND("Sessão longa em segundo plano")
}

enum class DiagnosticStatus(val label: String) {
    PENDING("aguardando"),
    RUNNING("executando"),
    OK("OK"),
    FAILED("FALHA"),
    SKIPPED("ignorado"),
    /** Só o usuário consegue confirmar (rotação, segundo plano). */
    MANUAL("confirmar manualmente")
}

data class DiagnosticItem(
    val id: DiagnosticId,
    val status: DiagnosticStatus = DiagnosticStatus.PENDING,
    val detail: String? = null
) {
    val title: String get() = id.title
}

data class DiagnosticReport(val items: List<DiagnosticItem>) {
    fun item(id: DiagnosticId): DiagnosticItem = items.first { it.id == id }

    fun with(id: DiagnosticId, status: DiagnosticStatus, detail: String? = null): DiagnosticReport =
        DiagnosticReport(items.map { if (it.id == id) DiagnosticItem(id, status, detail) else it })

    /** Verdadeiro se nenhum item automático falhou e nenhum ficou pendente/em execução. */
    val automaticChecksPassed: Boolean
        get() = items.none {
            it.status == DiagnosticStatus.FAILED ||
                it.status == DiagnosticStatus.PENDING ||
                it.status == DiagnosticStatus.RUNNING
        }

    val isFinished: Boolean
        get() = items.none { it.status == DiagnosticStatus.PENDING || it.status == DiagnosticStatus.RUNNING }

    fun toText(): String = buildString {
        appendLine("KhaLinux · relatório de diagnóstico")
        items.forEach { item ->
            append("[").append(item.status.label).append("] ").append(item.title)
            item.detail?.takeIf { it.isNotBlank() }?.let { append(" — ").append(it.trim().replace('\n', ' ')) }
            appendLine()
        }
    }

    companion object {
        fun initial(): DiagnosticReport = DiagnosticReport(DiagnosticId.values().map { DiagnosticItem(it) })
    }
}
