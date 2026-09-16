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
            return previous // A repeated start event must not restart the clock.
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
        if (!timer.running) return timer
        val total = TimerMath.studyElapsed(timer, time)
        val segment = buildJsonObject {
            put("from", timer.wallDeadline)
            put("millis", (total - timer.remainingMs).coerceAtLeast(0))
        }
        // A malformed stored segment is an error, not a reason to silently drop previous study intervals.
        val original = obj(timer.raw)
        val raw = original.change("segments" to JsonArray(original.arr("segments") + segment)).toString()
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
