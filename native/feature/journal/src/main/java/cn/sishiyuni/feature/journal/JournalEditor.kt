package cn.sishiyuni.feature.journal

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.sishiyuni.core.journal.MemoEditorSession
import cn.sishiyuni.core.journal.MemoFormatting
import cn.sishiyuni.designsystem.*

@Composable
fun JournalEditor(session: MemoEditorSession, vm: JournalViewModel, onClose: () -> Unit, onCopied: (String) -> Unit) {
    val state by session.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var reading by remember(state.id) { mutableStateOf(false) }
    var showMood by remember(state.id) { mutableStateOf(false) }
    var title by remember(state.id) { mutableStateOf(TextFieldValue(state.title)) }
    var body by remember(state.id) { mutableStateOf(TextFieldValue(state.body)) }
    // Text stays in the application-owned editor, not in a potentially oversized saved-state Bundle.
    var exportSnapshot by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val text = exportSnapshot
        exportSnapshot = null
        if (uri != null && text != null) vm.export(uri, text)
    }
    fun close() { if (!busy) vm.close(onClose) }
    fun format(prefix: String?) {
        val edited = if (prefix == null) MemoFormatting.bold(body.text, body.selection.start, body.selection.end)
            else MemoFormatting.lines(body.text, body.selection.start, body.selection.end, prefix)
        if (session.edit(body = edited.text)) body = TextFieldValue(edited.text, TextRange(edited.start, edited.end))
    }
    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler { close() }
        NativeScreenFrame("时光手记", onBack = ::close, actions = {
            IconButton(onClick = { reading = !reading }, modifier = Modifier.testTag("journal-read-toggle")) {
                Icon(if (reading) Icons.Outlined.Edit else Icons.Outlined.Visibility, if (reading) "继续编辑" else "阅读手记")
            }
            IconButton(onClick = {
                exportSnapshot = state.title + "\n\n" + state.body
                export.launch("手记.txt")
            }, enabled = !busy) { Icon(Icons.Outlined.FileDownload, "导出正文") }
        }, footer = { layer ->
            GlassChrome(layer, Modifier.fillMaxWidth(), fadeAtBottom = false) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AssetImage("images/companions/cat.webp", null, Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(when {
                            state.conflict -> "草稿待处理"
                            state.error != null -> "尚未保存"
                            state.saving || state.dirty -> "保存中"
                            else -> "已保存"
                        }, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f).testTag("journal-save-state"))
                        Text("${state.body.codePointCount(0, state.body.length)} 字", style = MaterialTheme.typography.labelSmall)
                    }
                    if (!reading) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("journal-format-dock")) {
                        IconButton(onClick = { showMood = true }) { Icon(Icons.Outlined.Mood, "记录心情") }
                        IconButton(onClick = { format("## ") }, modifier = Modifier.testTag("journal-heading")) { Icon(Icons.Outlined.Title, "二级标题") }
                        IconButton(onClick = { format("- ") }, modifier = Modifier.testTag("journal-list")) { Icon(Icons.Outlined.FormatListBulleted, "项目列表") }
                        IconButton(onClick = { format("- [ ] ") }, modifier = Modifier.testTag("journal-task")) { Icon(Icons.Outlined.CheckBoxOutlineBlank, "待办事项") }
                        IconButton(onClick = { format(null) }, modifier = Modifier.testTag("journal-bold")) { Icon(Icons.Outlined.FormatBold, "加粗") }
                        IconButton(onClick = { format("> ") }, modifier = Modifier.testTag("journal-quote")) { Icon(Icons.Outlined.FormatQuote, "引用") }
                    }
                }
            }
        }) { padding ->
            LazyColumn(Modifier.fillMaxSize().consumeWindowInsets(padding).testTag("journal-editor-content"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 12.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    ErrorNotice(error, vm::clearError)
                    if (state.error != null) {
                        Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { vm.retry() }, enabled = !busy && !state.conflict) { Text("重试保存") }
                            TextButton(onClick = { vm.copyDraft(onCopied) }, enabled = !busy) { Text("另存一篇") }
                        }
                    }
                }
                item {
                    if (reading) SelectionContainer { Text(state.title.ifBlank { "无标题手记" }, style = MaterialTheme.typography.headlineSmall) }
                    else OutlinedTextField(title, { next -> if (session.edit(title = next.text)) title = next },
                        placeholder = { Text("标题") }, textStyle = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.fillMaxWidth().testTag("journal-title"), maxLines = 4)
                }
                if (state.mood.isNotBlank()) item { Text(state.mood, style = MaterialTheme.typography.bodySmall, color = LocalSeason.current.muted) }
                item {
                    if (reading) SelectionContainer {
                        Text(state.body.ifBlank { "还没有正文" }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())
                    } else OutlinedTextField(body, { next -> if (session.edit(body = next.text)) body = next },
                        placeholder = { Text("把今天值得记住的小事留在这里。") }, textStyle = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp).testTag("journal-body"))
                }
            }
        }
        if (showMood) {
            var mood by remember { mutableStateOf(state.mood) }
            AlertDialog(onDismissRequest = { showMood = false }, title = { Text("此刻心情") },
                text = { OutlinedTextField(mood, { if (it.length <= 50) mood = it }, singleLine = true, placeholder = { Text("写下一个词，也可以留空") }) },
                confirmButton = { TextButton(onClick = { if (session.edit(mood = mood.trim())) showMood = false }) { Text("保存") } },
                dismissButton = { TextButton(onClick = { showMood = false }) { Text("取消") } })
        }
    }
}
