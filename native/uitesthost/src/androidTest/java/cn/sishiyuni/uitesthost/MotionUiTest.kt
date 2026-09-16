package cn.sishiyuni.uitesthost

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cn.sishiyuni.core.data.AppPreferences
import cn.sishiyuni.designsystem.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class MotionUiTest {
    @get:Rule val rule = createAndroidComposeRule<TestActivity>()
    private lateinit var pager: PagerState
    private var rightToLeft = false
    private fun tabs(reduced: Boolean = false, rtl: Boolean = false) {
        rightToLeft = rtl
        rule.setContent {
            LukeTheme(AppPreferences(effects = false, reduceMotion = reduced, season = "spring", period = "day")) {
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    pager = rememberPagerState { 3 }
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        NativeTabs(listOf("相伴", "今日", "回顾"), pager)
                        HorizontalPager(pager, Modifier.fillMaxSize().testTag("pager")) { index ->
                            Box(Modifier.fillMaxSize().background(LocalSeason.current.paper), contentAlignment = Alignment.Center) { Text("页面 $index") }
                        }
                    }
                }
            }
        }
    }
    /** Compose idleness alone does not wait for native ripple/PixelCopy on every Android version.
     * Wait for the actual painted frame, keeping the exact color, visible-area and position assertions.
     * The finger remains held for drag samples; this does not turn the gesture test into a settled-tab test.
     */
    private fun highlightCenter(): Float {
        var result: Float? = null
        var diagnostic = "No frame captured"
        try {
            rule.waitUntil(5000) {
                val image = rule.onNodeWithTag("section-tabs").captureToImage().toPixelMap()
                val expected = seasonColors("spring", false).soft
                val y = (image.height * .8f).toInt().coerceAtMost(image.height - 1)
                val xs = (0 until image.width).filter { x ->
                    val c = image[x, y]
                    abs(c.red - expected.red) < .018f && abs(c.green - expected.green) < .018f && abs(c.blue - expected.blue) < .018f
                }
                val logical = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, 2f)
                val position = if (rightToLeft) 2f - logical else logical
                val expectedCenter = image.width / 3f * (position + .5f)
                val centroid = if (xs.isEmpty()) Float.NaN else xs.average().toFloat()
                diagnostic = "API=${android.os.Build.VERSION.SDK_INT}, painted=${xs.size}/${image.width}, centroid=$centroid, expected=$expectedCenter, pager=$logical"
                if (xs.size > image.width / 12 && abs(centroid - expectedCenter) <= 5f) result = centroid
                result != null
            }
        } finally {
            println("Indicator pixel sample: $diagnostic")
        }
        return requireNotNull(result) { diagnostic }
    }
    @Test fun indicatorMovesBeforeFingerReleaseAndReversesWithFinger() {
        tabs(); val before = highlightCenter()
        rule.onNodeWithTag("pager").performTouchInput { down(center); moveBy(Offset(-width * .30f, 0f), 160) }
        rule.runOnIdle { assertTrue(pager.currentPage + pager.currentPageOffsetFraction > .05f) }
        val forward = highlightCenter(); assertTrue(forward > before + 4f)
        rule.onNodeWithTag("pager").performTouchInput { moveBy(Offset(width * .18f, 0f), 120) }
        assertTrue(highlightCenter() < forward)
        rule.onNodeWithTag("pager").performTouchInput { up() }
    }
    @Test fun rapidTabChangesSettleAtLastChoice() {
        tabs(); rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("section-tabs-2").performClick(); rule.mainClock.advanceTimeBy(32)
        rule.onNodeWithTag("section-tabs-0").performClick()
        rule.mainClock.autoAdvance = true; rule.waitForIdle()
        rule.runOnIdle { assertEquals(0, pager.currentPage); assertFalse(pager.isScrollInProgress) }
    }
    @Test fun reducedMotionTabSelectionHasNoPendingSpring() {
        tabs(reduced = true); rule.onNodeWithTag("section-tabs-2").performClick()
        rule.runOnIdle { assertEquals(2, pager.currentPage); assertEquals(0f, pager.currentPageOffsetFraction, .001f); assertFalse(pager.isScrollInProgress) }
    }
    @Test fun rightToLeftIndicatorMatchesRightmostFirstTab() {
        tabs(reduced = true, rtl = true); val first = highlightCenter()
        rule.onNodeWithTag("section-tabs-2").performClick()
        assertTrue(first > highlightCenter())
    }
    private fun ruler(enabled: Boolean = true, onRelease: (Int) -> Unit): MutableState<Int> {
        val minutes = mutableStateOf(25)
        rule.setContent {
            LukeTheme(AppPreferences(effects = false, reduceMotion = true)) {
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    TimerRuler(minutes.value, enabled, { minutes.value = it }, onRelease)
                }
            }
        }
        return minutes
    }
    @Test fun rulerTapDoesNotStartCountdown() {
        var releases = 0; ruler { releases++ }
        rule.onNodeWithTag("timer-ruler").performTouchInput { click(center) }
        rule.runOnIdle { assertEquals(0, releases) }
    }
    @Test fun rulerVerticalScrollDoesNotStartCountdown() {
        var releases = 0; val selected = ruler { releases++ }
        rule.onNodeWithTag("timer-ruler").performTouchInput { down(center); moveBy(Offset(2f, 90f), 200); up() }
        rule.runOnIdle { assertEquals(0, releases); assertEquals(25, selected.value) }
    }
    @Test fun rulerHorizontalReleaseStartsExactlyOnce() {
        val releases = mutableListOf<Int>(); val selected = ruler { releases += it }
        rule.onNodeWithTag("timer-ruler").performTouchInput { down(center); moveBy(Offset(-160f, 0f), 300); up() }
        rule.runOnIdle { assertEquals(1, releases.size); assertTrue(releases.single() in 26..180); assertEquals(releases.single(), selected.value) }
    }
    @Test fun secondPointerCancelsRulerWithoutCommitting() {
        var releases = 0; val selected = ruler { releases++ }
        rule.onNodeWithTag("timer-ruler").performTouchInput {
            down(0, center); moveBy(Offset(-90f, 0f), 120)
            down(1, center + Offset(10f, 10f)); moveTo(0, center + Offset(-130f, 0f), 32)
            up(1); up(0)
        }
        rule.runOnIdle { assertEquals(0, releases); assertEquals(25, selected.value) }
    }
    @Test fun cancelledPointerSequenceDoesNotStartCountdown() {
        var releases = 0; val selected = ruler { releases++ }
        rule.onNodeWithTag("timer-ruler").performTouchInput { down(center); moveBy(Offset(-110f, 0f), 120); cancel() }
        rule.runOnIdle { assertEquals(0, releases); assertEquals(25, selected.value) }
    }
    @Test fun accessibilitySelectionChangesValueWithoutUnexpectedStart() {
        var releases = 0; val selected = ruler { releases++ }
        rule.onNodeWithTag("timer-ruler").performSemanticsAction(SemanticsActions.SetProgress) { set -> assertTrue(set(75f)) }
        rule.runOnIdle { assertEquals(75, selected.value); assertEquals(0, releases) }
    }
    @Test fun disabledRulerDoesNotRespondToDrag() {
        var releases = 0; val selected = ruler(enabled = false) { releases++ }
        rule.onNodeWithTag("timer-ruler").performTouchInput { down(center); moveBy(Offset(-200f, 0f), 180); up() }
        rule.runOnIdle { assertEquals(0, releases); assertEquals(25, selected.value) }
    }
    @Test fun dialDigitsFitInsideItsInnerSafeAreaAtSeveralSizesAndFontScales() {
        val diameter = mutableStateOf(180); val systemFont = mutableStateOf(1f)
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, systemFont.value)) {
                LukeTheme(AppPreferences(effects = false, reduceMotion = true)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        MechanicalDial(3_661_000, false, false, Modifier.size(diameter.value.dp))
                    }
                }
            }
        }
        for (size in listOf(130, 160, 190, 250, 307)) for (font in listOf(1f, 1.5f, 2f)) {
            rule.runOnIdle { diameter.value = size; systemFont.value = font }; rule.waitForIdle()
            val circle = rule.onNodeWithTag("mechanical-dial").fetchSemanticsNode().boundsInRoot
            val text = rule.onNodeWithTag("dial-time").fetchSemanticsNode().boundsInRoot
            val density = circle.width / size
            val radius = circle.width / 2 - (if (size >= 150) 35 else 25) * density
            val distance = listOf(text.topLeft, text.topRight, text.bottomLeft, text.bottomRight).maxOf { p -> hypot(p.x - circle.center.x, p.y - circle.center.y) }
            assertTrue("Digits intersect inner perimeter: diameter=$size fontScale=$font distance=$distance radius=$radius", distance <= radius + density)
        }
    }
    @Test fun bottomNavigationKeepsItsBoundsDuringRepeatedChanges() {
        rule.setContent {
            LukeTheme(AppPreferences(effects = false)) {
                pager = rememberPagerState { 6 }
                Column(Modifier.fillMaxSize()) {
                    HorizontalPager(pager, Modifier.weight(1f)) { Text("页面 $it") }
                    NativeBottomBar(pager)
                }
            }
        }
        val initial = rule.onNodeWithTag("bottom-navigation").fetchSemanticsNode().boundsInRoot
        for (index in listOf(4, 2, 5, 0, 1, 4)) {
            rule.onNodeWithTag("main-tab-$index").performClick()
            val current = rule.onNodeWithTag("bottom-navigation").fetchSemanticsNode().boundsInRoot
            assertEquals(initial.top, current.top, 1f); assertEquals(initial.bottom, current.bottom, 1f)
        }
    }
    @Test fun offscreenParticleSceneStopsAdvancing() {
        var active by mutableStateOf(true)
        lateinit var time: State<Float>
        rule.mainClock.autoAdvance = false
        rule.setContent {
            LukeTheme(AppPreferences(effects = true, reduceMotion = false)) {
                time = rememberSceneTime(active); Text("帧时钟检查")
            }
        }
        rule.mainClock.advanceTimeBy(320)
        rule.runOnIdle { assertTrue("Frame clock must advance before suspension", time.value > .01f); active = false }
        rule.mainClock.advanceTimeBy(32)
        val stopped = time.value
        rule.mainClock.advanceTimeBy(500)
        assertEquals(stopped, time.value, .0001f)
        rule.mainClock.autoAdvance = true
    }
}
