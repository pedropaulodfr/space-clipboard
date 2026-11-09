package com.spaceclipboard.app.domain

import com.spaceclipboard.app.domain.model.ContentType
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.UUID

sealed interface TextValidation {
    data class Valid(val text: String, val type: ContentType) : TextValidation
    data class Invalid(val code: String, val message: String) : TextValidation
}

object ClipboardValidator {
    const val MAX_TEXT_BYTES = 64 * 1024

    fun validate(raw: String?): TextValidation {
        if (raw == null) {
            return TextValidation.Invalid("empty_content", "O conteúdo está vazio.")
        }
        val text = raw
        if (text.isBlank()) {
            return TextValidation.Invalid("empty_content", "O conteúdo está vazio.")
        }
        if (text.any { it == '\u0000' }) {
            return TextValidation.Invalid("invalid_content", "O conteúdo contém caracteres inválidos.")
        }
        val bytes = text.toByteArray(StandardCharsets.UTF_8).size
        if (bytes > MAX_TEXT_BYTES) {
            return TextValidation.Invalid(
                "content_too_large",
                "O conteúdo excede o limite de 64 KB.",
            )
        }
        return TextValidation.Valid(text, detectType(text))
    }

    fun validateId(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return runCatching { UUID.fromString(raw).toString() }.getOrNull()
    }

    fun sanitizeName(raw: String?, fallback: String): String {
        val cleaned = raw
            ?.replace(Regex("[\\u0000-\\u001F\\u007F]"), " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(48)
        return if (cleaned.isNullOrBlank()) fallback else cleaned
    }

    fun detectType(text: String): ContentType {
        val candidate = text.trim()
        if (!candidate.startsWith("http://", ignoreCase = true) &&
            !candidate.startsWith("https://", ignoreCase = true)
        ) {
            return ContentType.TEXT
        }
        return runCatching {
            val uri = URI(candidate)
            if ((uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()) {
                ContentType.URL
            } else {
                ContentType.TEXT
            }
        }.getOrDefault(ContentType.TEXT)
    }
}
