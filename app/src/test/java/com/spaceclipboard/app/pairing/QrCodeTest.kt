package com.spaceclipboard.app.pairing

import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class QrCodeTest {
    @Test
    fun `generated QR can be decoded and preserves pairing payload`() {
        val payload = PairingQrPayload(
            id = "11111111-1111-4111-8111-111111111111",
            host = "192.168.0.15",
            port = 8765,
            token = "abcdefghijklmnopqrstuv",
            exp = 1_900_000_000_000L,
        )
        val encoded = PairingPayloadCodec.encode(payload)
        val bitmap = QrCodeGenerator.generate(encoded, sizePx = 512)
        assertNotNull(bitmap)
        val size = bitmap!!.width
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        val source = RGBLuminanceSource(size, size, pixels)
        val result = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)))
        assertEquals(encoded, result.text)
        val decoded = PairingPayloadCodec.decode(result.text, now = 1_800_000_000_000L)
        assertEquals(payload, (decoded as PairingDecodeResult.Success).payload)
    }
}
