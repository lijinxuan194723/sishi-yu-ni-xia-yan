package cn.sishiyuni.feature.timer

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import cn.sishiyuni.core.AppGraph
import cn.sishiyuni.core.data.TimerEntity
import cn.sishiyuni.core.data.SubjectEntity
import cn.sishiyuni.core.timer.TimerMath
import cn.sishiyuni.core.timer.TimerTransitions
import cn.sishiyuni.designsystem.*
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TimerScreen(graph: AppGraph, padding: PaddingValues, active: Boolean) {
    val vm = nativeViewModel(graph, "timer") { TimerViewModel(graph) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val minutes by vm.minutes.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val reminderWarning by vm.reminderWarning.collectAsStateWithLifecycle()
    var manage by rememberSaveable { mutableStateOf(false) }
    val pager = rememberPagerState { 5 }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(active, lifecycle) {
        if (active) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { tick = SystemClock.elapsedRealtime() / 1000; vm.checkFinished(); delay(1000) }
        }
    }
    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NativeTabs(listOf("学习", "番茄钟", "倒计时", "统计", "提醒"), pager, tag = "timer-tabs")
        ErrorNotice(error, vm::clearError)
        ErrorNotice(reminderWarning, vm::clearReminderWarning)
        HorizontalPager(pager, Modifier.weight(1f), key = { it }) { page ->
            when (page) {
                0 -> {
                    val study = ui.timers.firstOrNull { it.id == "study" }?.takeIf { TimerTransitions.hasStudy(it) && !it.completed }
                    val elapsed = remember(study, tick) { study?.let { TimerMath.studyElapsed(it, vm.time) } ?: 0L }
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(study?.label ?: "夏彦陪你学习", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { manage = true }, enabled = !busy) { Text("科目") }
                        }
                        if (study == null) {
                            var choosing by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(onClick = { if (ui.subjects.isEmpty()) manage = true else choosing = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text(ui.subjects.firstOrNull { it.id == selected }?.name ?: "选择学习科目")
                                }
                                DropdownMenu(choosing, { choosing = false }) {
                                    ui.subjects.forEach { subject -> DropdownMenuItem(text = { Text(subject.name) }, onClick = { vm.choose(subject.id); choosing = false }) }
                                }
                            }
                        }
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            val diameter = minOf(maxWidth - 8.dp, maxHeight - 8.dp).coerceAtLeast(0.dp)
                            if (diameter >= 130.dp) MechanicalDial(elapsed, study?.running == true, active && pager.currentPage == 0,
                                Modifier.size(diameter), liveMillis = { study?.let { TimerMath.studyElapsed(it, vm.time) } ?: 0L })
                            else Text(TimerMath.duration(elapsed), style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
                        }
                        Text("花生也在这里", style = MaterialTheme.typography.bodySmall, color = LocalSeason.current.muted)
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (study == null) Button(onClick = { vm.startStudy() }, enabled = selected.isNotBlank() && !busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("start-study")) { Text("开始计时") }
                            else {
                                OutlinedButton(onClick = { if (study.running) vm.pause("study") else vm.resume("study") }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (study.running) "暂停" else "继续") }
                                Button(onClick = { vm.finish() }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("结束并保存") }
                            }
                        }
                    }
                }
                1 -> PomodoroPage(ui, busy, tick, active && pager.currentPage == 1, vm)
                2 -> {
                    val timer = ui.timers.firstOrNull { it.id == "countdown" }?.takeIf { it.generation.isNotBlank() && !it.completed }
                    val remaining = remember(timer, tick) { timer?.let { TimerMath.remaining(it, vm.time) } ?: minutes * 60000L }
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("给自己留一段时间", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            val diameter = minOf(maxWidth - 28.dp, maxHeight - 8.dp).coerceAtLeast(0.dp)
                            if (diameter >= 130.dp) MechanicalDial(remaining, timer?.running == true, active && pager.currentPage == 2,
                                Modifier.size(diameter), totalMillis = timer?.durationMs ?: minutes * 60000L,
                                liveMillis = { timer?.let { TimerMath.remaining(it, vm.time) } ?: minutes * 60000L })
                            else Text(TimerMath.duration(remaining), style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
                        }
                        TimerRuler(minutes, timer == null && !busy, vm::selectMinutes, { vm.startCountdown(it) })
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (timer == null) Button(onClick = { vm.startCountdown() }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("开始倒计时") }
                            else {
                                OutlinedButton(onClick = { vm.resetCountdown() }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("取消计时") }
                                Button(onClick = { if (timer.running) vm.pause() else vm.resume("countdown") }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (timer.running) "暂停" else "继续") }
                            }
                        }
                    }
                }
                3 -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    item { LukeCard(Modifier.fillMaxWidth()) { Text("专注时光", style = MaterialTheme.typography.titleMedium); Text("${ui.logs.sumOf { it.minutes }.toInt()} 分钟 · ${ui.logs.size} 条记录", style = MaterialTheme.typography.headlineMedium) } }
                    if (ui.logs.isEmpty()) item { Text("还没有学习记录。", style = MaterialTheme.typography.bodyMedium) }
                    items(ui.logs, key = { it.id }) { log -> LukeCard(Modifier.fillMaxWidth()) {
                        Text(log.group.ifBlank { log.title }, style = MaterialTheme.typography.titleSmall)
                        Text("${"%.1f".format(log.minutes)} 分钟 · ${Instant.ofEpochMilli(log.at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))}", style = MaterialTheme.typography.bodySmall)
                    } }
                }
                else -> ReminderPermissions(graph, tick, busy, vm::retryReminders)
            }
        }
    }
    if (manage) SubjectManager(ui.subjects, busy, vm, { manage = false })
}

