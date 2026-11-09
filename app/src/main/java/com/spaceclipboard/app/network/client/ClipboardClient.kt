package com.spaceclipboard.app.network.client

import com.spaceclipboard.app.network.ApiErrorResponse
import com.spaceclipboard.app.network.ApiException
import com.spaceclipboard.app.network.AuthCheckResponse
import com.spaceclipboard.app.network.ClipboardPayload
import com.spaceclipboard.app.network.DeleteResponse
import com.spaceclipboard.app.network.PairInspectionResponse
import com.spaceclipboard.app.network.PairRequest
import com.spaceclipboard.app.network.PairResponse
import com.spaceclipboard.app.network.Protocol
import com.spaceclipboard.app.network.ReceiveResponse
import com.spaceclipboard.app.network.StatusResponse
import com.spaceclipboard.app.pairing.NetworkAddressValidator
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import java.io.Closeable

class ClientConnectionException(
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause)

class ClipboardClient(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    },
) : Closeable {
    private val client = HttpClient(CIO) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 4_000
            requestTimeoutMillis = 8_000
            socketTimeoutMillis = 8_000
        }
    }

    suspend fun status(host: String, port: Int): StatusResponse {
        val response = get("${base(host, port)}/api/status")
        val body = decode(response, StatusResponse.serializer())
        if (body.protocol != Protocol.VERSION) {
            throw ApiException(409, "protocol_mismatch", "Versão de protocolo incompatível.")
        }
        return body
    }

    suspend fun inspectPair(
        host: String,
        port: Int,
        token: String? = null,
        code: String? = null,
    ): PairInspectionResponse {
        val response = get("${base(host, port)}/api/pair") {
            if (token != null) parameter("token", token)
            if (code != null) parameter("code", code)
        }
        return decode(response, PairInspectionResponse.serializer())
    }

    suspend fun pair(host: String, port: Int, request: PairRequest): PairResponse {
        val response = post("${base(host, port)}/api/pair") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(PairRequest.serializer(), request))
        }
        return decode(response, PairResponse.serializer())
    }

    suspend fun checkAuth(host: String, port: Int, token: String): AuthCheckResponse {
        val response = get("${base(host, port)}/api/auth/check") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        return decode(response, AuthCheckResponse.serializer())
    }

    suspend fun send(
        host: String,
        port: Int,
        token: String,
        payload: ClipboardPayload,
    ): ReceiveResponse {
        val response = post("${base(host, port)}/api/clipboard") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(Protocol.HEADER_PROTOCOL, Protocol.VERSION.toString())
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ClipboardPayload.serializer(), payload))
        }
        return decode(response, ReceiveResponse.serializer())
    }

    suspend fun deleteHistory(host: String, port: Int, token: String, id: String): DeleteResponse {
        val response = delete("${base(host, port)}/api/history/$id") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        return decode(response, DeleteResponse.serializer())
    }

    private suspend fun get(
        url: String,
        block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse = wrapNetwork {
        client.get(url) {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            block()
        }
    }

    private suspend fun post(
        url: String,
        block: io.ktor.client.request.HttpRequestBuilder.() -> Unit,
    ): HttpResponse = wrapNetwork {
        client.post(url) {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            block()
        }
    }

    private suspend fun delete(
        url: String,
        block: io.ktor.client.request.HttpRequestBuilder.() -> Unit,
    ): HttpResponse = wrapNetwork {
        client.delete(url) {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            block()
        }
    }

    private suspend fun <T> decode(
        response: HttpResponse,
        serializer: kotlinx.serialization.DeserializationStrategy<T>,
    ): T {
        val text = wrapNetwork { response.bodyAsText() }
        if (!response.status.isSuccess()) {
            val apiError = runCatching {
                json.decodeFromString(ApiErrorResponse.serializer(), text)
            }.getOrNull()
            throw ApiException(
                statusCode = response.status.value,
                errorCode = apiError?.error?.code ?: "http_error",
                message = apiError?.error?.message ?: "O receptor respondeu com erro ${response.status.value}.",
            )
        }
        return runCatching { json.decodeFromString(serializer, text) }
            .getOrElse {
                throw ApiException(502, "invalid_response", "Resposta inválida do dispositivo.")
            }
    }

    private suspend fun <T> wrapNetwork(block: suspend () -> T): T = try {
        block()
    } catch (error: ApiException) {
        throw error
    } catch (error: kotlinx.coroutines.CancellationException) {
        throw error
    } catch (error: Exception) {
        throw ClientConnectionException(
            message = when (error) {
                is io.ktor.client.plugins.HttpRequestTimeoutException ->
                    "O dispositivo não respondeu a tempo."
                is ConnectTimeoutException ->
                    "Não foi possível conectar ao dispositivo."
                else -> "Falha de rede ao conversar com o dispositivo."
            },
            cause = error,
        )
    }

    private fun base(host: String, port: Int): String =
        "http://${NetworkAddressValidator.formatUrlHost(host)}:$port"

    override fun close() {
        client.close()
    }
}
