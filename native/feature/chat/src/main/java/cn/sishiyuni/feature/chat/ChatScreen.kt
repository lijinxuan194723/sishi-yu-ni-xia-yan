package cn.sishiyuni.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.sishiyuni.core.AppGraph
import cn.sishiyuni.core.data.MessageEntity
import cn.sishiyuni.core.data.SessionEntity
import cn.sishiyuni.designsystem.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ChatScreen(graph: AppGraph, padding: PaddingValues, toolsOpen: Boolean, closeTools: () -> Unit,
               onSettings: () -> Unit, onSkills: () -> Unit) {
    val vm = nativeViewModel(graph, "chat") { ChatViewModel(graph) }
    val active by vm.active.collectAsStateWithLifecycle()
    val all by vm.messages.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val replyError by vm.replyError.collectAsStateWithLifecycle()
    val draftError by vm.draftError.collectAsStateWithLifecycle()
    val prefs = LocalAppPreferences.current
    val messages = remember(all, active) { all.filter { it.sessionId == active }.asReversed() }
    val scene = when (prefs.resolvedSeason()) {
        "spring" -> "images/luke-blossom.webp"
        "summer" -> "images/luke-camera.webp"
        "autumn" -> "images/luke-sunset.webp"
        else -> "images/luke-nap.webp"
    }
    var actions by remember { mutableStateOf<MessageEntity?>(null) }
    var editing by remember { mutableStateOf<MessageEntity?>(null) }
    var bannerHeight by remember { mutableIntStateOf(0) }
    val bannerSpace = with(LocalDensity.current) { bannerHeight.toDp() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flushDraft() }
    Box(Modifier.fillMaxSize().testTag("chat-screen")) {
        AssetImage(scene, null, Modifier.fillMaxSize(), ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(LocalSeason.current.paper.copy(alpha = .88f)))
        key(active) {
            val list = rememberLazyListState()
            val scope = rememberCoroutineScope()
            val nearLatest by remember { derivedStateOf { list.firstVisibleItemIndex < 2 } }
            LaunchedEffect(messages.firstOrNull()?.id) { if (nearLatest && messages.isNotEmpty()) list.scrollToItem(0) }
            LazyColumn(Modifier.fillMaxSize().testTag("chat-messages"), state = list, reverseLayout = true,
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp,
                    top = padding.calculateTopPadding() + bannerSpace + 16.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(messages, key = { it.id }) { message -> MessageRow(message, { actions = message }) }
                if (messages.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        AssetImage(prefs.avatarLuke, null, Modifier.size(72.dp))
                        Text("想说的话，慢慢说。", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (!nearLatest) SmallFloatingActionButton(onClick = { scope.launch { list.animateScrollToItem(0) } },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = padding.calculateBottomPadding() + 8.dp),
                containerColor = LocalSeason.current.paper) { Icon(Icons.Outlined.KeyboardDoubleArrowDown, "回到最新消息") }
        }
        Column(Modifier.align(Alignment.TopCenter).padding(top = padding.calculateTopPadding(), start = 16.dp, end = 16.dp)
            .onSizeChanged { bannerHeight = it.height }) {
            ErrorNotice(error, vm::clearError)
            ErrorNotice(replyError, vm::dismissReplyError)
            if (draftError != null) Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(draftError.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = vm::flushDraft) { Text("重试保存") }
                }
            }
        }
    }
    if (toolsOpen) ConversationTools(vm, closeTools, onSettings, onSkills)
    actions?.let { message -> NativeDialog("这条消息", { actions = null }) {
        TextButton(onClick = { vm.star(message.id); actions = null }) { Text(if (message.favorite) "取消加星" else "加星收藏") }
        if (message.who == "me") TextButton(onClick = { editing = message; actions = null }) { Text("更正消息") }
        SelectionContainer { Text(message.text, maxLines = 6, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }
    } }
    editing?.let { message ->
        var text by rememberSaveable(message.id) { mutableStateOf(message.text) }
        NativeDialog("更正消息", { editing = null }) {
            ErrorNotice(error, vm::clearError)
            OutlinedTextField(text, { if (it.length <= 20000) text = it }, Modifier.fillMaxWidth().heightIn(max = 300.dp), minLines = 3)
            Button(onClick = { vm.editMessage(message.id, text) { editing = null } }, enabled = text.isNotBlank()) { Text("保存更正") }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(message: MessageEntity, onMore: () -> Unit) {
    val prefs = LocalAppPreferences.current
    val colors = LocalSeason.current
    val mine = message.who == "me"
    val skin = if (mine) prefs.bubbleMine else prefs.bubbleLuke
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maximum = (maxWidth - 64.dp).coerceAtLeast(100.dp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
            horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
            if (!mine) {
                AssetImage(prefs.avatarLuke, "夏彦头像", Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).testTag("avatar-${message.id}"))
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.widthIn(max = maximum), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                val bubble = if (skin == "plain" && mine) colors.soft else colors.paper
                Surface(Modifier.combinedClickable(onClick = {}, onLongClick = onMore).testTag("bubble-${message.id}"),
                    shape = RoundedCornerShape(topStart = if (mine) 19.dp else 5.dp, topEnd = if (mine) 5.dp else 19.dp,
                        bottomEnd = 19.dp, bottomStart = 19.dp), color = bubble,
                    border = BorderStroke(.8.dp, if (skin == "plain") colors.border else colors.accent.copy(alpha = .42f))) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        SelectionContainer { Text(message.text.ifBlank {
                            if (message.status == "streaming") "正在回复…" else "这次回复没有正文"
                        }, fontSize = prefs.chatSize.sp, lineHeight = (prefs.chatSize * 1.65f).sp, color = colors.ink) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(Instant.ofEpochMilli(message.at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = MaterialTheme.typography.labelSmall, color = colors.muted)
                    if (message.favorite) Icon(Icons.Outlined.StarOutline, "已加星", Modifier.size(14.dp), tint = colors.accent)
                    if (message.status in listOf("cancelled", "interrupted", "error")) Text("未完成", style = MaterialTheme.typography.labelSmall, color = colors.muted)
                    IconButton(onClick = onMore, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.MoreHoriz, "消息操作", Modifier.size(16.dp), tint = colors.muted) }
                }
            }
            if (mine) {
                Spacer(Modifier.width(8.dp))
                AssetImage(prefs.avatarMine, "我的头像", Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).testTag("avatar-${message.id}"))
            }
        }
    }
}

@Composable
fun ChatComposer(graph: AppGraph, source: GraphicsLayer?, modifier: Modifier = Modifier) {
    val vm = nativeViewModel(graph, "chat") { ChatViewModel(graph) }
    val active by vm.active.collectAsStateWithLifecycle()
    val drafts by vm.drafts.collectAsStateWithLifecycle()
    val pending by vm.replying.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val text = drafts[active]?.text.orEmpty()
    val colors = LocalSeason.current
    GlassChrome(source, modifier.clip(RoundedCornerShape(32.dp))) {
        Surface(color = colors.paper.copy(alpha = .8f), shape = RoundedCornerShape(32.dp), border = BorderStroke(.7.dp, colors.border)) {
            Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
                TextField(text, vm::editDraft, modifier = Modifier.weight(1f).heightIn(min = 48.dp, max = 160.dp).testTag("chat-input"),
                    enabled = active in drafts, placeholder = { Text("想和你说……") }, maxLines = 5,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = LocalAppPreferences.current.chatSize.sp),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent))
                IconButton(onClick = { if (pending?.sessionId == active) vm.stop() else vm.send() },
                    enabled = !busy && (pending?.sessionId == active || text.isNotBlank()),
                    modifier = Modifier.size(48.dp).testTag("chat-send")) {
                    Icon(if (pending?.sessionId == active) Icons.Outlined.StopCircle else Icons.AutoMirrored.Outlined.Send,
                        if (pending?.sessionId == active) "停止回复" else "发送", tint = colors.accent)
                }
            }
        }
    }
}

