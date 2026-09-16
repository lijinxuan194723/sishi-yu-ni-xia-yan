package cn.sishiyuni.designsystem

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp

/** Record only page content into this layer; chrome must never record itself. */
fun Modifier.recordPage(layer: GraphicsLayer): Modifier = drawWithContent {
    layer.record { this@drawWithContent.drawContent() }
    drawLayer(layer)
}

/** Backdrop and scrim live behind the controls; text is never blurred or made translucent. */
@Composable
fun GlassChrome(source: GraphicsLayer?, modifier: Modifier = Modifier, fadeAtBottom: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    val paper = LocalSeason.current.paper
    val glass = LocalAppPreferences.current.glass
    var origin by remember { mutableStateOf(Offset.Zero) }
    val mask = if (fadeAtBottom) listOf(Color.Black, Color.Black, Color.Transparent)
        else listOf(Color.Transparent, Color.Black, Color.Black)
    val tint = if (!glass) listOf(paper, paper, paper)
        else if (fadeAtBottom) listOf(paper.copy(alpha = .96f), paper.copy(alpha = .77f), Color.Transparent)
        else listOf(Color.Transparent, paper.copy(alpha = .86f), paper.copy(alpha = .97f))
    Box(modifier.onGloballyPositioned { origin = it.positionInRoot() }) {
        if (glass && source != null && Build.VERSION.SDK_INT >= 31) {
            Box(Modifier.matchParentSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.verticalGradient(mask), blendMode = BlendMode.DstIn)
                }
                .graphicsLayer { renderEffect = BlurEffect(14.dp.toPx(), 14.dp.toPx(), TileMode.Clamp) }
                .drawWithContent { translate(-origin.x, -origin.y) { drawLayer(source) } })
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(tint)))
        content()
    }
}
