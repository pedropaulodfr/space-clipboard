package com.spaceclipboard.app

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifica o clipboard atual no aparelho receptor após um envio real. */
@RunWith(AndroidJUnit4::class)
class ClipboardCurrentProbeTest {
    @Test
    fun clipboardContainsExpectedText() {
        val expected = InstrumentationRegistry.getArguments().getString("expectedClipboard")
        assumeTrue("Defina expectedClipboard para validar o clipboard", !expected.isNullOrBlank())

        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(context, ClipboardProbeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        try {
            instrumentation.waitForIdleSync()
            var value: String? = null
            instrumentation.runOnMainSync {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                value = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            }
            assertEquals(expected, value)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
