package cn.sishiyuni.uitesthost

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
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
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
    @Test fun opaqueWindowBackingTracksAllEightLogicalPalettes() {
        val state = mutableStateOf(preferences(true)); screen(state)
        for (night in listOf(true,false)) for (season in listOf("spring","summer","autumn","winter")) {
            rule.runOnIdle { state.value = preferences(night).copy(season=season) }
            rule.runOnIdle {
                val background = rule.activity.window.decorView.background
                assertTrue("The activity needs a solid seasonal backing", background is ColorDrawable)
                val actual = (background as ColorDrawable).color
                assertEquals(seasonColors(season,night).paper.toArgb(),actual)
                assertEquals(255,AndroidColor.alpha(actual))
            }
        }
    }
    private fun commitPlatformFrame() {
        val latch = CountDownLatch(1)
        val callback = Runnable { latch.countDown() }
        val decor = rule.activity.window.decorView
        rule.runOnIdle {
            assertTrue("Pixel checks require an actual rendered frame",decor.isHardwareAccelerated)
            decor.viewTreeObserver.registerFrameCommitCallback(callback)
            decor.invalidate()
        }
        try { assertTrue("Android did not submit its frame",latch.await(5,TimeUnit.SECONDS)) }
        finally { rule.runOnUiThread { decor.viewTreeObserver.unregisterFrameCommitCallback(callback) } }
    }
    private fun captureWindow(): Bitmap {
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        val latch = CountDownLatch(1)
        var result = PixelCopy.ERROR_UNKNOWN
        PixelCopy.request(rule.activity.window,bitmap,{ result=it; latch.countDown() },Handler(Looper.getMainLooper()))
        if (!latch.await(5,TimeUnit.SECONDS) || result != PixelCopy.SUCCESS) {
            bitmap.recycle(); fail("Window PixelCopy failed: $result")
        }
        return bitmap
    }
    private fun saveImage(name: String, bitmap: Bitmap) {
        val path = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let(::File) ?: File(rule.activity.getExternalFilesDir(null),"system-bars-checks")
        path.mkdirs()
        File(path,name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
    }
    private fun assertDarkEdges(image: Bitmap, label: String) {
        for(x in listOf(2,image.width-3)) {
            val color=image.getPixel(x,image.height-6)
            assertTrue("$label navigation protection is white: $color",maxOf(AndroidColor.red(color),AndroidColor.green(color),AndroidColor.blue(color)) < 100)
        }
    }
    @Test fun actualNightWindowHasNoWhiteNavigationStrip() {
        screen(mutableStateOf(preferences(true)))
        val navigation=WindowInsetsCompat.Type.navigationBars()
        rule.waitUntil(5000) {
            rule.activity.hasWindowFocus() && !icons().isAppearanceLightNavigationBars &&
                rootInsets()?.let { it.isVisible(navigation) && it.getInsets(navigation).bottom>0 } == true
        }
        rule.onNodeWithTag("system-navigation-protection").assertIsDisplayed()
        rule.waitForIdle()
        // A correct icon flag is not a rendered buffer. Check the app's committed
        // frame as well as the real display, without polling until a color passes.
        commitPlatformFrame()
        val window=captureWindow()
        try { saveImage("navigation-night-window.png",window); assertDarkEdges(window,"App window") }
        finally { window.recycle() }
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.waitForIdle(150,5000)
        assertTrue("Screenshot must belong to the foreground activity",rule.activity.hasWindowFocus())
        val display=requireNotNull(automation.takeScreenshot())
        try { saveImage("navigation-night-display.png",display); assertDarkEdges(display,"Full display") }
        finally { display.recycle() }
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
        val background=requireNotNull(dialogWindow).decorView.background
        assertTrue("Dialog margins must remain transparent",background !is ColorDrawable || AndroidColor.alpha(background.color)==0)
        rule.onNodeWithContentDescription("关闭夜间弹层").performClick()
        rule.waitUntil(5000) { !icons().isAppearanceLightStatusBars && !icons().isAppearanceLightNavigationBars }
    }
}
