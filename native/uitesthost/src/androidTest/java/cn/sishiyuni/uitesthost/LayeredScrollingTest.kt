package cn.sishiyuni.uitesthost

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import cn.sishiyuni.core.data.AppPreferences
import cn.sishiyuni.designsystem.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LayeredScrollingTest {
    @get:Rule val rule = createAndroidComposeRule<TestActivity>()
    private lateinit var list: LazyListState
    private lateinit var scope: CoroutineScope
    private var presses = 0
    private fun content(glass: State<Boolean> = mutableStateOf(true)) {
        rule.setContent {
            LukeTheme(AppPreferences(season = "spring", period = "day", glass = glass.value, effects = false, reduceMotion = true)) {
                list = rememberLazyListState(); scope = rememberCoroutineScope()
                LayeredContent(PaddingValues(top = 24.dp, bottom = 30.dp), header = {
                    TextButton(onClick = { presses++ }, modifier = Modifier.height(48.dp).testTag("floating-action")) { Text("分区操作") }
                }) { insets ->
                    LazyColumn(Modifier.fillMaxSize().testTag("scroll-body"), state = list, contentPadding = insets) {
                        item { Box(Modifier.fillMaxWidth().height(960.dp).background(Color.Magenta).testTag("long-content")) }
                        item { Text("最后一项", Modifier.height(48.dp).testTag("last-content")) }
                    }
                }
            }
        }
    }
    private fun scroll() {
        rule.runOnIdle { scope.launch { list.scrollToItem(0, 180) } }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(64); rule.waitForIdle()
    }
    private fun tailPixel(): Color {
        val image = rule.onNodeWithTag("section-overlay").captureToImage().toPixelMap()
        return image[image.width / 2, image.height - 3]
    }
    @Test fun firstItemStartsBelowHeaderButCanScrollBehindIt() {
        content()
        val header = rule.onNodeWithTag("section-overlay").fetchSemanticsNode().boundsInRoot
        val first = rule.onNodeWithTag("long-content").fetchSemanticsNode().boundsInRoot
        assertTrue("Initial content obstructs header", first.top >= header.bottom)
        scroll()
        val shifted = rule.onNodeWithTag("long-content").fetchSemanticsNode().boundsInRoot
        assertTrue("Scroll viewport still clips at header", shifted.top < header.bottom - 20f)
        val after = rule.onNodeWithTag("section-overlay").fetchSemanticsNode().boundsInRoot
        assertEquals(header.top, after.top, 1f); assertEquals(header.bottom, after.bottom, 1f)
    }
    @Test fun transparentTailShowsActualScrollingContentNotAStationaryBackground() {
        content(); val initial = tailPixel()
        scroll(); val changed = tailPixel()
        assertTrue("Scrolling content was not visible through fading chrome", initial.green - changed.green > .15f)
        assertTrue("Wrong recorded content under header", changed.red > .75f && changed.blue > .7f)
    }
    @Test fun overlayActionRemainsClickableWithoutJumpingList() {
        content(); scroll()
        var offset = 0
        rule.runOnIdle { offset = list.firstVisibleItemScrollOffset }
        rule.onNodeWithTag("floating-action").performClick()
        rule.runOnIdle { assertEquals(1, presses); assertEquals(offset, list.firstVisibleItemScrollOffset) }
    }
    @Test fun solidFallbackChangesOnlyTheBackgroundNotHeaderBounds() {
        val glass = mutableStateOf(true); content(glass); scroll()
        val before = rule.onNodeWithTag("section-overlay").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle { glass.value = false }; rule.waitForIdle()
        val solid = tailPixel()
        val after = rule.onNodeWithTag("section-overlay").fetchSemanticsNode().boundsInRoot
        assertTrue("Opaque fallback is still revealing magenta", solid.green > .8f)
        assertEquals(before, after)
    }
    @Test fun lastItemCanBeScrolledCompletelyAboveBottomInset() {
        content()
        rule.runOnIdle { scope.launch { list.scrollToItem(1) } }
        rule.waitForIdle()
        rule.onNodeWithTag("last-content").assertIsDisplayed()
        val last = rule.onNodeWithTag("last-content").fetchSemanticsNode().boundsInRoot
        val viewport = rule.onNodeWithTag("scroll-body").fetchSemanticsNode().boundsInRoot
        assertTrue("Last line remains underneath bottom controls", last.bottom < viewport.bottom - 20f)
    }
}
