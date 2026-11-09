package com.spaceclipboard.app

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spaceclipboard.app.domain.model.ContentType
import com.spaceclipboard.app.domain.model.PeerIdentity
import com.spaceclipboard.app.network.ClipboardPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ClipboardInstrumentedTest {
    @Test
    fun receivingContentCopiesToClipboardAndPersistsHistory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(context, ClipboardProbeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        try {
            instrumentation.waitForIdleSync()
            assertTrue(activity.isFinishing.not())
            val app = context as SpaceClipboardApplication
            val text = "teste automático ${UUID.randomUUID()}"
            val id = UUID.randomUUID().toString()
            val payload = ClipboardPayload(
                id = id,
                text = text,
                type = "text",
                sender = "Instrumentation",
                timestamp = System.currentTimeMillis(),
            )
            val peer = PeerIdentity(
                id = UUID.randomUUID().toString(),
                name = "Instrumentation",
                host = "127.0.0.1",
                port = 8765,
            )
            val response = runBlocking(Dispatchers.IO) {
                app.container.receiverController.receive(payload, ContentType.TEXT, peer)
            }
            assertTrue(response.success)

            var clipboardText: String? = null
            instrumentation.runOnMainSync {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboardText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            }
            assertEquals(text, clipboardText)
            val readBack = runBlocking(Dispatchers.IO) {
                app.container.clipboardWriter.readText()
            }
            assertEquals(text, readBack.getOrNull())

            val stored = runBlocking(Dispatchers.IO) { app.container.historyRepository.find(id) }
            assertNotNull(stored)
            assertEquals(text, stored?.text)
            assertEquals(ContentType.TEXT, stored?.type)
            assertEquals("Instrumentation", stored?.senderName)

            runBlocking(Dispatchers.IO) { app.container.historyRepository.delete(id) }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test
    fun manifestDeclaresNoBackgroundServices() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SERVICES,
        )
        val services = info.services
        assertTrue("O app não deve declarar serviços", services.isNullOrEmpty())
    }

    @Test
    fun sharesheetTextIntentResolvesToShareActivityAndKeepsText() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://example.com/share")
        }
        val resolved = context.packageManager.resolveActivity(
            Intent(intent).setPackage(context.packageName),
            0,
        )
        assertEquals(ShareActivity::class.java.name, resolved?.activityInfo?.name)
    }
}
