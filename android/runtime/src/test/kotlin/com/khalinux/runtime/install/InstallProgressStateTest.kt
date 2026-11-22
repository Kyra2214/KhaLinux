package com.khalinux.runtime.install

import java.nio.file.Files
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstallProgressStateTest {
    @Test fun globalPercentIsMonotonicAcrossPhases() {
        val steps = listOf(
            InstallPhase.CHECKING to 0.0,
            InstallPhase.DOWNLOADING_PROOT to 1.0,
            InstallPhase.DOWNLOADING_ROOTFS to 0.0,
            InstallPhase.DOWNLOADING_ROOTFS to 0.5,
            InstallPhase.DOWNLOADING_ROOTFS to 1.0,
            InstallPhase.VERIFYING to 1.0,
            InstallPhase.EXTRACTING to 0.0,
            InstallPhase.EXTRACTING to 1.0,
            InstallPhase.FINALIZING to 1.0,
            InstallPhase.READY to 1.0
        ).map { (phase, fraction) -> InstallProgressCalculator.percent(phase, fraction) }
        assertEquals(steps.sorted(), steps)
        assertEquals(0, steps.first())
        assertEquals(100, steps.last())
    }

    @Test fun fractionIsClampedAndSafe() {
        assertEquals(0.0, InstallProgressCalculator.fraction(5, null))
        assertEquals(0.0, InstallProgressCalculator.fraction(5, 0))
        assertEquals(1.0, InstallProgressCalculator.fraction(20, 10))
        assertEquals(3, InstallProgressCalculator.percent(InstallPhase.DOWNLOADING_ROOTFS, Double.NaN))
        assertEquals(58, InstallProgressCalculator.percent(InstallPhase.DOWNLOADING_ROOTFS, 5.0))
    }

    @Test fun stateStorePersistsAndSurvivesCorruption() {
        val dir = Files.createTempDirectory("khalinux-state").toFile()
        try {
            val file = dir.resolve("runtime/install-state.properties")
            val store = InstallStateStore(file)
            assertNull(store.read())
            store.write(InstallPhase.DOWNLOADING_ROOTFS, now = 42L)
            assertEquals(StoredInstallState(InstallPhase.DOWNLOADING_ROOTFS, null, 42L), store.read())
            store.write(InstallPhase.FAILED, "sem rede", now = 43L)
            assertEquals("sem rede", store.read()!!.message)
            file.writeText("phase=NAO_EXISTE\n")
            assertNull(store.read())
            assertTrue(!file.parentFile.resolve("install-state.properties.tmp").exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
