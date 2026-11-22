package com.khalinux.runtime

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProotLauncherTest {
    @Test
    fun hostEnvironmentUsesWritablePrivateTmpDirectory() {
        val appFiles = Files.createTempDirectory("khalinux-proot-runtime").toFile()
        val proot = File(appFiles, "proot").apply {
            writeText("test executable placeholder")
            setExecutable(true)
        }
        val rootfs = File(appFiles, "rootfs").apply { mkdirs() }
        File(rootfs, "bin").mkdirs()
        File(rootfs, "bin/bash").writeText("test shell placeholder")

        try {
            val launcher = ProotLauncher(ProotConfig(prootExecutable = proot, rootfsDirectory = rootfs))
            val environment = launcher.hostEnvironment()
            val expectedTmp = File(appFiles, "tmp")

            assertEquals(expectedTmp.absolutePath, environment["TMPDIR"])
            assertTrue(expectedTmp.isDirectory)
            assertTrue(expectedTmp.canWrite())
        } finally {
            appFiles.deleteRecursively()
        }
    }
}
