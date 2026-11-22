package com.khalinux.runtime.install

import java.nio.file.Files
import java.io.File
import java.io.FileOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RootfsInstallerTest {
    private val installer = RootfsInstaller()

    @Test
    fun extractsMinimalGzipTarAndKeepsExecutableBit() {
        val temp = Files.createTempDirectory("khalinux-tar-test").toFile()
        val archive = File(temp, "rootfs.tar.gz")
        GzipCompressorOutputStream(FileOutputStream(archive)).use { gzip ->
            TarArchiveOutputStream(gzip).use { tar ->
                val rootEntry = TarArchiveEntry("./")
                tar.putArchiveEntry(rootEntry)
                tar.closeArchiveEntry()
                val dir = TarArchiveEntry("bin/")
                tar.putArchiveEntry(dir)
                tar.closeArchiveEntry()
                val bash = TarArchiveEntry("bin/bash")
                bash.size = 3
                bash.mode = 0b111101101
                tar.putArchiveEntry(bash)
                tar.write(byteArrayOf(1, 2, 3))
                tar.closeArchiveEntry()
            }
        }
        val destination = File(temp, "unpacked")
        assertTrue(destination.mkdir())
        assertEquals(3L, installer.extractSafely(archive, destination))
        assertTrue(File(destination, "bin/bash").isFile)
        assertTrue(File(destination, "bin/bash").canExecute())
    }

    @Test
    fun archiveEntryMustStayInsideRoot() {
        val root = Files.createTempDirectory("khalinux-rootfs-test").toRealPath()
        assertEquals(root.resolve("usr/bin/bash"), installer.resolveEntry(root, "usr/bin/bash"))
        listOf("../outside", "usr/../../outside", "/etc/passwd").forEach { path ->
            val rejected = runCatching { installer.resolveEntry(root, path) }.isFailure
            assertTrue("path should be rejected: " + path, rejected)
        }
    }

    @Test
    fun absoluteGuestSymlinkIsMappedInsideRoot() {
        val root = Files.createTempDirectory("khalinux-rootfs-link").toRealPath()
        val parent = root.resolve("usr/bin")
        val absoluteGuestTarget = installer.resolveLink(root, parent, "/usr/bin/bash.distrib")
        assertEquals(root.resolve("usr/bin/bash.distrib"), absoluteGuestTarget)
        assertTrue(absoluteGuestTarget.startsWith(root))
    }

    @Test
    fun escapingSymlinkIsRejected() {
        val root = Files.createTempDirectory("khalinux-rootfs-link-escape").toRealPath()
        val parent = root.resolve("usr/bin")
        assertTrue(runCatching {
            installer.resolveLink(root, parent, "../../../../../../outside")
        }.isFailure)
    }
}
