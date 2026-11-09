package com.spaceclipboard.app.domain

import com.spaceclipboard.app.domain.model.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardValidatorTest {
    @Test
    fun `accepts plain text and preserves whitespace`() {
        val result = ClipboardValidator.validate("  hello world\n")
        assertTrue(result is TextValidation.Valid)
        result as TextValidation.Valid
        assertEquals("  hello world\n", result.text)
        assertEquals(ContentType.TEXT, result.type)
    }

    @Test
    fun `detects http and https links`() {
        assertEquals(ContentType.URL, (ClipboardValidator.validate("https://youtube.com") as TextValidation.Valid).type)
        assertEquals(ContentType.URL, (ClipboardValidator.validate("http://192.168.0.2:8080/a") as TextValidation.Valid).type)
    }

    @Test
    fun `does not classify invalid urls as links`() {
        assertEquals(ContentType.TEXT, (ClipboardValidator.validate("https://") as TextValidation.Valid).type)
        assertEquals(ContentType.TEXT, (ClipboardValidator.validate("veja https://youtube.com") as TextValidation.Valid).type)
    }

    @Test
    fun `rejects empty and blank content`() {
        assertEquals("empty_content", (ClipboardValidator.validate(null) as TextValidation.Invalid).code)
        assertEquals("empty_content", (ClipboardValidator.validate("   \n\t") as TextValidation.Invalid).code)
    }

    @Test
    fun `rejects nul characters`() {
        assertEquals("invalid_content", (ClipboardValidator.validate("a\u0000b") as TextValidation.Invalid).code)
    }

    @Test
    fun `accepts exactly 64 KB and rejects one byte more`() {
        val exact = "a".repeat(64 * 1024)
        val over = "a".repeat(64 * 1024 + 1)
        assertTrue(ClipboardValidator.validate(exact) is TextValidation.Valid)
        assertEquals("content_too_large", (ClipboardValidator.validate(over) as TextValidation.Invalid).code)
    }

    @Test
    fun `counts utf8 bytes instead of characters`() {
        val text = "á".repeat(32 * 1024 + 1)
        assertEquals("content_too_large", (ClipboardValidator.validate(text) as TextValidation.Invalid).code)
    }

    @Test
    fun `validates uuid and sanitizes names`() {
        assertNull(ClipboardValidator.validateId("not-a-uuid"))
        assertEquals(
            "11111111-1111-4111-8111-111111111111",
            ClipboardValidator.validateId("11111111-1111-4111-8111-111111111111"),
        )
        assertEquals("Sala Azul", ClipboardValidator.sanitizeName("  Sala\n Azul  ", "fallback"))
        assertEquals("fallback", ClipboardValidator.sanitizeName("\u0000", "fallback"))
    }
}
