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

/** Tests the real activity, never replaces its content. */
class AppStartupSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val graph get() = (rule.activity.application as GraphOwner).graph
    @Before fun ready() {
        runBlocking {
            withTimeout(15000) { graph.ready.first { it } }
            graph.prefs.flag("reduceMotion", true); graph.prefs.flag("effects", false)
            graph.prefs.text("season", "spring"); graph.prefs.text("period", "day")
        }
        waitFor("app-workspace")
    }
    private fun waitFor(tag: String) {
        rule.waitUntil(10000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag(tag).assertIsDisplayed()
    }
    private fun awaitActivityInput() {
        // Compose can finish a route's layout before the dismissed Dialog returns Android focus.
        // Wait for that actual condition, not a fixed delay or a bypassed semantics click.
        rule.waitUntil(10000) { rule.activity.hasWindowFocus() }
        rule.waitForIdle()
    }
    private fun assertTabSettled(index: Int) {
        rule.waitUntil(10000) {
            rule.onAllNodes(hasTestTag("main-tab-$index") and isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("main-tab-$index").assertIsSelected()
    }
    private fun tab(index: Int) {
        awaitActivityInput()
        rule.onNodeWithTag("main-tab-$index").performClick()
        assertTabSettled(index)
    }
    private fun hideKeyboard() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val content = rule.activity.findViewById<View>(android.R.id.content)
        var visible = false
        instrumentation.runOnMainSync { visible = ViewCompat.getRootWindowInsets(content)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
        if (visible) instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitUntil(10000) {
            ViewCompat.getRootWindowInsets(content)?.isVisible(WindowInsetsCompat.Type.ime()) == false
        }
        waitFor("bottom-navigation"); awaitActivityInput()
    }
    private fun capture(name: String) {
        rule.waitForIdle()
        val requested = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val root = requested?.let(::File) ?: File(requireNotNull(rule.activity.externalCacheDir), "app-smoke")
        val dir = File(root,"production-app")
        check(dir.isDirectory || dir.mkdirs()) { "Cannot create app screenshot directory: $dir" }
        val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try { File(dir,"$name.png").outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG,100,it)) } }
        finally { screenshot.recycle() }
    }
    @Test fun actualLauncherAndPackagedArtworkCanStart() {
        assertEquals("cn.sishiyuni.nativeapp",rule.activity.packageName)
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(rule.activity.packageName)
        @Suppress("DEPRECATION") val resolved = rule.activity.packageManager.resolveActivity(intent,0)
        assertEquals(MainActivity::class.java.name, resolved?.activityInfo?.name)
        waitFor("home-shared-root")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        rule.activity.assets.open("images/luke-blossom.webp").use { BitmapFactory.decodeStream(it,null,bounds) }
        assertTrue(bounds.outWidth > 100 && bounds.outHeight > 100)
        assertTrue(graph.ready.value); capture("launcher-home")
    }
    @Test fun mainNavigationKeepsEveryDestinationAndHonestMigrationGates() {
        for (i in 0..5) rule.onNodeWithTag("main-tab-$i").assertIsDisplayed()
        for (i in 2..4) { tab(i);waitFor("migration-pending-$i") }
        tab(5);waitFor("timer-tabs");rule.onNodeWithTag("start-study").assertIsDisplayed()
        capture("launcher-timer");tab(0);waitFor("home-shared-root")
    }
    @Test fun followFingerPagingWorksFromTheRealChatPage() {
        tab(1);waitFor("chat-input")
        rule.onNodeWithTag("main-pager").performTouchInput { swipeLeft() }
        assertTabSettled(2);waitFor("migration-pending-2")
        rule.onNodeWithTag("main-pager").performTouchInput { swipeRight() }
        assertTabSettled(1);waitFor("chat-input")
    }
    @Test fun activityRecreationKeepsCurrentConversationAndDraft() {
        tab(1);waitFor("chat-input")
        rule.waitUntil(10000) { graph.prefs.state.value.activeSession in graph.drafts.state.value }
        val text = "旋转与返回以后仍保留的草稿"
        val session = graph.prefs.state.value.activeSession
        rule.onNodeWithTag("chat-input").performTextReplacement(text)
        assertEquals("Repository must accept the typed input immediately",text,graph.drafts.state.value[session]?.text)
        rule.onNodeWithTag("chat-input").assertTextEquals(text)
        hideKeyboard()
        assertEquals("IME closure must not erase the draft",text,graph.drafts.state.value[session]?.text)
        rule.activityRule.scenario.recreate();waitFor("chat-input")
        assertEquals("Application draft must survive activity recreation",text,graph.drafts.state.value[session]?.text)
        rule.onNodeWithTag("main-tab-1").assertIsSelected()
        rule.onNodeWithTag("chat-input").assertTextEquals(text)
        assertEquals(session,graph.prefs.state.value.activeSession)
        tab(5);waitFor("timer-tabs");tab(1)
        rule.onNodeWithTag("chat-input").assertTextEquals(text)
    }
    @Test fun conversationToolsOpenSkillsAndReturnWithoutChangingTheTab() {
        tab(1);waitFor("chat-input");rule.onNodeWithTag("open-chat-tools").performClick()
        waitFor("new-conversation");rule.onNodeWithText("Skills").performClick();waitFor("skills-route");awaitActivityInput()
        rule.onNodeWithText("选择文件").assertIsDisplayed();rule.onNodeWithContentDescription("返回").performClick()
        waitFor("chat-input");rule.onNodeWithTag("main-tab-1").assertIsSelected();rule.onNodeWithTag("new-conversation").assertDoesNotExist()
    }
    @Test fun settingsSectionAndRootHaveDistinctBackDestinations() {
        rule.onNodeWithTag("open-settings").performClick();waitFor("settings-search")
        rule.onNodeWithTag("settings-group-typography").performClick();waitFor("size-scale")
        rule.onNodeWithContentDescription("返回").performClick();waitFor("settings-search")
        rule.onNodeWithContentDescription("返回").performClick();waitFor("app-workspace")
        rule.onNodeWithTag("main-tab-0").assertIsSelected()
    }
    @Test fun keyboardKeepsComposerVisibleInsteadOfStackingTwoBottomBars() {
        tab(1);waitFor("chat-input");rule.onNodeWithTag("chat-input").performClick()
        val content = rule.activity.findViewById<View>(android.R.id.content)
        rule.waitUntil(10000) { ViewCompat.getRootWindowInsets(content)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
        rule.onNodeWithTag("chat-input").assertIsDisplayed();rule.onNodeWithTag("chat-send").assertIsDisplayed()
        rule.onNodeWithTag("bottom-navigation").assertDoesNotExist();capture("launcher-keyboard")
        hideKeyboard();rule.onNodeWithTag("main-tab-1").assertIsSelected()
    }
    @Test fun repeatedToolsRoundTripKeepsTheRealNavigationUsable() {
        tab(1); waitFor("chat-input")
        repeat(3) {
            rule.onNodeWithTag("open-chat-tools").performClick(); waitFor("new-conversation")
            rule.onNodeWithText("Skills").performClick(); waitFor("skills-route"); awaitActivityInput()
            rule.onNodeWithContentDescription("返回").performClick()
            waitFor("chat-input"); awaitActivityInput(); assertTabSettled(1)
            tab(5); waitFor("timer-tabs"); tab(1); waitFor("chat-input")
        }
    }
    @Test fun navigationAfterKeyboardClosureRetainsDraftAndCanReturnHome() {
        tab(1); waitFor("chat-input")
        rule.waitUntil(10000) { graph.prefs.state.value.activeSession in graph.drafts.state.value }
        val text = "收起键盘之后不丢失的文字"
        rule.onNodeWithTag("chat-input").performTextReplacement(text)
        hideKeyboard(); tab(5); waitFor("timer-tabs")
        tab(1); rule.onNodeWithTag("chat-input").assertTextEquals(text)
        tab(0); waitFor("home-shared-root"); capture("launcher-return-home")
    }

}