@Composable
private fun ConversationTools(vm: ChatViewModel, close: () -> Unit, onSettings: () -> Unit, onSkills: () -> Unit) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val hits by vm.matches.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var rename by remember { mutableStateOf<SessionEntity?>(null) }
    NativeDialog("对话与工具", close) {
        ErrorNotice(error, vm::clearError)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.create() }, enabled = !busy, modifier = Modifier.testTag("new-conversation")) { Icon(Icons.Outlined.Add, null); Text("新对话") }
            TextButton(onClick = { mode = (mode + 1) % 3 }) { Text(listOf("已归档", "已加星", "全部对话")[mode]) }
        }
        OutlinedTextField(query, { vm.query.value = it.take(200) }, label = { Text("搜索对话或消息") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 360.dp)) {
            if (mode == 2) {
                items(favorites.filter { query.isBlank() || it.text.contains(query, true) }, key = { it.id }) { message ->
                    ListItem(headlineContent = { Text(message.text, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(sessions.firstOrNull { it.id == message.sessionId }?.title.orEmpty()) },
                        trailingContent = { IconButton(onClick = { vm.star(message.id) }) { Icon(Icons.Outlined.Star, "取消加星") } })
                }
            } else {
                val foundIds = hits.map { it.sessionId }.toSet()
                items(sessions.filter { it.archived == (mode == 1) && (query.isBlank() || it.title.contains(query, true) || it.id in foundIds) }, key = { it.id }) { session ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { if (session.archived) vm.archive(session.id, false) else { vm.switch(session.id); close() } },
                            enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(session.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (active == session.id) Text("当前对话", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(onClick = { rename = session }, enabled = !busy) { Icon(Icons.Outlined.Edit, "重命名${session.title}") }
                        IconButton(onClick = { vm.archive(session.id, !session.archived) }, enabled = !busy) {
                            Icon(if (session.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, if (session.archived) "恢复对话" else "归档对话")
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { close(); onSkills() }) { Text("Skills") }
            TextButton(onClick = { close(); onSettings() }) { Text("聊天外观与设置") }
        }
    }
    rename?.let { session ->
        var title by rememberSaveable(session.id) { mutableStateOf(session.title) }
        NativeDialog("对话名称", { rename = null }) {
            ErrorNotice(error, vm::clearError)
            OutlinedTextField(title, { if (it.length <= 100) title = it }, singleLine = true)
            Button(onClick = { vm.rename(session.id, title) { rename = null } }, enabled = !busy && title.isNotBlank()) { Text("保存") }
        }
    }
}
