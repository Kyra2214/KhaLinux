package com.khalinux.runtime

import java.io.File

interface ProcessLauncher {
    fun launch(arguments: List<String>, environment: Map<String, String>): Process
}

class SystemProcessLauncher : ProcessLauncher {
    override fun launch(arguments: List<String>, environment: Map<String, String>): Process {
        require(arguments.isNotEmpty()) { "lista de argumentos vazia" }
        val process = ProcessBuilder(arguments)
            .redirectErrorStream(false)
        process.environment().clear()
        process.environment().putAll(environment)
        return process.start()
    }
}

class ProotLauncher(
    private val config: ProotConfig,
    private val processLauncher: ProcessLauncher = SystemProcessLauncher()
) {
    init { config.validate() }

    fun launch(request: CommandRequest): Process {
        val arguments = config.commandFor(request)
        val environment = mapOf("LC_ALL" to "C.UTF-8", "LANG" to "C.UTF-8")
        return processLauncher.launch(arguments, environment)
    }

    fun commandPreview(request: CommandRequest): List<String> = config.commandFor(request)
}
