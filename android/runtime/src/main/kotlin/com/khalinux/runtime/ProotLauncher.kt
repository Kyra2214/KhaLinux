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
        val usesProcessGroup = setsid.canExecute()
        val command = if (usesProcessGroup) listOf(setsid.absolutePath) + arguments else arguments
        val process = ProcessBuilder(command)
            .redirectErrorStream(false)
        process.environment().clear()
        process.environment().putAll(environment)
        val child = process.start()
        return if (usesProcessGroup) {
            ProcessGroupManagedProcess(child) { pid, force ->
                try {
                    NativePty.signal(pid, force)
                } catch (_: UnsatisfiedLinkError) {
                    if (force) child.destroyForcibly() else child.destroy()
                    false
                }
            }
        } else {
            child
        }
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
