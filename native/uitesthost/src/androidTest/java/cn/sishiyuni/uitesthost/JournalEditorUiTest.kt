package cn.sishiyuni.uitesthost

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import cn.sishiyuni.core.AppGraph
import cn.sishiyuni.core.data.AppPreferences
import cn.sishiyuni.core.journal.MemoEditorSession
import cn.sishiyuni.designsystem.LukeTheme
import cn.sishiyuni.feature.journal.JournalEditor
import cn.sishiyuni.feature.journal.JournalViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class JournalEditorUiTest {
    @get:Rule val rule = createAndroidComposeRule<TestActivity>()
    private fun graph(): AppGraph = (rule.activity.application as TestApplication).graph.also { app ->
        runBlocking { withTimeout(15000) { app.ready.first { it } } }
    }
    private fun viewModel(app: AppGraph): JournalViewModel = ViewModelProvider(rule.activity, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = JournalViewModel(app) as T
    }).get("journal", JournalViewModel::class.java)
    private fun fixture(app: AppGraph, vm: JournalViewModel, title: String = "今天", text: String = ""): MemoEditorSession {
        val id = runBlocking { app.journal.create(title, text) }
        runBlocking { vm.open(id).join() }
        return requireNotNull(vm.editor.value)
    }
    @Test fun typingThenImmediatelyClosingWaitsForTheActualDatabaseWrite() {
        val app = graph(); val vm = viewModel(app); val editor = fixture(app, vm)
        var showing by mutableStateOf(true)
        rule.setContent { LukeTheme(AppPreferences(effects=false, reduceMotion=true)) {
            if (showing) JournalEditor(editor, vm, { showing=false }, {})
        } }
        rule.onNodeWithTag("journal-body").performTextInput("来不及等待自动保存，也不能丢失这句话。")
        rule.onNodeWithContentDescription("返回").performClick()
        rule.waitUntil(10000) { !showing }
        assertEquals("来不及等待自动保存，也不能丢失这句话。", runBlocking { app.dao.memo(editor.state.value.id)!!.body })
        assertNull(runBlocking { app.dao.record("journal-draft", editor.state.value.id) })
    }
    @Test fun formattingTargetsTheCurrentParagraphAndIsPersisted() {
        val app = graph(); val vm = viewModel(app); val editor = fixture(app, vm)
        rule.setContent { LukeTheme(AppPreferences(effects=false, reduceMotion=true)) { JournalEditor(editor, vm, {}, {}) } }
        rule.onNodeWithTag("journal-body").performTextInput("第一行\n第二行")
        rule.onNodeWithTag("journal-list").performClick()
        assertEquals("第一行\n- 第二行", editor.state.value.body)
        assertTrue(runBlocking { editor.flush() })
        assertEquals("第一行\n- 第二行", runBlocking { app.dao.memo(editor.state.value.id)!!.body })
        rule.onNodeWithTag("journal-list").performClick()
        assertEquals("第一行\n第二行", editor.state.value.body)
    }
    @Test fun readingModeDoesNotResetTheDraftOrItsContent() {
        val app = graph(); val vm = viewModel(app); val editor = fixture(app, vm)
        rule.setContent { LukeTheme(AppPreferences(effects=false, reduceMotion=true)) { JournalEditor(editor, vm, {}, {}) } }
        rule.onNodeWithTag("journal-body").performTextInput("留给明天的线索")
        rule.onNodeWithTag("journal-read-toggle").performClick()
        rule.onNodeWithText("留给明天的线索").assertExists()
        rule.onNodeWithTag("journal-format-dock").assertDoesNotExist()
        rule.onNodeWithTag("journal-read-toggle").performClick()
        rule.onNodeWithTag("journal-body").assertTextContains("留给明天的线索")
    }
    @Test fun staleRevisionCannotCloseAsSavedOrOverwriteTheRemoteEdit() {
        val app = graph(); val vm = viewModel(app); val editor = fixture(app, vm, text="原文")
        val id = editor.state.value.id
        runBlocking {
            val old = requireNotNull(app.dao.memo(id))
            app.dao.putMemo(old.copy(body="另一位置的修改", revision=old.revision+1))
        }
        var closed by mutableStateOf(false)
        rule.setContent { LukeTheme(AppPreferences(effects=false, reduceMotion=true)) { JournalEditor(editor, vm, {closed=true}, {}) } }
        rule.onNodeWithTag("journal-body").performTextReplacement("本机新草稿")
        assertFalse(runBlocking { editor.flush() })
        rule.onNodeWithContentDescription("返回").performClick()
        rule.waitUntil(10000) { vm.error.value != null }
        assertFalse(closed)
        assertEquals("另一位置的修改", runBlocking { app.dao.memo(id)!!.body })
        assertEquals("本机新草稿", editor.state.value.body)
        assertNotNull(runBlocking { app.dao.record("journal-draft", id) })
    }
    @Test fun nightLargeTextDockStaysInsideTheDialogAndCloseRemainsReachable() {
        val app = graph(); val vm = viewModel(app); val editor = fixture(app, vm, text="今天的手记")
        rule.setContent { LukeTheme(AppPreferences(effects=false, reduceMotion=true, scale=1.6f, period="night")) { JournalEditor(editor, vm, {}, {}) } }
        rule.onNodeWithContentDescription("返回").assertIsDisplayed()
        rule.onNodeWithTag("journal-format-dock").assertIsDisplayed()
        val frame = rule.onNodeWithTag("native-frame").fetchSemanticsNode().boundsInRoot
        val dock = rule.onNodeWithTag("journal-format-dock").fetchSemanticsNode().boundsInRoot
        assertTrue(dock.left >= frame.left && dock.right <= frame.right)
        assertTrue(dock.bottom <= frame.bottom && dock.top >= frame.top)
        val output = File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: rule.activity.getExternalFilesDir(null)!!.path, "screenshots").apply { mkdirs() }
        val bitmap = rule.onNodeWithTag("native-frame").captureToImage().asAndroidBitmap()
        File(output,"journal-editor-night-large.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}
