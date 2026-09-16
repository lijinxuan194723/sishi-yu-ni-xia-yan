package cn.sishiyuni.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.sishiyuni.core.timer.TimerMath
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MechanicalDial(millis: Long, running: Boolean, active: Boolean, modifier: Modifier = Modifier,
                   totalMillis: Long = 0, liveMillis: () -> Long = { millis }) {
    val colors = LocalSeason.current
    val motion = LocalLukeMotion.current
    val clock = rememberSceneTime(active && running)
    val latestMillis by rememberUpdatedState(liveMillis)
    val vectors = remember { List(60) { i ->
        val angle = (i * 6 - 90) * Math.PI / 180
        Offset(cos(angle).toFloat(), sin(angle).toFloat())
    } }
    Surface(modifier.aspectRatio(1f).testTag("mechanical-dial"), shape = CircleShape, color = colors.paper) {
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            val dialWidth = maxWidth
            val dialHeight = maxHeight
            Canvas(Modifier.fillMaxSize()) {
                clock.value // Only this draw node observes frames; no database or system-provider reads.
                val radius = size.minDimension / 2
                val ring = radius - 8.dp.toPx()
                if (ring <= 0) return@Canvas
                drawCircle(colors.border.copy(alpha = .65f), ring, style = Stroke(2.dp.toPx()))
                vectors.forEachIndexed { i, v ->
                    val length = if (i % 5 == 0) 12.dp.toPx() else 5.dp.toPx()
                    drawLine(if (i % 5 == 0) colors.accent.copy(alpha = .65f) else colors.border,
                        center + v * (ring - 6.dp.toPx() - length), center + v * (ring - 6.dp.toPx()),
                        strokeWidth = if (i % 5 == 0) 1.7.dp.toPx() else 1.dp.toPx(), cap = StrokeCap.Round)
                }
                val shown = if (running && motion.effects) latestMillis().coerceAtLeast(0) else millis.coerceAtLeast(0)
                val progress = if (totalMillis > 0) (shown.toDouble() / totalMillis).coerceIn(0.0, 1.0).toFloat()
                    else (shown % 3_600_000L) / 3_600_000f
                val arcRadius = ring - 27.dp.toPx()
                if (arcRadius > 0) drawArc(colors.accent.copy(alpha = .45f), -90f, 360f * progress, false,
                    topLeft = center - Offset(arcRadius, arcRadius), size = Size(arcRadius * 2, arcRadius * 2),
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                val angle = ((shown % 60000) / 60000.0 * 360 - 90) * Math.PI / 180
                val v = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                drawLine(colors.accent, center + v * (ring - 19.dp.toPx()), center + v * (ring - 2.dp.toPx()),
                    2.6.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(colors.accent, 3.dp.toPx(), center + v * (ring - 1.dp.toPx()))
            }
            val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            val sizeSp = (dialWidth.value / 6.2f / fontScale).coerceIn(15f, 38f)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (dialHeight >= 190.dp) AssetImage("images/companions/cat.webp", null, Modifier.size((dialWidth.value * .15f).dp))
                Text(TimerMath.duration(millis), style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = sizeSp.sp, lineHeight = (sizeSp * 1.2f).sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium), color = colors.ink, maxLines = 1,
                    modifier = Modifier.testTag("dial-time"))
            }
        }
    }
}
