package cn.sishiyuni.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cn.sishiyuni.core.network.WeatherParser
import kotlin.math.sin
import kotlin.random.Random

/** Original vector weather scene: bounded particles, no bitmaps/allocating objects per animation tick. */
@Composable
fun WeatherScene(code: Int, day: Boolean, active: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalSeason.current
    val kind = WeatherParser.scene(code)
    val time = rememberSceneTime(active && kind != "none")
    val particles = remember { val random = Random(210); FloatArray(72) { random.nextFloat() } }
    val backdrop = remember(colors) { Brush.verticalGradient(listOf(colors.soft, colors.paper.copy(alpha = .2f))) }
    Canvas(modifier.fillMaxSize().testTag("weather-scene")) {
        val t = time.value
        drawRect(backdrop)
        val sky = Offset(size.width * .78f, size.height * .30f)
        val r = size.minDimension * .135f
        if (kind in setOf("sun", "cloud", "none")) {
            val glow = if (day) colors.accent.copy(alpha = .12f) else colors.ink.copy(alpha = .09f)
            drawCircle(glow, r * (1.62f + sin(t * .6f) * .05f), sky)
            drawCircle(if (day) colors.accent.copy(alpha = .36f) else colors.muted.copy(alpha = .4f), r, sky)
            if (!day) drawCircle(colors.soft, r * .86f, sky + Offset(r * .44f, -r * .25f))
        }
        fun cloud(x: Float, y: Float, width: Float, alpha: Float) {
            val c = colors.paper.copy(alpha = alpha)
            drawCircle(c, width * .20f, Offset(x + width * .26f, y))
            drawCircle(c, width * .27f, Offset(x + width * .52f, y - width * .1f))
            drawCircle(c, width * .18f, Offset(x + width * .77f, y + width * .02f))
            drawRoundRect(c, Offset(x + width * .1f, y), Size(width * .82f, width * .22f), CornerRadius(width * .12f))
        }
        if (kind in setOf("cloud", "rain", "snow", "thunder", "fog")) {
            cloud(size.width * .55f + sin(t * .15f) * 9.dp.toPx(), size.height * .23f, size.width * .4f, .65f)
            cloud(size.width * .75f - sin(t * .19f) * 6.dp.toPx(), size.height * .42f, size.width * .25f, .4f)
        }
        when (kind) {
            "rain", "thunder" -> repeat(20) { i ->
                val x = particles[i * 3] * size.width
                val fraction = (particles[i * 3 + 1] + t * (.48f + particles[i * 3 + 2] * .3f)) % 1f
                val y = fraction * size.height
                drawLine(colors.accent.copy(alpha = .13f + particles[i * 3 + 2] * .12f),
                    Offset(x, y), Offset(x - 3.dp.toPx(), y + 13.dp.toPx()), 1.dp.toPx(), cap = StrokeCap.Round)
            }
            "snow" -> repeat(24) { i ->
                val x = particles[i * 3] * size.width + sin(t * .4f + i) * 9.dp.toPx()
                val y = ((particles[i * 3 + 1] + t * (.06f + particles[i * 3 + 2] * .045f)) % 1f) * size.height
                drawCircle(colors.muted.copy(alpha = .24f), (1.3f + particles[i * 3 + 2] * 1.4f).dp.toPx(), Offset(x, y))
            }
            "fog" -> repeat(4) { i ->
                val y = size.height * (.26f + i * .13f)
                val x = size.width * .5f + sin(t * .22f + i) * 12.dp.toPx()
                drawLine(colors.muted.copy(alpha = .13f), Offset(x, y), Offset(x + size.width * .45f, y), 6.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}
