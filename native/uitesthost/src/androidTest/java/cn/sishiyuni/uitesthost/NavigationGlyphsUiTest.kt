package cn.sishiyuni.uitesthost

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
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
        automation.waitForIdle(300,5000)
        val image=requireNotNull(automation.takeScreenshot())
        val dir=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
            ?: File(rule.activity.getExternalFilesDir(null),"system-bars-checks")
        dir.mkdirs()
        try {
            File(dir,"navigation-glyphs-$name.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
            val height=requireNotNull(ViewCompat.getRootWindowInsets(content)).getInsets(navigation).bottom
            try { assertNightNavigationGlyphs(image,height) }
            catch (failure: AssertionError) {
                // Diagnostic only: keep the ORIGINAL assertion and screenshot as the verdict.
                // Dumps belong to the disposable CI emulator, never a user's device.
                runCatching {
                    @Suppress("DEPRECATION")
                    val state=rule.runOnIdle {
                        "appearance=${decor.windowInsetsController?.systemBarsAppearance}\nlegacy=${decor.systemUiVisibility}\nfocus=${decor.hasWindowFocus()}\nflags=${rule.activity.window.attributes.flags}\n"
                    }
                    File(dir,"navigation-client-$name.txt").writeText(state)
                    for ((label,command) in listOf("window" to "dumpsys window", "systemui" to "dumpsys activity service com.android.systemui/.SystemUIService")) {
                        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { input ->
                            File(dir,"navigation-$label-$name.txt").outputStream().use { input.copyTo(it) }
                        }
                    }
                    SystemClock.sleep(1000)
                    val later=requireNotNull(automation.takeScreenshot())
                    try { File(dir,"navigation-later-$name.png").outputStream().use { later.compress(Bitmap.CompressFormat.PNG,100,it) } }
                    finally { later.recycle() }
                }.onFailure { failure.addSuppressed(it) }
                throw failure
            }
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
