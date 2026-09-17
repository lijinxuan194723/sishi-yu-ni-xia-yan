package cn.sishiyuni.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.KeyEvent
import android.view.View
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import cn.sishiyuni.core.GraphOwner
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Launches the actual manifest activity and production composition; never calls setContent. */
class AppStartupSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val graph get() = (rule.activity.application as GraphOwner).graph

    @Before fun ready() {
        runBlocking {
            withTimeout(15000) { graph.ready.first { it } }
            graph.prefs.flag("reduceMotion", true)
            graph.prefs.flag("effects", false)
            graph.prefs.text("season", "spring")
            graph.prefs.text("period", "day")
        }
        waitFor("app-workspace")
    }
    private fun waitFor(tag: String) {
        rule.waitUntil(10000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag(tag).assertIsDisplayed()
    }
    private fun tab(index: Int) {
        rule.onNodeWithTag("main-tab-$index").performClick()
        rule.onNodeWithTag("main-tab-$index").assertIsSelected()
    }
    private fun hideKeyboard() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val content = rule.activity.findViewById<View>(android.R.id.content)
        var visible = false
        instrumentation.runOnMainSync { visible = ViewCompat.getRootWindowInsets(content)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
        if (visible) instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        waitFor("bottom-navigation")
    }
    private fun capture(name: String) {
        rule.waitForIdle()
        val path = requireNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir"))
        val dir = File(path,"production-app").apply { mkdirs() }
        val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try { File(dir,"$name.png").outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG,100,it)) } }
        finally { screenshot.recycle() }
    }
    @Test fun actualLauncherAndPackagedArtworkCanStart() {
        assertEquals("cn.sishiyuni.nativeapp",rule.activity.packageName)
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(rule.activity.packageName)
        @Suppress("DEPRECATION")
        val resolved = rule.activity.packageManager.resolveActivity(intent,0)
        assertEquals(MainActivity::class.java.name, resolved?.activityInfo?.name)
        waitFor("home-shared-root")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        rule.activity.assets.open("images/luke-blossom.webp").use { BitmapFactory.decodeStream(it,null,bounds) }
        assertTrue(bounds.outWidth > 100 && bounds.outHeight > 100)
        assertTrue(graph.ready.value)
        capture("launcher-home")
    }
    @Test fun mainNavigationKeepsEveryDestinationAndHonestMigrationGates() {
        for (i in 0..5) rule.onNodeWithTag("main-tab-$i").assertIsDisplayed()
        for (i in 2..4) { tab(i);waitFor("migration-pending-$i") }
        tab(5);waitFor("timer-tabs");rule.onNodeWithTag("start-study").assertIsDisplayed()
        capture("launcher-timer")
        tab(0);waitFor("home-shared-root")
    }
    @Test fun followFingerPagingWorksFromTheRealChatPage() {
        tab(1);waitFor("chat-input")
        rule.onNodeWithTag("main-pager").performTouchInput { swipeLeft() }
        rule.onNodeWithTag("main-tab-2").assertIsSelected()
        waitFor("migration-pending-2")
        rule.onNodeWithTag("main-pager").performTouchInput { swipeRight() }
        rule.onNodeWithTag("main-tab-1").assertIsSelected()
        waitFor("chat-input")
    }
    @Test fun activityRecreationKeepsCurrentConversationAndDraft() {
        tab(1);waitFor("chat-input")
        rule.waitUntil(10000) { graph.prefs.state.value.activeSession in graph.drafts.state.value }
        rule.onNodeWithTag("chat-input").performTextReplacement("旋转与返回以后仍保留的草稿")
        hideKeyboard()
        val session = graph.prefs.state.value.activeSession
        rule.activityRule.scenario.recreate()
        waitFor("chat-input")
        rule.onNodeWithTag("main-tab-1").assertIsSelected()
        rule.onNodeWithTag("chat-input").assertTextEquals("旋转与返回以后仍保留的草稿")
        assertEquals(session,graph.prefs.state.value.activeSession)
        tab(5);waitFor("timer-tabs");tab(1)
        rule.onNodeWithTag("chat-input").assertTextEquals("旋转与返回以后仍保留的草稿")
    }
    @Test fun conversationToolsOpenSkillsAndReturnWithoutChangingTheTab() {
        tab(1);waitFor("chat-input")
        rule.onNodeWithTag("open-chat-tools").performClick()
        waitFor("new-conversation")
        rule.onNodeWithText("Skills").performClick()
        waitFor("skills-route")
        rule.onNodeWithText("选择文件").assertIsDisplayed()
        rule.onNodeWithContentDescription("返回").performClick()
        waitFor("chat-input")
        rule.onNodeWithTag("main-tab-1").assertIsSelected()
        rule.onNodeWithTag("new-conversation").assertDoesNotExist()
    }
    @Test fun settingsSectionAndRootHaveDistinctBackDestinations() {
        rule.onNodeWithTag("open-settings").performClick()
        waitFor("settings-search")
        rule.onNodeWithTag("settings-group-typography").performClick()
        waitFor("size-scale")
        rule.onNodeWithContentDescription("返回").performClick()
        waitFor("settings-search")
        rule.onNodeWithContentDescription("返回").performClick()
        waitFor("app-workspace")
        rule.onNodeWithTag("main-tab-0").assertIsSelected()
    }
    @Test fun keyboardKeepsComposerVisibleInsteadOfStackingTwoBottomBars() {
        tab(1);waitFor("chat-input")
        rule.onNodeWithTag("chat-input").performClick()
        val content = rule.activity.findViewById<View>(android.R.id.content)
        rule.waitUntil(10000) { ViewCompat.getRootWindowInsets(content)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
        rule.onNodeWithTag("chat-input").assertIsDisplayed()
        rule.onNodeWithTag("chat-send").assertIsDisplayed()
        rule.onNodeWithTag("bottom-navigation").assertDoesNotExist()
        capture("launcher-keyboard")
        hideKeyboard()
        rule.onNodeWithTag("main-tab-1").assertIsSelected()
    }
}
