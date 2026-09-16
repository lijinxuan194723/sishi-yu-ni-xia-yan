package cn.sishiyuni.designsystem

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp

/** Record page content only. The header/composer must remain outside this recorded layer. */
fun Modifier.recordPage(layer: GraphicsLayer): Modifier = drawWithContent {
    layer.record { this@drawWithContent.drawContent() }
    drawLayer(layer)
}

/** Real backdrop on API 31+, translucent gradient on older devices; foreground stays sharp. */
@Composable
fun GlassChrome(source: GraphicsLayer?, modifier: Modifier = Modifier, fadeAtBottom: Boolean = true,
                sourceOrigin: Offset = Offset.Zero, content: @Composable BoxScope.() -> Unit) {
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
                .drawWithContent { translate(sourceOrigin.x - origin.x, sourceOrigin.y - origin.y) { drawLayer(source) } })
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(tint)))
        content()
    }
}
