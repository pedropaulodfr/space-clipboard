package com.spaceclipboard.app.pairing

import com.spaceclipboard.app.network.Protocol
import com.spaceclipboard.app.security.Tokens

data class PairingSession(
    val token: String,
    val code: String,
    val createdAt: Long,
    val expiresAt: Long,
) {
    fun isExpired(now: Long): Boolean = now >= expiresAt

    fun matches(token: String?, code: String?): Boolean {
        val tokenOk = token != null && Tokens.constantTimeEquals(token, this.token)
        val codeOk = code != null && Tokens.constantTimeEquals(code, this.code)
        return tokenOk || codeOk
    }

    companion object {
        fun create(now: Long, ttlMillis: Long = Protocol.PAIRING_TTL_MS): PairingSession =
            PairingSession(
                token = Tokens.newToken(16),
                code = Tokens.newPairingCode(),
                createdAt = now,
                expiresAt = now + ttlMillis,
            )
    }
}

/** Limita tentativas de pareamento por IP para reduzir brute force do código de 6 dígitos. */
class PairAttemptLimiter(
    private val maxAttempts: Int = 12,
    private val windowMillis: Long = 60_000L,
) {
    private val attempts = mutableMapOf<String, ArrayDeque<Long>>()

    @Synchronized
    fun allow(key: String, now: Long): Boolean {
        val queue = attempts.getOrPut(key) { ArrayDeque() }
        while (queue.isNotEmpty() && now - queue.first() > windowMillis) queue.removeFirst()
        if (queue.size >= maxAttempts) return false
        queue.addLast(now)
        if (attempts.size > 128) {
            val cutoff = now - windowMillis
            attempts.entries.removeAll { entry ->
                entry.value.isNotEmpty() && entry.value.last() < cutoff
            }
        }
        return true
    }

    @Synchronized
    fun reset(key: String) {
        attempts.remove(key)
    }
}
