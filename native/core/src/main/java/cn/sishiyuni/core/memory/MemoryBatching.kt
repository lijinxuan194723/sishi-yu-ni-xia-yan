package cn.sishiyuni.core.memory

import cn.sishiyuni.core.data.MessageEntity
import kotlinx.serialization.json.*

/** The checkpoint only advances across whole messages, never a truncated copy of a message. */
object MemoryBatching {
    const val FORMAT_VERSION = 2
    private fun readable(message: MessageEntity) = !message.muted && message.status == "complete" &&
        message.who in setOf("me", "luke") && message.text.isNotBlank()

    private fun encode(message: MessageEntity) = buildJsonObject {
        put("id", message.id)
        put("role", message.who)
        put("text", message.text)
    }

    fun payload(messages: List<MessageEntity>): JsonArray = buildJsonArray {
        messages.filter(::readable).forEach { add(encode(it)) }
    }

    fun next(rows: List<MessageEntity>, through: Long, maxPayloadCharacters: Int = 60_000, maxMessages: Int = 24): List<MessageEntity> {
        require(maxPayloadCharacters > 2 && maxMessages > 0)
        val result = ArrayList<MessageEntity>()
        var size = 2 // JSON array brackets; count escaping as well as the original text.
        for (message in rows.asSequence().filter { it.ordinal > through }.sortedBy { it.ordinal }) {
            if (message.status == "streaming") break
            val increment = if (readable(message)) encode(message).toString().length + 1 else 0
            if (size + increment > maxPayloadCharacters) {
                check(result.isNotEmpty()) { "单条历史消息超过自动整理的请求上限；原文与整理进度均已保留" }
                break
            }
            result += message
            size += increment
            if (result.size >= maxMessages) break
        }
        return result
    }

    fun answerHasSummary(answer: JsonObject): Boolean =
        (answer["summary"] as? JsonPrimitive)?.contentOrNull?.isNotBlank() == true && answer["facts"] is JsonArray
}
