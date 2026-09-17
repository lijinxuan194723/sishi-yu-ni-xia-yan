package cn.sishiyuni.core.timer

import cn.sishiyuni.core.data.FocusLogEntity
import cn.sishiyuni.core.data.TimerEntity
import cn.sishiyuni.core.model.*
import kotlinx.serialization.json.*

/** Same work/short/long cycle as the legacy app. Starting each new phase is explicit. */
data class PomodoroConfig(val work: Int = 25, val short: Int = 5, val long: Int = 15, val rounds: Int = 4) {
    fun validate(): PomodoroConfig = apply {
        require(work in 1..180 && short in 1..60 && long in 1..120 && rounds in 1..12) {
            "专注需 1–180 分钟，短休 1–60 分钟，长休 1–120 分钟，每组 1–12 轮"
        }
    }
}

data class PomodoroState(
    val config: PomodoroConfig = PomodoroConfig(),
    val phase: String = "work",
    val completed: Int = 0,
    val group: String = "",
) {
    val phaseName: String get() = when (phase) { "work" -> "专注"; "short" -> "短休息"; else -> "长休息" }
    val minutes: Int get() = when (phase) { "work" -> config.work; "short" -> config.short; else -> config.long }
    fun validate(): PomodoroState = apply {
        config.validate()
        require(group.length <= 30)
        require(when (phase) {
            "work" -> completed in 0 until config.rounds
            "short" -> completed in 1 until config.rounds
            "long" -> completed == config.rounds
            else -> false
        }) { "番茄钟阶段数据不完整，原记录已保留" }
    }
    fun next(): PomodoroState = when (phase) {
        "work" -> copy(completed = completed + 1, phase = if (completed + 1 == config.rounds) "long" else "short")
        "short" -> copy(phase = "work")
        else -> copy(phase = "work", completed = 0)
    }.validate()
}

data class PomodoroCompletion(val next: TimerEntity, val log: FocusLogEntity?)

object Pomodoro {
    const val ID = "pomodoro"
    fun isActive(timer: TimerEntity?): Boolean = timer != null && timer.kind == ID && !timer.completed && timer.generation.isNotBlank()

    fun state(timer: TimerEntity): PomodoroState {
        require(timer.kind == ID && timer.generation.isNotBlank())
        val raw = obj(timer.raw)
        val p = raw[ID] as? JsonObject ?: error("番茄钟配置读取失败，未重置原记录")
        fun number(name: String): Int = (p[name] as? JsonPrimitive)?.intOrNull ?: error("番茄钟配置 $name 无效")
        val result = PomodoroState(PomodoroConfig(number("work"), number("short"), number("long"), number("rounds")),
            p.str("phase"), number("completed"), p.str("group")).validate()
        require(timer.durationMs == result.minutes * 60000L && timer.remainingMs in 0..timer.durationMs) {
            "番茄钟时长与阶段不一致，原记录已保留"
        }
        return result
    }

    private fun raw(state: PomodoroState, previous: String = "{}"): String = obj(previous).change(ID to buildJsonObject {
        put("work", state.config.work); put("short", state.config.short); put("long", state.config.long)
        put("rounds", state.config.rounds); put("phase", state.phase); put("completed", state.completed); put("group", state.group)
    }).toString()

    fun ready(config: PomodoroConfig, label: String, group: String, generation: String): TimerEntity {
        require(generation.isNotBlank())
        val title = label.trim()
        require(title.length in 1..100) { "请填写 1–100 个字的专注任务" }
        val state = PomodoroState(config.validate(), group = group.trim()).validate()
        return TimerEntity(id = ID, kind = ID, label = title, durationMs = state.minutes * 60000L,
            remainingMs = state.minutes * 60000L, generation = generation, raw = raw(state))
    }

    fun start(timer: TimerEntity, time: TimeSource): TimerEntity {
        state(timer)
        check(!timer.completed)
        if (timer.running) return timer
        return timer.copy(running = true, elapsedDeadline = time.elapsed() + timer.remainingMs,
            wallDeadline = time.wall() + timer.remainingMs, bootCount = time.boot(),
            startedWall = timer.startedWall.takeIf { it > 0 } ?: time.wall())
    }

    /** Called in the same Room transaction as its optional log. A late alarm cannot start a break. */
    fun complete(timer: TimerEntity, time: TimeSource, nextGeneration: String): PomodoroCompletion? {
        if (!timer.running || timer.completed || TimerMath.remaining(timer, time) > 0L) return null
        val current = state(timer)
        require(nextGeneration.isNotBlank() && nextGeneration != timer.generation)
        val next = current.next()
        val lateBy = if (timer.bootCount == time.boot()) (time.elapsed() - timer.elapsedDeadline).coerceAtLeast(0) else 0
        val finishedAt = if (timer.bootCount == time.boot()) (time.wall() - lateBy).coerceAtLeast(0) else timer.wallDeadline.coerceAtLeast(0)
        val log = if (current.phase == "work") FocusLogEntity(
            id = "pomodoro:${timer.generation}", at = finishedAt, minutes = current.config.work.toDouble(),
            title = timer.label, group = current.group, kind = ID,
            raw = buildJsonObject { put("round", current.completed + 1); put("rounds", current.config.rounds) }.toString(),
        ) else null
        return PomodoroCompletion(timer.copy(durationMs = next.minutes * 60000L, remainingMs = next.minutes * 60000L,
            running = false, completed = false, elapsedDeadline = 0, wallDeadline = 0, startedWall = 0,
            generation = nextGeneration, raw = raw(next, timer.raw)), log)
    }
}
