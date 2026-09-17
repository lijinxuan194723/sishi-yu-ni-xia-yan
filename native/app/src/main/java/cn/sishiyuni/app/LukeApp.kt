package cn.sishiyuni.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.sishiyuni.core.AppGraph
import cn.sishiyuni.core.data.AppPreferences
import cn.sishiyuni.designsystem.*
import cn.sishiyuni.feature.chat.ChatComposer
import cn.sishiyuni.feature.chat.ChatScreen
import cn.sishiyuni.feature.home.HomeScreen
import cn.sishiyuni.feature.settings.SettingsScreen
import cn.sishiyuni.feature.settings.SkillsScreen
import cn.sishiyuni.feature.timer.TimerScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val pageTitles = listOf("四时与你", "夏彦", "他的此刻", "一起计划", "时光手记", "计时")

@Composable
fun LukeApp(graph: AppGraph, activity: MainActivity) {
    val preferences by graph.prefs.state.collectAsStateWithLifecycle()
    val ready by graph.ready.collectAsStateWithLifecycle()
    val error by graph.errors.collectAsStateWithLifecycle()
    val preferenceError by graph.prefs.error.collectAsStateWithLifecycle()
    LukeTheme(preferences) {
        RefreshRateEffect(activity, preferences.preferHighRefresh)
        if (ready) {
            NativeWorkspace(graph)
            if (error != null) NativeDialog("操作未完成", { graph.errors.value = null }, scrollBody = true) {
                Text(error.orEmpty(), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            NativeScreenFrame("四时与你") { padding ->
                Column(Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState())
                    .testTag("startup-state"), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AssetImage("images/companions/cat.webp", null, Modifier.size(64.dp))
                    if (error == null && preferenceError == null) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                        Text("正在读取本机记录", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("本机记录尚未读取完成", style = MaterialTheme.typography.titleMedium)
                        Text(error ?: preferenceError.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        Text("原文件没有被清除或替换。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun StartupFailure(message: String) {
    LukeTheme(AppPreferences()) {
        NativeScreenFrame("四时与你") { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("无法打开本机数据", style = MaterialTheme.typography.titleMedium)
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Text("没有执行清除数据或重建数据库。", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun NativeWorkspace(graph: AppGraph) {
    // Only small navigation identifiers enter saved state; messages and drafts stay in their repositories.
    val pager = rememberPagerState { pageTitles.size }
    var route by rememberSaveable { mutableStateOf("main") }
    var skillsParent by rememberSaveable { mutableStateOf("main") }
    var toolsOpen by rememberSaveable { mutableStateOf(false) }
    val retained = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    var navigation by remember { mutableStateOf<Job?>(null) }
    val reduced = LocalLukeMotion.current.reduced
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    fun navigate(page: Int) {
        if (page !in pageTitles.indices) return
        navigation?.cancel()
        if (page != 1) { focus.clearFocus(); keyboard?.hide() }
        navigation = scope.launch {
            if (reduced) pager.scrollToPage(page)
            else pager.animateScrollToPage(page, animationSpec = spring(dampingRatio = 1f, stiffness = 420f))
        }
    }
    fun settings() { toolsOpen = false; focus.clearFocus(); keyboard?.hide(); route = "settings" }
    fun skills() { toolsOpen = false; focus.clearFocus(); keyboard?.hide(); skillsParent = route; route = "skills" }
    fun back() { route = if (route == "skills") skillsParent else "main" }

    BackHandler(route != "main") { back() }
    BackHandler(route == "main" && pager.currentPage != 0 && !toolsOpen && !imeVisible) { navigate(0) }
    LaunchedEffect(pager.targetPage) {
        if (pager.targetPage != 1) { toolsOpen = false; focus.clearFocus(); keyboard?.hide() }
    }

    AnimatedContent(route, modifier = Modifier.fillMaxSize().testTag("app-root"), label = "app-route",
        transitionSpec = {
            if (reduced) EnterTransition.None togetherWith ExitTransition.None
            else (fadeIn(tween(150)) + slideInHorizontally(spring(dampingRatio = 1f, stiffness = 550f)) { it / 16 }) togetherWith fadeOut(tween(100))
        }) { destination ->
        retained.SaveableStateProvider(destination) {
            when (destination) {
                "settings" -> SettingsScreen(graph, ::back, ::skills)
                "skills" -> {
                    SeasonSystemBars()
                    Box(Modifier.fillMaxSize().background(LocalSeason.current.ground).imePadding().testTag("skills-route")) {
                        SkillsScreen(graph, padding = WindowInsets.safeDrawing.asPaddingValues(), onBack = ::back)
                    }
                }
                else -> NativeScreenFrame(pageTitles[pager.currentPage], modifier = Modifier.testTag("app-workspace"), actions = {
                    if (pager.currentPage == 1) IconButton(onClick = { toolsOpen = true }, modifier = Modifier.testTag("open-chat-tools")) {
                        Icon(Icons.Outlined.MoreHoriz, "对话与工具")
                    } else IconButton(onClick = ::settings, modifier = Modifier.testTag("open-settings")) {
                        Icon(Icons.Outlined.Settings, "设置")
                    }
                }, footer = { layer ->
                    Column(Modifier.fillMaxWidth()) {
                        if (pager.currentPage == 1) ChatComposer(graph, layer, Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                        if (!imeVisible) GlassChrome(layer, Modifier.fillMaxWidth(), fadeAtBottom = false) {
                            NativeBottomBar(pager)
                        }
                    }
                }) { padding ->
                    HorizontalPager(pager, Modifier.fillMaxSize().testTag("main-pager"), key = { pageTitles[it] }) { page ->
                        val active = route == "main" && pager.currentPage == page
                        when (page) {
                            0 -> HomeScreen(graph, padding, active, ::navigate)
                            1 -> ChatScreen(graph, padding, toolsOpen && active, { toolsOpen = false }, ::settings, ::skills)
                            5 -> TimerScreen(graph, padding, active)
                            else -> PendingMigrationPage(page, padding)
                        }
                    }
                }
            }
        }
    }
}

/** Explicit feature gate. It must never masquerade as an empty user collection or a finished page. */
@Composable
private fun PendingMigrationPage(page: Int, padding: PaddingValues) {
    val detail = when (page) {
        2 -> "音乐、书籍推荐与收藏界面还在迁移，当前安装包暂未开放。"
        3 -> "月历组件与计划数据层已经建立，完整计划操作页面尚未接入。"
        else -> "手记编辑器已经建立，列表、笔记本与完整入口仍在迁移。"
    }
    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState())
        .testTag("migration-pending-$page"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LukeCard(Modifier.fillMaxWidth()) {
            Text("此页尚未完成原生迁移", style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            Text("这不表示原来的记录为空。请继续保留旧应用及完整备份。", style = MaterialTheme.typography.bodySmall)
        }
    }
}
