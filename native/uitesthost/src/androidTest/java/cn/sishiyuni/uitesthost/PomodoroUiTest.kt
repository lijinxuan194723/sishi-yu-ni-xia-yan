package cn.sishiyuni.uitesthost

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import cn.sishiyuni.core.AppGraph
import cn.sishiyuni.core.data.AppPreferences
import cn.sishiyuni.core.data.RecordEntity
import cn.sishiyuni.core.timer.*
import cn.sishiyuni.designsystem.*
import cn.sishiyuni.feature.timer.TimerScreen
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class PomodoroUiTest {
    @get:Rule val rule = createAndroidComposeRule<TestActivity>()
    private var app: AppGraph? = null
    private val title = "阅读-${UUID.randomUUID().toString().take(6)}"
    private fun screen(large: Boolean = false, rounds: Int = 2): AppGraph {
        val graph = (rule.activity.application as TestApplication).graph
        app = graph
        runBlocking {
            withTimeout(15000) { graph.ready.first { it } }
            graph.timer.cancelPomodoro(true)
            graph.timer.savePomodoroPreset(PomodoroPreset(PomodoroConfig(1,1,2,rounds), title, "阅读"))
        }
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 1.3f else density.fontScale)) {
                LukeTheme(AppPreferences(effects=false, reduceMotion=true, scale=if (large) 1.6f else .95f, period="night")) {
                    val pager = rememberPagerState(initialPage=5) { 6 }
                    NativeScreenFrame("计时", footer={ NativeBottomBar(pager) }) { padding ->
                        TimerScreen(graph, padding, false)
                    }
                }
            }
        }
        rule.onNodeWithTag("timer-tabs-1").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("pomodoro-primary").fetchSemanticsNodes().any {
            !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
        } }
        return graph
    }
    @After fun stopFixtureAlarm() { app?.let { runBlocking { it.timer.cancelPomodoro(true) } } }
    private fun waitTimer(predicate: (cn.sishiyuni.core.data.TimerEntity?) -> Boolean) {
        rule.waitUntil(10000) { predicate(runBlocking { app!!.dao.timer(Pomodoro.ID) }) }
    }
    private fun expirePhase() = runBlocking {
        val graph=app!!
        val timer=requireNotNull(graph.dao.timer(Pomodoro.ID))
        graph.dao.putTimer(timer.copy(elapsedDeadline=graph.timer.time.elapsed()-1, wallDeadline=graph.timer.time.wall()-1))
        graph.timer.fire(Pomodoro.ID,timer.generation)
    }
    @Test fun pageStartsPausesContinuesAndRequiresConfirmationToEnd() {
        val graph=screen()
        rule.onNodeWithTag("pomodoro-primary").assertIsDisplayed().performClick()
        waitTimer { it?.running==true }
        val first=runBlocking { graph.dao.timer(Pomodoro.ID)!! }
        rule.onNodeWithTag("pomodoro-configure").assertIsNotEnabled()
        rule.onNodeWithTag("pomodoro-primary").performClick()
        waitTimer { it?.running==false }
        val paused=runBlocking { graph.dao.timer(Pomodoro.ID)!! }
        rule.onNodeWithTag("pomodoro-primary").performClick()
        waitTimer { it?.running==true }
        assertEquals(first.generation,runBlocking { graph.dao.timer(Pomodoro.ID)!!.generation })
        assertTrue(paused.remainingMs in 1..first.durationMs)
        rule.onNodeWithTag("pomodoro-end").performClick()
        rule.onNodeWithText("返回计时").performClick()
        assertTrue(runBlocking { graph.dao.timer(Pomodoro.ID)!!.running })
        rule.onNodeWithTag("pomodoro-end").performClick()
        rule.onNodeWithTag("pomodoro-confirm-end").performClick()
        waitTimer { !Pomodoro.isActive(it) }
        assertEquals(title,PomodoroPreset.decode(runBlocking { graph.dao.record("timer-config",Pomodoro.ID)!!.payload }).title)
    }
    @Test fun shortBreakWaitsForExplicitStartAndIsNeverCountedAsFocus() {
        val graph=screen()
        rule.onNodeWithTag("pomodoro-primary").performClick(); waitTimer { it?.running==true }
        expirePhase()
        waitTimer { it!=null && !it.running && Pomodoro.state(it).phase=="short" }
        rule.onNodeWithTag("pomodoro-primary").assertTextContains("开始短休息")
        val logs=runBlocking { graph.dao.allFocusLogs().filter { it.title==title } }
        assertEquals(1,logs.size); assertEquals(1.0,logs.single().minutes,.0001)
        rule.onNodeWithTag("pomodoro-primary").performClick(); waitTimer { it?.running==true }
        expirePhase()
        waitTimer { it!=null && !it.running && Pomodoro.state(it).phase=="work" }
        assertEquals(1,runBlocking { graph.dao.allFocusLogs().count { it.title==title } })
        rule.onNodeWithTag("pomodoro-primary").assertTextContains("开始专注")
    }
    @Test fun finalRoundPreparesALongBreakWithoutAutomaticallyRunningIt() {
        screen(rounds=1)
        rule.onNodeWithTag("pomodoro-primary").performClick(); waitTimer { it?.running==true }
        expirePhase()
        waitTimer { it!=null && !it.running && Pomodoro.state(it).phase=="long" }
        rule.onNodeWithTag("pomodoro-primary").assertTextContains("开始长休息")
        assertEquals(120000L,runBlocking { app!!.dao.timer(Pomodoro.ID)!!.remainingMs })
    }
    @Test fun invalidSettingsStayOpenAndDoNotOverwriteTheSavedPreset() {
        val graph=screen()
        val before=runBlocking { graph.dao.record("timer-config",Pomodoro.ID) }
        rule.onNodeWithTag("pomodoro-configure").performClick()
        rule.onNodeWithTag("pomodoro-work").performScrollTo().performTextReplacement("0")
        rule.onNodeWithTag("pomodoro-save").performClick()
        rule.onNodeWithContentDescription("关闭番茄钟设置").assertExists()
        assertEquals(before,runBlocking { graph.dao.record("timer-config",Pomodoro.ID) })
        rule.onNodeWithTag("pomodoro-work").assertTextContains("0")
        rule.onNodeWithTag("pomodoro-work").performTextReplacement("37")
        rule.onNodeWithTag("pomodoro-save").performClick()
        rule.waitUntil(10000) { runBlocking { PomodoroPreset.decode(graph.dao.record("timer-config",Pomodoro.ID)!!.payload).config.work==37 } }
        assertFalse(Pomodoro.isActive(runBlocking { graph.dao.timer(Pomodoro.ID) }))
    }
    @Test fun damagedPresetDisablesStartWithoutReplacingTheOriginal() {
        val graph=screen()
        val damaged=RecordEntity("timer-config",Pomodoro.ID,"{invalid preset}")
        runBlocking { graph.dao.putRecord(damaged) }
        rule.waitUntil(10000) { rule.onAllNodesWithTag("pomodoro-primary").fetchSemanticsNodes().any {
            it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
        } }
        rule.onNodeWithTag("pomodoro-primary").assertIsNotEnabled()
        assertEquals(damaged,runBlocking { graph.dao.record("timer-config",Pomodoro.ID) })
        runBlocking { graph.dao.putRecord(RecordEntity("timer-config",Pomodoro.ID,PomodoroPreset().encode())) }
    }
    @Test fun largeTypeKeepsThePrimaryActionVisibleAndSettingsScrollable() {
        screen(large=true)
        rule.onNodeWithTag("pomodoro-primary").assertIsDisplayed().assertIsEnabled()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val dir=File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: rule.activity.getExternalFilesDir(null)!!.path,"screenshots").apply { mkdirs() }
        val image=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { File(dir,"pomodoro-night-large.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) } }
        finally { image.recycle() }
        rule.onNodeWithTag("pomodoro-configure").performClick()
        rule.onNodeWithTag("pomodoro-rounds").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("pomodoro-save").assertIsDisplayed()
    }
}
