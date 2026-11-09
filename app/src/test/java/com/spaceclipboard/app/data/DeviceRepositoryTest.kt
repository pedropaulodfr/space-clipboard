package com.spaceclipboard.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spaceclipboard.app.data.db.AppDatabase
import com.spaceclipboard.app.domain.model.PeerIdentity
import com.spaceclipboard.app.security.TokenCipher
import com.spaceclipboard.app.security.Tokens
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class DeviceRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: DeviceRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DeviceRepository(database.deviceDao(), ReversingCipher())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `pairing stores outbound token and inbound hash`() = runBlocking {
        val peer = PeerIdentity("dev-a", "TV", "192.168.0.5", 8765)
        val saved = repository.savePairing(
            peer = peer,
            protocol = 1,
            outboundToken = "outbound-secret",
            inboundToken = "inbound-secret",
            now = 100L,
        )
        assertEquals("TV", saved.name)
        assertEquals("outbound-secret", repository.outboundToken(saved.id))
        assertEquals("TV", repository.authenticate(Tokens.sha256("inbound-secret"))?.name)
        assertNull(repository.authenticate(Tokens.sha256("wrong")))
    }

    @Test
    fun `removing device revokes both credentials`() = runBlocking {
        val peer = PeerIdentity("dev-a", "TV", "192.168.0.5", 8765)
        val saved = repository.savePairing(peer, 1, "out", "in", 100L)
        repository.remove(saved.id)
        assertNull(repository.outboundToken(saved.id))
        assertNull(repository.authenticate(Tokens.sha256("in")))
    }

    @Test
    fun `repairing replaces endpoint and credentials`() = runBlocking {
        val peer = PeerIdentity("dev-a", "TV", "192.168.0.5", 8765)
        val saved = repository.savePairing(peer, 1, "old-out", "old-in", 100L)
        repository.savePairing(
            peer.copy(host = "192.168.0.9", port = 9000),
            1,
            "new-out",
            "new-in",
            200L,
        )
        assertEquals("new-out", repository.outboundToken(saved.id))
        assertNull(repository.authenticate(Tokens.sha256("old-in")))
        assertEquals("TV", repository.authenticate(Tokens.sha256("new-in"))?.name)
        assertEquals("192.168.0.9", repository.find(saved.id)?.host)
    }

    private class ReversingCipher : TokenCipher {
        override fun encrypt(plainText: String): String = plainText.reversed()
        override fun decrypt(cipherText: String): String = cipherText.reversed()
    }
}
