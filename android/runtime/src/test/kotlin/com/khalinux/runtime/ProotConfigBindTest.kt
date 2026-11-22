package com.khalinux.runtime

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotConfigBindTest {
    @Test
    fun commandUsesOnlySupportedBindSyntax() {
        val directory = Files.createTempDirectory("khalinux-proot-config").toFile()
        try {
            val proot = File(directory, "proot").apply {
                writeText("placeholder")
                setExecutable(true)
            }
            val rootfs = File(directory, "rootfs").apply { mkdirs() }
            File(rootfs, "bin").mkdirs()
            File(rootfs, "bin/bash").writeText("placeholder")
            val hostNull = File(directory, "host-null").apply { writeText("") }
            val bind = BindMount(hostNull, "/dev/null")
            val config = ProotConfig(proot, rootfs, bindMounts = listOf(bind))

            config.validate()
            val command = config.commandFor(CommandRequest(shellCommand = "echo ready"))
            val bindIndex = command.indexOf("-b")

            assertTrue(bindIndex >= 0)
            assertEquals("${hostNull.absolutePath}:/dev/null", command[bindIndex + 1])
            assertFalse(command[bindIndex + 1].endsWith(":ro"))
            assertTrue(command.contains("TERM=ansi"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun commandEmulatesHardLinksForDpkgOnAndroid() {
        val directory = Files.createTempDirectory("khalinux-proot-link").toFile()
        try {
            val proot = File(directory, "proot").apply {
                writeText("placeholder")
                setExecutable(true)
            }
            val rootfs = File(directory, "rootfs").apply { mkdirs() }
            File(rootfs, "bin").mkdirs()
            File(rootfs, "bin/bash").writeText("placeholder")
            val command = ProotConfig(proot, rootfs).commandFor(CommandRequest(shellCommand = "true"))

            // Sem isso o dpkg falha em /var/lib/dpkg/status-old (link() negado pelo SELinux).
            assertTrue(command.contains("--link2symlink"))
            assertTrue(command.indexOf("--link2symlink") < command.indexOf("-r"))
        } finally {
            directory.deleteRecursively()
        }
    }
}
