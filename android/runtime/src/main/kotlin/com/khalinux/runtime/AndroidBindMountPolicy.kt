package com.khalinux.runtime

import java.io.File

/**
 * Expõe somente nós de dispositivo necessários ao shell.
 * Não monta /dev inteiro, /proc ou /sys do Android dentro do Debian.
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

    fun defaultMounts(hostDevRoot: File = File("/dev")): List<BindMount> {
        require(hostDevRoot.isAbsolute) { "Diretório /dev do host deve ser absoluto" }
        return allowedDeviceNames.mapNotNull { name ->
            val hostPath = File(hostDevRoot, name)
            if (hostPath.exists()) BindMount(hostPath, "/dev/$name") else null
        }
    }
}
