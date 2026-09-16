package cn.sishiyuni.core

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.sishiyuni.core.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageIdentityInstrumentedTest {
    private lateinit var db: LukeDatabase
    private lateinit var dao: LukeDao
    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, LukeDatabase::class.java).build()
        dao = db.dao()
    }
    @After fun close() { db.close() }

    @Test fun existingMessageCanBeUpdatedWithoutDuplicatingItsOrder() = runBlocking {
        dao.putSession(SessionEntity("s"))
        val message = MessageEntity("m", "s", 0, "me", "原文")
        dao.putMessage(message); dao.putMessage(message.copy(text = "改过的原文", muted = true))
        assertEquals(1, dao.messageCount())
        assertEquals("改过的原文", dao.message("m")!!.text)
        assertTrue(dao.message("m")!!.muted)
    }
    @Test fun updatingMessageCannotMoveItToAnotherConversation() = runBlocking {
        dao.putSession(SessionEntity("a")); dao.putSession(SessionEntity("b"))
        val message = MessageEntity("m", "a", 0, "me", "属于 A")
        dao.putMessage(message)
        assertTrue(runCatching { dao.putMessage(message.copy(sessionId = "b")) }.isFailure)
        assertEquals("a", dao.message("m")!!.sessionId)
        assertTrue(dao.messagesNow("b").isEmpty())
    }
    @Test fun updatingMessageCannotMoveItToAnotherOrdinal() = runBlocking {
        dao.putSession(SessionEntity("s"))
        val message = MessageEntity("m", "s", 0, "me", "保留顺序")
        dao.putMessage(message)
        assertTrue(runCatching { dao.putMessage(message.copy(ordinal = 2)) }.isFailure)
        assertEquals(0L, dao.message("m")!!.ordinal)
        assertEquals(1L, dao.nextOrdinal("s"))
    }
    @Test fun collisionRollsBackTheWholeImportTransaction() = runBlocking {
        dao.putSession(SessionEntity("s"))
        dao.putMessage(MessageEntity("original", "s", 0, "me", "不能丢失"))
        val result = runCatching { db.withTransaction {
            dao.putMemo(MemoEntity("new-note", body = "尚未导入完成"))
            dao.putMessage(MessageEntity("collision", "s", 0, "me", "冲突"))
        } }
        assertTrue(result.isFailure)
        assertNull(dao.memo("new-note")); assertNull(dao.message("collision"))
        assertEquals("不能丢失", dao.message("original")!!.text)
    }
}
