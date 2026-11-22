package com.khalinux.runtime.startup

import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Escrito em 8.3; **não executado** (sem `kotlinc`/rede): entra na validação final. */
class AssetSyncStampTest {
    private fun stamp() = AssetSyncStamp(Files.createTempDirectory("stamp").resolve("fixtures.stamp").toFile())

    @Test fun copiesOnceThenSkips() {
        val s = stamp(); var copies = 0
        assertTrue(s.syncIfStale("v1") { copies++ })
        assertFalse(s.syncIfStale("v1") { copies++ })
        assertEquals(1, copies)
    }

    @Test fun copiesAgainWhenStampChanges() {
        val s = stamp(); var copies = 0
        s.syncIfStale("v1") { copies++ }
        assertTrue(s.syncIfStale("v2") { copies++ })
        assertEquals(2, copies)
    }

    @Test fun failedCopyDoesNotMarkAsCurrent() {
        val s = stamp()
        assertFailsWith<IllegalStateException> { s.syncIfStale("v1") { error("falhou") } }
        assertFalse(s.isCurrent("v1"))
    }
}
