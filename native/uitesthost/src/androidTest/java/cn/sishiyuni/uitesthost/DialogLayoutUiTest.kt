package cn.sishiyuni.uitesthost

import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import cn.sishiyuni.core.data.AppPreferences
import cn.sishiyuni.designsystem.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DialogLayoutUiTest {
    @get:Rule val rule = createAndroidComposeRule<TestActivity>()

    @Test fun scrollingLongDialogDoesNotMoveItsTitleOrHideClose() {
        var closed by mutableStateOf(false)
        rule.setContent { LukeTheme(AppPreferences(effects=false, reduceMotion=true)) {
            if (!closed) NativeDialog("更多设置", {closed=true}) {
                repeat(40) { Text("设置条目 $it", Modifier.fillMaxWidth().padding(vertical=12.dp)) }
                Button(onClick={}, modifier=Modifier.testTag("dialog-last")) { Text("最后一项") }
            }
        } }
        val before = rule.onNodeWithTag("dialog-title").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("dialog-last").performScrollTo().assertIsDisplayed()
        val after = rule.onNodeWithTag("dialog-title").fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, after.top, 1f); assertEquals(before.bottom, after.bottom, 1f)
        rule.onNodeWithContentDescription("关闭更多设置").assertIsDisplayed().performClick()
        rule.waitUntil(5000) { closed }
    }

    @Test fun largeTextKeepsHeaderSeparateFromTheScrollableBody() {
        rule.setContent { LukeTheme(AppPreferences(effects=false, reduceMotion=true, scale=1.6f, period="night")) {
            NativeDialog("较长的设置分组标题", {}) {
                repeat(20) { Text("内容应该在标题下方滚动，而不是把关闭按钮推走。") }
                TextButton(onClick={}, modifier=Modifier.testTag("large-last")) { Text("完成") }
            }
        } }
        rule.onNodeWithTag("large-last").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("关闭较长的设置分组标题").assertIsDisplayed()
        val title = rule.onNodeWithTag("dialog-title").fetchSemanticsNode().boundsInRoot
        val body = rule.onNodeWithTag("dialog-body").fetchSemanticsNode().boundsInRoot
        val surface = rule.onNodeWithTag("dialog-surface").fetchSemanticsNode().boundsInRoot
        assertTrue(title.bottom <= body.top)
        assertTrue(body.bottom <= surface.bottom)
    }

    @Test fun actualKeyboardStillAllowsReachingTheLastActionAndTheCloseButton() {
        var view: View? = null
        var text by mutableStateOf("")
        rule.setContent { LukeTheme(AppPreferences(effects=false, reduceMotion=true)) {
            NativeDialog("编辑内容", {}) {
                val current = LocalView.current
                SideEffect { view = current }
                OutlinedTextField(text, {text=it}, modifier=Modifier.fillMaxWidth().testTag("dialog-input"), singleLine=true)
                repeat(12) { Text("表单说明 $it", Modifier.padding(vertical=8.dp)) }
                Button(onClick={}, modifier=Modifier.testTag("keyboard-last")) { Text("保存") }
            }
        } }
        rule.onNodeWithTag("dialog-input").performClick().performTextInput("输入法检查")
        rule.waitUntil(10000) { view?.let { ViewCompat.getRootWindowInsets(it)?.isVisible(WindowInsetsCompat.Type.ime()) } == true }
        rule.onNodeWithTag("keyboard-last").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("关闭编辑内容").assertIsDisplayed()
        val body = rule.onNodeWithTag("dialog-body").fetchSemanticsNode().boundsInRoot
        val last = rule.onNodeWithTag("keyboard-last").fetchSemanticsNode().boundsInRoot
        assertTrue(last.top >= body.top && last.bottom <= body.bottom)
        assertEquals("输入法检查", text)
    }
}
