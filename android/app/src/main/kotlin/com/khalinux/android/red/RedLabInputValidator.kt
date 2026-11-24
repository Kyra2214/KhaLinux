package com.khalinux.android.red

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class RedSyntheticValidationResult(
    val accepted: Boolean,
    val fields: Map<String, String>,
    val message: String
)

object RedLabInputValidator {
    private val syntheticKeys = setOf("demo_id", "demo_code")

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
        val demoId = values["demo_id"].orEmpty()
        val demoCode = values["demo_code"].orEmpty()
        val accepted = demoId.matches(Regex("^aluno-[a-z0-9-]{1,24}$")) &&
            demoCode.matches(Regex("^LAB-[0-9]{4}$"))
        return if (accepted) {
            RedSyntheticValidationResult(
                true,
                mapOf("demo_id" to demoId, "demo_code" to demoCode),
                "Campos sintéticos recebidos e validados localmente."
            )
        } else {
            RedSyntheticValidationResult(
                false,
                emptyMap(),
                "Envio rejeitado: use identificador aluno-... e código LAB-0000."
            )
        }
    }

    /** Valida somente a escolha por categoria; nunca recebe nem persiste valores de cookies. */
    fun validateCookieConsent(body: ByteArray, contentType: String?): String? {
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
        fun label(value: String) = if (value == "1") "aceito" else "recusado"
        return "Escolha=$choice; análise=${'${'}label(analytics)}; preferências=${'${'}label(preferences)}; personalização=${'${'}label(personalization)}"
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
    private const val MAX_QUERY_CHARS = 1024
}
