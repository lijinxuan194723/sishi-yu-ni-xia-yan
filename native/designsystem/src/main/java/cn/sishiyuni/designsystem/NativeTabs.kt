package cn.sishiyuni.designsystem

import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Pointer movement is the indicator's source of truth, not a second competing animation. */
@Composable
fun NativeTabs(labels: List<String>, pager: PagerState, modifier: Modifier = Modifier, tag: String = "section-tabs") {
    require(labels.isNotEmpty() && labels.size == pager.pageCount)
    val colors = LocalSeason.current
    val reduced = LocalLukeMotion.current.reduced
    val scope = rememberCoroutineScope()
    var clickJob by remember { mutableStateOf<Job?>(null) }
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
        .background(colors.paper.copy(alpha = .84f))
        .drawBehind {
            val position = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, labels.lastIndex.toFloat())
            val inset = 4.dp.toPx()
            val slot = size.width / labels.size
            drawRoundRect(colors.soft, topLeft = Offset(position * slot + inset, inset),
                size = Size((slot - inset * 2).coerceAtLeast(0f), (size.height - inset * 2).coerceAtLeast(0f)),
                cornerRadius = CornerRadius(18.dp.toPx()))
        }.selectableGroup().testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { index, title ->
            Box(Modifier.weight(1f).heightIn(min = 48.dp)
                .selectable(selected = pager.currentPage == index, role = Role.Tab, onClick = {
                    clickJob?.cancel()
                    clickJob = scope.launch {
                        if (reduced) pager.scrollToPage(index)
                        else pager.animateScrollToPage(index, animationSpec = spring(dampingRatio = 1f, stiffness = 420f))
                    }
                }).padding(horizontal = 4.dp, vertical = 12.dp).testTag("$tag-$index"), contentAlignment = Alignment.Center) {
                Text(title, style = MaterialTheme.typography.labelLarge,
                    color = if (pager.currentPage == index) colors.accent else colors.muted,
                    textAlign = TextAlign.Center)
            }
        }
    }
}
