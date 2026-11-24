package com.khalinux.android.red

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedPortalServerHeartbeatTest {
    @Test
    fun acceptsMatchingSessionCookieAmongOtherCookies() {
        assertTrue(
            hasValidHeartbeatSessionCookie(
                "theme=dark; kl_lab_session=abc123; preference=compact",
                "abc123"
            )
        )
    }

    @Test
    fun rejectsWrongSessionToken() {
        assertFalse(hasValidHeartbeatSessionCookie("kl_lab_session=wrong", "expected"))
    }

    @Test
    fun rejectsMissingOrBlankCookie() {
        assertFalse(hasValidHeartbeatSessionCookie(null, "expected"))
        assertFalse(hasValidHeartbeatSessionCookie("", "expected"))
        assertFalse(hasValidHeartbeatSessionCookie("theme=dark", "expected"))
    }

    @Test
    fun rejectsMissingOrBlankExpectedToken() {
        assertFalse(hasValidHeartbeatSessionCookie("kl_lab_session=abc123", null))
        assertFalse(hasValidHeartbeatSessionCookie("kl_lab_session=abc123", ""))
    }

    @Test
    fun rejectsAmbiguousDuplicateSessionCookiesInEitherOrder() {
        assertFalse(
            hasValidHeartbeatSessionCookie(
                "kl_lab_session=wrong; kl_lab_session=abc123",
                "abc123"
            )
        )
        assertFalse(
            hasValidHeartbeatSessionCookie(
                "kl_lab_session=abc123; kl_lab_session=wrong",
                "abc123"
            )
        )
    }
}
