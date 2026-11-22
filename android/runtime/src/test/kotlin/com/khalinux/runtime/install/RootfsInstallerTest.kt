package com.khalinux.runtime.install

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootfsInstallerTest {
    private val installer = RootfsInstaller()

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
        assertFalse(absoluteGuestTarget.startsWith(root.parent))
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
