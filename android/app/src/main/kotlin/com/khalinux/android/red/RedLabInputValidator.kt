package com.khalinux.android.red

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class RedSyntheticValidationResult(
    val accepted: Boolean,
    val fields: Map<String, String>,
    val message: String
)

object RedLabInputValidator {
    private val syntheticKeys = setOf("email", "password")

    fun validateSyntheticForm(body: ByteArray, contentType: String?): RedSyntheticValidationResult {
        if (contentType?.substringBefore(';')?.trim()
                ?.equals("application/x-www-form-urlencoded", ignoreCase = true) != true
        ) {
            return RedSyntheticValidationResult(false, emptyMap(), "Tipo de formulário não permitido.")
        }
        if (body.size > MAX_FORM_BYTES) {
            return RedSyntheticValidationResult(false, emptyMap(), "Formulário acima do limite.")
        }
        val decoded = runCatching { String(body, StandardCharsets.UTF_8) }.getOrDefault("")
        val values = decodePairs(decoded) ?: return RedSyntheticValidationResult(
            false, emptyMap(), "Estrutura de formulário inválida."
        )
        if (values.keys != syntheticKeys) {
            return RedSyntheticValidationResult(false, emptyMap(), "Campos não permitidos.")
        }
        val email = values["email"].orEmpty().trim().lowercase()
        val password = values["password"].orEmpty()
        val validEmail = email.length <= 254 &&
            email.matches(Regex("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$"))
        val validPassword = password.length in 8..64 && password.none { it.isISOControl() }
        return if (validEmail && validPassword) {
            val local = email.substringBefore('@')
            val domain = email.substringAfter('@')
            val maskedLocal = when {
                local.length <= 1 -> "*"
                local.length == 2 -> local.first() + "*"
                else -> local.first() + "***" + local.last()
            }
            RedSyntheticValidationResult(
                true,
                mapOf("email" to "$maskedLocal@$domain", "auth_method" to "email_password_demo"),
                "Dados de demonstração validados localmente. Nenhuma senha foi armazenada."
            )
        } else {
            RedSyntheticValidationResult(
                false,
                emptyMap(),
                "Informe um e-mail válido e uma senha de 8 a 64 caracteres."
            )
        }
    }

    /** Valida somente a escolha por categoria; nunca recebe nem persiste valores de cookies. */
    fun validateCookieConsent(body: ByteArray, contentType: String?): RedCookieConsent? {
        if (contentType?.substringBefore(';')?.trim()?.equals("application/x-www-form-urlencoded", ignoreCase = true) != true) return null
        if (body.size > MAX_FORM_BYTES) return null
        val encoded = runCatching { String(body, StandardCharsets.UTF_8) }.getOrNull() ?: return null
        val values = decodePairs(encoded) ?: return null
        if (values.keys != setOf("choice", "analytics", "preferences", "personalization")) return null
        val choice = values["choice"] ?: return null
        if (choice !in setOf("accept_all", "reject_optional", "custom")) return null
        val analytics = values["analytics"] ?: return null
        val preferences = values["preferences"] ?: return null
        val personalization = values["personalization"] ?: return null
        if (listOf(analytics, preferences, personalization).any { it !in setOf("0", "1") }) return null
        if (choice == "accept_all" && listOf(analytics, preferences, personalization).any { it != "1" }) return null
        if (choice == "reject_optional" && listOf(analytics, preferences, personalization).any { it != "0" }) return null
        val typedChoice = when (choice) {
            "accept_all" -> RedCookieChoice.ACCEPT_ALL
            "reject_optional" -> RedCookieChoice.REJECT_OPTIONAL
            else -> RedCookieChoice.CUSTOM
        }
        return RedCookieConsent(
            choice = typedChoice,
            analytics = analytics == "1",
            preferences = preferences == "1",
            personalization = personalization == "1"
        )
    }

    /** Recebe somente recurso e estado; nunca aceita coordenadas, mídia ou conteúdo do navegador. */
    fun validateWebPermission(body: ByteArray, contentType: String?): RedWebPermissionObservation? {
        if (contentType?.substringBefore(';')?.trim()
                ?.equals("application/x-www-form-urlencoded", ignoreCase = true) != true
        ) return null
        if (body.size > MAX_FORM_BYTES) return null
        val encoded = runCatching { String(body, StandardCharsets.UTF_8) }.getOrNull() ?: return null
        val values = decodePairs(encoded) ?: return null
        if (values.keys != setOf("resource", "state")) return null
        val resource = when (values["resource"]) {
            "camera" -> RedWebPermissionResource.CAMERA
            "microphone" -> RedWebPermissionResource.MICROPHONE
            "location" -> RedWebPermissionResource.LOCATION
            else -> return null
        }
        val state = when (values["state"]) {
            "requested" -> RedWebPermissionState.REQUESTED
            "granted" -> RedWebPermissionState.GRANTED
            "denied" -> RedWebPermissionState.DENIED
            "cancelled" -> RedWebPermissionState.CANCELLED
            "unavailable" -> RedWebPermissionState.UNAVAILABLE
            "revoked" -> RedWebPermissionState.REVOKED
            else -> return null
        }
        return RedWebPermissionObservation(resource, state)
    }

    private fun decodePairs(encoded: String): Map<String, String>? {
        if (encoded.isBlank()) return null
        val pairs = encoded.split('&')
        if (pairs.isEmpty() || pairs.size > 8) return null
        val values = linkedMapOf<String, String>()
        for (pair in pairs) {
            val separator = pair.indexOf('=')
            if (separator <= 0) return null
            val key = runCatching { URLDecoder.decode(pair.substring(0, separator), "UTF-8") }.getOrNull()
                ?: return null
            val value = runCatching { URLDecoder.decode(pair.substring(separator + 1), "UTF-8") }.getOrNull()
                ?: return null
            if (key.isBlank() || key in values) return null
            values[key] = value
        }
        return values
    }

    private const val MAX_FORM_BYTES = 4096
}
