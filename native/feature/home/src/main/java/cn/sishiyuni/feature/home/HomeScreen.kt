package cn.sishiyuni.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.sishiyuni.core.AppGraph
import cn.sishiyuni.core.CoreViewModel
import cn.sishiyuni.core.data.*
import cn.sishiyuni.core.model.*
import cn.sishiyuni.designsystem.*
import kotlinx.coroutines.flow.*
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.time.ZoneId

data class HomeUi(val checks: List<RecordEntity> = emptyList(), val anniversaries: List<RecordEntity> = emptyList(),
    val plans: List<PlanEntity> = emptyList(), val memos: List<MemoEntity> = emptyList(), val logs: List<FocusLogEntity> = emptyList())
class HomeViewModel(private val app: AppGraph) : CoreViewModel(app) {
    val ui = combine(app.dao.records("check"), app.dao.records("anniversary"), app.dao.plans(), app.dao.memos(), app.dao.focusLogs()) { checks, dates, plans, notes, logs ->
        HomeUi(checks, dates, plans, notes, logs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUi())
    fun checkIn(date: LocalDate) = task { app.dao.putRecord(RecordEntity("check", date.toString(), "\"$date\"")) }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(graph: AppGraph, padding: PaddingValues, active: Boolean, navigate: (Int) -> Unit) {
    val vm = nativeViewModel(graph, "home") { HomeViewModel(graph) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val p = LocalAppPreferences.current
    val colors = LocalSeason.current
    val reduced = LocalLukeMotion.current.reduced
    val pager = rememberPagerState { 4 }
    val paths = remember { listOf("images/luke-blossom.webp", "images/luke-camera.webp", "images/luke-sunset.webp", "images/luke-nap.webp") }
    val seasonIndex = listOf("spring", "summer", "autumn", "winter").indexOf(p.resolvedSeason()).coerceAtLeast(0)
    val image = paths[(seasonIndex + p.photoIndex).mod(paths.size)]
    var expandedPath by rememberSaveable { mutableStateOf<String?>(null) }
    val today = LocalDate.now()
    val days = runCatching { togetherDays(LocalDate.parse(p.since), today) }.getOrDefault(1)
    val zone = ZoneId.systemDefault()
    val week = remember(ui, today, zone) { companionWeek(today, zone, ui.checks, ui.plans, ui.memos, ui.logs) }
    BackHandler(expandedPath != null) { expandedPath = null }
    SharedTransitionLayout(Modifier.fillMaxSize().testTag("home-shared-root")) {
        AnimatedContent(targetState = expandedPath, label = "home-photo-detail",
            transitionSpec = { (fadeIn(tween(if (reduced) 0 else 180)) togetherWith fadeOut(tween(if (reduced) 0 else 120))).using(SizeTransform(clip = false)) }) { photo ->
            val visibility = this
            if (photo != null) {
                Box(Modifier.fillMaxSize().background(colors.ground).padding(padding).testTag("photo-detail")) {
                    AssetImage(photo, "夏彦照片", Modifier.fillMaxSize().padding(12.dp)
                        .sharedElement(rememberSharedContentState("home-photo:$photo"), visibility,
                            boundsTransform = { _, _ -> if (reduced) snap() else spring(dampingRatio = .9f, stiffness = 300f) })
                        .clip(RoundedCornerShape(24.dp)), ContentScale.Fit)
                    IconButton(onClick = { expandedPath = null }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).testTag("close-photo")) {
                        Icon(Icons.Outlined.Close, "返回首页")
                    }
                }
            } else {
                LayeredContent(padding, header = {
                    NativeTabs(listOf("相伴", "今日", "约定", "回顾"), pager, tag = "home-tabs")
                    ErrorNotice(error, vm::clearError)
                }) { listPadding ->
                    HorizontalPager(pager, Modifier.fillMaxSize(), key = { it }) { section ->
                        LazyColumn(Modifier.fillMaxSize().testTag("home-section-$section"), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = listPadding) {
                            when (section) {
                                0 -> {
                                    item {
                                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).testTag("home-hero")) {
                                            AssetImage(image, "查看夏彦照片", Modifier.matchParentSize()
                                                .sharedElement(rememberSharedContentState("home-photo:$image"), visibility,
                                                    boundsTransform = { _, _ -> if (reduced) snap() else spring(dampingRatio = .9f, stiffness = 300f) })
                                                .clickable { expandedPath = image }.testTag("open-photo"), ContentScale.Crop)
                                            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .12f), Color.Transparent, Color.Black.copy(alpha = .6f)))))
                                            // Intrinsic content determines height. Enlarged text never collides with the top label.
                                            Column(Modifier.fillMaxWidth().heightIn(min = 520.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                    Surface(color = colors.paper.copy(alpha = .88f), shape = RoundedCornerShape(24.dp)) {
                                                        Text(listOf("春日", "盛夏", "秋日", "冬日")[seasonIndex], Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
                                                    }
                                                    Text("Luke Pearce", Modifier.weight(1f), fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, color = Color.White,
                                                        style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                                Spacer(Modifier.height(152.dp))
                                                Text("夏彦 & ${p.name}", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                                                Text(if (p.isNight()) "今天辛苦了，剩下的话可以慢慢说。" else "醒来见到你，今天就有了一个好开头。", color = Color.White.copy(alpha = .94f), style = MaterialTheme.typography.bodyMedium)
                                                Text("${days} 天的陪伴", color = Color.White, style = MaterialTheme.typography.titleMedium)
                                                Text("Since ${p.since}", color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.bodySmall)
                                                Button(onClick = { navigate(1) }) { Text("和夏彦说说话") }
                                            }
                                        }
                                    }
                                    item { LukeCard(Modifier.fillMaxWidth()) { Text("你慢慢说，我没有在赶时间。", style = MaterialTheme.typography.titleMedium) } }
                                }
                                1 -> {
                                    item { WeatherCard(graph, active && pager.currentPage == 1) }
                                    item { LukeCard(Modifier.fillMaxWidth()) {
                                        Text("今天也在身边", style = MaterialTheme.typography.titleMedium)
                                        val checked = ui.checks.any { it.id == today.toString() }
                                        Button(onClick = { vm.checkIn(today) }, enabled = !checked) { Text(if (checked) "今天已经见过面了" else "留下今天的足迹") }
                                    } }
                                    items(ui.plans.filter { it.date == today.toString() }, key = { it.id }) { plan -> LukeCard(Modifier.fillMaxWidth()) {
                                        Text(plan.text, style = MaterialTheme.typography.bodyMedium)
                                        Text(if (plan.done) "已完成" else "今天的计划", style = MaterialTheme.typography.bodySmall)
                                    } }
                                    item { OutlinedButton(onClick = { navigate(3) }, modifier = Modifier.fillMaxWidth()) { Text("一起计划") } }
                                }
                                2 -> {
                                    if (ui.anniversaries.isEmpty()) item { LukeCard(Modifier.fillMaxWidth()) {
                                        Text("把重要的日子留在这里", style = MaterialTheme.typography.titleMedium)
                                        Text("相伴开始于 ${p.since}", style = MaterialTheme.typography.bodyMedium)
                                        if (p.birthday.isNotBlank()) Text("生日 ${p.birthday}", style = MaterialTheme.typography.bodyMedium)
                                    } }
                                    items(ui.anniversaries, key = { it.id }) { row ->
                                        val record = remember(row.payload) { runCatching { obj(row.payload) }.getOrNull() }
                                        val title = record?.str("title").orEmpty().ifBlank { record?.str("name", "我们的约定") ?: "我们的约定" }
                                        LukeCard(Modifier.fillMaxWidth()) {
                                            Text(title, style = MaterialTheme.typography.titleMedium)
                                            Text(record?.str("date").orEmpty(), style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    item { OutlinedButton(onClick = { navigate(3) }, modifier = Modifier.fillMaxWidth()) { Text("安排下一次约定") } }
                                }
                                else -> {
                                    item { LukeCard(Modifier.fillMaxWidth()) {
                                        Text("我们的最近七天", style = MaterialTheme.typography.titleMedium)
                                        Text("${week.checkDays} 天相伴", style = MaterialTheme.typography.headlineMedium)
                                        Text("${week.completedPlans} 件计划完成", style = MaterialTheme.typography.bodyLarge)
                                        Text("${week.notes} 篇珍藏手记", style = MaterialTheme.typography.bodyLarge)
                                        Text("${week.minutes.toInt()} 分钟专注时光", style = MaterialTheme.typography.bodyLarge)
                                    } }
                                    item { Button(onClick = { navigate(5) }, modifier = Modifier.fillMaxWidth()) { Text("接一份专注委托") } }
                                    item { OutlinedButton(onClick = { navigate(4) }, modifier = Modifier.fillMaxWidth()) { Text("收藏今天的线索") } }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
