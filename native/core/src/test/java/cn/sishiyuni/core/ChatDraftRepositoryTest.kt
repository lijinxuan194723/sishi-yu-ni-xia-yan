package cn.sishiyuni.core

import cn.sishiyuni.core.data.ChatDraftRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatDraftRepositoryTest {
    @Test fun loadsStoredDraftWithoutClearingIt() = runTest {
        val repo = ChatDraftRepository({ "原草稿" }, { _, _ -> error("must not write on read") }, backgroundScope)
        repo.load("a"); assertEquals("原草稿", repo.state.value.getValue("a").text)
    }
    @Test fun repeatedLoadDoesNotOverwriteNewTyping() = runTest {
        val repo = ChatDraftRepository({ "原稿" }, { _, _ -> }, backgroundScope)
        repo.load("a"); repo.edit("a", "新内容"); repo.load("a")
        assertEquals("新内容", repo.state.value.getValue("a").text)
    }
    @Test fun consecutiveEditsAreCoalesced() = runTest {
        val writes = mutableListOf<String>()
        val repo = ChatDraftRepository({ "" }, { _, text -> writes += text }, backgroundScope)
        repo.load("a")
        repeat(14) { repo.edit("a", "文字$it"); advanceTimeBy(20) }
        advanceTimeBy(351); runCurrent()
        assertEquals(listOf("文字13"), writes)
    }
    @Test fun flushPersistsLatestWithoutWaitingForDebounce() = runTest {
        val writes = mutableListOf<String>()
        val repo = ChatDraftRepository({ "" }, { _, text -> writes += text }, backgroundScope)
        repo.load("a"); repo.edit("a", "立即保留"); repo.flush("a")
        advanceTimeBy(500); runCurrent()
        assertEquals(listOf("立即保留"), writes)
    }
    @Test fun separateSessionsNeverShareDrafts() = runTest {
        val disk = mutableMapOf("a" to "", "b" to "")
        val repo = ChatDraftRepository({ disk[it] }, { id, text -> disk[id] = text }, backgroundScope)
        repo.load("a"); repo.load("b"); repo.edit("a", "甲"); repo.edit("b", "乙")
        repo.flush("b"); repo.flush("a")
        assertEquals("甲", disk["a"]); assertEquals("乙", disk["b"])
    }
    @Test fun successfulSubmissionClearsOnlyTheSubmittedRevision() = runTest {
        var saved = ""
        val repo = ChatDraftRepository({ "" }, { _, text -> saved = text }, backgroundScope)
        repo.load("a"); repo.edit("a", "发出去")
        var sent = ""
        assertTrue(repo.submit("a") { sent = it })
        assertEquals("发出去", sent); assertEquals("", saved); assertEquals("", repo.state.value.getValue("a").text)
    }
    @Test fun typingWhileSendIsSuspendedSurvivesSendCompletion() = runTest {
        val entered = CompletableDeferred<Unit>(); val resume = CompletableDeferred<Unit>()
        var saved = ""
        val repo = ChatDraftRepository({ "" }, { _, text -> saved = text }, backgroundScope)
        repo.load("a"); repo.edit("a", "第一条")
        val sending = async { repo.submit("a") { assertEquals("第一条", it); entered.complete(Unit); resume.await() } }
        entered.await(); repo.edit("a", "第二条草稿"); resume.complete(Unit); assertTrue(sending.await())
        assertEquals("第二条草稿", repo.state.value.getValue("a").text); assertEquals("第二条草稿", saved)
    }
    @Test fun rejectedSendRetainsInput() = runTest {
        val repo = ChatDraftRepository({ "" }, { _, _ -> }, backgroundScope)
        repo.load("a"); repo.edit("a", "不能丢失")
        val result = runCatching { repo.submit("a") { error("no key") } }
        assertTrue(result.isFailure); assertEquals("不能丢失", repo.state.value.getValue("a").text)
    }
    @Test fun failedDiskWriteCanBeRetriedWithoutLosingInput() = runTest {
        var broken = true; var saved = ""
        val repo = ChatDraftRepository({ "" }, { _, text -> if (broken) error("full") else saved = text }, backgroundScope)
        repo.load("a"); repo.edit("a", "待保存")
        advanceTimeBy(351); runCurrent(); assertNotNull(repo.error.value)
        assertEquals("待保存", repo.state.value.getValue("a").text)
        broken = false; repo.flush("a"); assertEquals("待保存", saved); assertNull(repo.error.value)
    }
    @Test fun missingSessionDoesNotCreateAnEmptyDraft() = runTest {
        val repo = ChatDraftRepository({ null }, { _, _ -> error("must not write") }, backgroundScope)
        repo.load("missing"); assertTrue(repo.state.value.isEmpty())
        assertTrue(runCatching { repo.edit("missing", "text") }.isFailure)
    }
    @Test fun oversizedEditKeepsPreviousText() = runTest {
        val repo = ChatDraftRepository({ "原文" }, { _, _ -> }, backgroundScope)
        repo.load("a"); assertTrue(runCatching { repo.edit("a", "x".repeat(20001)) }.isFailure)
        assertEquals("原文", repo.state.value.getValue("a").text)
    }
    @Test fun blankSubmissionDoesNotInvokeModel() = runTest {
        val repo = ChatDraftRepository({ "  " }, { _, _ -> error("must not write") }, backgroundScope)
        repo.load("a"); assertFalse(repo.submit("a") { error("must not send") })
    }
}
