package cn.sishiyuni.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.sishiyuni.core.AppGraph
import cn.sishiyuni.designsystem.*
import cn.sishiyuni.feature.chat.ChatViewModel

/** Synchronous editing echo; persistence is still owned by the application repository. */
@Composable
internal fun AppChatComposer(graph: AppGraph, source: GraphicsLayer?, modifier: Modifier = Modifier) {
    val vm = nativeViewModel(graph, "chat") { ChatViewModel(graph) }
    val active by vm.active.collectAsStateWithLifecycle()
    val drafts by vm.drafts.collectAsStateWithLifecycle()
    val pending by vm.replying.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val initial = remember(active) { vm.drafts.value[active] }
    var field by remember(active) { mutableStateOf(TextFieldValue(initial?.text.orEmpty(), TextRange(initial?.text?.length ?: 0))) }
    var revision by remember(active) { mutableLongStateOf(initial?.revision ?: -1L) }
    val draft = drafts[active]
    LaunchedEffect(active, draft?.revision, draft?.text) {
        val latest = vm.drafts.value[active]
        if (latest != null && latest.revision >= revision) {
            if (field.text != latest.text) field = TextFieldValue(latest.text, TextRange(latest.text.length))
            revision = latest.revision
        }
    }
    val colors = LocalSeason.current
    GlassChrome(source, modifier.clip(RoundedCornerShape(32.dp))) {
        Surface(color = colors.paper.copy(alpha = .8f), shape = RoundedCornerShape(32.dp), border = BorderStroke(.7.dp, colors.border)) {
            Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
                TextField(field, { next ->
                    // A stopped/replaced activity's InputConnection must not overwrite a live draft.
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && vm.active.value == active && graph.prefs.state.value.activeSession == active) {
                        if (next.text == field.text) field = next
                        else {
                            vm.editDraft(next.text)
                            val accepted = vm.drafts.value[active]
                            if (accepted?.text == next.text) { field = next; revision = accepted.revision }
                        }
                    }
                }, modifier = Modifier.weight(1f).heightIn(min = 48.dp, max = 160.dp).testTag("chat-input"),
                    enabled = active in drafts, placeholder = { Text("想和你说……") }, maxLines = 5,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = LocalAppPreferences.current.chatSize.sp),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent))
                IconButton(onClick = { if (pending?.sessionId == active) vm.stop() else vm.send() },
                    enabled = !busy && (pending?.sessionId == active || field.text.isNotBlank()),
                    modifier = Modifier.size(48.dp).testTag("chat-send")) {
                    Icon(if (pending?.sessionId == active) Icons.Outlined.StopCircle else Icons.AutoMirrored.Outlined.Send,
                        if (pending?.sessionId == active) "停止回复" else "发送", tint = colors.accent)
                }
            }
        }
    }
}
