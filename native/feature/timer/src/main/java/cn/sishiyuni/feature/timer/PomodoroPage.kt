package cn.sishiyuni.feature.timer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.sishiyuni.core.timer.*
import cn.sishiyuni.designsystem.*

@Composable
internal fun PomodoroPage(ui: TimerUi, busy: Boolean, tick: Long, active: Boolean, vm: TimerViewModel) {
    val timer = ui.timers.firstOrNull { it.id == Pomodoro.ID }?.takeIf { Pomodoro.isActive(it) }
    val decoded = remember(timer) { timer?.let { runCatching { Pomodoro.state(it) } } }
    val phase = decoded?.getOrNull()
    val issue = if (timer == null) ui.presetError else decoded?.exceptionOrNull()?.let { "计时记录无法读取，未重置：${it.message}" }
    var configure by rememberSaveable { mutableStateOf(false) }
    var ending by rememberSaveable { mutableStateOf(false) }
    val error by vm.error.collectAsStateWithLifecycle()
    val remaining = remember(timer, ui.preset, tick) {
        timer?.let { TimerMath.remaining(it, vm.time) } ?: (ui.preset?.config?.work ?: 25) * 60000L
    }
    val enabled = ui.loaded && !busy && issue == null
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("pomodoro-page"), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(timer?.label ?: ui.preset?.title ?: "番茄钟", style = MaterialTheme.typography.titleMedium)
                        Text(when {
                            !ui.loaded -> "正在读取设置"
                            timer == null -> "把这一段时间，留给眼前的事"
                            timer.running -> "${phase?.phaseName ?: "计时"}中"
                            timer.startedWall > 0 -> "已暂停"
                            else -> "${phase?.phaseName ?: "下一阶段"}已就绪，点击后开始"
                        }, style = MaterialTheme.typography.bodySmall, color = LocalSeason.current.muted)
                    }
                    TextButton(onClick = { vm.clearError(); configure = true }, enabled = enabled && timer == null,
                        modifier = Modifier.testTag("pomodoro-configure")) { Text("设置") }
                }
            }
            issue?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) } }
            item {
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val diameter = minOf(maxWidth, 260.dp)
                    MechanicalDial(remaining, timer?.running == true, active, Modifier.size(diameter),
                        totalMillis = timer?.durationMs ?: remaining,
                        liveMillis = { timer?.let { TimerMath.remaining(it, vm.time) } ?: remaining })
                }
            }
            item {
                LukeCard(Modifier.fillMaxWidth()) {
                    val config = phase?.config ?: ui.preset?.config
                    Text(if (phase == null) "专注与休息" else "${phase.phaseName} · 已完成 ${phase.completed}/${phase.config.rounds} 轮",
                        style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("pomodoro-phase"))
                    if (config != null) Text("专注 ${config.work} 分钟 · 短休 ${config.short} 分钟\n每 ${config.rounds} 轮长休 ${config.long} 分钟",
                        style = MaterialTheme.typography.bodyMedium)
                    (phase?.group ?: ui.preset?.group)?.takeIf { it.isNotBlank() }?.let {
                        Text("科目：$it", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("到时后先停下来，下一阶段由你点击开始。休息不计入专注时长。", style = MaterialTheme.typography.bodySmall,
                        color = LocalSeason.current.muted)
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (timer == null) vm.startPomodoro()
                else if (timer.running) vm.pause(Pomodoro.ID) else vm.resume(Pomodoro.ID)
            }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("pomodoro-primary")) {
                Text(when {
                    timer == null -> "开始专注"
                    timer.running -> "暂停"
                    timer.startedWall > 0 -> "继续${phase?.phaseName.orEmpty()}"
                    else -> "开始${phase?.phaseName.orEmpty()}"
                })
            }
            if (timer != null) OutlinedButton(onClick = { vm.clearError(); ending = true }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("pomodoro-end")) { Text("结束这一组") }
        }
    }
    if (configure && timer == null && ui.preset != null) PomodoroSettings(ui.preset, busy, error, vm::clearError,
        { configure = false }, { value -> vm.savePomodoro(value) { configure = false } })
    if (ending && timer != null) NativeDialog("结束这一组番茄钟？", { if (!busy) ending = false }) {
        ErrorNotice(error, vm::clearError)
        Text("已完成的专注记录会保留。当前尚未完成的阶段不会计入统计；设置和任务名称仍会保留。",
            style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { ending = false }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("返回计时") }
        Button(onClick = { vm.endPomodoro { ending = false } }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("pomodoro-confirm-end")) { Text("确认结束") }
    }
}

@Composable
private fun PomodoroSettings(preset: PomodoroPreset, busy: Boolean, error: String?, clearError: () -> Unit,
                             close: () -> Unit, save: (PomodoroPreset) -> Unit) {
    // Seed once. Database emissions must not erase partially typed settings.
    var title by rememberSaveable { mutableStateOf(preset.title) }
    var group by rememberSaveable { mutableStateOf(preset.group) }
    var work by rememberSaveable { mutableStateOf(preset.config.work.toString()) }
    var short by rememberSaveable { mutableStateOf(preset.config.short.toString()) }
    var long by rememberSaveable { mutableStateOf(preset.config.long.toString()) }
    var rounds by rememberSaveable { mutableStateOf(preset.config.rounds.toString()) }
    var invalid by rememberSaveable { mutableStateOf<String?>(null) }
    NativeDialog("番茄钟设置", { if (!busy) close() }) {
        ErrorNotice(error, clearError)
        ErrorNotice(invalid, { invalid = null })
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).testTag("pomodoro-fields"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title, { if (it.length <= 100) title = it }, label = { Text("专注任务") }, enabled = !busy,
                singleLine = true, modifier = Modifier.fillMaxWidth().testTag("pomodoro-title"))
            OutlinedTextField(group, { if (it.length <= 30) group = it }, label = { Text("科目（可留空）") }, enabled = !busy,
                singleLine = true, modifier = Modifier.fillMaxWidth().testTag("pomodoro-group"))
            MinuteField("专注分钟（1–180）", work, !busy, "work") { work = it }
            MinuteField("短休分钟（1–60）", short, !busy, "short") { short = it }
            MinuteField("长休分钟（1–120）", long, !busy, "long") { long = it }
            MinuteField("每组轮数（1–12）", rounds, !busy, "rounds") { rounds = it }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = close, enabled = !busy, modifier = Modifier.weight(1f)) { Text("取消") }
            Button(onClick = {
                val parsed = runCatching {
                    fun number(value: String) = value.toIntOrNull() ?: kotlin.error("请填写有效的整数")
                    PomodoroPreset(PomodoroConfig(number(work), number(short), number(long), number(rounds)), title.trim(), group.trim()).validate()
                }
                parsed.onSuccess { invalid = null; save(it) }.onFailure { invalid = it.message ?: "请核对设置" }
            }, enabled = !busy, modifier = Modifier.weight(1f).testTag("pomodoro-save")) { Text("保存") }
        }
    }
}

@Composable
private fun MinuteField(label: String, value: String, enabled: Boolean, tag: String, change: (String) -> Unit) {
    OutlinedTextField(value, { if (it.length <= 3 && it.all { c -> c in '0'..'9' }) change(it) },
        label = { Text(label) }, singleLine = true, enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().testTag("pomodoro-$tag"))
}
