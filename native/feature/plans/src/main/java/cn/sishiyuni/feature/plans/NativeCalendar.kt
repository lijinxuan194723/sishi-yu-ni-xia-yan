package cn.sishiyuni.feature.plans

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.sishiyuni.core.network.Holiday
import cn.sishiyuni.core.plans.CalendarModel
import cn.sishiyuni.designsystem.*
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun NativeCalendar(month: YearMonth, selected: LocalDate, today: LocalDate, holidays: Map<String, Holiday>, counts: Map<String, Int>,
                   onMonth: (YearMonth) -> Unit, onDate: (LocalDate) -> Unit, modifier: Modifier = Modifier,
                   labels: Map<String, String> = emptyMap()) {
    val colors = LocalSeason.current
    val reduced = LocalLukeMotion.current.reduced
    val days = remember(month) { CalendarModel.cells(month) }
    val textMeasurer = rememberTextMeasurer()
    LukeCard(modifier.fillMaxWidth().testTag("native-calendar"), padding = 10.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonth(CalendarModel.shift(month, -1)) }, enabled = month > YearMonth.from(CalendarModel.first)) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "上个月")
            }
            Text("${month.year} 年 ${month.monthValue} 月", Modifier.weight(1f).testTag("calendar-month"), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { onMonth(CalendarModel.shift(month, 1)) }, enabled = month < YearMonth.from(CalendarModel.last)) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "下个月")
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val width = (maxWidth - 18.dp) / 7
            val nominal = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 18.sp)
            val measured = textMeasurer.measure("88", style = nominal).size.width.coerceAtLeast(1)
            val ratio = (with(density) { (width - 6.dp).toPx() } / measured).coerceIn(.1f, 1f)
            val numberStyle = nominal.copy(fontSize = 14.sp * ratio, lineHeight = 18.sp * ratio)
            val markStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp)
            val markHeight = maxOf(13.dp, with(density) { markStyle.lineHeight.toDp() } + 3.dp)
            val mainHeight = maxOf(26.dp, with(density) { numberStyle.lineHeight.toDp() } + 3.dp)
            val height = maxOf(width, markHeight + mainHeight + 8.dp)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth()) { listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                    Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = numberStyle, color = colors.muted)
                } }
                days.chunked(7).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    row.forEach { date ->
                        val inMonth = YearMonth.from(date) == month
                        val holiday = holidays[date.toString()]
                        val count = counts[date.toString()] ?: 0
                        val annotation = labels[date.toString()]
                        val target = if (date == selected) colors.soft else Color.Transparent
                        val background by animateColorAsState(target, if (reduced) snap() else tween(130), label = "calendar-selection")
                        Column(Modifier.weight(1f).height(height).clip(RoundedCornerShape(12.dp)).background(background)
                            .clickable(enabled = date in CalendarModel.first..CalendarModel.last, role = Role.Button) { onDate(date) }
                            .semantics(mergeDescendants = true) {
                                this.selected = date == selected
                                contentDescription = buildString {
                                    append(date); if (date == today) append("，今天")
                                    holiday?.let { append("，${it.name}，${if (it.off) "放假" else "调休上班"}") }
                                    annotation?.let { append("，$it") }
                                    if (count > 0) append("，$count 项约定")
                                }
                            }.testTag("day-$date"), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.fillMaxWidth().height(markHeight).padding(end = 4.dp), contentAlignment = Alignment.CenterEnd) {
                                if (holiday != null) Text(if (holiday.off) "休" else "班", style = markStyle, maxLines = 1,
                                    color = if (holiday.off) colors.accent else colors.muted, modifier = Modifier.testTag("holiday-$date"))
                            }
                            Box(Modifier.height(mainHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                if (date == today) AssetImage("images/companions/cat.webp", null, Modifier.size(24.dp).testTag("today-avatar"))
                                else Text(date.dayOfMonth.toString(), style = numberStyle, maxLines = 1,
                                    color = if (inMonth) colors.ink else colors.muted.copy(alpha = .5f), modifier = Modifier.testTag("day-number-$date"))
                            }
                            Box(Modifier.height(8.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                if (count > 0 || annotation != null) Box(Modifier.size(3.dp).clip(RoundedCornerShape(50)).background(colors.accent))
                            }
                        }
                    }
                } }
            }
        }
    }
}
