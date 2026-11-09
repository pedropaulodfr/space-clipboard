package com.spaceclipboard.app.network.server

import com.spaceclipboard.app.domain.model.ContentType
import com.spaceclipboard.app.domain.model.PeerIdentity
import com.spaceclipboard.app.network.AuthCheckResponse
import com.spaceclipboard.app.network.ClipboardPayload
import com.spaceclipboard.app.network.PairRequest
import com.spaceclipboard.app.network.PairResponse
import com.spaceclipboard.app.network.Protocol
import com.spaceclipboard.app.network.ReceiveResponse
import com.spaceclipboard.app.network.StatusResponse
import com.spaceclipboard.app.security.Tokens
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

class ClipboardHttpServerTest {
    private lateinit var server: ClipboardHttpServer
    private lateinit var host: FakeHost
    private lateinit var binding: ServerBinding
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val http = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build()

    @Before
    fun setUp() = runBlocking {
        host = FakeHost()
        server = ClipboardHttpServer(host)
        binding = server.start(0)
        host.port = binding.port
    }

    @After
    fun tearDown() = runBlocking {
        server.stop()
    }

    @Test
    fun `status is public and reports protocol`() {
        val response = request("GET", "/api/status")
        assertEquals(200, response.statusCode())
        val status = json.decodeFromString(StatusResponse.serializer(), response.body())
        assertEquals("online", status.server)
        assertEquals(Protocol.VERSION, status.protocol)
        assertEquals(binding.port, status.port)
        assertTrue(status.pairingAvailable)
    }

    @Test
    fun `pair endpoint validates code and returns credential`() = runBlocking {
        val inspect = request("GET", "/api/pair?code=123456")
        assertEquals(200, inspect.statusCode())
        assertTrue(inspect.body().contains("\"valid\":true"))

        val request = PairRequest(
            pairCode = "123456",
            senderId = UUID.randomUUID().toString(),
            senderName = "Phone",
            senderPort = 8765,
            senderToken = "sender-token-abcdefghijklmnopqrstuv",
        )
        val response = request(
            "POST",
            "/api/pair",
            json.encodeToString(PairRequest.serializer(), request),
        )
        assertEquals(200, response.statusCode())
        val parsed = json.decodeFromString(PairResponse.serializer(), response.body())
        assertEquals("receiver-credential", parsed.credential)
        assertEquals("Receiver", parsed.receiver.name)
    }

    @Test
    fun `clipboard requires authentication`() {
        val payload = validPayload()
        val response = request(
            "POST",
            "/api/clipboard",
            json.encodeToString(ClipboardPayload.serializer(), payload),
        )
        assertEquals(401, response.statusCode())
        assertTrue(host.received.isEmpty())
    }

    @Test
    fun `clipboard receives text and URLs`() {
        val url = validPayload(text = "https://www.youtube.com/")
        val first = request(
            "POST",
            "/api/clipboard",
            json.encodeToString(ClipboardPayload.serializer(), url),
            token = "valid-token-123456789012345",
        )
        assertEquals(201, first.statusCode())
        val body = json.decodeFromString(ReceiveResponse.serializer(), first.body())
        assertTrue(body.success)
        assertEquals(1, host.received.size)
        assertEquals(ContentType.URL, host.received.first().second)

        val textPayload = validPayload(text = "texto simples")
        val second = request(
            "POST",
            "/api/clipboard",
            json.encodeToString(ClipboardPayload.serializer(), textPayload),
            token = "valid-token-123456789012345",
        )
        assertEquals(201, second.statusCode())
        assertEquals(ContentType.TEXT, host.received.last().second)
    }

    @Test
    fun `duplicate uuid is idempotent`() {
        val payload = validPayload()
        val body = json.encodeToString(ClipboardPayload.serializer(), payload)
        val first = request("POST", "/api/clipboard", body, token = "valid-token-123456789012345")
        val second = request("POST", "/api/clipboard", body, token = "valid-token-123456789012345")
        assertEquals(201, first.statusCode())
        assertEquals(200, second.statusCode())
        assertTrue(second.body().contains("\"duplicate\":true"))
        assertEquals(1, host.received.size)
    }

    @Test
    fun `rejects empty text and invalid id`() {
        val empty = request(
            "POST",
            "/api/clipboard",
            json.encodeToString(ClipboardPayload.serializer(), validPayload(text = "   ")),
            token = "valid-token-123456789012345",
        )
        assertEquals(400, empty.statusCode())
        assertTrue(empty.body().contains("empty_content"))

        val invalidId = request(
            "POST",
            "/api/clipboard",
            json.encodeToString(ClipboardPayload.serializer(), validPayload(id = "abc")),
            token = "valid-token-123456789012345",
        )
        assertEquals(400, invalidId.statusCode())
        assertTrue(invalidId.body().contains("invalid_id"))
    }

