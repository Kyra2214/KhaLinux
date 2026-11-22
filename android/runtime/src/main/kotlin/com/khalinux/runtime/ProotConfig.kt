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
        // Emulador suporta o subconjunto ANSI/VT básico, não xterm completo.
        "TERM" to "ansi",
        "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
    ),
    val preserveHostUid: Boolean = false,
    /** Diretório temporário privado (PROOT_TMP_DIR/TMPDIR); padrão: `tmp` ao lado do executável. */
    val tempDirectory: File? = null,
    /** Variáveis do lado do host exigidas pela origem do PRoot (ver [ProotLocation.environment]). */
    val prootEnvironment: Map<String, String> = emptyMap()
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
        require(File(rootfsDirectory, "bin/bash").isFile) {
            val detail = com.khalinux.runtime.install.RootfsValidator.problem(rootfsDirectory)
            "RootFS não contém /bin/bash: ${rootfsDirectory.path}" + (detail?.let { " ($it)" } ?: "")
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
        // Encerra os processos convidados quando o PRoot termina (evita órfãos no Android).
        args += "--kill-on-exit"
        // Usar apenas flags portáteis; opções específicas do PRoot Android serão validadas depois.
        qemuExecutable?.let { args += listOf("-q", it.absolutePath) }
        args += listOf("-r", rootfsDirectory.absolutePath)
        bindMounts.forEach { mount -> args += listOf("-b", mount.asArgument()) }
        args += listOf("-w", request.workingDirectory)
        args += "/usr/bin/env"
        args += "-i"
        val env = baseEnvironment + request.environment
        val effectiveEnvironment = if (request.interactive && "PS1" !in request.environment) {
            env + ("PS1" to "khalinux:\\w\\$ ")
        } else {
            env
        }
        effectiveEnvironment.toSortedMap().forEach { (key, value) ->
            require(ENVIRONMENT_KEY.matches(key)) { "nome de variável inválido: $key" }
            args += "$key=$value"
        }
        return if (request.interactive) {
            args += listOf("/bin/bash", "--noprofile", "--norc", "-i")
            args
        } else {
            // O comando é um argumento único do Bash interno; não há shell intermediário no host.
            args + listOf("/bin/bash", "--noprofile", "--norc", "-lc", request.shellCommand)
        }
    }

    companion object {
        private val ENVIRONMENT_KEY = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}

data class BindMount(val hostPath: File, val guestPath: String) {
    fun validate() {
        require(hostPath.isAbsolute && hostPath.exists()) {
            "origem de bind ausente ou não absoluta: ${hostPath.path}"
        }
        require(!hostPath.absolutePath.contains(':')) { "origem de bind contém separador inválido" }
        require(
            guestPath.startsWith("/") &&
                guestPath != "/" &&
                !guestPath.endsWith("/") &&
                !guestPath.contains(':') &&
                !guestPath.contains('\u0000') &&
                !guestPath.contains("//") &&
                guestPath.split('/').none { it == "." || it == ".." }
        ) { "destino de bind deve ser absoluto e não pode escapar do RootFS" }
    }

    // PRoot -b não implementa montagem read-only. Não acrescentar sufixo :ro,
    // pois ele seria interpretado como parte do caminho de destino no guest.
    fun asArgument(): String = "${hostPath.absolutePath}:${guestPath}"
}
