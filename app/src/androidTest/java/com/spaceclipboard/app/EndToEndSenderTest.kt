package com.spaceclipboard.app

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.spaceclipboard.app.pairing.PairingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Teste de integração real entre dois aparelhos. É ignorado quando os argumentos
 * receiverHost/pairCode não são fornecidos pelo Gradle/ADB.
 *
 * Exemplo:
 * adb shell am instrument -w -e class com.spaceclipboard.app.EndToEndSenderTest \
 *   -e receiverHost 192.168.0.6 -e pairCode 123456 -e expectedText https://example.com \
 *   com.spaceclipboard.app.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class EndToEndSenderTest {
    @Test
    fun sharesheetSendsToRealReceiver() {
        val args = InstrumentationRegistry.getArguments()
        val host = args.getString("receiverHost")
        val code = args.getString("pairCode")
        val text = args.getString("expectedText") ?: "https://example.com/space-clipboard"
        assumeTrue("Defina receiverHost e pairCode para o teste real", !host.isNullOrBlank() && code?.length == 6)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = context as SpaceClipboardApplication
        val pairing = runBlocking(Dispatchers.IO) {
            app.container.pairingManager.pairManually(host!!, 8765, code!!)
        }
        assertTrue("Pareamento falhou: $pairing", pairing is PairingResult.Success)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.startActivitySync(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                setClassName(context, ShareActivity::class.java.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )

        val device = UiDevice.getInstance(instrumentation)
        assertTrue(
            "A tela de envio não confirmou o sucesso",
            device.wait(Until.hasObject(By.textContains("Enviado para")), 25_000),
        )
    }
}
