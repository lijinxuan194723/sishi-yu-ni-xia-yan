package cn.sishiyuni.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * The scrolling viewport stays full-size. Only its first/last items receive padding.
 * The measured controls float above the actual recorded content, never inside the
 * recording; this avoids both recursive blur and the old rectangular scroll cutoff.
 */
@Composable
fun LayeredContent(
    outerPadding: PaddingValues,
    modifier: Modifier = Modifier,
    header: @Composable ColumnScope.() -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val source = rememberGraphicsLayer()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    var headerPixels by remember { mutableIntStateOf(0) }
    var sourceOrigin by remember { mutableStateOf(Offset.Zero) }
    val top = outerPadding.calculateTopPadding()
    val horizontalStart = outerPadding.calculateStartPadding(direction)
    val horizontalEnd = outerPadding.calculateEndPadding(direction)
    val inset = PaddingValues(
        start = horizontalStart + 16.dp,
        end = horizontalEnd + 16.dp,
        top = top + with(density) { headerPixels.toDp() } + 6.dp,
        bottom = outerPadding.calculateBottomPadding() + 20.dp,
    )
    Box(modifier.fillMaxSize().consumeWindowInsets(outerPadding).testTag("layered-content")) {
        Box(Modifier.fillMaxSize().onGloballyPositioned { sourceOrigin = it.positionInRoot() }.recordPage(source)) {
            content(inset)
        }
        GlassChrome(source, Modifier.align(Alignment.TopCenter).fillMaxWidth()
            .padding(top = top, start = horizontalStart, end = horizontalEnd)
            .onSizeChanged { headerPixels = it.height }.testTag("section-overlay"), sourceOrigin = sourceOrigin) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp), content = header)
        }
    }
}
