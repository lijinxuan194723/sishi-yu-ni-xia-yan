package cn.sishiyuni.uitesthost

import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.View
import android.view.Window
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import cn.sishiyuni.core.data.AppPreferences
import cn.sishiyuni.designsystem.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

class SystemBarsUiTest {
    @get:Rule val rule = createAndroidComposeRule<TestActivity>()
    private fun screen(prefs: State<AppPreferences>, overlay: @Composable () -> Unit = {}) {
        rule.setContent {
            LukeTheme(prefs.value, now = LocalDateTime.of(2026,9,16,12,0)) {
                NativeScreenFrame("系统栏检查") { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) { Text("内容仍在安全区域内") }
                }
                overlay()
            }
        }
    }
    private fun preferences(night: Boolean) = AppPreferences(effects=false, reduceMotion=true, season="spring", period=if(night)"night" else "day")
    private fun icons(window: Window = rule.activity.window) = WindowCompat.getInsetsController(window, window.decorView)
    private fun rootInsets(): WindowInsetsCompat? = ViewCompat.getRootWindowInsets(
        rule.activity.findViewById<View>(android.R.id.content))

    @Test fun manualNightAndDayUpdateBothSystemIconFamilies() {
        val p = mutableStateOf(preferences(false)); screen(p)
        rule.waitUntil(5000) { icons().isAppearanceLightStatusBars && icons().isAppearanceLightNavigationBars }
        rule.runOnIdle { p.value = preferences(true) }
        rule.waitUntil(5000) { !icons().isAppearanceLightStatusBars && !icons().isAppearanceLightNavigationBars }
        rule.runOnIdle { p.value = preferences(false) }
        rule.waitUntil(5000) { icons().isAppearanceLightStatusBars && icons().isAppearanceLightNavigationBars }
    }
    @Suppress("DEPRECATION")
    @Test fun seasonalProtectionDoesNotHideButtonsOrKeepTheWhitePlatformScrim() {
        screen(mutableStateOf(preferences(true)))
        // Compose idleness does not mean that the platform's first inset dispatch has
        // happened. A deprecated aggregate inset of zero is not a visibility signal.
        // Wait for real navigation-bar insets, then assert visibility AND geometry.
        val navigation = WindowInsetsCompat.Type.navigationBars()
        rule.waitUntil(5000) {
            val insets = rootInsets()
            insets != null && insets.isVisible(navigation) && insets.getInsets(navigation).bottom > 0
        }
        rule.runOnIdle {
            val window = rule.activity.window
            assertEquals(AndroidColor.TRANSPARENT, window.statusBarColor)
            assertEquals(AndroidColor.TRANSPARENT, window.navigationBarColor)
            if(Build.VERSION.SDK_INT>=29) assertFalse(window.isNavigationBarContrastEnforced)
            assertTrue("System navigation must remain visible", requireNotNull(rootInsets()).isVisible(navigation))
        }
        val protection = rule.onNodeWithTag("system-navigation-protection").assertExists().fetchSemanticsNode().boundsInRoot
        val expected = requireNotNull(rootInsets()).getInsets(navigation).bottom.toFloat()
        assertTrue("Navigation protection must have a visible height", protection.height > 0f)
        assertEquals("Protection must match the actual platform inset", expected, protection.height, 1.1f)
    }
    @Test fun actualNightWindowHasNoWhiteNavigationStrip() {
        screen(mutableStateOf(preferences(true)))
        rule.waitUntil(5000) { !icons().isAppearanceLightNavigationBars }
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val image = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            for(x in listOf(2, image.width-3)) {
                val color = image.getPixel(x,image.height-6)
                assertTrue("Night navigation protection is white: $color", maxOf(AndroidColor.red(color),AndroidColor.green(color),AndroidColor.blue(color)) < 100)
            }
        } finally { image.recycle() }
    }
    @Test fun dialogOwnsItsSystemStyleAndClosingDoesNotRestoreStaleDayIcons() {
        var open by mutableStateOf(true)
        var dialogWindow: Window? = null
        screen(mutableStateOf(preferences(true))) {
            if(open) NativeDialog("夜间弹层", {open=false}) {
                val view=LocalView.current
                SideEffect { dialogWindow=nativeWindow(view) }
                Text("关闭后保留夜间状态")
            }
        }
        rule.waitUntil(5000) { dialogWindow!=null && !icons(requireNotNull(dialogWindow)).isAppearanceLightStatusBars }
        assertNotSame(rule.activity.window,dialogWindow)
        rule.onNodeWithContentDescription("关闭夜间弹层").performClick()
        rule.waitUntil(5000) { !icons().isAppearanceLightStatusBars && !icons().isAppearanceLightNavigationBars }
    }
}
