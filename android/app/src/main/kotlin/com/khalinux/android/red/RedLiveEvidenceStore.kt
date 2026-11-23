package com.khalinux.android.red

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Evidências efêmeras compartilhadas entre RED e BLUE no processo atual. Nunca é persistida. */
data class RedLiveEvidenceSnapshot(
    val sessionId: String? = null,
    val portalMode: RedPortalMode = RedPortalMode.LOCAL_LOGIN,
    val requests: List<RedRequestObservation> = emptyList(),
    val latestSubmission: RedSyntheticSubmission? = null,
    val events: List<RedEvent> = emptyList()
)

object RedLiveEvidenceStore {
    private val mutableState = MutableStateFlow(RedLiveEvidenceSnapshot())
    val state: StateFlow<RedLiveEvidenceSnapshot> = mutableState.asStateFlow()

    @Synchronized
    fun begin(session: RedSession) {
        mutableState.value = RedLiveEvidenceSnapshot(
            sessionId = session.id,
            portalMode = session.portalMode,
            events = session.events
        )
    }

    @Synchronized
    fun updateSession(session: RedSession) {
        if (mutableState.value.sessionId != session.id) return
        mutableState.value = mutableState.value.copy(
            portalMode = session.portalMode,
            events = session.events
        )
    }

    @Synchronized
    fun updateRequests(requests: List<RedRequestObservation>) {
        mutableState.value = mutableState.value.copy(requests = requests.takeLast(100))
    }

    @Synchronized
    fun updateSubmission(submission: RedSyntheticSubmission?) {
        mutableState.value = mutableState.value.copy(latestSubmission = submission)
    }

    @Synchronized
    fun clear() {
        mutableState.value = RedLiveEvidenceSnapshot()
    }
}
