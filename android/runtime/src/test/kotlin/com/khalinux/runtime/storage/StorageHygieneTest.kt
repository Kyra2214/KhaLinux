package com.khalinux.runtime.storage

import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Escrito em 8.1; **não executado** (sem `kotlinc`/rede): entra na validação final. */
class StorageHygieneTest {
    private val now = 1_000_000_000_000L
    private fun tmp() = Files.createTempDirectory("hygiene").toFile()
    private fun File.write(bytes: Int, ageMillis: Long = 0L): File {
        parentFile.mkdirs(); writeBytes(ByteArray(bytes)); setLastModified(now - ageMillis); return this
    }

    private fun hygiene(runtime: File, home: File? = null, pkgTmp: File? = null) =
        StorageHygiene(runtime, home, pkgTmp, clock = { now })

    @Test fun removesStagingAndKeepsInstalledData() {
        val runtime = tmp()
        File(runtime, "rootfs-arm64/bin").mkdirs()
        File(runtime, "rootfs-arm64/bin/bash").write(10)
        File(runtime, ".rootfs-arm64.staging/x").write(100)
        val result = hygiene(runtime).cleanup(installRunning = false)
        assertFalse(File(runtime, ".rootfs-arm64.staging").exists())
        assertTrue(File(runtime, "rootfs-arm64/bin/bash").isFile)
        assertEquals(100L, result.freedBytes)
    }

    @Test fun nothingIsRemovedWhileInstalling() {
        val runtime = tmp()
        File(runtime, ".rootfs-arm64.staging/x").write(5)
        val result = hygiene(runtime).cleanup(installRunning = true)
        assertTrue(result.removed.isEmpty())
        assertTrue(File(runtime, ".rootfs-arm64.staging/x").exists())
    }

    @Test fun partialDownloadKeptUntilOld() {
        val runtime = tmp()
        val fresh = File(runtime, ".rootfs-arm64.download").write(50, ageMillis = StorageHygiene.DAY)
        hygiene(runtime).cleanup(false)
        assertTrue(fresh.exists())
        fresh.setLastModified(now - 8 * StorageHygiene.DAY)
        hygiene(runtime).cleanup(false)
        assertFalse(fresh.exists())
    }

    @Test fun previousKeptWhenDestinationMissing() {
        val runtime = tmp()
        val previous = File(runtime, ".rootfs-arm64.previous/bin/bash").write(7)
        hygiene(runtime).cleanup(false)
        assertTrue(previous.exists(), "pode ser a única cópia utilizável")
        File(runtime, "rootfs-arm64").mkdirs()
        hygiene(runtime).cleanup(false)
        assertFalse(File(runtime, ".rootfs-arm64.previous").exists())
    }

    @Test fun neverTouchesHome() {
        val runtime = tmp(); val home = tmp()
        val note = File(home, "reports/nota.md").write(20, ageMillis = 365 * StorageHygiene.DAY)
        File(runtime, ".proot.staging").write(3)
        hygiene(runtime, home).cleanup(false)
        assertTrue(note.exists())
    }

    @Test fun oldPackageTempRemovedRecentKept() {
        val runtime = tmp(); val pkg = tmp()
        val old = File(pkg, "a.part").write(10, ageMillis = 2 * StorageHygiene.DAY)
        val recent = File(pkg, "b.part").write(10, ageMillis = 1000)
        hygiene(runtime, null, pkg).cleanup(false)
        assertFalse(old.exists()); assertTrue(recent.exists())
    }

    @Test fun reportSeparatesPreservedFromReclaimable() {
        val runtime = tmp()
        File(runtime, "rootfs-arm64/bin/bash").write(1000)
        File(runtime, ".proot.staging").write(40)
        val report = hygiene(runtime).report()
        assertEquals(40L, report.reclaimableBytes)
        assertTrue(report.totalBytes >= 1040L)
    }
}
