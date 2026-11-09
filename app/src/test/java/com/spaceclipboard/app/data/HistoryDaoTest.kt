package com.spaceclipboard.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spaceclipboard.app.data.db.AppDatabase
import com.spaceclipboard.app.data.db.HistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class HistoryDaoTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `keeps only the 20 most recent records`() = runBlocking {
        val dao = database.historyDao()
        repeat(25) { index ->
            dao.insertAndTrim(entity(index.toLong(), "item-$index"))
        }
        val recent = dao.recent()
        assertEquals(20, recent.size)
        assertEquals("item-24", recent.first().text)
        assertEquals("item-5", recent.last().text)
    }

    @Test
    fun `limit holds with concurrent inserts`() = runBlocking {
        val dao = database.historyDao()
        coroutineScope {
            repeat(40) { index ->
                launch(Dispatchers.IO) {
                    dao.insertAndTrim(entity(index.toLong(), "concurrent-$index"))
                }
            }
        }
        val recent = dao.recent()
        assertEquals(20, recent.size)
        assertEquals(20, dao.recent(100).size.coerceAtMost(20))
    }

    @Test
    fun `duplicate id is ignored and delete removes one record`() = runBlocking {
        val dao = database.historyDao()
        val item = entity(1L, "original")
        assertTrue(dao.insertAndTrim(item))
        assertFalse(dao.insertAndTrim(item.copy(text = "duplicate")))
        assertEquals("original", dao.findById(item.id)?.text)
        assertEquals(1, dao.deleteById(item.id))
        assertEquals(0, dao.deleteById(item.id))
    }

    @Test
    fun `clear removes everything`() = runBlocking {
        val dao = database.historyDao()
        repeat(5) { dao.insertAndTrim(entity(it.toLong(), "item-$it")) }
        dao.clear()
        assertEquals(0, dao.recent().size)
    }

    private fun entity(timestamp: Long, text: String) = HistoryEntity(
        id = UUID.randomUUID().toString(),
        text = text,
        type = "text",
        senderName = "Phone",
        senderId = "11111111-1111-4111-8111-111111111111",
        receivedAt = timestamp,
    )
}
