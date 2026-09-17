package cn.sishiyuni.feature.timer

import androidx.lifecycle.viewModelScope
import cn.sishiyuni.core.AppGraph
import cn.sishiyuni.core.CoreViewModel
import cn.sishiyuni.core.data.*
import cn.sishiyuni.core.model.newId
import cn.sishiyuni.core.timer.TimeSource
import cn.sishiyuni.core.timer.Pomodoro
import cn.sishiyuni.core.timer.PomodoroPreset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex


data class TimerUi(
    val timers: List<TimerEntity> = emptyList(),
    val subjects: List<SubjectEntity> = emptyList(),
    val logs: List<FocusLogEntity> = emptyList(),
    val preset: PomodoroPreset? = null,
    val presetError: String? = null,
    val loaded: Boolean = false,
)
class TimerViewModel(graph: AppGraph) : CoreViewModel(graph) {
    val ui = combine(graph.timer.states, graph.dao.subjects(), graph.dao.focusLogs(), graph.dao.records("timer-config")) { timers, subjects, logs, configs ->
        val saved = configs.firstOrNull { it.id == Pomodoro.ID }
        val parsed = runCatching { saved?.let { PomodoroPreset.decode(it.payload) } ?: PomodoroPreset() }
        TimerUi(timers, subjects.filterNot { it.deleted }, logs, parsed.getOrNull(),
            parsed.exceptionOrNull()?.let { "番茄钟设置无法读取，原设置未覆盖：${it.message}" }, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimerUi())
    val selected = MutableStateFlow("")
    val minutes = MutableStateFlow(25)
    val busy = MutableStateFlow(false)
    val reminderWarning = graph.timer.reminderWarning.asStateFlow()
    fun clearReminderWarning() { graph.timer.reminderWarning.value = null }
    fun retryReminders() = command { graph.timer.restore() }
    fun startPomodoro() = command {
        check(ui.value.loaded) { "正在读取计时设置" }
        // Re-read the persisted preset at the command boundary, not a stale UI copy.
        val saved = graph.dao.record("timer-config", Pomodoro.ID)
        val preset = saved?.let { PomodoroPreset.decode(it.payload) } ?: PomodoroPreset()
        graph.timer.startPomodoro(preset.config, preset.title, preset.group)
    }
    fun savePomodoro(preset: PomodoroPreset, done: () -> Unit) = command {
        graph.timer.savePomodoroPreset(preset.validate())
        done()
    }
    fun endPomodoro(done: () -> Unit) = command {
        graph.timer.cancelPomodoro(confirmed = true)
        done()
    }
    private val commands = Mutex()
    private val boot = graph.timer.time.boot()
    val time = object : TimeSource {
        override fun boot() = boot
        override fun elapsed() = graph.timer.time.elapsed()
        override fun wall() = graph.timer.time.wall()
    }
    private fun command(block: suspend () -> Unit) = viewModelScope.launch {
        if (!commands.tryLock()) return@launch
        busy.value = true
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error.value = e.message ?: "操作没有保存，请重试" }
        finally { busy.value = false; commands.unlock() }
    }
    fun choose(id: String) { selected.value = id }
    fun selectMinutes(value: Int) { minutes.value = value.coerceIn(1, 180) }
    fun startStudy() = command {
        val subject = graph.dao.allSubjects().firstOrNull { it.id == selected.value && !it.deleted }
            ?: error("请先选择学习科目")
        graph.timer.startStudy(subject.name)
    }
    fun startCountdown(value: Int = minutes.value) = command { selectMinutes(value); graph.timer.startCountdown(minutes.value) }
    fun pause(id: String = "countdown") = command { graph.timer.pause(id) }
    fun resume(id: String) = command { graph.timer.resume(id) }
    fun finish() = command { graph.timer.finishStudy() }
    fun resetCountdown() = command { graph.timer.reset() }
    fun checkFinished() = task { graph.timer.checkForeground() }
    fun saveSubject(id: String?, name: String, done: () -> Unit) = command {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        require(clean.length in 1..30) { "科目名称需要 1–30 个字" }
        require(graph.dao.allSubjects().none { !it.deleted && it.id != id && it.name == clean }) { "已经有这个科目了" }
        val key = id ?: newId()
        graph.dao.putSubject(SubjectEntity(key, clean))
        if (selected.value.isEmpty()) selected.value = key
        done()
    }
    fun deleteSubject(subject: SubjectEntity) = command {
        graph.dao.putSubject(subject.copy(deleted = true))
        if (selected.value == subject.id) selected.value = ""
    }
}
