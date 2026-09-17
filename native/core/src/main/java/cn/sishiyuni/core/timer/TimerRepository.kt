package cn.sishiyuni.core.timer

import android.content.*
import android.os.SystemClock
import android.provider.Settings
import androidx.room.withTransaction
import cn.sishiyuni.core.GraphOwner
import cn.sishiyuni.core.data.*
import cn.sishiyuni.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

class AndroidTime(private val context: Context) : TimeSource {
    override fun wall() = System.currentTimeMillis()
    override fun elapsed() = SystemClock.elapsedRealtime()
    override fun boot() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
}

class TimerRepository(
    context: Context,
    private val db: LukeDatabase,
    val time: TimeSource = AndroidTime(context),
    private val alarms: TimerAlarms = AndroidTimerAlarms(context, time),
) {
    private val mutex = Mutex()
    val states = db.dao().timers()
    val reminderWarning = MutableStateFlow<String?>(null)

    private fun schedule(timer: TimerEntity) {
        try { alarms.schedule(timer) }
        catch (_: RuntimeException) { reminderWarning.value = "计时已保存，但系统提醒未设置成功。可在提醒页重试。" }
    }
    fun exactAllowed(): Boolean = alarms.exactAllowed()

    suspend fun startCountdown(minutes: Int, label: String = "夏彦提醒你：休息一下", replaceExisting: Boolean = false) = mutex.withLock {
        require(minutes in 1..180)
        val old = db.dao().timer("countdown")
        TimerTransitions.requireCountdownReplacement(old, replaceExisting)
        val duration = minutes * 60000L
        val timer = TimerEntity(durationMs = duration, remainingMs = duration,
            elapsedDeadline = time.elapsed() + duration, wallDeadline = time.wall() + duration,
            bootCount = time.boot(), running = true, startedWall = time.wall(), generation = newId(), label = label.take(100))
        db.dao().putTimer(timer)
        if (old != null) alarms.cancel(old)
        alarms.dismiss(timer.id)
        reminderWarning.value = null
        schedule(timer)
    }

    suspend fun savePomodoroPreset(preset: PomodoroPreset) = mutex.withLock {
        preset.validate()
        check(!Pomodoro.isActive(db.dao().timer(Pomodoro.ID))) { "请先结束这一组，再修改番茄钟设置" }
        db.dao().putRecord(RecordEntity("timer-config", Pomodoro.ID, preset.encode(), time.wall()))
    }
    suspend fun startPomodoro(config: PomodoroConfig, label: String, group: String = "") = mutex.withLock {
        val preset = PomodoroPreset(config, label.trim(), group.trim()).validate()
        val old = db.dao().timer(Pomodoro.ID)
        if (Pomodoro.isActive(old)) {
            val state = Pomodoro.state(old!!)
            check(old.running && state.config == preset.config && old.label == preset.title && state.group == preset.group) {
                "还有未结束的番茄钟，请继续或先结束这一组"
            }
            return@withLock
        }
        val timer = Pomodoro.start(Pomodoro.ready(preset.config, preset.title, preset.group, newId()), time)
        db.withTransaction {
            db.dao().putTimer(timer)
            db.dao().putRecord(RecordEntity("timer-config", Pomodoro.ID, preset.encode(), time.wall()))
        }
        if (old != null) alarms.cancel(old)
        alarms.dismiss(timer.id)
        reminderWarning.value = null
        schedule(timer)
    }

    suspend fun pause(id: String = "countdown") = mutex.withLock {
        val timer = db.dao().timer(id) ?: return@withLock
        if (!timer.running) return@withLock
        if (timer.kind != "study" && TimerMath.remaining(timer, time) == 0L) {
            finishLocked(timer.id, timer.generation)
            return@withLock
        }
        val paused = if (timer.kind == "study") TimerTransitions.pauseStudy(timer, time)
            else timer.copy(remainingMs = TimerMath.remaining(timer, time), running = false)
        db.dao().putTimer(paused)
        alarms.cancel(timer)
    }

    suspend fun resume(id: String = "countdown") = mutex.withLock {
        val timer = db.dao().timer(id) ?: return@withLock
        if (timer.running || timer.completed || timer.generation.isBlank()) return@withLock
        val study = timer.kind == "study"
        val resumed = if (timer.kind == Pomodoro.ID) Pomodoro.start(timer, time) else timer.copy(
            running = true, elapsedDeadline = if (study) time.elapsed() else time.elapsed() + timer.remainingMs,
            wallDeadline = if (study) time.wall() else time.wall() + timer.remainingMs, bootCount = time.boot())
        db.dao().putTimer(resumed)
        alarms.dismiss(id)
        if (!study) {
            reminderWarning.value = null
            if (TimerMath.remaining(resumed, time) == 0L) finishLocked(id, resumed.generation) else schedule(resumed)
        }
    }

    suspend fun reset(id: String = "countdown", discardStudyConfirmed: Boolean = false) = mutex.withLock {
        val timer = db.dao().timer(id) ?: return@withLock
        check(!Pomodoro.isActive(timer)) { "结束番茄钟需要确认，已完成的专注记录会保留" }
        resetLocked(timer, discardStudyConfirmed)
    }
    suspend fun cancelPomodoro(confirmed: Boolean) = mutex.withLock {
        check(confirmed) { "请先确认结束这一组番茄钟" }
        val old = db.dao().timer(Pomodoro.ID) ?: return@withLock
        // Cancellation at or after the deadline must not lose an already completed work period.
        if (old.running && TimerMath.remaining(old, time) == 0L) finishLocked(old.id, old.generation)
        db.dao().timer(Pomodoro.ID)?.let { resetLocked(it, false) }
    }
    private suspend fun resetLocked(timer: TimerEntity, discardStudyConfirmed: Boolean) {
        db.dao().putTimer(TimerTransitions.reset(timer, discardStudyConfirmed))
        alarms.cancel(timer)
        alarms.dismiss(timer.id)
    }
    suspend fun startStudy(subject: String) = mutex.withLock {
        val old = db.dao().timer("study")
        val next = TimerTransitions.startStudy(old, subject, time, newId())
        if (next !== old) db.dao().putTimer(next)
    }
    suspend fun finishStudy() = mutex.withLock {
        db.withTransaction {
            val timer = db.dao().timer("study") ?: return@withTransaction
            if (timer.completed || !TimerTransitions.hasStudy(timer)) return@withTransaction
            val paused = TimerTransitions.pauseStudy(timer, time)
            obj(paused.raw).arr("segments").forEachIndexed { i, element ->
                val segment = element.jsonObject
                TimerMath.splitStudy(segment.num("from"), segment.num("millis").coerceIn(0, 24 * 3600000L)).forEachIndexed { j, (at, minutes) ->
                    if (minutes > 0) db.dao().putFocusLog(FocusLogEntity("${timer.generation}:$i:$j", at, minutes, timer.label, timer.label, "study"))
                }
            }
            db.dao().putTimer(paused.copy(completed = true))
        }
    }

    private suspend fun finishLocked(id: String, generation: String) {
        val fired = db.withTransaction {
            val timer = db.dao().timer(id) ?: return@withTransaction null
            if (timer.kind == "study" || !timer.running || timer.completed || timer.generation != generation) return@withTransaction null
            if (TimerMath.remaining(timer, time) > 0) { schedule(timer); return@withTransaction null }
            if (timer.kind == Pomodoro.ID) {
                val result = Pomodoro.complete(timer, time, newId()) ?: return@withTransaction null
                result.log?.let { db.dao().putFocusLog(it) }
                db.dao().putTimer(result.next)
            } else db.dao().putTimer(timer.copy(running = false, completed = true, remainingMs = 0))
            timer
        }
        if (fired != null) {
            alarms.cancel(fired)
            try { alarms.notifyFinished(fired) }
            catch (_: RuntimeException) { reminderWarning.value = "计时已结束，系统未能显示提醒。" }
        }
    }
    suspend fun fire(id: String, generation: String) = mutex.withLock { finishLocked(id, generation) }

    suspend fun restore() = mutex.withLock {
        reminderWarning.value = null
        db.dao().allTimers().filter { it.running && !it.completed && it.kind != "study" }.forEach { timer ->
            val remaining = TimerMath.remaining(timer, time)
            if (remaining == 0L) {
                // Finish against the original boot/deadline before rebasing. Otherwise a
                // delayed reboot would move yesterday's finished study record into today.
                finishLocked(timer.id, timer.generation)
            } else {
                val updated = if (timer.bootCount != time.boot()) timer.copy(bootCount = time.boot(),
                    elapsedDeadline = time.elapsed() + remaining, wallDeadline = time.wall() + remaining) else timer
                db.dao().putTimer(updated)
                schedule(updated)
            }
        }
    }
    suspend fun checkForeground() {
        db.dao().allTimers().filter { it.running && it.kind != "study" && TimerMath.remaining(it, time) == 0L }
            .forEach { fire(it.id, it.generation) }
    }
}

private fun BroadcastReceiver.timerWork(context: Context, work: suspend (cn.sishiyuni.core.AppGraph) -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            withTimeout(8000) {
                val graph = (context.applicationContext as? GraphOwner)?.graph ?: return@withTimeout
                try { work(graph) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { graph.errors.value = "计时状态暂未处理完成，重新打开应用后会重试。" }
            }
        } finally { pending.finish() }
    }
}
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "finish") return
        val id = intent.getStringExtra("id") ?: return
        val generation = intent.getStringExtra("generation") ?: return
        timerWork(context) { it.timer.fire(id, generation) }
    }
}
class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { timerWork(context) { it.timer.restore() } }
}
