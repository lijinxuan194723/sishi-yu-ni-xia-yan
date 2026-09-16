package cn.sishiyuni.core

import cn.sishiyuni.core.data.MemoEntity
import cn.sishiyuni.core.journal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JournalEditorTest {
    @Test fun typingIsCoalescedWithoutTruncatingText() = runTest {
        val writes = mutableListOf<MemoDraft>()
        val editor = MemoEditorSession(MemoEntity("n"), null, { writes += it; it.baseRevision + 1 }, backgroundScope)
        repeat(40) { editor.edit(body = "正文".repeat(it + 1)) }
        runCurrent(); assertTrue(writes.isEmpty())
        advanceTimeBy(351); runCurrent()
        assertEquals(1, writes.size); assertEquals("正文".repeat(40), writes.single().body)
        assertFalse(editor.state.value.dirty)
    }
    @Test fun closeFlushSavesBeforeDebounceExpires() = runTest {
        var saved = ""
        val editor = MemoEditorSession(MemoEntity("n"), null, { saved = it.body; 1 }, backgroundScope)
        editor.edit(body = "立刻返回也不能丢失")
        assertTrue(editor.flush()); assertEquals("立刻返回也不能丢失", saved)
    }
    @Test fun newerTypingDuringAWriteIsNotAcknowledgedAsSaved() = runTest {
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val writes = mutableListOf<MemoDraft>()
        val editor = MemoEditorSession(MemoEntity("n"), null, {
            if (writes.isEmpty()) { started.complete(Unit); release.await() }
            writes += it; it.baseRevision + 1
        }, backgroundScope)
        editor.edit(body = "第一段")
        val saving = async { editor.flush() }; started.await()
        editor.edit(body = "第一段，接着写的新文字")
        release.complete(Unit); assertTrue(saving.await())
        assertEquals(2, writes.size); assertEquals(1L, writes.last().baseRevision)
        assertEquals("第一段，接着写的新文字", writes.last().body); assertFalse(editor.state.value.dirty)
    }
    @Test fun failedSaveKeepsTheWholeDraftAndCanRetry() = runTest {
        var failing = true
        val editor = MemoEditorSession(MemoEntity("n"), null, { if (failing) error("空间不足") else it.baseRevision + 1 }, backgroundScope)
        editor.edit(body = "没有保存也必须保留")
        assertFalse(editor.flush()); assertTrue(editor.state.value.dirty)
        assertEquals("没有保存也必须保留", editor.state.value.body)
        failing = false; assertTrue(editor.flush()); assertNull(editor.state.value.error)
    }
    @Test fun staleRevisionIsNotSilentlyOverwritten() = runTest {
        val editor = MemoEditorSession(MemoEntity("n", revision = 3), null, { throw MemoRevisionConflict() }, backgroundScope)
        editor.edit(body = "我的未保存版本")
        assertFalse(editor.flush()); assertTrue(editor.state.value.conflict)
        assertEquals(3L, editor.state.value.revision); assertEquals("我的未保存版本", editor.state.value.body)
    }
    @Test fun recoveredDraftRetainsItsOwnBaseRevision() = runTest {
        val editor = MemoEditorSession(MemoEntity("n", body = "新版本", revision = 9),
            MemoDraft("n", "草稿", "断电前内容", "平静", 4), { throw MemoRevisionConflict() }, backgroundScope)
        assertEquals(4L, editor.state.value.revision); assertEquals("断电前内容", editor.state.value.body)
        assertFalse(editor.flush())
    }
    @Test fun unchangedMemoDoesNotWrite() = runTest {
        var writes = 0
        val editor = MemoEditorSession(MemoEntity("n", body = "相同"), null, { writes++; 1 }, backgroundScope)
        editor.edit(body = "相同"); assertTrue(editor.flush()); assertEquals(0, writes)
    }
    @Test fun limitsRejectNewValueWithoutDestroyingPreviousText() = runTest {
        val editor = MemoEditorSession(MemoEntity("n", body = "原文"), null, { 1 }, backgroundScope)
        assertFalse(editor.edit(body = "字".repeat(100001))); assertEquals("原文", editor.state.value.body)
        assertTrue(editor.edit(body = "恢复正常")); assertNull(editor.state.value.error)
    }
    @Test fun cancellationDoesNotReportSuccessfulSave() = runTest {
        val editor = MemoEditorSession(MemoEntity("n"), null, { throw CancellationException("cancel") }, backgroundScope)
        editor.edit(body = "保持草稿")
        try { editor.flush(); fail("must propagate cancellation") } catch (_: CancellationException) { }
        assertFalse(editor.state.value.saving); assertTrue(editor.state.value.dirty)
    }
    @Test fun titleBodyAndMoodAreSavedTogether() = runTest {
        var saved: MemoDraft? = null
        val editor = MemoEditorSession(MemoEntity("n"), null, { saved = it; 1 }, backgroundScope)
        editor.edit(title = "今天", body = "正文", mood = "开心"); editor.flush()
        assertEquals(MemoDraft("n", "今天", "正文", "开心", 0), saved)
    }
    @Test fun boldKeepsInsertionBetweenMarkers() {
        assertEquals(MemoSelection("a****b", 3, 3), MemoFormatting.bold("ab", 1, 1))
    }
    @Test fun boldCanBeToggledOffWithoutRemovingText() {
        val marked = MemoFormatting.bold("夏彦", 0, 2)
        assertEquals(MemoSelection("夏彦", 0, 2), MemoFormatting.bold(marked.text, marked.start, marked.end))
    }
    @Test fun selectedLinesAreAllFormattedAndToggleBack() {
        val result = MemoFormatting.lines("一\n二\n三", 0, 3, "- ")
        assertEquals("- 一\n- 二\n三", result.text)
        assertEquals("一\n二\n三", MemoFormatting.lines(result.text, result.start, result.end, "- ").text)
    }
    @Test fun selectionEndingAtNextLineDoesNotChangeThatLine() {
        assertEquals("> 一\n二", MemoFormatting.lines("一\n二", 0, 2, "> ").text)
    }
    @Test fun reversedSelectionAndEmojiKeepOffsets() {
        val text = "夏彦😀"
        val formatted = MemoFormatting.bold(text, text.length, 0)
        assertEquals("**夏彦😀**", formatted.text); assertEquals(text.length + 2, formatted.end)
    }
    @Test fun emptyLineCanBecomeTodo() {
        assertEquals("- [ ] ", MemoFormatting.lines("", 0, 0, "- [ ] ").text)
    }
}
