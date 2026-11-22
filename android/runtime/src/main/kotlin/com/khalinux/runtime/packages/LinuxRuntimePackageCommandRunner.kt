package com.khalinux.runtime.packages

import com.khalinux.runtime.CommandRequest
import com.khalinux.runtime.LinuxRuntime
import com.khalinux.runtime.RuntimeEvent
import java.time.Duration

/**
 * Executa comandos do gerenciador no RootFS através de [LinuxRuntime] (sessão não interativa,
 * `shellCommand` interpretado pelo Bash do RootFS, nunca por shell do host).
 * Bloqueia a thread chamadora até o fim do comando; o chamador deve usar thread de trabalho.
 */
class LinuxRuntimePackageCommandRunner(
    private val runtime: LinuxRuntime,
    private val maxOutputChars: Int = 8_000
) : PackageCommandRunner {
    override fun run(shellCommand: String, timeout: Duration): PackageCommandOutcome {
        val output = StringBuilder()
        fun append(bytes: ByteArray) {
            output.append(String(bytes, Charsets.UTF_8))
            if (output.length > maxOutputChars) output.delete(0, output.length - maxOutputChars)
        }
        runtime.start(CommandRequest(shellCommand = shellCommand, timeout = timeout)).use { session ->
            for (event in session.events()) {
                when (event) {
                    is RuntimeEvent.Stdout -> append(event.bytes)
                    is RuntimeEvent.Stderr -> append(event.bytes)
                    is RuntimeEvent.Exited ->
                        return PackageCommandOutcome(if (event.timedOut) -1 else event.exitCode,
                            if (event.timedOut) output.append("\n[tempo esgotado]").toString() else output.toString())
                    is RuntimeEvent.Failed ->
                        return PackageCommandOutcome(-1, output.append("\n").append(event.message).toString())
                    is RuntimeEvent.Started -> Unit
                }
            }
        }
        return PackageCommandOutcome(-1, output.append("\n[sessão terminou sem código de saída]").toString())
    }
}
