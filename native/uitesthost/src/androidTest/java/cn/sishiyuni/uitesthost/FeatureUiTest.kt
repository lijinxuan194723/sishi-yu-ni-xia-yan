package cn.sishiyuni.uitesthost

import android.graphics.Bitmap
import android.util.Base64
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import cn.sishiyuni.core.AppGraph
import cn.sishiyuni.core.data.*
import cn.sishiyuni.core.model.sha256
import cn.sishiyuni.designsystem.*
import cn.sishiyuni.feature.chat.*
import cn.sishiyuni.feature.home.HomeScreen
import cn.sishiyuni.feature.settings.SkillsScreen
import cn.sishiyuni.feature.timer.TimerScreen
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.UUID

class FeatureUiTest {
    @get:Rule val rule = createAndroidComposeRule<TestActivity>()
    private fun graph(): AppGraph {
        val app = (rule.activity.application as TestApplication).graph
        runBlocking { withTimeout(15000) { app.ready.first { it } } }
        return app
    }
    private fun newSession(app: AppGraph): String = runBlocking {
        val id = "ui-${UUID.randomUUID()}"
        app.dao.putSession(SessionEntity(id, "界面检查"))
        app.prefs.text("activeSession", id); app.prefs.state.first { it.activeSession == id }
        id
    }
    private fun chat(app: AppGraph) {
        rule.setContent {
            LukeTheme(AppPreferences(effects = false, reduceMotion = true)) {
                var tools by remember { mutableStateOf(false) }
                NativeScreenFrame("夏彦", actions = {
                    IconButton(onClick = { tools = true }, modifier = Modifier.testTag("conversation-tools")) { Icon(Icons.Outlined.MoreHoriz, "对话与工具") }
                }, footer = { layer -> ChatComposer(app, layer, Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }) { padding ->
                    ChatScreen(app, padding, tools, { tools = false }, {}, {})
                }
            }
        }
        rule.waitUntil(10000) {
            rule.onAllNodesWithTag("chat-input").fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) }
        }
    }
    @Test fun messageAvatarsAlignWithTheirBubbleNotWithTheMetadataLine() {
        val app = graph(); val id = newSession(app)
        runBlocking {
            app.dao.putMessage(MessageEntity("$id-me", id, 0, "me", "这是一条自己的消息。", System.currentTimeMillis()))
            app.dao.putMessage(MessageEntity("$id-luke", id, 1, "luke", "我在。今天发生了什么，慢慢说给我听。", System.currentTimeMillis()))
        }
        chat(app)
        rule.waitUntil(10000) { rule.onAllNodesWithTag("bubble-$id-luke", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        for (suffix in listOf("me", "luke")) {
            val avatar = rule.onNodeWithTag("avatar-$id-$suffix", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val bubble = rule.onNodeWithTag("bubble-$id-$suffix", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(avatar.top, bubble.top, 2f)
            if (suffix == "me") assertTrue(bubble.right < avatar.left) else assertTrue(avatar.right < bubble.left)
        }
    }
    @Test fun failedModelValidationDoesNotClearTheComposer() {
        val app = graph(); val id = newSession(app); chat(app)
        rule.onNodeWithTag("chat-input").performTextInput("没有配置模型也不能丢掉这句话")
        rule.onNodeWithTag("chat-send").performClick()
        val vm = ViewModelProvider(rule.activity).get("chat", ChatViewModel::class.java)
        rule.waitUntil(10000) { vm.error.value != null }
        rule.onNodeWithTag("chat-input").assertTextContains("没有配置模型也不能丢掉这句话")
        assertEquals(0, runBlocking { app.dao.messagesNow(id).size })
    }
    @Test fun creatingConversationPreservesTheOriginalDraft() {
        val app = graph(); val old = newSession(app); chat(app)
        rule.onNodeWithTag("chat-input").performTextInput("留在旧会话里的草稿")
        rule.onNodeWithTag("conversation-tools").performClick()
        rule.onNodeWithTag("new-conversation").performClick()
        rule.waitUntil(10000) { app.prefs.state.value.activeSession != old }
        assertEquals("留在旧会话里的草稿", runBlocking { app.dao.session(old)!!.draft })
        assertTrue(runBlocking { app.dao.session(app.prefs.state.value.activeSession) != null })
    }
    @Test fun galleryCompatibleDataUriAvatarRendersPixelsAndPreservesTransparentCorners() {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        for (y in 4..11) for (x in 4..11) bitmap.setPixel(x, y, android.graphics.Color.RED)
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        val uri = "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        rule.setContent {
            LukeTheme(AppPreferences(effects = false)) {
                Box(Modifier.size(64.dp).background(Color.Magenta).testTag("avatar-fixture")) {
                    AssetImage(uri, "本机头像", Modifier.fillMaxSize())
                }
            }
        }
        rule.waitUntil(10000) {
            val pixels = rule.onNodeWithTag("avatar-fixture").captureToImage().toPixelMap()
            val centre = pixels[pixels.width / 2, pixels.height / 2]
            centre.red > .95f && centre.green < .05f && centre.blue < .05f
        }
        val image = rule.onNodeWithTag("avatar-fixture").captureToImage().toPixelMap()
        val corner = image[1, 1]
        assertTrue("Transparent PNG corner must reveal the host background", corner.red > .95f && corner.blue > .95f && corner.green < .05f)
    }
    private fun seedSkill(app: AppGraph): SkillEntity {
        val content = "---\nname: 原生界面技能\ndescription: 只用于检查确认流程\n---\n回答时先确认问题。"
        val skill = SkillEntity("ui-${UUID.randomUUID()}", "原生界面技能", "确认后才启用", content, "local:SKILL.md", content.sha256())
        runBlocking { app.dao.putSkill(skill) }; return skill
    }
    @Test fun skillEnableRequiresReviewAndExplicitConfirmation() {
        val app = graph(); val skill = seedSkill(app)
        rule.setContent { LukeTheme(AppPreferences(effects = false, reduceMotion = true)) { SkillsScreen(app) } }
        rule.waitUntil(10000) { rule.onAllNodesWithTag("skill-enabled-${skill.id}").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("skill-enabled-${skill.id}").performScrollTo().performClick()
        assertFalse(runBlocking { app.dao.skill(skill.id)!!.enabled })
        rule.onNodeWithText("确认启用这个版本").performClick()
        rule.waitUntil(10000) { runBlocking { app.dao.skill(skill.id)!!.enabled } }
    }
    @Test fun changedSkillCannotBeEnabledUsingAStaleReview() {
        val app = graph(); val skill = seedSkill(app)
        rule.setContent { LukeTheme(AppPreferences(effects = false, reduceMotion = true)) { SkillsScreen(app) } }
        rule.waitUntil(10000) { rule.onAllNodesWithTag("skill-enabled-${skill.id}").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("skill-enabled-${skill.id}").performScrollTo().performClick()
        val changed = skill.content + "\n新的方法说明。"
        runBlocking { app.dao.putSkill(skill.copy(content = changed, digest = changed.sha256(), enabled = false)) }
        rule.onNodeWithText("确认启用这个版本").performClick()
        val vm = ViewModelProvider(rule.activity).get("skills", cn.sishiyuni.feature.settings.SkillsViewModel::class.java)
        rule.waitUntil(10000) { vm.error.value != null }
        assertFalse(runBlocking { app.dao.skill(skill.id)!!.enabled })
    }
    @Test fun sharedPhotoOpensAndReturnsWithoutChangingTheSelectedSection() {
        val app = graph()
        rule.setContent { LukeTheme(AppPreferences(effects = false, season = "spring", period = "day")) {
            NativeScreenFrame("四时与你") { padding -> HomeScreen(app, padding, false, {}) }
        } }
        rule.onNodeWithTag("open-photo").performClick()
        rule.onNodeWithTag("photo-detail").assertIsDisplayed()
        rule.onNodeWithTag("close-photo").performClick()
        rule.onNodeWithTag("home-tabs-0").assertIsSelected()
        rule.onNodeWithTag("home-hero").assertIsDisplayed()
    }
    @Test fun subjectManagementAndStudyStartUseRealRoomState() {
        val app = graph()
        runBlocking { app.timer.reset("study", discardStudyConfirmed = true) }
        val subject = "检查${UUID.randomUUID().toString().take(5)}"
        rule.setContent { LukeTheme(AppPreferences(effects = false, reduceMotion = true)) {
            val pager = rememberPagerState(initialPage = 5) { 6 }
            NativeScreenFrame("计时", footer = { NativeBottomBar(pager) }) { padding -> TimerScreen(app, padding, false) }
        } }
        rule.onNodeWithText("科目", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("subject-name").performTextInput(subject)
        rule.onNodeWithText("添加", useUnmergedTree = true).performClick()
        rule.waitUntil(10000) { runBlocking { app.dao.allSubjects().any { it.name == subject && !it.deleted } } }
        rule.onNodeWithContentDescription("关闭学习科目").performClick()
        // Android owns the dialog-window and IME transitions. Compose idleness alone
        // is not enough after dismissal; wait on their real state, not a fixed sleep.
        val content = rule.activity.findViewById<View>(android.R.id.content)
        rule.waitUntil(5000) {
            rule.onAllNodesWithContentDescription("关闭学习科目").fetchSemanticsNodes().isEmpty() &&
                rule.activity.hasWindowFocus() &&
                ViewCompat.getRootWindowInsets(content)?.isVisible(WindowInsetsCompat.Type.ime()) == false &&
                rule.onNodeWithTag("start-study").isDisplayed()
        }
        val start = rule.onNodeWithTag("start-study").assertIsDisplayed().assertIsEnabled()
        assertTrue("Timer action must retain its complete touch height", start.fetchSemanticsNode().boundsInRoot.height >= 47f * rule.activity.resources.displayMetrics.density)
        start.performClick()
        rule.waitUntil(10000) { runBlocking { app.dao.timer("study")?.running == true } }
        rule.onNodeWithText("暂停").performClick()
        rule.waitUntil(10000) { runBlocking { app.dao.timer("study")?.running == false } }
        rule.onNodeWithText("结束并保存").performClick()
        rule.waitUntil(10000) { runBlocking { app.dao.allFocusLogs().any { it.group == subject } } }
    }
    @Test fun testHostContainsNoWebRuntimeAssetsOrWebViewInstance() {
        rule.setContent { LukeTheme(AppPreferences(effects = false)) { NativeScreenFrame("原生检查") { } } }
        rule.runOnIdle {
            fun inspect(view: android.view.View) {
                assertFalse(view.javaClass.name.contains("android.webkit.WebView"))
                if (view is android.view.ViewGroup) repeat(view.childCount) { inspect(view.getChildAt(it)) }
            }
            inspect(rule.activity.window.decorView)
        }
        fun walk(path: String) {
            rule.activity.assets.list(path).orEmpty().forEach { name ->
                val child = if (path.isEmpty()) name else "$path/$name"
                assertFalse("Unexpected web runtime asset: $child", child.endsWith(".html") || child.endsWith(".js") || child.endsWith(".mjs"))
                walk(child)
            }
        }
        walk("")
    }
}
