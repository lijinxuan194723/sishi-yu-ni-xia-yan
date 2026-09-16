package cn.sishiyuni.designsystem

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import cn.sishiyuni.core.timer.TimerMath
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Drag selects; only a genuine single-pointer release starts. Cancellation restores the previous value. */
@Composable
fun TimerRuler(minutes: Int, enabled: Boolean, onSelect: (Int) -> Unit, onRelease: (Int) -> Unit, modifier: Modifier = Modifier) {
    var position by remember { mutableFloatStateOf(minutes.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val selected by remember { derivedStateOf { position.roundToInt().coerceIn(1, 180) } }
    val colors = LocalSeason.current
    val reduced = LocalLukeMotion.current.reduced
    val select by rememberUpdatedState(onSelect)
    val release by rememberUpdatedState(onRelease)
    val scope = rememberCoroutineScope()
    var settle by remember { mutableStateOf<Job?>(null) }
    val pxPerMinute = with(LocalDensity.current) { 12.dp.toPx() }
    LaunchedEffect(minutes) { if (!dragging) position = minutes.toFloat() }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$selected 分钟", style = MaterialTheme.typography.titleMedium)
        Canvas(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(18.dp))
            .background(colors.soft.copy(alpha = .52f))
            .testTag("timer-ruler")
            .semantics {
                role = Role.Button
                contentDescription = "倒计时刻度尺，左右拖动后松手开始"
                progressBarRangeInfo = ProgressBarRangeInfo(selected.toFloat(), 1f..180f, 178)
                setProgress { value ->
                    if (!enabled || !value.isFinite()) false
                    else { select(value.roundToInt().coerceIn(1, 180)); true }
                }
                if (!enabled) disabled()
            }
            .onKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) false else when (event.key) {
                    Key.DirectionLeft -> { select((selected - 1).coerceAtLeast(1)); true }
                    Key.DirectionRight -> { select((selected + 1).coerceAtMost(180)); true }
                    Key.Enter, Key.NumPadEnter -> { release(selected); true }
                    else -> false
                }
            }.focusable(enabled)
            .pointerInput(enabled, pxPerMinute, reduced) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    settle?.cancel()
                    val before = position
                    val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                    var started = false
                    var released = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } > 1) break
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                            velocity.addPosition(change.uptimeMillis, change.position)
                            val distance = change.position - down.position
                            if (!started) {
                                if (abs(distance.y) > viewConfiguration.touchSlop && abs(distance.y) >= abs(distance.x)) break
                                if (abs(distance.x) > viewConfiguration.touchSlop) { started = true; dragging = true }
                            }
                            if (started) {
                                position = TimerMath.rulerResistance(before - distance.x / pxPerMinute, 1f, 180f)
                                change.consume()
                            }
                            if (!change.pressed) {
                                if (started) {
                                    val target = TimerMath.releaseMinutes(position, -velocity.calculateVelocity().x / pxPerMinute)
                                    val initial = position
                                    released = true
                                    dragging = false
                                    select(target)
                                    release(target) // Real timer begins now, never after an animation callback.
                                    settle = scope.launch {
                                        if (reduced) position = target.toFloat()
                                        else animate(initial, target.toFloat(), animationSpec = spring(dampingRatio = .86f, stiffness = 480f)) { value, _ -> position = value }
                                    }
                                }
                                break
                            }
                        }
                    } finally {
                        dragging = false
                        if (!released) position = before
                    }
                }
            }) {
            val centerX = size.width / 2
            val current = position
            val first = (current - size.width / pxPerMinute / 2 - 1).toInt().coerceAtLeast(1)
            val last = (current + size.width / pxPerMinute / 2 + 1).toInt().coerceAtMost(180)
            for (i in first..last) {
                val x = centerX + (i - current) * pxPerMinute
                val major = i % 5 == 0
                drawLine(if (enabled) colors.accent.copy(alpha = if (major) .65f else .3f) else colors.muted.copy(alpha = .25f),
                    Offset(x, if (major) 12.dp.toPx() else 23.dp.toPx()), Offset(x, size.height - 12.dp.toPx()),
                    if (major) 1.5.dp.toPx() else 1.dp.toPx(), cap = StrokeCap.Round)
            }
            drawLine(colors.accent, Offset(centerX, 4.dp.toPx()), Offset(centerX, size.height - 4.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}
