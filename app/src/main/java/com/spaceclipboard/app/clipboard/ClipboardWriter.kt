package com.spaceclipboard.app.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ClipboardWriter {
    suspend fun copy(text: String): Result<Unit>
    suspend fun readText(): Result<String>
}

class AndroidClipboardWriter(context: Context) : ClipboardWriter {
    private val appContext = context.applicationContext
    private val clipboard = appContext
        .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override suspend fun copy(text: String): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            check(
                ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
            ) { "O aplicativo precisa estar em primeiro plano para copiar." }
            val clip = ClipData.newPlainText("Space Clipboard", text)
            clipboard.setPrimaryClip(clip)
        }
    }

    override suspend fun readText(): Result<String> = withContext(Dispatchers.Main) {
        runCatching {
            check(
                ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
            ) { "O aplicativo precisa estar em primeiro plano para ler o clipboard." }
            val clip = clipboard.primaryClip
            check(clip != null && clip.itemCount > 0) { "Área de transferência vazia." }
            val text = clip.getItemAt(0).coerceToText(appContext).toString()
            check(text.isNotBlank()) { "Área de transferência vazia." }
            text
        }
    }
}
