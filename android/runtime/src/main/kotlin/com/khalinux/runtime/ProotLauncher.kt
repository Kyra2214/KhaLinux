package com.khalinux.runtime

interface ProcessLauncher {
    fun launch(arguments: List<String>, environment: Map<String, String>): Process
}

/** Launcher capaz de criar uma sessão associada a terminal pseudoterminal. */
interface TerminalProcessLauncher : ProcessLauncher {
    fun launch(
        arguments: List<String>,
        environment: Map<String, String>,
        rows: Int,
        columns: Int
    ): Process

    override fun launch(arguments: List<String>, environment: Map<String, String>): Process =
        launch(arguments, environment, rows = 24, columns = 80)
}

/** Caminho host-side usado por comandos não interativos e pelos testes do runtime. */
class SystemProcessLauncher : ProcessLauncher {
    override fun launch(arguments: List<String>, environment: Map<String, String>): Process {
        require(arguments.isNotEmpty()) { "lista de argumentos vazia" }
        val setsid = java.io.File("/system/bin/setsid")
        if (!setsid.canExecute()) {
            return ProcessBuilder(arguments).apply {
                environment().clear()
                environment().putAll(environment)
            }.start()
        }

        val pidFile = java.io.File.createTempFile("khalinux-process-group-", ".pid")
        try {
            val wrapperScript = "printf '%s\\n' \"\$\$\" > \"\$1\"; shift; exec \"\$@\""
            val wrappedCommand = listOf(
                setsid.absolutePath,
                "-w",
                "/system/bin/sh",
                "-c",
                wrapperScript,
                "khalinux-process-group",
                pidFile.absolutePath
            ) + arguments
            val child = ProcessBuilder(wrappedCommand).apply {
                environment().clear()
                environment().putAll(environment)
            }.start()
            val processGroupId = awaitProcessGroupId(pidFile, child)
                ?: return child
            return ProcessGroupManagedProcess(child, processGroupId) { groupId, force ->
                try {
                    NativePty.signal(groupId, force)
                } catch (_: LinkageError) {
                    if (force) child.destroyForcibly() else child.destroy()
                    false
                } catch (_: java.io.IOException) {
                    if (force) child.destroyForcibly() else child.destroy()
                    false
                }
            }
        } finally {
            pidFile.delete()
        }
    }

    private fun awaitProcessGroupId(pidFile: java.io.File, process: Process): Int? {
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2)
        while (System.nanoTime() < deadline) {
            val value = runCatching { pidFile.readText().trim().toInt() }.getOrNull()
            if (value != null && value > 0) return value
            if (!process.isAlive) break
            try {
                Thread.sleep(10)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return null
            }
        }
        return runCatching { pidFile.readText().trim().toInt() }
            .getOrNull()
            ?.takeIf { it > 0 }
    }
}

class ProotLauncher(
    private val config: ProotConfig,
    private val processLauncher: ProcessLauncher = SystemProcessLauncher(),
    private val interactiveProcessLauncher: TerminalProcessLauncher? = null
) {
    init { config.validate() }

    fun launch(request: CommandRequest): Process {
        val arguments = config.commandFor(request)
        val environment = mapOf("LC_ALL" to "C.UTF-8", "LANG" to "C.UTF-8")
        return if (request.interactive) {
            requireNotNull(interactiveProcessLauncher) {
                "launcher PTY não configurado para sessão interativa"
            }.launch(arguments, environment, request.initialRows, request.initialColumns)
        } else {
            processLauncher.launch(arguments, environment)
        }
    }

    fun commandPreview(request: CommandRequest): List<String> = config.commandFor(request)
}
