package cn.sishiyuni.uitesthost

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

class NavigationGlyphsUiTest {
    @get:Rule val rule=createAndroidComposeRule<TestActivity>()
    private fun assertReadable(name:String) {
        val decor=rule.activity.window.decorView
        val content=rule.activity.findViewById<View>(android.R.id.content)
        val navigation=WindowInsetsCompat.Type.navigationBars()
        rule.waitUntil(5000) {
            rule.activity.hasWindowFocus() &&
                !WindowCompat.getInsetsController(rule.activity.window,decor).isAppearanceLightNavigationBars &&
                ViewCompat.getRootWindowInsets(content)?.let { it.isVisible(navigation) && it.getInsets(navigation).bottom>0 }==true
        }
        val committed=CountDownLatch(1)
        val callback=Runnable {committed.countDown()}
        rule.runOnIdle {
            decor.viewTreeObserver.registerFrameCommitCallback(callback)
            decor.invalidate()
        }
        try {assertTrue("No committed Android frame",committed.await(5,TimeUnit.SECONDS))}
        finally {rule.runOnUiThread {decor.viewTreeObserver.unregisterFrameCommitCallback(callback)}}
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        // SystemUI owns the glyphs; wait for its accessibility/window event stream,
        // not just the client's Compose clock. Never poll for the expected color.
        automation.waitForIdle(300,5000)
        val image=requireNotNull(automation.takeScreenshot())
        try {
            val dir=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
                ?: File(rule.activity.getExternalFilesDir(null),"system-bars-checks")
            dir.mkdirs()
            File(dir,"navigation-glyphs-$name.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
            val height=requireNotNull(ViewCompat.getRootWindowInsets(content)).getInsets(navigation).bottom
            assertNightNavigationGlyphs(image,height)
        } finally {image.recycle()}
    }
    @Test fun initialNightAndDialogReturnKeepActualSystemButtonsReadable() {
        var open by mutableStateOf(false)
        rule.setContent {
            LukeTheme(AppPreferences(effects=false,reduceMotion=true,season="spring",period="night"),
                now=LocalDateTime.of(2026,9,17,12,0)) {
                NativeScreenFrame("夜间系统按键") {padding->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        TextButton(onClick={open=true}) {Text("打开设置弹层")}
                    }
                }
                if(open) NativeDialog("按键对比检查",{open=false}) {Text("关闭后继续保持夜间按键")}
            }
        }
        assertReadable("initial-night")
        rule.onNodeWithText("打开设置弹层").performClick()
        rule.onNodeWithContentDescription("关闭按键对比检查").performClick()
        assertReadable("after-dialog")
    }
}
