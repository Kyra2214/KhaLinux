package com.khalinux.runtime

import java.io.File

/** Caminhos privados do app; não aceitar caminhos vindos diretamente da UI sem validação. */
data class ProotConfig(
    val prootExecutable: File,
    val rootfsDirectory: File,
    /** Necessário apenas quando o host não é ARM64; em Android ARM64 deve ser nulo. */
    val qemuExecutable: File? = null,
    val defaultWorkingDirectory: String = "/home/khalinux",
    val bindMounts: List<BindMount> = emptyList(),
    val baseEnvironment: Map<String, String> = mapOf(
        "HOME" to "/home/khalinux",
        "LANG" to "C.UTF-8",
        "LC_ALL" to "C.UTF-8",
        // Pipes não são PTY; TERM=dumb evita sequências de controle espúrias.
        "TERM" to "dumb",
        "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
    ),
    val preserveHostUid: Boolean = false
) {
    fun validate() {
        require(prootExecutable.isFile && prootExecutable.canExecute()) {
            "PRoot ausente ou não executável: ${prootExecutable.path}"
        }
        if (qemuExecutable != null) {
            require(qemuExecutable.isFile && qemuExecutable.canExecute()) {
                "QEMU ausente ou não executável: ${qemuExecutable.path}"
            }
        }
        require(rootfsDirectory.isDirectory) { "RootFS ausente: ${rootfsDirectory.path}" }
        require(File(rootfsDirectory, "bin/bash").exists()) {
            "RootFS não contém /bin/bash: ${rootfsDirectory.path}"
        }
        require(defaultWorkingDirectory.startsWith("/")) {
            "defaultWorkingDirectory deve ser absoluto no RootFS"
        }
        bindMounts.forEach { it.validate() }
    }

    fun commandFor(request: CommandRequest): List<String> {
        require(request.workingDirectory.startsWith("/")) {
            "workingDirectory deve ser absoluto no RootFS"
        }
        val args = mutableListOf<String>()
        args += prootExecutable.absolutePath
        if (!preserveHostUid) args += "-0"
        // Usar apenas flags portáteis; opções específicas do PRoot Android serão validadas depois.
        qemuExecutable?.let { args += listOf("-q", it.absolutePath) }
        args += listOf("-r", rootfsDirectory.absolutePath)
        bindMounts.forEach { mount -> args += listOf("-b", mount.asArgument()) }
        args += listOf("-w", request.workingDirectory)
        args += "/usr/bin/env"
        args += "-i"
        val env = baseEnvironment + request.environment
        env.toSortedMap().forEach { (key, value) ->
            require(ENVIRONMENT_KEY.matches(key)) { "nome de variável inválido: $key" }
            args += "$key=$value"
        }
        // O comando é um argumento único do Bash interno; não há shell intermediário no host.
        args += listOf("/bin/bash", "--noprofile", "--norc", "-lc", request.shellCommand)
        return args
    }

    companion object {
        private val ENVIRONMENT_KEY = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}

data class BindMount(val hostPath: File, val guestPath: String, val readOnly: Boolean = true) {
    fun validate() {
        require(hostPath.exists()) { "origem de bind inexistente: ${hostPath.path}" }
        require(guestPath.startsWith("/")) { "destino de bind deve ser absoluto" }
    }

    fun asArgument(): String = "${hostPath.absolutePath}:${guestPath}${if (readOnly) ":ro" else ""}"
}
