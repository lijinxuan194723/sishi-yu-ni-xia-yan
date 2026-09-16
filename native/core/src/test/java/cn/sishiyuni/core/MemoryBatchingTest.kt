package cn.sishiyuni.core

import cn.sishiyuni.core.data.MessageEntity
import cn.sishiyuni.core.memory.*
import cn.sishiyuni.core.model.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class MemoryBatchingTest {
    private fun row(n: Long, text: String = "消息$n") = MessageEntity("m$n", "s", n, "me", text)

    @Test fun longMessageTailIsIncludedInExtractionPayload() {
        val text = "开头" + "中".repeat(19_980) + "我喜欢爵士乐"
        val selected = MemoryBatching.next(listOf(row(0, text)), -1)
        assertEquals(text, MemoryBatching.payload(selected).single().jsonObject.str("text"))
        assertTrue(MemoryBatching.payload(selected).toString().contains("我喜欢爵士乐"))
    }
    @Test fun successiveBatchesCoverEveryMessageWithoutSkippingOrTruncating() {
        val messages = (0L..80L).map { row(it, "对话".repeat(500) + "尾部$it") }
        var through = -1L
        val extracted = ArrayList<MessageEntity>()
        repeat(100) {
            val batch = MemoryBatching.next(messages, through, maxPayloadCharacters = 6_000, maxMessages = 4)
            if (batch.isNotEmpty()) {
                assertTrue(MemoryBatching.payload(batch).toString().length <= 6_000)
                extracted.addAll(batch); through = batch.last().ordinal
            }
        }
        assertEquals(messages, extracted)
    }
    @Test fun payloadBudgetIncludesJsonEscaping() {
        // Pure newlines are intentionally excluded as blank content; test escaping in a real message.
        val messages = listOf(row(0, "正文" + "\n".repeat(100)), row(1, "正文" + "\n".repeat(100)))
        val batch = MemoryBatching.next(messages, -1, maxPayloadCharacters = 300)
        assertEquals(1, batch.size)
        assertTrue(MemoryBatching.payload(batch).toString().length <= 300)
        assertEquals(messages.first().text, MemoryBatching.payload(batch).single().jsonObject.str("text"))
    }
    @Test fun unrepresentableSingleMessageFailsWithoutInventingACheckpoint() {
        assertTrue(runCatching { MemoryBatching.next(listOf(row(0, "x".repeat(500))), -1, 100) }.isFailure)
    }
    @Test fun streamingReplyStopsCheckpointBeforeUnfinishedText() {
        val messages = listOf(row(0), row(1).copy(who = "luke", status = "streaming"), row(2))
        assertEquals(listOf("m0"), MemoryBatching.next(messages, -1).map { it.id })
        assertTrue(MemoryBatching.next(messages, 0).isEmpty())
    }
    @Test fun longRunOfUserMessagesDoesNotWaitForeverForAssistantInFirst24Rows() {
        val messages = (0L..49L).map { row(it) } + row(50).copy(who = "luke", source = "model")
        assertEquals(24, MemoryBatching.next(messages, -1).size)
        assertEquals(23L, MemoryBatching.next(messages, -1).last().ordinal)
    }
    @Test fun mutedAndFailedTextIsNeverSentToTheExtractor() {
        val batch = MemoryBatching.next(listOf(row(0).copy(muted = true), row(1).copy(who = "luke", status = "error"), row(2)), -1)
        assertEquals(3, batch.size)
        assertEquals(listOf("m2"), MemoryBatching.payload(batch).map { it.jsonObject.str("id") })
    }
    @Test fun allMutedBatchDoesNotNeedModelText() {
        val batch = MemoryBatching.next(listOf(row(0).copy(muted = true)), -1)
        assertEquals(1, batch.size); assertTrue(MemoryBatching.payload(batch).isEmpty())
    }
    @Test fun emptyOrIncompleteModelSummaryIsNotACompletedExtraction() {
        assertFalse(MemoryBatching.answerHasSummary(obj("{}")))
        assertFalse(MemoryBatching.answerHasSummary(obj("""{"summary":" ","facts":[]}""")))
        assertFalse(MemoryBatching.answerHasSummary(obj("""{"summary":"摘要","facts":null}""")))
        assertFalse(MemoryBatching.answerHasSummary(obj("""{"summary":42,"facts":[]}""")))
        assertFalse(MemoryBatching.answerHasSummary(obj("""{"summary":true,"facts":[]}""")))
        assertTrue(MemoryBatching.answerHasSummary(obj("""{"summary":"摘要","facts":[]}""")))
    }
    @Test fun newFingerprintInvalidatesOldTruncatedSummaries() {
        val messages = listOf(row(0))
        val old = buildJsonArray { messages.forEach { m -> add(buildJsonObject {
            put("id",m.id);put("text",m.text);put("who",m.who);put("status",m.status);put("muted",m.muted);put("source",m.source)
        }) } }.toString().sha256()
        assertNotEquals(old, MemoryPolicy.fingerprint(messages))
        assertEquals(MemoryPolicy.fingerprint(messages), MemoryPolicy.fingerprint(messages))
    }
}
