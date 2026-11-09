package com.spaceclipboard.app.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingCodecTest {
    private val payload = PairingQrPayload(
        id = "11111111-1111-4111-8111-111111111111",
        host = "192.168.0.10",
        port = 8765,
        token = "abcdefghijklmnopqrstuv",
        exp = 2_000_000L,
    )

    @Test
    fun `roundtrip preserves pairing data`() {
        val encoded = PairingPayloadCodec.encode(payload)
        val decoded = PairingPayloadCodec.decode(encoded, now = 1_000L)
        assertTrue(decoded is PairingDecodeResult.Success)
        assertEquals(payload, (decoded as PairingDecodeResult.Success).payload)
    }

    @Test
    fun `rejects expired payload`() {
        val encoded = PairingPayloadCodec.encode(payload)
        val decoded = PairingPayloadCodec.decode(encoded, now = 2_000_001L)
        assertTrue(decoded is PairingDecodeResult.Error)
    }

    @Test
    fun `rejects foreign or corrupted codes`() {
        assertTrue(PairingPayloadCodec.decode("https://example.com") is PairingDecodeResult.Error)
        assertTrue(PairingPayloadCodec.decode("SCP1:%%%") is PairingDecodeResult.Error)
        assertTrue(PairingPayloadCodec.decode("") is PairingDecodeResult.Error)
    }

    @Test
    fun `pairing session expires and validates token or code`() {
        val session = PairingSession.create(now = 10_000L, ttlMillis = 5_000L)
        assertTrue(session.matches(session.token, null))
        assertTrue(session.matches(null, session.code))
        assertFalse(session.matches("wrong", "000000"))
        assertFalse(session.isExpired(14_999L))
        assertTrue(session.isExpired(15_000L))
    }

    @Test
    fun `pair attempt limiter blocks after configured attempts`() {
        val limiter = PairAttemptLimiter(maxAttempts = 3, windowMillis = 1_000L)
        assertTrue(limiter.allow("10.0.0.2", 0L))
        assertTrue(limiter.allow("10.0.0.2", 10L))
        assertTrue(limiter.allow("10.0.0.2", 20L))
        assertFalse(limiter.allow("10.0.0.2", 30L))
        assertTrue(limiter.allow("10.0.0.3", 30L))
        assertTrue(limiter.allow("10.0.0.2", 1_100L))
    }

    @Test
    fun `local address validator accepts private networks and rejects public hosts`() {
        assertTrue(NetworkAddressValidator.isLocalHost("192.168.0.2"))
        assertTrue(NetworkAddressValidator.isLocalHost("10.1.2.3"))
        assertTrue(NetworkAddressValidator.isLocalHost("172.16.0.9"))
        assertTrue(NetworkAddressValidator.isLocalHost("127.0.0.1"))
        assertFalse(NetworkAddressValidator.isLocalHost("8.8.8.8"))
        assertFalse(NetworkAddressValidator.isLocalHost("example.com"))
    }
}
