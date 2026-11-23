package com.khalinux.runtime

import java.io.File

/**
 * Expõe os nós de /dev necessários ao shell e o /proc do Android (ADR-0005, revisada).
 * Não monta /dev inteiro nem /sys. O PRoot não implementa montagem somente leitura.
 */
object AndroidBindMountPolicy {
    private val allowedDeviceNames = listOf(
        "null",
        "zero",
        "random",
        "urandom",
        "tty",
        "pts"
    )

    fun defaultMounts(hostDevRoot: File = File("/dev"), hostProc: File = File("/proc")): List<BindMount> {
        require(hostDevRoot.isAbsolute) { "Diretório /dev do host deve ser absoluto" }
        val devices = allowedDeviceNames.mapNotNull { name ->
            val hostPath = File(hostDevRoot, name)
            if (hostPath.exists()) BindMount(hostPath, "/dev/$name") else null
        }
        // /proc: necessário a ps/top/free/ss, lynis, rkhunter, chkrootkit e a scripts de pós-instalação do dpkg.
        return devices + (if (hostProc.isDirectory) listOf(BindMount(hostProc, "/proc")) else emptyList())
    }
}
