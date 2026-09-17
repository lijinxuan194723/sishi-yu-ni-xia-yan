package cn.sishiyuni.core

import cn.sishiyuni.core.data.MemoEntity
import cn.sishiyuni.core.journal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MemoEditorLifecycleTest {
    @Test fun successfulCloseSavesAndRejectsFurtherInputThroughAStaleHandle() = runTest {
        val saved = mutableListOf<MemoDraft>()
        val editor = MemoEditorSession(MemoEntity("n"), null, { saved += it; it.baseRevision + 1 }, backgroundScope)
        assertTrue(editor.edit(body = "关闭前输入"))
        assertTrue(editor.close())
        assertTrue(editor.state.value.closed)
        assertFalse(editor.edit(body = "不该被接受的新输入"))
        assertEquals("关闭前输入", editor.state.value.body)
        assertEquals("关闭前输入", saved.single().body)
        assertTrue(editor.close())
        assertEquals(1, saved.size)
    }
    @Test fun closeFreezesInputBeforeTheLastAsynchronousWrite() = runTest {
        val entered = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        var saved = ""
        val editor = MemoEditorSession(MemoEntity("n"), null, {
            entered.complete(Unit); finish.await(); saved = it.body; it.baseRevision + 1
        }, backgroundScope)
        editor.edit(body = "已接受的正文")
        val closing = async { editor.close() }; entered.await()
        assertTrue(editor.state.value.closing)
        assertFalse(editor.edit(body = "关闭期间输入"))
        finish.complete(Unit)
        assertTrue(closing.await()); assertEquals("已接受的正文", saved)
        assertFalse(editor.state.value.dirty)
    }
    @Test fun failedCloseRetainsDraftAndAllowsEditingAndRetry() = runTest {
        var fail = true
        var saved = ""
        val editor = MemoEditorSession(MemoEntity("n"), null, {
            if (fail) error("磁盘写入失败")
            saved = it.body; it.baseRevision + 1
        }, backgroundScope)
        editor.edit(body = "保留")
        assertFalse(editor.close())
        assertFalse(editor.state.value.closed); assertFalse(editor.state.value.closing)
        assertTrue(editor.edit(body = "保留并继续写"))
        fail = false
        assertTrue(editor.close()); assertEquals("保留并继续写", saved)
    }
    @Test fun cancelledCloseDoesNotPermanentlyDisableTheEditor() = runTest {
        val entered = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val editor = MemoEditorSession(MemoEntity("n"), null, {
            entered.complete(Unit); finish.await(); it.baseRevision + 1
        }, backgroundScope)
        editor.edit(body = "不要丢掉")
        val job = launch { editor.close() }; entered.await(); job.cancelAndJoin()
        assertFalse(editor.state.value.closing); assertFalse(editor.state.value.closed)
        assertTrue(editor.edit(body = "取消关闭后继续"))
        finish.complete(Unit); assertTrue(editor.close())
    }
    @Test fun closeWaitsForAnAlreadyRunningSaveAndIncludesAcceptedNewerText() = runTest {
        val entered = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val saved = mutableListOf<MemoDraft>()
        val editor = MemoEditorSession(MemoEntity("n"), null, {
            if (saved.isEmpty()) { entered.complete(Unit); finish.await() }
            saved += it; it.baseRevision + 1
        }, backgroundScope)
        editor.edit(body = "第一段")
        val saving = async { editor.flush() }; entered.await()
        assertTrue(editor.edit(body = "第一段和第二段"))
        val closing = async { editor.close() }; runCurrent()
        assertFalse(editor.edit(body = "已经进入关闭阶段"))
        finish.complete(Unit)
        assertTrue(saving.await()); assertTrue(closing.await())
        assertEquals(listOf("第一段", "第一段和第二段"), saved.map { it.body })
        assertEquals(2L, editor.state.value.revision)
    }
    @Test fun aConflictingCloseNeverClaimsThatTheDraftIsSaved() = runTest {
        val editor = MemoEditorSession(MemoEntity("n"), null, { throw MemoRevisionConflict() }, backgroundScope)
        editor.edit(body = "我的草稿")
        assertFalse(editor.close()); assertTrue(editor.state.value.dirty)
        assertTrue(editor.state.value.conflict); assertFalse(editor.state.value.closed)
        assertTrue(editor.edit(body = "我的完整草稿"))
        assertEquals("我的完整草稿", editor.state.value.body)
    }
}
