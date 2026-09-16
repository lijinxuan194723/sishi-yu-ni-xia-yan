package cn.sishiyuni.uitesthost

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import cn.sishiyuni.core.data.AppPreferences
import cn.sishiyuni.designsystem.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

class ThemeStabilityTest {
    @get:Rule val rule = createAndroidComposeRule<TestActivity>()
    private val seasons = listOf("spring", "summer", "autumn", "winter")
    private fun mix(a: SeasonColors, b: SeasonColors, t: Float) = SeasonColors(
        lerp(a.ground,b.ground,t),lerp(a.paper,b.paper,t),lerp(a.soft,b.soft,t),
        lerp(a.accent,b.accent,t),lerp(a.ink,b.ink,t),lerp(a.muted,b.muted,t),lerp(a.border,b.border,t))

    @Test fun allEightPalettesUseTheSeasonForEveryContainerFamily() {
        for (season in seasons) for (night in listOf(false,true)) {
            val raw = seasonColors(season, night)
            val c = accessibleSeasonColors(raw)
            val scheme = seasonColorScheme(c, night)
            assertEquals(raw.paper, scheme.surfaceContainer)
            assertEquals(raw.ground, scheme.surfaceContainerLowest)
            assertEquals(c.accent, scheme.tertiary)
            assertEquals(c.soft, scheme.tertiaryContainer)
            assertEquals(c.accent, scheme.surfaceTint)
            assertTrue(contrastRatio(scheme.onPrimary, scheme.primary) >= 4.49f)
            assertTrue(contrastRatio(scheme.onSurfaceVariant, scheme.surfaceVariant) >= 4.49f)
        }
    }
    @Test fun everyThemePairKeepsInkReadableThroughoutTheTransition() {
        val palettes = seasons.flatMap { listOf(seasonColors(it,false),seasonColors(it,true)) }
        for ((from,a) in palettes.withIndex()) for ((to,b) in palettes.withIndex()) for (step in 0..30) {
            val c = accessibleSeasonColors(mix(a,b,step/30f))
            for (text in listOf(c.ink,c.muted,c.accent)) for (paper in listOf(c.ground,c.paper,c.soft)) {
                assertTrue("Low contrast from=$from to=$to step=$step ratio=${contrastRatio(text,paper)}",contrastRatio(text,paper)>=4.49f)
            }
        }
    }
    @Test fun everyTypographyRoleFollowsTheSameScaleWithoutChangingLineHeightRatio() {
        fun sizes(scale: Float) = seasonTypography(scale).let { listOf(it.displayLarge,it.displayMedium,it.displaySmall,
            it.headlineLarge,it.headlineMedium,it.headlineSmall,it.titleLarge,it.titleMedium,it.titleSmall,
            it.bodyLarge,it.bodyMedium,it.bodySmall,it.labelLarge,it.labelMedium,it.labelSmall) }
        val baseline = sizes(1f)
        for (scale in listOf(.75f,.95f,1.6f)) sizes(scale).forEachIndexed { index,value ->
            assertEquals(baseline[index].fontSize.value*scale,value.fontSize.value,.001f)
            assertEquals(baseline[index].lineHeight.value*scale,value.lineHeight.value,.001f)
        }
        assertEquals(18f*.95f,seasonTypography(Float.NaN).titleLarge.fontSize.value,.001f)
    }
    @Test fun automaticSeasonAndDayPeriodUpdateWithoutRecreatingContentState() {
        val clock = mutableStateOf(LocalDateTime.of(2026,2,28,18,59))
        var actual: SeasonColors? = null
        var time: LocalDateTime? = null
        var retained: Any? = null
        rule.setContent {
            LukeTheme(AppPreferences(effects=false,reduceMotion=true),now=clock.value) {
                val marker = remember { Any() }
                actual = LocalSeason.current; time = LocalAppTime.current; retained = marker
                Text("时光继续",style=MaterialTheme.typography.bodyMedium)
            }
        }
        val before = rule.runOnIdle { retained }
        rule.runOnIdle { clock.value = LocalDateTime.of(2026,3,1,19,0) }
        rule.runOnIdle {
            assertEquals(seasonColors("spring",true).paper,actual!!.paper)
            assertEquals(clock.value,time); assertSame(before,retained)
        }
    }
    @Test fun foregroundClockStopsCollectionAndResamplesOnResume() {
        val source = MutableStateFlow(LocalDateTime.of(2026,9,30,18,59))
        val collectors = AtomicInteger()
        val flow = flow {
            collectors.incrementAndGet()
            try { emitAll(source) } finally { collectors.decrementAndGet() }
        }
        lateinit var time: State<LocalDateTime>
        rule.setContent { time = rememberForegroundTime(flow); Text("前台时钟") }
        rule.waitUntil(10000) { collectors.get()==1 && time.value==source.value }
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.waitUntil(10000) { collectors.get()==0 }
        val stopped = time.value
        source.value = LocalDateTime.of(2026,10,1,19,1)
        assertEquals(stopped,time.value)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitUntil(10000) { collectors.get()==1 && time.value==source.value }
    }
    @Test fun repeatedThemeTargetsSettleAtLastChoiceAndKeepAnOpaqueBackground() {
        val prefs = mutableStateOf(AppPreferences(effects=false,season="spring",period="day"))
        var actual: SeasonColors? = null
        rule.setContent { LukeTheme(prefs.value,now=LocalDateTime.of(2026,9,1,12,0)) { actual=LocalSeason.current; Text("配色检查") } }
        rule.mainClock.autoAdvance=false
        for ((index,season) in seasons.withIndex()) {
            rule.runOnUiThread { prefs.value=prefs.value.copy(season=season,period=if(index%2==0)"night" else "day") }
            rule.mainClock.advanceTimeBy(48)
            rule.runOnUiThread { assertEquals(1f,actual!!.ground.alpha,.0001f) }
        }
        rule.mainClock.autoAdvance=true; rule.waitForIdle()
        rule.runOnIdle { assertEquals(seasonColors("winter",false).paper,actual!!.paper) }
    }
}