    @Test
    fun `rejects content above 64 KB and bodies above server limit`() {
        val tooLarge = request(
            "POST",
            "/api/clipboard",
            json.encodeToString(
                ClipboardPayload.serializer(),
                validPayload(text = "a".repeat(64 * 1024 + 1)),
            ),
            token = "valid-token-123456789012345",
        )
        assertEquals(400, tooLarge.statusCode())
        assertTrue(tooLarge.body().contains("content_too_large"))
        assertTrue(host.received.isEmpty())

        val huge = request(
            "POST",
            "/api/clipboard",
            json.encodeToString(
                ClipboardPayload.serializer(),
                validPayload(text = "a".repeat(110 * 1024)),
            ),
            token = "valid-token-123456789012345",
        )
        assertTrue(huge.statusCode() == 413 || huge.statusCode() == 400)
    }

    @Test
    fun `handles malformed JSON`() {
        val response = request("POST", "/api/clipboard", "{ invalid", token = "valid-token-123456789012345")
        assertEquals(400, response.statusCode())
    }

    @Test
    fun `auth check and delete history`() = runBlocking {
        val auth = request("GET", "/api/auth/check", token = "valid-token-123456789012345")
        assertEquals(200, auth.statusCode())
        assertTrue(auth.body().contains("Phone"))
        val authBody = json.decodeFromString(AuthCheckResponse.serializer(), auth.body())
        assertEquals("Phone", authBody.device.name)

        val payload = validPayload()
        request(
            "POST",
            "/api/clipboard",
            json.encodeToString(ClipboardPayload.serializer(), payload),
            token = "valid-token-123456789012345",
        )
        val delete = request("DELETE", "/api/history/${payload.id}", token = "valid-token-123456789012345")
        assertEquals(200, delete.statusCode())
        assertTrue(delete.body().contains("\"deleted\":true"))
        val missing = request("DELETE", "/api/history/${payload.id}", token = "valid-token-123456789012345")
        assertEquals(404, missing.statusCode())
    }

    @Test
    fun `chooses another port when configured port is busy`() = runBlocking {
        server.stop()
        val blocker = ServerSocket(0)
        val occupied = blocker.localPort
        val second = ClipboardHttpServer(host)
        try {
            val newBinding = second.start(occupied)
            binding = newBinding
            host.port = newBinding.port
            assertNotEquals(occupied, newBinding.port)
            assertEquals(200, request("GET", "/api/status").statusCode())
        } finally {
            second.stop()
            blocker.close()
        }
    }

    @Test
    fun `stopping releases the port`() = runBlocking {
        val port = binding.port
        server.stop()
        var bound = false
        repeat(20) {
            bound = runCatching {
                ServerSocket().use { socket ->
                    socket.reuseAddress = false
                    socket.bind(java.net.InetSocketAddress("0.0.0.0", port))
                }
                true
            }.getOrDefault(false)
            if (bound) return@repeat
            Thread.sleep(100)
        }
        assertTrue("A porta $port nÃ£o foi liberada", bound)
    }

    private fun validPayload(
        id: String = UUID.randomUUID().toString(),
        text: String = "https://www.youtube.com/",
    ) = ClipboardPayload(
        id = id,
        text = text,
        type = if (text.startsWith("http")) "url" else "text",
        sender = "Phone",
        timestamp = System.currentTimeMillis(),
    )

    private fun request(
        method: String,
        path: String,
        body: String? = null,
        token: String? = null,
    ): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:${binding.port}$path"))
            .timeout(java.time.Duration.ofSeconds(10))
            .header("Accept", "application/json")
        if (token != null) builder.header("Authorization", "Bearer $token")
        when (method) {
            "GET" -> builder.GET()
            "DELETE" -> builder.DELETE()
            else -> builder.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body ?: ""))
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private class FakeHost : ClipboardServerHost {
        var port: Int = 0
        val received = mutableListOf<Pair<ClipboardPayload, ContentType>>()
        private val ids = mutableSetOf<String>()

        override fun receiverInfo() = ReceiverInfo(
            id = "22222222-2222-4222-8222-222222222222",
            name = "Receiver",
            port = port,
        )

        override fun pairingAvailable(): Boolean = true

        override suspend fun inspectPair(
            token: String?,
            code: String?,
            remoteHost: String,
        ): PairInspection {
            val valid = token == "pair-token" || code == "123456"
            return PairInspection(valid, if (valid) "ok" else "invalid", System.currentTimeMillis() + 10_000)
        }

        override suspend fun completePair(
            request: PairRequest,
            remoteHost: String,
        ): PairCompletion = PairCompletion.Success(
            receiver = ReceiverInfo(
                id = "22222222-2222-4222-8222-222222222222",
                name = "Receiver",
                port = port,
            ),
            credential = "receiver-credential",
        )

        override suspend fun authenticate(tokenHash: String): PeerIdentity? {
            if (tokenHash != Tokens.sha256("valid-token-123456789012345")) return null
            return PeerIdentity(
                id = "33333333-3333-4333-8333-333333333333",
                name = "Phone",
                host = "192.168.0.2",
                port = 8765,
            )
        }

        override suspend fun receive(
            payload: ClipboardPayload,
            type: ContentType,
            peer: PeerIdentity,
        ): ReceiveResponse {
            val isNew = ids.add(payload.id)
            if (isNew) received += payload to type
            return ReceiveResponse(duplicate = !isNew)
        }

        override suspend fun deleteHistory(id: String, peer: PeerIdentity): Boolean {
            val removed = ids.remove(id)
            received.removeAll { it.first.id == id }
            return removed
        }
    }
}
