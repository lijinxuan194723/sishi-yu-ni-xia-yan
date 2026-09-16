package cn.sishiyuni.core

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import cn.sishiyuni.core.data.*
import cn.sishiyuni.core.journal.*
import cn.sishiyuni.core.plans.PlansRepository
import cn.sishiyuni.core.model.obj
import cn.sishiyuni.core.model.str
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class JournalAndPlansInstrumentedTest {
    private lateinit var db: LukeDatabase
    private lateinit var dao: LukeDao
    private lateinit var journal: JournalRepository
    private lateinit var plans: PlansRepository
    private lateinit var worker: Job
    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, LukeDatabase::class.java).build()
        dao = db.dao(); worker = SupervisorJob()
        journal = JournalRepository(db, CoroutineScope(worker + Dispatchers.Default))
        plans = PlansRepository(db)
    }
    @After fun teardown() = runBlocking { worker.cancelAndJoin(); db.close() }

    @Test fun journalSaveCommitsAndClearsRecoveryRecord() = runBlocking {
        val id = journal.create("原题", "原文"); val editor = journal.open(id)
        editor.edit(title = "新题", body = "保存正文", mood = "期待")
        assertTrue(journal.close(id))
        val note = requireNotNull(dao.memo(id))
        assertEquals("保存正文", note.body); assertEquals("新题", note.title); assertEquals("期待", note.mood)
        assertEquals(1L, note.revision); assertNull(dao.record("journal-draft", id))
    }
    @Test fun concurrentRevisionKeepsNewMemoAndRecoverableUserDraft() = runBlocking {
        val id = journal.create(body = "原文"); val editor = journal.open(id)
        val old = requireNotNull(dao.memo(id)); dao.putMemo(old.copy(body = "其他位置更新", revision = 1))
        editor.edit(body = "我的草稿")
        assertFalse(editor.flush())
        assertEquals("其他位置更新", dao.memo(id)?.body)
        assertEquals("我的草稿", obj(requireNotNull(dao.record("journal-draft", id)).payload).str("body"))
        assertTrue(editor.state.value.conflict)
    }
    @Test fun reopeningRepositoryRecoversConflictingDraftInsteadOfLosingIt() = runBlocking {
        val id = journal.create(body = "原文"); val editor = journal.open(id)
        dao.putMemo(requireNotNull(dao.memo(id)).copy(body = "外部版本", revision = 2))
        editor.edit(body = "恢复前的文字"); assertFalse(editor.flush())
        worker.cancelAndJoin()
        worker = SupervisorJob(); journal = JournalRepository(db, CoroutineScope(worker + Dispatchers.Default))
        val recovered = journal.open(id)
        assertEquals("恢复前的文字", recovered.state.value.body)
        assertEquals(0L, recovered.state.value.revision)
        assertFalse(recovered.flush()); assertEquals("外部版本", dao.memo(id)?.body)
    }
    @Test fun noteMetadataChangesPreserveTitleBodyAndUnknownPayload() = runBlocking {
        dao.putMemo(MemoEntity("note", "标题", "原内容", raw = "{\"future\":true}"))
        journal.change("note", "star"); journal.change("note", "pin")
        val item = requireNotNull(dao.memo("note"))
        assertEquals("原内容", item.body); assertTrue(item.starred); assertNotNull(item.pinnedAt)
        assertEquals("{\"future\":true}", item.raw); assertEquals(2L, item.revision)
    }
    @Test fun trashIsReversibleAndPurgeRequiresTrashFirst() = runBlocking {
        val id = journal.create(body = "保留")
        try { journal.purge(id); fail("live memo must not be purged") } catch (_: IllegalArgumentException) { }
        journal.change(id, "trash"); assertNotNull(dao.memo(id)?.deletedAt)
        journal.change(id, "restore"); assertNull(dao.memo(id)?.deletedAt); assertEquals("保留", dao.memo(id)?.body)
    }
    @Test fun deletingFolderDoesNotDeleteContainedNotes() = runBlocking {
        val folder = journal.folder(name = "日常"); val id = journal.create(body = "依然在这里")
        journal.change(id, "folder", folder); journal.deleteFolder(folder)
        assertTrue(dao.allFolders().isEmpty()); assertEquals("依然在这里", dao.memo(id)?.body); assertNull(dao.memo(id)?.folderId)
    }
    @Test fun duplicateNotebookNameIsRejectedWithoutLosingExistingFolder() = runBlocking {
        val id = journal.folder(name = "日常")
        try { journal.folder(name = " 日常 "); fail("duplicate must be rejected") } catch (_: IllegalArgumentException) { }
        assertEquals(id, dao.allFolders().single().id)
    }
    @Test fun changingPlanTextDoesNotRevertNewCompletionState() = runBlocking {
        val id = plans.save("2026-09-16", "一起散步")
        val old = dao.allPlans().single()
        plans.done(id, true); plans.important(id, true)
        plans.save("2026-09-17", "一起读书", old)
        val current = dao.allPlans().single()
        assertTrue(current.done); assertTrue(current.important); assertEquals("一起读书", current.text)
    }
    @Test fun stalePlanEditIsRejectedRatherThanOverwritingNewText() = runBlocking {
        plans.save("2026-09-16", "原来的安排"); val old = dao.allPlans().single()
        plans.save("2026-09-16", "已更新的安排", old)
        try { plans.save("2026-09-18", "过期输入", old); fail("stale edit must fail") } catch (_: IllegalStateException) { }
        assertEquals("已更新的安排", dao.allPlans().single().text)
    }
    @Test fun planDeletionHasLosslessUndo() = runBlocking {
        val original = PlanEntity("plan", "2026-09-16", "约定", true, true, "{\"oldVersion\":7}")
        dao.putPlan(original); plans.delete(original); assertTrue(dao.allPlans().isEmpty())
        plans.undoDelete(original.id); assertEquals(original, dao.allPlans().single())
        assertNull(dao.record("deleted-plan", original.id))
    }
    @Test fun changedPlanCannotBeDeletedThroughStaleConfirmation() = runBlocking {
        val id = plans.save("2026-09-16", "原文"); val old = dao.allPlans().single()
        plans.done(id, true)
        try { plans.delete(old); fail("stale delete must fail") } catch (_: IllegalStateException) { }
        assertTrue(dao.allPlans().single().done)
    }
    @Test fun invalidDateDoesNotInsertPlan() = runBlocking {
        try { plans.save("2026-02-29", "无效日期"); fail("invalid date") } catch (_: IllegalArgumentException) { }
        assertTrue(dao.allPlans().isEmpty())
    }
    @Test fun anniversaryUpdateRetainsUnknownFieldsAndRejectsStaleVersion() = runBlocking {
        val old = RecordEntity("anniversary", "date", "{\"title\":\"之前\",\"date\":\"2026-09-16\",\"future\":\"kept\"}")
        dao.putRecord(old); plans.anniversary("后来", "2026-09-17", old)
        val updated = requireNotNull(dao.record("anniversary", "date"))
        assertEquals("kept", obj(updated.payload).str("future"))
        try { plans.deleteAnniversary(old); fail("stale confirmation") } catch (_: IllegalStateException) { }
        assertNotNull(dao.record("anniversary", "date"))
    }
}
