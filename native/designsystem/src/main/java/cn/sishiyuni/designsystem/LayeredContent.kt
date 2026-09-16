package cn.sishiyuni.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

private enum class LayerSlot { Header, Content }

/**
 * Measure the floating controls before composing the full-size scrolling viewport.
 * A zero-height first frame used to clamp a restored list offset on photo return.
 * Both layers now receive their final geometry in the same measure pass. Only list
 * content padding makes room for chrome; the viewport itself is never cropped.
 */
@Composable
fun LayeredContent(
    outerPadding: PaddingValues,
    modifier: Modifier = Modifier,
    header: @Composable ColumnScope.() -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val source = rememberGraphicsLayer()
    val direction = LocalLayoutDirection.current
    var sourceOrigin by remember { mutableStateOf(Offset.Zero) }
    val top = outerPadding.calculateTopPadding()
    val horizontalStart = outerPadding.calculateStartPadding(direction)
    val horizontalEnd = outerPadding.calculateEndPadding(direction)
    SubcomposeLayout(modifier.fillMaxSize().consumeWindowInsets(outerPadding).testTag("layered-content")) { constraints ->
        require(constraints.hasBoundedWidth && constraints.hasBoundedHeight) { "LayeredContent requires a bounded page viewport" }
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val overlay = subcompose(LayerSlot.Header) {
            GlassChrome(source, Modifier.fillMaxWidth()
                .padding(top = top, start = horizontalStart, end = horizontalEnd)
                .testTag("section-overlay"), sourceOrigin = sourceOrigin) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp), content = header)
            }
        }.single().measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
        val inset = PaddingValues(
            start = horizontalStart + 16.dp,
            end = horizontalEnd + 16.dp,
            top = overlay.height.toDp() + 6.dp,
            bottom = outerPadding.calculateBottomPadding() + 20.dp,
        )
        val page = subcompose(LayerSlot.Content) {
            Box(Modifier.fillMaxSize().onGloballyPositioned { sourceOrigin = it.positionInRoot() }.recordPage(source)) {
                content(inset)
            }
        }.single().measure(Constraints.fixed(width, height))
        layout(width, height) {
            page.placeRelative(0, 0)
            overlay.placeRelative(0, 0, zIndex = 1f)
        }
    }
}
