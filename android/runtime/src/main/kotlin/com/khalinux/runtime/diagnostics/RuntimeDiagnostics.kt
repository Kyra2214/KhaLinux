package com.khalinux.runtime.diagnostics

import com.khalinux.runtime.CommandRequest
import com.khalinux.runtime.LinuxRuntime
import com.khalinux.runtime.ProotLocator
import com.khalinux.runtime.RuntimeEvent
import com.khalinux.runtime.RuntimeSession
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class ProbeResult(val exitCode: Int?, val output: String, val timedOut: Boolean = false, val error: String? = null)

/** Executa um programa do host (usado só para `proot --version`); substituível em testes. */
fun interface HostCommandRunner {
    fun run(arguments: List<String>, environment: Map<String, String>, timeoutMillis: Long): ProbeResult
}

class ProcessHostCommandRunner : HostCommandRunner {
    override fun run(arguments: List<String>, environment: Map<String, String>, timeoutMillis: Long): ProbeResult {
        val process = try {
            ProcessBuilder(arguments).redirectErrorStream(true).apply {
                environment().clear()
                environment().putAll(environment)
            }.start()
        } catch (error: IOException) {
            return ProbeResult(null, "", error = error.message ?: error.javaClass.simpleName)
        }
        val output = StringBuilder()
        val reader = Thread {
            runCatching { process.inputStream.bufferedReader().useLines { lines -> lines.forEach { output.appendLine(it) } } }
        }.apply { isDaemon = true; start() }
        val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        if (!finished) process.destroyForcibly()
        reader.join(500)
        return ProbeResult(if (finished) process.exitValue() else null, output.toString(), timedOut = !finished)
    }
}

/**
 * Diagnóstico do ponto de teste físico. Cada item é independente; falhas não interrompem os demais,
 * exceto quando o PRoot não executa (os itens dependentes são ignorados com o motivo).
 * Os itens de rotação e segundo plano dependem do usuário e ficam como [DiagnosticStatus.MANUAL].
 */
