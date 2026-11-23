package com.khalinux.android.red

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedLabInputValidatorTest {
    @Test
    fun acceptsOnlyExpectedSyntheticFields() {
        val result = RedLabInputValidator.validateSyntheticForm(
            "demo_id=aluno-demo&demo_code=LAB-2048".toByteArray(),
            "application/x-www-form-urlencoded; charset=UTF-8"
        )

        assertTrue(result.accepted)
        assertEquals("aluno-demo", result.fields["demo_id"])
        assertEquals("LAB-2048", result.fields["demo_code"])
    }

    @Test
    fun rejectsRealisticCredentialFieldsAndNeverReturnsTheirValues() {
        val result = RedLabInputValidator.validateSyntheticForm(
            "email=user%40example.com&password=MyRealPassword123".toByteArray(),
            "application/x-www-form-urlencoded"
        )

        assertFalse(result.accepted)
        assertTrue(result.fields.isEmpty())
    }

    @Test
    fun rejectsUnknownAndDuplicateFields() {
        val unknown = RedLabInputValidator.validateSyntheticForm(
            "demo_id=aluno-demo&demo_code=LAB-2048&token=unexpected".toByteArray(),
            "application/x-www-form-urlencoded"
        )
        val duplicate = RedLabInputValidator.validateSyntheticForm(
            "demo_id=aluno-demo&demo_id=aluno-other&demo_code=LAB-2048".toByteArray(),
            "application/x-www-form-urlencoded"
        )

        assertFalse(unknown.accepted)
        assertFalse(duplicate.accepted)
        assertTrue(unknown.fields.isEmpty())
        assertTrue(duplicate.fields.isEmpty())
    }

    @Test
    fun rejectsUnsupportedContentTypeAndOversizedBody() {
        val wrongType = RedLabInputValidator.validateSyntheticForm(
            "demo_id=aluno-demo&demo_code=LAB-2048".toByteArray(),
            "application/json"
        )
        val oversized = RedLabInputValidator.validateSyntheticForm(
            ByteArray(4097) { 'x'.code.toByte() },
            "application/x-www-form-urlencoded"
        )

        assertFalse(wrongType.accepted)
        assertFalse(oversized.accepted)
    }

    @Test
    fun acceptsOnlyAuthenticatedParticipantEvents() {
        val token = "A1B2C3D4E5F6"
        val event = RedLabInputValidator.validateParticipantEvent(
            "token=$token&kind=camera&result=granted",
            token
        )

        assertEquals(RedParticipantEvent("camera", "granted"), event)
    }

    @Test
    fun rejectsInvalidTokenUnknownEventAndDuplicateQueryKeys() {
        val token = "A1B2C3D4E5F6"

        assertNull(
            RedLabInputValidator.validateParticipantEvent(
                "token=wrong&kind=camera&result=granted",
                token
            )
        )
        assertNull(
            RedLabInputValidator.validateParticipantEvent(
                "token=$token&kind=network_scan&result=granted",
                token
            )
        )
        assertNull(
            RedLabInputValidator.validateParticipantEvent(
                "token=$token&token=$token&kind=camera&result=granted",
                token
            )
        )
    }
}
