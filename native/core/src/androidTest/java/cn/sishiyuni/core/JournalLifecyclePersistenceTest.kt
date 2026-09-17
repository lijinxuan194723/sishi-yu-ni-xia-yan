package cn.sishiyuni.core

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import cn.sishiyuni.core.data.*
import cn.sishiyuni.core.journal.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class JournalLifecyclePersistenceTest {
    private lateinit var db: LukeDatabase
    private lateinit var repo: JournalRepository
    private lateinit var worker: Job
    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, LukeDatabase::class.java).build()
        worker = SupervisorJob()
        repo = JournalRepository(db, CoroutineScope(worker + Dispatchers.Default))
    }
    @After fun finish() = runBlocking { worker.cancelAndJoin(); db.close() }

    @Test fun closeThenReopenProducesAFreshEditorWithSavedContent() = runBlocking {
        val id = repo.create(); val first = repo.open(id)
        first.edit(title = "标题", body = "刚才的内容")
        assertTrue(repo.close(id)); assertFalse(first.edit(body = "过期输入"))
        val next = repo.open(id)
        assertNotSame(first, next); assertEquals("刚才的内容", next.state.value.body)
        assertTrue(next.edit(body = "第二次编辑")); assertTrue(repo.close(id))
        assertEquals("第二次编辑", db.dao().memo(id)?.body)
    }
    @Test fun metadataChangeCannotLeaveAnEditorThatAcceptsUnsavableInput() = runBlocking {
        val id = repo.create(); val old = repo.open(id)
        old.edit(body = "正文与加星都要保留")
        repo.change(id, "star")
        assertTrue(old.state.value.closed); assertFalse(old.edit(body = "过期句柄"))
        val next = repo.open(id)
        assertNotSame(old, next); assertEquals("正文与加星都要保留", next.state.value.body)
        assertTrue(next.edit(body = "继续编辑")); assertTrue(repo.close(id))
        val stored = requireNotNull(db.dao().memo(id))
        assertTrue(stored.starred); assertEquals("继续编辑", stored.body)
    }
    @Test fun conflictKeepsTheSameEditableSessionAndStoredRecoveryCopy() = runBlocking {
        val id = repo.create(body = "原文"); val editor = repo.open(id)
        val before = requireNotNull(db.dao().memo(id))
        db.dao().putMemo(before.copy(body = "外部内容", revision = before.revision + 1))
        editor.edit(body = "本机草稿")
        assertFalse(repo.close(id)); assertSame(editor, repo.open(id))
        assertFalse(editor.state.value.closed); assertTrue(editor.edit(body = "本机草稿续写"))
        assertFalse(editor.flush()); assertNotNull(db.dao().record("journal-draft", id))
        assertEquals("外部内容", db.dao().memo(id)?.body)
    }
    @Test fun recoveryCopyIncludesTitleBodyAndMoodWithoutChangingTheOriginal() = runBlocking {
        val id = repo.create(title = "原题", body = "原文"); val editor = repo.open(id)
        db.dao().putMemo(requireNotNull(db.dao().memo(id)).copy(body = "外部版本", revision = 3))
        editor.edit(title = "我的标题", body = "我的正文", mood = "平静")
        assertFalse(editor.flush())
        val copy = requireNotNull(db.dao().memo(repo.copyDraft(id)))
        assertEquals("我的标题", copy.title); assertEquals("我的正文", copy.body); assertEquals("平静", copy.mood)
        assertEquals("外部版本", db.dao().memo(id)?.body)
        assertNotNull(db.dao().record("journal-draft", id))
    }
    @Test fun aRestoredNoteCannotBePurgedByAnOldConfirmation() = runBlocking {
        val id = repo.create(body = "恢复后必须保留")
        repo.change(id, "trash"); repo.change(id, "restore")
        try { repo.purge(id); fail("restored notes must not be purged") } catch (_: IllegalArgumentException) { }
        assertEquals("恢复后必须保留", db.dao().memo(id)?.body)
        assertNull(db.dao().memo(id)?.deletedAt)
    }
}
