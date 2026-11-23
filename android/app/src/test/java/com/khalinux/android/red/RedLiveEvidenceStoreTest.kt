package com.khalinux.android.red

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RedLiveEvidenceStoreTest {
    @Before
    fun resetStore() {
        RedLiveEvidenceStore.clear()
    }

    @Test
    fun sharesSessionRequestsAndSyntheticSubmissionOnlyInMemory() {
        val session = RedSession(
            operationId = "red-wifi-public-01",
            portalMode = RedPortalMode.OPEN_ACCESS
        )
        RedLiveEvidenceStore.begin(session)
        RedLiveEvidenceStore.updateRequests(
            listOf(
                RedRequestObservation(
                    method = "GET",
                    path = "/",
                    statusCode = 200,
                    bodyBytes = 0
                )
            )
        )
        RedLiveEvidenceStore.updateSubmission(
            RedSyntheticSubmission(
                fields = mapOf("demo_id" to "aluno-demo", "demo_code" to "LAB-2048"),
                accepted = true,
                message = "Campos sintéticos recebidos."
            )
        )

        val snapshot = RedLiveEvidenceStore.state.value
        assertEquals(session.id, snapshot.sessionId)
        assertEquals(RedPortalMode.OPEN_ACCESS, snapshot.portalMode)
        assertEquals(1, snapshot.requests.size)
        assertEquals("aluno-demo", snapshot.latestSubmission?.fields?.get("demo_id"))
    }

    @Test
    fun clearRemovesLiveValuesAndSessionReference() {
        val session = RedSession(operationId = "red-wifi-public-01")
        RedLiveEvidenceStore.begin(session)
        RedLiveEvidenceStore.updateSubmission(
            RedSyntheticSubmission(
                fields = mapOf("demo_id" to "aluno-demo", "demo_code" to "LAB-2048"),
                accepted = true,
                message = "Campos sintéticos recebidos."
            )
        )

        RedLiveEvidenceStore.clear()

        assertNull(RedLiveEvidenceStore.state.value.sessionId)
        assertNull(RedLiveEvidenceStore.state.value.latestSubmission)
        assertEquals(0, RedLiveEvidenceStore.state.value.requests.size)
    }
}
