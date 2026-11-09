package com.spaceclipboard.app.util

import android.content.Intent
import com.spaceclipboard.app.domain.model.ContentType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateTimeFormatter = DateTimeFormatter.ofPattern(
    "dd/MM/yyyy · HH:mm",
    Locale.forLanguageTag("pt-BR"),
)

fun formatDateTime(epochMillis: Long): String =
    dateTimeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

fun typeLabel(type: ContentType): String = when (type) {
    ContentType.URL -> "Link"
    ContentType.TEXT -> "Texto"
}

fun Intent.sharedText(): String? {
    if (action != Intent.ACTION_SEND) return null
    if (type?.startsWith("text/") != true) return null
    val value = getCharSequenceExtra(Intent.EXTRA_TEXT) ?: getStringExtra(Intent.EXTRA_TEXT)
    return value?.toString()
}
