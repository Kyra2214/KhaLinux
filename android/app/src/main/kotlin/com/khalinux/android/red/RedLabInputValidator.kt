package com.khalinux.android.red

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class RedSyntheticValidationResult(
    val accepted: Boolean,
    val fields: Map<String, String>,
    val message: String
)

data class RedParticipantEvent(val kind: String, val result: String)

object RedLabInputValidator {
    private val syntheticKeys = setOf("demo_id", "demo_code")
    private val participantKinds = setOf("camera", "microphone", "location", "file")
    private val participantResults = setOf(
        "granted", "denied", "cancelled", "preview_started", "preview_stopped", "selected"
    )

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

    fun validateParticipantEvent(query: String, expectedToken: String?): RedParticipantEvent? {
        if (expectedToken.isNullOrBlank() || query.length > MAX_QUERY_CHARS) return null
        val values = decodePairs(query) ?: return null
        if (values.keys != setOf("token", "kind", "result")) return null
        val supplied = values["token"] ?: return null
        val tokenMatches = MessageDigest.isEqual(
            expectedToken.toByteArray(StandardCharsets.UTF_8),
            supplied.toByteArray(StandardCharsets.UTF_8)
        )
        if (!tokenMatches) return null
        val kind = values["kind"] ?: return null
        val result = values["result"] ?: return null
        if (kind !in participantKinds || result !in participantResults) return null
        return RedParticipantEvent(kind, result)
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
