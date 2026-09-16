package cn.sishiyuni.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.sishiyuni.core.AppGraph
import cn.sishiyuni.core.CoreViewModel
import cn.sishiyuni.core.data.SkillEntity
import cn.sishiyuni.core.skills.SkillCandidate
import cn.sishiyuni.designsystem.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import androidx.compose.ui.unit.dp


data class SkillReview(val candidate: SkillCandidate, val previousDigest: String?)
class SkillsViewModel(private val app: AppGraph) : CoreViewModel(app) {
    val installed = app.dao.skills().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val reviews = MutableStateFlow<List<SkillReview>>(emptyList())
    val inspecting = MutableStateFlow(false)
    val writing = MutableStateFlow(false)
    private var inspectJob: Job? = null
    private var generation = 0L
    private val gate = Mutex()
    private fun inspect(fetch: suspend () -> List<SkillCandidate>) {
        val current = ++generation
        inspectJob?.cancel()
        inspecting.value = true
        reviews.value = emptyList()
        inspectJob = viewModelScope.launch {
            try {
                val candidates = fetch()
                val checked = candidates.map { SkillReview(it, app.dao.skill(it.skill.id)?.digest) }
                if (generation == current) reviews.value = checked
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (generation == current) error.value = e.message ?: "无法读取技能" }
            finally { if (generation == current) inspecting.value = false }
        }
    }
    fun fromFile(uri: Uri) = inspect { app.skills.inspect(uri) }
    fun fromGithub(url: String) = inspect { app.skills.github(url) }
    fun cancelInspect() { generation++; inspectJob?.cancel(); inspecting.value = false }
    fun dismissReview(id: String) { reviews.update { rows -> rows.filterNot { it.candidate.skill.id == id } } }
    private fun write(block: suspend () -> Unit) = viewModelScope.launch {
        if (!gate.tryLock()) return@launch
        writing.value = true
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error.value = e.message ?: "修改未保存" }
        finally { writing.value = false; gate.unlock() }
    }
    fun install(review: SkillReview, done: () -> Unit) = write {
        app.skills.install(review.candidate, review.previousDigest)
        dismissReview(review.candidate.skill.id); done()
    }
    fun enable(reviewed: SkillEntity, enabled: Boolean, done: () -> Unit = {}) = write {
        app.skills.setEnabled(reviewed.id, enabled, reviewed.digest); done()
    }
    fun uninstall(id: String, done: () -> Unit) = write { app.skills.uninstall(id); done() }
}

@Composable
fun SkillsScreen(graph: AppGraph, padding: PaddingValues = PaddingValues(), onBack: (() -> Unit)? = null) {
    val vm = nativeViewModel(graph, "skills") { SkillsViewModel(graph) }
    val installed by vm.installed.collectAsStateWithLifecycle()
    val reviews by vm.reviews.collectAsStateWithLifecycle()
    val inspecting by vm.inspecting.collectAsStateWithLifecycle()
    val writing by vm.writing.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var github by rememberSaveable { mutableStateOf("") }
    var openReview by remember { mutableStateOf<SkillReview?>(null) }
    var readInstalled by remember { mutableStateOf<SkillEntity?>(null) }
    var removal by remember { mutableStateOf<SkillEntity?>(null) }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::fromFile) }
    Column(Modifier.fillMaxSize().padding(padding)) {
        NativeTitle("Skills", onBack)
        ErrorNotice(error, vm::clearError, Modifier.padding(horizontal = 16.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { LukeCard(Modifier.fillMaxWidth()) {
                Text("添加技能", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(github, { github = it.take(2000) }, modifier = Modifier.fillMaxWidth(), label = { Text("GitHub 仓库或 SKILL.md 地址") }, maxLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.fromGithub(github) }, enabled = github.isNotBlank() && !inspecting && !writing) { Text("读取链接") }
                    OutlinedButton(onClick = { file.launch(arrayOf("text/*", "application/zip", "application/octet-stream")) }, enabled = !inspecting && !writing) { Text("选择文件") }
                }
                if (inspecting) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    TextButton(onClick = vm::cancelInspect) { Text("取消读取") }
                }
            } }
            items(reviews, key = { "review:${it.candidate.skill.id}" }) { review -> LukeCard(Modifier.fillMaxWidth()) {
                Text(review.candidate.skill.name, style = MaterialTheme.typography.titleMedium)
                Text(review.candidate.skill.description, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { openReview = review }, enabled = !writing) { Text(if (review.previousDigest == null) "查看后安装" else "查看更新") }
            } }
            if (installed.isNotEmpty()) item { Text("已安装", style = MaterialTheme.typography.titleMedium) }
            items(installed, key = { it.id }) { skill -> LukeCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(skill.name, style = MaterialTheme.typography.titleMedium)
                        Text(skill.description, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = skill.enabled, enabled = !writing, onCheckedChange = { enabled ->
                        if (enabled) readInstalled = skill else vm.enable(skill, false)
                    }, modifier = Modifier.testTag("skill-enabled-${skill.id}"))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { readInstalled = skill }) { Text("内容") }
                    if (skill.source.startsWith("https://github.com/")) TextButton(onClick = { vm.fromGithub(skill.source) }, enabled = !inspecting && !writing) { Text("检查更新") }
                    TextButton(onClick = { removal = skill }, enabled = !writing) { Text("卸载") }
                }
            } }
            item { Text("这里安装的是聊天方法说明，不会执行脚本或取得设备控制权限。启用的技能内容会随聊天请求发送给你配置的模型服务。", style = MaterialTheme.typography.bodySmall, color = LocalSeason.current.muted) }
        }
    }
    openReview?.let { review -> NativeDialog(if (review.previousDigest == null) "安装技能" else "更新技能", { if (!writing) openReview = null }) {
        ErrorNotice(error, vm::clearError)
        Text(review.candidate.skill.name, style = MaterialTheme.typography.titleMedium)
        SelectionContainer { Text(review.candidate.skill.content, Modifier.weight(1f, fill = false).heightIn(max = 350.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) }
        Text(review.candidate.warnings.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
        Button(onClick = { vm.install(review) { openReview = null } }, enabled = !writing, modifier = Modifier.testTag("confirm-skill-install")) { Text("确认安装，保持停用") }
    } }
    readInstalled?.let { skill -> NativeDialog("技能内容", { if (!writing) readInstalled = null }) {
        ErrorNotice(error, vm::clearError)
        Text(skill.name, style = MaterialTheme.typography.titleMedium)
        SelectionContainer { Text(skill.content, Modifier.weight(1f, fill = false).heightIn(max = 360.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) }
        Text("仅将这份方法说明用于聊天；不开放脚本、相册或密钥访问。", style = MaterialTheme.typography.bodySmall)
        if (!skill.enabled) Button(onClick = { vm.enable(skill, true) { readInstalled = null } }, enabled = !writing) { Text("确认启用这个版本") }
    } }
    removal?.let { skill -> AlertDialog(onDismissRequest = { removal = null }, title = { Text("卸载 ${skill.name}？") },
        text = { Text("聊天记录不会删除，后续请求不再使用这个技能。") },
        confirmButton = { TextButton(onClick = { vm.uninstall(skill.id) { removal = null } }, enabled = !writing) { Text("卸载") } },
        dismissButton = { TextButton(onClick = { removal = null }) { Text("取消") } }) }
}
