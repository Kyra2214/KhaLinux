package com.khalinux.android.red

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedLabInputValidatorTest {
    @Test
    fun acceptsOnlyExpectedSyntheticParticipantName() {
        val result = RedLabInputValidator.validateSyntheticForm(
            "participant_name=Aluno%20Demo".toByteArray(),
            "application/x-www-form-urlencoded; charset=UTF-8"
        )

        assertTrue(result.accepted)
        assertEquals(mapOf("participant_name" to "Aluno Demo"), result.fields)
    }

    @Test
    fun rejectsCredentialAndUnknownFieldsWithoutReturningValues() {
        val credentials = RedLabInputValidator.validateSyntheticForm(
            "email=user%40example.com&password=Example123".toByteArray(),
            "application/x-www-form-urlencoded"
        )
        val unknown = RedLabInputValidator.validateSyntheticForm(
            "participant_name=Aluno&token=unexpected".toByteArray(),
            "application/x-www-form-urlencoded"
        )

        assertFalse(credentials.accepted)
        assertFalse(unknown.accepted)
        assertTrue(credentials.fields.isEmpty())
        assertTrue(unknown.fields.isEmpty())
    }

    @Test
    fun rejectsDuplicateFieldsUnsupportedContentTypeAndOversizedBody() {
        val duplicate = RedLabInputValidator.validateSyntheticForm(
            "participant_name=Aluno&participant_name=Outra".toByteArray(),
            "application/x-www-form-urlencoded"
        )
        val wrongType = RedLabInputValidator.validateSyntheticForm(
            "participant_name=Aluno".toByteArray(),
            "application/json"
        )
        val oversized = RedLabInputValidator.validateSyntheticForm(
            ByteArray(4097) { 'x'.code.toByte() },
            "application/x-www-form-urlencoded"
        )

        assertFalse(duplicate.accepted)
        assertFalse(wrongType.accepted)
        assertFalse(oversized.accepted)
    }

    @Test
    fun validatesCookieConsentChoicesAndCategories() {
        val accepted = RedLabInputValidator.validateCookieConsent(
            "choice=accept_all&analytics=1&preferences=1&personalization=1".toByteArray(),
            "application/x-www-form-urlencoded"
        )
        val rejected = RedLabInputValidator.validateCookieConsent(
            "choice=reject_optional&analytics=0&preferences=0&personalization=0".toByteArray(),
            "application/x-www-form-urlencoded"
        )
        val custom = RedLabInputValidator.validateCookieConsent(
            "choice=custom&analytics=1&preferences=0&personalization=1".toByteArray(),
            "application/x-www-form-urlencoded"
        )

        assertEquals(RedCookieChoice.ACCEPT_ALL, accepted?.choice)
        assertEquals(RedCookieChoice.REJECT_OPTIONAL, rejected?.choice)
        assertEquals(RedCookieChoice.CUSTOM, custom?.choice)
        assertTrue(accepted?.let { it.analytics && it.preferences && it.personalization } == true)
        assertTrue(rejected?.let { !it.analytics && !it.preferences && !it.personalization } == true)
        assertTrue(custom?.let { it.analytics && !it.preferences && it.personalization } == true)
    }

    @Test
    fun rejectsInvalidCookieConsentCombinationsAndDuplicateKeys() {
        assertNull(
            RedLabInputValidator.validateCookieConsent(
                "choice=accept_all&analytics=1&preferences=0&personalization=1".toByteArray(),
                "application/x-www-form-urlencoded"
            )
        )
        assertNull(
            RedLabInputValidator.validateCookieConsent(
                "choice=custom&analytics=1&analytics=0&preferences=0&personalization=1".toByteArray(),
                "application/x-www-form-urlencoded"
            )
        )
    }

    @Test
    fun validatesOnlySupportedWebPermissionResourceAndState() {
        val camera = RedLabInputValidator.validateWebPermission(
            "resource=camera&state=granted".toByteArray(),
            "application/x-www-form-urlencoded"
        )
        val microphone = RedLabInputValidator.validateWebPermission(
            "resource=microphone&state=denied".toByteArray(),
            "application/x-www-form-urlencoded"
        )
        val location = RedLabInputValidator.validateWebPermission(
            "resource=location&state=unavailable".toByteArray(),
            "application/x-www-form-urlencoded"
        )

        assertEquals(RedWebPermissionObservation(RedWebPermissionResource.CAMERA, RedWebPermissionState.GRANTED), camera)
        assertEquals(RedWebPermissionObservation(RedWebPermissionResource.MICROPHONE, RedWebPermissionState.DENIED), microphone)
        assertEquals(RedWebPermissionObservation(RedWebPermissionResource.LOCATION, RedWebPermissionState.UNAVAILABLE), location)
    }

    @Test
    fun rejectsUnsupportedPermissionResourceStateAndExtraFields() {
        assertNull(
            RedLabInputValidator.validateWebPermission(
                "resource=network_scan&state=granted".toByteArray(),
                "application/x-www-form-urlencoded"
            )
        )
        assertNull(
            RedLabInputValidator.validateWebPermission(
                "resource=camera&state=allowed".toByteArray(),
                "application/x-www-form-urlencoded"
            )
        )
        assertNull(
            RedLabInputValidator.validateWebPermission(
                "resource=camera&state=granted&latitude=1".toByteArray(),
                "application/x-www-form-urlencoded"
            )
        )
    }
}
