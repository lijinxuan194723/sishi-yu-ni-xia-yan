package cn.sishiyuni.designsystem

import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Tab labels do not change size or vertical position when selected. */
@Composable
fun NativeBottomBar(pager: PagerState, modifier: Modifier = Modifier) {
    val labels = remember { listOf("回到身边", "悄悄话", "他的此刻", "一起计划", "时光手记", "计时") }
    val icons = remember { listOf(Icons.Outlined.Home, Icons.Outlined.ChatBubbleOutline, Icons.Outlined.FavoriteBorder,
        Icons.Outlined.CalendarMonth, Icons.Outlined.MenuBook, Icons.Outlined.Timer) }
    require(pager.pageCount == labels.size)
    val colors = LocalSeason.current
    val reduced = LocalLukeMotion.current.reduced
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    var click by remember { mutableStateOf<Job?>(null) }
    Row(modifier.fillMaxWidth().heightIn(min = 72.dp).selectableGroup().testTag("bottom-navigation")
        .drawBehind {
            val logical = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, 5f)
            val position = if (rtl) 5f - logical else logical
            val slot = size.width / 6
            val width = minOf(52.dp.toPx(), slot - 8.dp.toPx()).coerceAtLeast(0f)
            drawRoundRect(colors.soft.copy(alpha = .83f), Offset(position * slot + (slot - width) / 2, 7.dp.toPx()),
                Size(width, 32.dp.toPx()), CornerRadius(16.dp.toPx()))
        }, verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { index, label ->
            Column(Modifier.weight(1f).heightIn(min = 72.dp).selectable(pager.currentPage == index, role = Role.Tab, onClick = {
                click?.cancel()
                click = scope.launch {
                    if (reduced) pager.scrollToPage(index)
                    else pager.animateScrollToPage(index, animationSpec = spring(dampingRatio = 1f, stiffness = 420f))
                }
            }).padding(horizontal = 2.dp, vertical = 10.dp).testTag("main-tab-$index"),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(icons[index], null, Modifier.size(25.dp), tint = if (pager.currentPage == index) colors.accent else colors.muted)
                Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                    color = if (pager.currentPage == index) colors.ink else colors.muted)
            }
        }
    }
}
