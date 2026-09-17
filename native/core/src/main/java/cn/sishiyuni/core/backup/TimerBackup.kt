package cn.sishiyuni.core.backup

import cn.sishiyuni.core.data.TimerEntity
import cn.sishiyuni.core.model.*
import cn.sishiyuni.core.timer.*
import kotlinx.serialization.json.*

/** A portable paused snapshot, not a mutation of the live clock or its Android alarm. */
object TimerBackup {
    private const val DAY = 86_400_000L
    private fun segments(timer: TimerEntity): Long {
        val raw = obj(timer.raw)
        val rows = when (val value = raw["segments"]) {
            null -> JsonArray(emptyList())
            is JsonArray -> value
            else -> error("学习时段格式错误，未丢弃原记录")
        }
        require(rows.size <= 20000) { "学习时段数量过多，请先结束并保存学习" }
        return rows.fold(0L) { sum, item ->
            val row = item as? JsonObject ?: error("学习时段格式错误")
            val from = (row["from"] as? JsonPrimitive)?.longOrNull ?: error("学习时段缺少开始时间")
            val millis = (row["millis"] as? JsonPrimitive)?.longOrNull ?: error("学习时段缺少时长")
            require(from >= 0 && millis in 0..DAY && from <= Long.MAX_VALUE - millis)
            Math.addExact(sum, millis)
        }
    }
    fun encode(timer: TimerEntity, time: TimeSource): JsonObject {
        require(!timer.completed || !timer.running) { "计时状态不一致，未改动原记录" }
        if (timer.kind == "study") require(segments(timer) == timer.remainingMs) { "学习时长与已保存时段不一致" }
        val value = if (timer.kind == "study") TimerTransitions.pauseStudy(timer, time)
            else timer.copy(running = false, remainingMs = TimerMath.remaining(timer, time))
        return buildJsonObject {
            put("id", value.id); put("kind", value.kind); put("label", value.label)
            put("durationMs", value.durationMs); put("remainingMs", value.remainingMs)
            put("completed", value.completed); put("running", false); put("wasRunning", timer.running)
            put("startedWall", value.startedWall); put("generation", value.generation); put("raw", value.raw)
            put("snapshotVersion", 1)
        }
    }
    fun decode(row: JsonObject): TimerEntity {
        fun long(key: String) = (row[key] as? JsonPrimitive)?.longOrNull ?: error("计时备份缺少有效的 $key")
        val kind = row.str("kind")
        require(kind in setOf("study", "countdown", Pomodoro.ID)) { "此计时类型暂不支持导入" }
        val duration = long("durationMs"); val remaining = long("remainingMs")
        require(duration in 0..DAY && remaining >= 0 && remaining <= Long.MAX_VALUE - DAY)
        if (kind != "study") require(remaining <= duration) { "倒计时剩余时间大于总时长" }
        val generation = row.str("generation")
        require(generation.length <= 200)
        val completed = row.flag("completed")
        val sourceRunning = row.flag("running")
        require(!completed || !sourceRunning)
        if (generation.isBlank()) require(!sourceRunning && !completed && if (kind == "study") remaining == 0L else remaining == duration) {
            "计时备份缺少活动编号，请在原应用重新导出"
        }
        val timer = TimerEntity(row.str("id"), kind, bounded(row.str("label"), 100, "提醒标签"), duration, remaining,
            0, 0, 0, false, completed, long("startedWall"), if (generation.isBlank()) "" else newId(),
            bounded(row.str("raw", "{}"), 2_000_000, "计时附加数据"))
        if (kind == "study") require(segments(timer) == remaining) {
            "此备份未完整保存学习时段，请在原应用暂停计时后重新导出。原文件未改动。"
        }
        if (Pomodoro.isActive(timer)) Pomodoro.state(timer)
        return timer
    }
}
