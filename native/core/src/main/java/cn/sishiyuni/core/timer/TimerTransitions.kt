package cn.sishiyuni.core.timer

import cn.sishiyuni.core.data.TimerEntity
import cn.sishiyuni.core.model.*
import kotlinx.serialization.json.*

/** Pure transitions shared by the repository and tests; animation never owns timer state. */
object TimerTransitions {
    fun startStudy(previous: TimerEntity?, subject: String, time: TimeSource, generation: String): TimerEntity {
        val name = subject.trim()
        require(name.length in 1..30) { "请选择学习科目" }
        require(generation.isNotBlank())
        if (previous?.running == true) {
            require(previous.label == name) { "请先结束当前科目的学习" }
            return previous
        }
        check(previous == null || previous.completed || !hasStudy(previous)) { "还有暂停中的学习，请继续或先结束这一段" }
        return TimerEntity(id = "study", kind = "study", label = name,
            elapsedDeadline = time.elapsed(), wallDeadline = time.wall(), bootCount = time.boot(),
            running = true, startedWall = time.wall(), generation = generation)
    }

    fun hasStudy(timer: TimerEntity): Boolean = timer.kind == "study" && !timer.completed &&
        (timer.generation.isNotBlank() || timer.running || timer.remainingMs > 0 || obj(timer.raw).arr("segments").isNotEmpty())

    fun pauseStudy(timer: TimerEntity, time: TimeSource): TimerEntity {
        require(timer.kind == "study")
        val original = obj(timer.raw)
        val intervals = when (val stored = original["segments"]) {
            null -> JsonArray(emptyList())
            is JsonArray -> stored
            else -> error("学习时段格式异常，原记录未改动")
        }
        var accounted = 0L
        for (element in intervals) {
            val row = element as? JsonObject ?: error("学习时段格式异常")
            val from = (row["from"] as? JsonPrimitive)?.longOrNull ?: error("学习时段缺少开始时间")
            val millis = (row["millis"] as? JsonPrimitive)?.longOrNull ?: error("学习时段缺少时长")
            require(from >= 0 && millis in 0..86_400_000L && from <= Long.MAX_VALUE - millis) { "学习时段超出有效范围" }
            accounted = Math.addExact(accounted, millis)
        }
        require(accounted == timer.remainingMs) { "学习累计时长与明细不一致，原记录未改动" }
        // Validation also runs for an already-paused timer: finishStudy uses this
        // transition before recording it, and must not turn malformed data into zero logs.
        if (!timer.running) return timer
        val total = TimerMath.studyElapsed(timer, time)
        val duration = (total - timer.remainingMs).coerceAtLeast(0)
        require(timer.wallDeadline >= 0 && timer.wallDeadline <= Long.MAX_VALUE - duration)
        val segment = buildJsonObject { put("from", timer.wallDeadline); put("millis", duration) }
        val raw = original.change("segments" to JsonArray(intervals + segment)).toString()
        return timer.copy(remainingMs = total, running = false, raw = raw)
    }

    fun requireCountdownReplacement(previous: TimerEntity?, confirmed: Boolean) {
        val unfinished = previous != null && !previous.completed &&
            (previous.running || previous.generation.isNotBlank() && previous.remainingMs > 0)
        check(!unfinished || confirmed) { "已有未结束的倒计时，请先暂停、结束或确认替换" }
    }

    fun reset(timer: TimerEntity, discardStudyConfirmed: Boolean): TimerEntity {
        if (timer.kind == "study") {
            check(!hasStudy(timer) || discardStudyConfirmed) { "重置会丢弃这段学习，请先结束保存或明确确认丢弃" }
            return TimerEntity(id = timer.id, kind = "study", label = timer.label)
        }
        return timer.copy(running = false, completed = false, remainingMs = timer.durationMs,
            elapsedDeadline = 0, wallDeadline = 0, startedWall = 0, generation = "", raw = "{}")
    }
}
