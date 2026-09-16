package cn.sishiyuni.core

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.sishiyuni.core.data.*
import cn.sishiyuni.core.skills.SkillParser
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceInstrumentedTest {
    private lateinit var database: LukeDatabase
    private lateinit var dao: LukeDao
    @Before fun open() {
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, LukeDatabase::class.java).build()
        dao = database.dao()
    }
    @After fun close() { database.close() }
    private fun skill(body: String = "# writing\nWrite clearly.") = SkillParser.parse(body, "local").skill

    @Test fun messagesNeverLeakBetweenSessions() = runBlocking {
        dao.putSession(SessionEntity("a")); dao.putSession(SessionEntity("b"))
        dao.putMessage(MessageEntity("ma", "a", 0, "me", "属于 A"))
        dao.putMessage(MessageEntity("mb", "b", 0, "me", "属于 B"))
        assertEquals(listOf("ma"), dao.messagesNow("a").map { it.id })
        assertEquals(listOf("mb"), dao.messagesNow("b").map { it.id })
    }
    @Test fun foreignKeyRejectsOrphanMessage() = runBlocking {
        assertTrue(runCatching { dao.putMessage(MessageEntity("bad", "missing", 0, "me", "不能丢失归属")) }.isFailure)
        assertEquals(0, dao.messageCount())
    }
    @Test fun messageOrderIsUniqueWithinSession() = runBlocking {
        dao.putSession(SessionEntity("s"))
        dao.putMessage(MessageEntity("one", "s", 0, "me", "保留原消息"))
        assertTrue(runCatching { dao.putMessage(MessageEntity("two", "s", 0, "me", "冲突")) }.isFailure)
        assertEquals("保留原消息", dao.message("one")!!.text)
    }
    @Test fun metadataUpdatesDoNotOverwriteNewDraftOrArchiveChoice() = runBlocking {
        dao.putSession(SessionEntity("s", createdAt = 1, updatedAt = 1))
        dao.draft("s", "新的草稿")
        assertEquals(1, dao.renameSession("s", "改过的标题"))
        dao.archiveSession("s", true); dao.touchSession("s", 100)
        val actual = dao.session("s")!!
        assertEquals("新的草稿", actual.draft); assertEquals("改过的标题", actual.title)
        assertTrue(actual.archived); assertEquals(100L, actual.updatedAt)
    }
    @Test fun lateTouchCannotMoveSessionBackwardsInHistory() = runBlocking {
        dao.putSession(SessionEntity("s", createdAt = 1, updatedAt = 100))
        dao.touchSession("s", 50); assertEquals(100L, dao.session("s")!!.updatedAt)
    }
    @Test fun staleNoteRevisionCannotOverwriteNewText() = runBlocking {
        dao.putMemo(MemoEntity("n", body = "原文"))
        assertEquals(1, dao.editMemo("n", "标题", "新正文", "", 100, 0))
        assertEquals(0, dao.editMemo("n", "旧标题", "旧窗口正文", "", 101, 0))
        assertEquals("新正文", dao.memo("n")!!.body); assertEquals(1L, dao.memo("n")!!.revision)
    }
    @Test fun removingFolderPreservesNotesAndInvalidatesStaleEdits() = runBlocking {
        dao.putFolder(FolderEntity("f", "文件夹")); dao.putMemo(MemoEntity("n", body = "正文", folderId = "f"))
        database.withTransaction { dao.unfileMemos("f"); dao.deleteFolder("f") }
        assertNull(dao.memo("n")!!.folderId); assertEquals("正文", dao.memo("n")!!.body)
        assertEquals(0, dao.editMemo("n", "", "旧内容", "", 100, 0))
    }
    @Test fun transactionFailureRollsBackAllInsertedRows() = runBlocking {
        val failure = runCatching { database.withTransaction {
            dao.putSession(SessionEntity("imported"))
            dao.putMessage(MessageEntity("m", "imported", 0, "me", "导入中"))
            dao.putMemo(MemoEntity("n", body = "不能半份导入"))
            error("模拟导入失败")
        } }
        assertTrue(failure.isFailure); assertNull(dao.session("imported")); assertEquals(0, dao.messageCount()); assertEquals(0, dao.memoCount())
    }
    @Test fun identicalSkillImportPreservesEnabledChoice() = runBlocking {
        val original = skill()
        assertTrue(dao.installReviewedSkill(original, null))
        assertEquals(1, dao.setSkillEnabledIfUnchanged(original.id, true, original.digest))
        assertTrue(dao.installReviewedSkill(original, null))
        assertTrue(dao.skill(original.id)!!.enabled)
    }
    @Test fun changedSkillRequiresReviewAndIsDisabledAfterUpdate() = runBlocking {
        val original = skill(); val changed = skill("# writing\nA different instruction.")
        assertEquals(original.id, changed.id)
        dao.installReviewedSkill(original, null); dao.setSkillEnabledIfUnchanged(original.id, true, original.digest)
        assertFalse(dao.installReviewedSkill(changed, null)); assertTrue(dao.skill(original.id)!!.enabled)
        assertTrue(dao.installReviewedSkill(changed, original.digest))
        assertEquals(changed.content, dao.skill(original.id)!!.content); assertFalse(dao.skill(original.id)!!.enabled)
    }
    @Test fun delayedSkillUpdateCannotResurrectAnUninstalledSkill() = runBlocking {
        val original = skill(); val changed = skill("# writing\nUpdate.")
        dao.installReviewedSkill(original, null); dao.deleteSkill(original.id)
        assertFalse(dao.installReviewedSkill(changed, original.digest)); assertNull(dao.skill(original.id))
        assertEquals(0, dao.setSkillEnabledIfUnchanged(original.id, true, original.digest))
    }
    @Test fun enableActionForOlderSkillVersionCannotEnableNewVersion() = runBlocking {
        val original = skill(); val changed = skill("# writing\nNew content.")
        dao.installReviewedSkill(original, null); dao.installReviewedSkill(changed, original.digest)
        assertEquals(0, dao.setSkillEnabledIfUnchanged(original.id, true, original.digest))
        assertFalse(dao.skill(original.id)!!.enabled)
    }
}
