package com.khalinux.runtime.install

import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Escrito em 8.5; **não executado** (sem `kotlinc`/rede): entra na validação final. */
class InstallRecoveryTest {
    private fun runtime() = Files.createTempDirectory("recovery").toFile()
    private fun File.file(bytes: Int = 100): File { parentFile.mkdirs(); writeBytes(ByteArray(bytes)); return this }

    @Test fun restoresRootfsWhenDestinationMissing() {
        val r = runtime()
        File(r, ".rootfs-arm64.previous/bin/bash").file()
        val result = InstallRecovery.recover(r)
        assertTrue(File(r, "rootfs-arm64/bin/bash").isFile)
        assertFalse(File(r, ".rootfs-arm64.previous").exists())
        assertTrue(result.recovered)
    }

    @Test fun neverOverwritesExistingDestination() {
        val r = runtime()
        File(r, "rootfs-arm64/usr/bin/bash").file(1)
        File(r, ".rootfs-arm64.previous/bin/bash").file(2)
        assertFalse(InstallRecovery.recover(r).recovered)
        assertTrue(File(r, ".rootfs-arm64.previous/bin/bash").isFile)
    }

    @Test fun ignoresInvalidPreviousRootfs() {
        val r = runtime()
        File(r, ".rootfs-arm64.previous/lixo.txt").file()
        assertFalse(InstallRecovery.recover(r).recovered)
        assertFalse(File(r, "rootfs-arm64").exists())
    }

    @Test fun restoresProot() {
        val r = runtime()
        File(r, ".proot.previous").file(200)
        assertTrue(InstallRecovery.recover(r).recovered)
        assertTrue(File(r, "proot").isFile)
    }

    @Test fun doesNothingWhileInstalling() {
        val r = runtime()
        File(r, ".rootfs-arm64.previous/bin/bash").file()
        assertFalse(InstallRecovery.recover(r, installRunning = true).recovered)
        assertFalse(File(r, "rootfs-arm64").exists())
    }

    @Test fun recordsInterruptedInstallState() {
        val r = runtime()
        val store = InstallStateStore(File(r, "install-state.properties"))
        store.write(InstallPhase.EXTRACTING)
        val result = InstallRecovery.recover(r, store)
        assertEquals(InstallPhase.FAILED, store.read()!!.phase)
        assertTrue(result.actions.any { it.contains("EXTRACTING") })
    }

    @Test fun readyStateIsLeftAlone() {
        val r = runtime()
        val store = InstallStateStore(File(r, "install-state.properties"))
        store.write(InstallPhase.READY)
        assertFalse(InstallRecovery.recover(r, store).recovered)
        assertEquals(InstallPhase.READY, store.read()!!.phase)
    }
}
