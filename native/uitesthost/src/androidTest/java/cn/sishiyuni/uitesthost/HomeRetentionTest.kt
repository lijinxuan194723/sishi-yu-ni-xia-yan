package cn.sishiyuni.uitesthost

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import cn.sishiyuni.core.data.AppPreferences
import cn.sishiyuni.designsystem.LukeTheme
import cn.sishiyuni.feature.home.HomeScreen
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

class HomeRetentionTest {
    @get:Rule val rule = createAndroidComposeRule<TestActivity>()
    private fun content(clock: State<LocalDateTime> = mutableStateOf(LocalDateTime.of(2026,9,16,12,0))) {
        val graph = (rule.activity.application as TestApplication).graph
        runBlocking { withTimeout(15000) { graph.ready.first { it } } }
        rule.setContent {
            LukeTheme(AppPreferences(effects=false,reduceMotion=true,season="spring",period="day",since="2026-09-16"),now=clock.value) {
                HomeScreen(graph,PaddingValues(top=28.dp,bottom=80.dp),true,{})
            }
        }
    }
    private fun position() = rule.onNodeWithTag("home-section-0").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
    private fun scroll() {
        rule.onNodeWithTag("home-section-0").performTouchInput { swipeUp(startY=height*.82f,endY=height*.55f,durationMillis=300) }
        rule.waitForIdle(); assertTrue("Fixture must actually scroll",position()>1f)
    }
    @Test fun photoRoundTripRetainsTheExactVerticalPosition() {
        content(); scroll(); val before=position()
        rule.onNodeWithTag("open-photo").performClick()
        rule.onNodeWithTag("photo-detail").assertExists()
        rule.onNodeWithTag("close-photo").performClick()
        assertEquals(before,position(),1f)
    }
    @Test fun leavingAPagerSectionDoesNotResetItsList() {
        content(); scroll(); val before=position()
        rule.onNodeWithTag("home-tabs-3").performClick()
        rule.onNodeWithTag("home-tabs-0").performClick()
        assertEquals(before,position(),1f)
    }
    @Test fun companionDateAdvancesEvenWithAManuallyFixedTheme() {
        val clock=mutableStateOf(LocalDateTime.of(2026,9,16,23,59))
        content(clock)
        rule.onNodeWithTag("home-day-count").assertTextEquals("1 天的陪伴")
        rule.runOnIdle { clock.value=LocalDateTime.of(2026,9,17,0,0) }
        rule.onNodeWithTag("home-day-count").assertTextEquals("2 天的陪伴")
    }
}
