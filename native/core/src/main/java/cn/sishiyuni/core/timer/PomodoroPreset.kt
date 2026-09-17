package cn.sishiyuni.core.timer

import cn.sishiyuni.core.model.*
import kotlinx.serialization.json.*

/** A preset is independent of the active clock, so editing it never mutates a running phase. */
data class PomodoroPreset(val config: PomodoroConfig = PomodoroConfig(), val title: String = "专注", val group: String = "") {
    fun validate(): PomodoroPreset = apply {
        config.validate()
        require(title.isNotBlank() && title.length <= 100 && group.length <= 30) { "任务名称需 1–100 个字，科目不超过 30 个字" }
    }
    fun encode(): String = buildJsonObject {
        put("title", title); put("group", group)
        put("work", config.work); put("short", config.short); put("long", config.long); put("rounds", config.rounds)
    }.toString()
    companion object {
        fun decode(text: String): PomodoroPreset {
            val root = obj(text)
            val data = root[Pomodoro.ID] as? JsonObject ?: root
            fun integer(key: String) = (data[key] as? JsonPrimitive)?.intOrNull ?: error("番茄钟设置 $key 无效")
            return PomodoroPreset(PomodoroConfig(integer("work"), integer("short"), integer("long"), integer("rounds")),
                root.str("title", "专注"), data.str("group")).validate()
        }
    }
}