class RuntimeDiagnostics(
    private val locator: ProotLocator,
    private val runtimeProvider: () -> LinuxRuntime?,
    private val tempDirectory: File,
    private val hostRunner: HostCommandRunner = ProcessHostCommandRunner(),
    private val commandTimeoutMillis: Long = 20_000L,
    private val ptyStepTimeoutMillis: Long = 8_000L
) {
    fun run(onUpdate: (DiagnosticReport) -> Unit = {}): DiagnosticReport {
        var report = DiagnosticReport.initial()
        fun publish(id: DiagnosticId, status: DiagnosticStatus, detail: String? = null) {
            report = report.with(id, status, detail)
            onUpdate(report)
        }
        onUpdate(report)

        publish(DiagnosticId.PROOT_EXEC, DiagnosticStatus.RUNNING)
        val prootOk = checkProot().also { (status, detail) -> publish(DiagnosticId.PROOT_EXEC, status, detail) }
            .first == DiagnosticStatus.OK

        val dependents = listOf(DiagnosticId.ROOTFS_SHELL, DiagnosticId.DNS, DiagnosticId.NETWORK, DiagnosticId.PTY)
        val runtime = if (prootOk) runtimeProvider() else null
        if (runtime == null) {
            val reason = if (!prootOk) "PRoot não executa" else "runtime Linux indisponível (RootFS não instalado?)"
            dependents.forEach { publish(it, DiagnosticStatus.SKIPPED, reason) }
        } else {
            step(DiagnosticId.ROOTFS_SHELL, ::publish) { checkShell(runtime) }
            step(DiagnosticId.DNS, ::publish) { checkDns(runtime) }
            step(DiagnosticId.NETWORK, ::publish) { checkNetwork(runtime) }
            step(DiagnosticId.PTY, ::publish) { checkPty(runtime) }
        }
        publish(DiagnosticId.ROTATION, DiagnosticStatus.MANUAL,
            "Abra o shell, gire o aparelho e confirme que a sessão continua ativa.")
        publish(DiagnosticId.BACKGROUND, DiagnosticStatus.MANUAL,
            "Abra o shell, rode `sleep 600`, deixe o app em segundo plano por alguns minutos e confirme ao voltar.")
        return report
    }

    private fun step(
        id: DiagnosticId,
        publish: (DiagnosticId, DiagnosticStatus, String?) -> Unit,
        check: () -> Pair<DiagnosticStatus, String?>
    ) {
        publish(id, DiagnosticStatus.RUNNING, null)
        val (status, detail) = try {
            check()
        } catch (error: Throwable) {
            DiagnosticStatus.FAILED to (error.message ?: error.javaClass.simpleName)
        }
        publish(id, status, detail)
    }

    private fun checkProot(): Pair<DiagnosticStatus, String?> {
        val location = locator.locate()
            ?: return DiagnosticStatus.FAILED to "PRoot não encontrado. Origens tentadas: ${locator.describe()}"
        tempDirectory.mkdirs()
        val environment = location.environment + mapOf(
            "PROOT_TMP_DIR" to tempDirectory.absolutePath,
            "TMPDIR" to tempDirectory.absolutePath
        )
        val result = hostRunner.run(listOf(location.executable.absolutePath, "--version"), environment, commandTimeoutMillis)
        val firstLine = result.output.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
        return when {
            result.error != null -> DiagnosticStatus.FAILED to
                "Não foi possível executar ${location.executable.path} (${location.source}): ${result.error}. $PLAN_B_HINT"
            result.timedOut -> DiagnosticStatus.FAILED to "Tempo esgotado ao executar proot --version"
            result.exitCode == 0 -> DiagnosticStatus.OK to
                "${firstLine ?: "proot --version retornou 0"} (origem: ${location.source})"
            else -> DiagnosticStatus.FAILED to
                "proot --version saiu com código ${result.exitCode}: ${firstLine ?: "sem saída"}. $PLAN_B_HINT"
        }
    }

    private fun checkShell(runtime: LinuxRuntime): Pair<DiagnosticStatus, String?> {
        val result = execute(runtime, "uname -m; id -u")
        val lines = result.output.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return when {
            result.failure != null -> DiagnosticStatus.FAILED to result.failure
            result.exitCode != 0 -> DiagnosticStatus.FAILED to "saiu com código ${result.exitCode}: ${result.output.take(200)}"
            lines.firstOrNull() != "aarch64" -> DiagnosticStatus.FAILED to
                "arquitetura inesperada: ${lines.firstOrNull() ?: "sem saída"} (esperado aarch64)"
            else -> DiagnosticStatus.OK to "aarch64, uid ${lines.getOrNull(1) ?: "?"}"
        }
    }

    private fun checkDns(runtime: LinuxRuntime): Pair<DiagnosticStatus, String?> {
        val result = execute(
            runtime,
            "getent hosts debian.org || { echo '--- /etc/resolv.conf'; cat /etc/resolv.conf 2>&1; exit 1; }"
        )
        return when {
            result.failure != null -> DiagnosticStatus.FAILED to result.failure
            result.exitCode == 0 && result.output.isNotBlank() ->
                DiagnosticStatus.OK to result.output.lineSequence().first { it.isNotBlank() }.trim()
            else -> DiagnosticStatus.FAILED to "sem resolução de nomes. ${result.output.take(300)}"
        }
    }

    private fun checkNetwork(runtime: LinuxRuntime): Pair<DiagnosticStatus, String?> {
        // HTTP simples: testa a rede sem depender de ca-certificates.
        val result = execute(
            runtime,
            "if command -v curl >/dev/null 2>&1; then " +
                "curl -sS -m 10 -o /dev/null -w '%{http_code}' http://deb.debian.org/ ; " +
                "else echo NO_CURL; exit 3; fi"
        )
        val code = result.output.trim()
        return when {
            result.failure != null -> DiagnosticStatus.FAILED to result.failure
            result.exitCode == 3 -> DiagnosticStatus.SKIPPED to "curl não está no RootFS (será instalado pelo gerenciador de pacotes)"
            result.exitCode == 0 && code.length == 3 && code.all(Char::isDigit) && code != "000" ->
                DiagnosticStatus.OK to "HTTP $code"
            else -> DiagnosticStatus.FAILED to "curl falhou (código ${result.exitCode}): ${result.output.take(200)}"
        }
    }

    /** Resize via TIOCSWINSZ e Ctrl+C (0x03) reais, observados pela saída do próprio shell. */
    private fun checkPty(runtime: LinuxRuntime): Pair<DiagnosticStatus, String?> {
        val session = runtime.start(CommandRequest(shellCommand = "", interactive = true, initialRows = 24, initialColumns = 80))
        val collector = EventCollector(session)
        try {
            val prompt = "echo PTY_READY_\$((40+2))\n"
            session.writeInput(prompt.toByteArray())
            if (!collector.await("PTY_READY_42", ptyStepTimeoutMillis)) {
                return DiagnosticStatus.FAILED to "o shell não respondeu ao teclado (sem eco de PTY_READY_42)"
            }
            session.writeInput("stty size\n".toByteArray())
            if (!collector.await("24 80", ptyStepTimeoutMillis)) {
                return DiagnosticStatus.FAILED to "tamanho inicial incorreto (esperado 24 80)"
            }
            if (!session.resize(30, 100)) return DiagnosticStatus.FAILED to "resize recusado pelo runtime"
            session.writeInput("stty size\n".toByteArray())
            if (!collector.await("30 100", ptyStepTimeoutMillis)) {
                return DiagnosticStatus.FAILED to "resize não chegou ao PTY (esperado 30 100)"
            }
            session.writeInput("sleep 60\n".toByteArray())
            Thread.sleep(400)
            session.writeInput(byteArrayOf(0x03))
            session.writeInput("echo CTRLC_\$((40+2))\n".toByteArray())
            if (!collector.await("CTRLC_42", ptyStepTimeoutMillis)) {
                return DiagnosticStatus.FAILED to "Ctrl+C não interrompeu o comando em primeiro plano"
            }
            return DiagnosticStatus.OK to "resize 24x80→30x100 e Ctrl+C funcionam"
        } finally {
            runCatching { session.cancel() }
            collector.close()
        }
    }

    private class ExecResult(val exitCode: Int?, val output: String, val failure: String?)

    private fun execute(runtime: LinuxRuntime, command: String): ExecResult {
        val session = runtime.start(CommandRequest(shellCommand = command))
        val collector = EventCollector(session)
        try {
            if (!collector.awaitFinished(commandTimeoutMillis)) {
                return ExecResult(null, collector.text(), "tempo esgotado (${commandTimeoutMillis / 1000}s)")
            }
            return ExecResult(collector.exitCode, collector.text(), collector.failure)
        } finally {
            runCatching { session.cancel() }
            collector.close()
        }
    }

    /** Lê os eventos da sessão em uma thread própria para permitir esperas com tempo limite. */
    private class EventCollector(session: RuntimeSession) {
        private val buffer = StringBuilder()
        private val done = CountDownLatch(1)
        @Volatile var exitCode: Int? = null
        @Volatile var failure: String? = null
        private val thread = Thread {
            try {
                session.events().forEach { event ->
                    when (event) {
                        is RuntimeEvent.Stdout -> append(event.bytes)
                        is RuntimeEvent.Stderr -> append(event.bytes)
                        is RuntimeEvent.Exited -> exitCode = event.exitCode
                        is RuntimeEvent.Failed -> failure = event.message
                        is RuntimeEvent.Started -> Unit
                    }
                }
            } catch (_: InterruptedException) {
            } finally {
                done.countDown()
            }
        }.apply { isDaemon = true; start() }

        private fun append(bytes: ByteArray) = synchronized(buffer) { buffer.append(String(bytes, Charsets.UTF_8)) }

        fun text(): String = synchronized(buffer) { buffer.toString() }

        fun awaitFinished(timeoutMillis: Long): Boolean = done.await(timeoutMillis, TimeUnit.MILLISECONDS)

        fun await(token: String, timeoutMillis: Long): Boolean {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            while (System.nanoTime() < deadline) {
                if (text().contains(token)) return true
                if (done.count == 0L) return text().contains(token)
                try { Thread.sleep(25) } catch (_: InterruptedException) { return false }
            }
            return false
        }

        fun close() { thread.interrupt() }
    }

    companion object {
        const val PLAN_B_HINT =
            "Se o Android bloqueia a execução em filesDir, o plano B é empacotar libproot.so em jniLibs (ProotLocator)."
    }
}
