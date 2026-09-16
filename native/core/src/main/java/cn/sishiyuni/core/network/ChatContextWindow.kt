package cn.sishiyuni.core.network

import cn.sishiyuni.core.data.MessageEntity

object ChatContextWindow {
    fun select(messages: List<MessageEntity>, maxCharacters: Int = 45000, maxMessages: Int = 24): List<MessageEntity> {
        require(maxCharacters > 0 && maxMessages > 0)
        val eligible = messages.filter { it.text.isNotBlank() && (it.who == "me" || it.who == "luke" && it.status == "complete") }
            .sortedBy { it.ordinal }.takeLast(maxMessages)
        var remaining = maxCharacters
        val selected = ArrayList<MessageEntity>()
        for (message in eligible.asReversed()) {
            // Do not skip a large middle message and then stitch unrelated, older messages onto the latest turn.
            if (message.text.length > remaining) break
            selected += message
            remaining -= message.text.length
        }
        return selected.asReversed()
    }
}
