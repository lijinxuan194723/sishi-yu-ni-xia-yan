package cn.sishiyuni.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Scaffold measures insets before content. Chrome is outside the captured content layer. */
@Composable
fun NativeScreenFrame(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    footer: @Composable (GraphicsLayer) -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    SeasonSystemBars()
    val page = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    Scaffold(modifier.fillMaxSize().imePadding().background(LocalSeason.current.ground).testTag("native-frame"),
        containerColor = LocalSeason.current.ground,
        topBar = {
            GlassChrome(page, Modifier.fillMaxWidth().testTag("glass-header"), sourceOrigin = origin) {
                Column(Modifier.statusBarsPadding().padding(bottom = 14.dp)) { NativeTitle(title, onBack, actions) }
            }
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth()) {
                footer(page)
                GlassChrome(page, Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars)
                    .testTag("system-navigation-protection"), fadeAtBottom = false, sourceOrigin = origin) { }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }.recordPage(page)) { content(padding) }
    }
}
