package com.khalinux.android.red

import org.junit.Assert.assertEquals
import org.junit.Test

class RedSystemHotspotControllerTest {
    @Test
    fun selectsSingleNewAddressOutsideDefaultNetwork() {
        val baseline = setOf("wlan0" to "192.168.1.20")
        val current = baseline + ("wlan1" to "192.168.43.1")

        assertEquals(
            setOf("wlan1" to "192.168.43.1"),
            selectSystemHotspotCandidates(current, baseline, setOf("192.168.1.20"))
        )
    }

    @Test
    fun rejectsNewAddressBelongingToDefaultNetwork() {
        val baseline = setOf("wlan0" to "192.168.1.20")
        val current = baseline + ("wlan1" to "192.168.1.21")

        assertEquals(
            emptySet<Pair<String, String>>(),
            selectSystemHotspotCandidates(current, baseline, setOf("192.168.1.20", "192.168.1.21"))
        )
    }

    @Test
    fun recognizesSingleApNamedInterfaceEvenWhenAlreadyPresentInBaseline() {
        val ap = "ap0" to "192.168.43.1"
        val current = setOf(ap, "wlan0" to "192.168.1.20")

        assertEquals(
            setOf(ap),
            selectSystemHotspotCandidates(current, current, setOf("192.168.1.20"))
        )
    }

    @Test
    fun preservesMultipleApCandidatesSoCallerFailsClosed() {
        val candidates = setOf(
            "ap0" to "192.168.43.1",
            "softap0" to "192.168.44.1"
        )

        assertEquals(
            candidates,
            selectSystemHotspotCandidates(candidates, emptySet(), emptySet())
        )
    }

    @Test
    fun returnsNoCandidateWhenOnlyBaselineStationInterfaceExists() {
        val current = setOf("wlan0" to "192.168.1.20")

        assertEquals(
            emptySet<Pair<String, String>>(),
            selectSystemHotspotCandidates(current, current, emptySet())
        )
    }
}