@Composable
private fun SubjectManager(subjects: List<SubjectEntity>, busy: Boolean, vm: TimerViewModel, close: () -> Unit) {
    var editing by remember { mutableStateOf<SubjectEntity?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var remove by remember { mutableStateOf<SubjectEntity?>(null) }
    val error by vm.error.collectAsStateWithLifecycle()
    NativeDialog("学习科目", close) {
        ErrorNotice(error, vm::clearError)
        OutlinedTextField(name, { if (it.length <= 30) name = it }, label = { Text(if (editing == null) "新科目" else "修改科目") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("subject-name"))
        Button(onClick = { vm.saveSubject(editing?.id, name) { name = ""; editing = null } }, enabled = !busy && name.isNotBlank()) { Text(if (editing == null) "添加" else "保存修改") }
        LazyColumn(Modifier.heightIn(max = 270.dp)) {
            items(subjects, key = { it.id }) { subject -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(subject.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { editing = subject; name = subject.name }, enabled = !busy) { Icon(Icons.Outlined.Edit, "修改${subject.name}") }
                IconButton(onClick = { remove = subject }, enabled = !busy) { Icon(Icons.Outlined.DeleteOutline, "删除${subject.name}") }
            } }
        }
    }
    remove?.let { subject -> AlertDialog(onDismissRequest = { remove = null }, title = { Text("删除${subject.name}？") }, text = { Text("已有学习记录不会删除。") },
        confirmButton = { TextButton(onClick = { vm.deleteSubject(subject); remove = null }) { Text("删除") } }, dismissButton = { TextButton(onClick = { remove = null }) { Text("取消") } }) }
}

@Composable
private fun ReminderPermissions(graph: AppGraph, tick: Long, busy: Boolean, retry: () -> Unit) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val now = remember(tick) { java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { LukeCard(Modifier.fillMaxWidth()) { Text(now, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace); Text("时间到了，夏彦会提醒你。", style = MaterialTheme.typography.bodyMedium) } }
        item { LukeCard(Modifier.fillMaxWidth()) {
            Text("到时提醒", style = MaterialTheme.typography.titleMedium)
            Text("倒计时与番茄钟使用系统闹钟。权限或电池策略可能延迟提醒；没有准时提醒权限时仍保留计时。", style = MaterialTheme.typography.bodySmall)
            Text(if (graph.timer.exactAllowed()) "准时提醒权限已允许" else "当前使用非精确提醒，可能延迟", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = retry, enabled = !busy, modifier = Modifier.testTag("retry-timer-reminders")) { Text("重新设置进行中的提醒") }
            if (Build.VERSION.SDK_INT >= 33) OutlinedButton(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("允许计时通知") }
            if (Build.VERSION.SDK_INT >= 31 && !graph.timer.exactAllowed()) OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            }) { Text("设置准时提醒权限") }
        } }
    }
}
