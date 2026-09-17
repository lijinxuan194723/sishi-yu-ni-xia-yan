package cn.sishiyuni.uitesthost

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** A platform control case: no Compose, Luke theme, Dialog, or app-painted system-bar overlay. */
class PlatformNavigationBaselineUiTest {
    @get:Rule val rule = ActivityScenarioRule(TestActivity::class.java)

    @Suppress("DEPRECATION")
    @Test fun plainAndroidWindowCanDisplayLightNavigationButtons() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        lateinit var activity: TestActivity
        lateinit var root: FrameLayout
        rule.scenario.onActivity {
            activity = it
            root = FrameLayout(it).apply { setBackgroundColor(Color.rgb(40, 59, 50)) }
            it.setContentView(root)
            WindowCompat.setDecorFitsSystemWindows(it.window, false)
            it.window.setBackgroundDrawable(ColorDrawable(Color.rgb(40, 59, 50)))
            it.window.statusBarColor = Color.TRANSPARENT
            it.window.navigationBarColor = Color.TRANSPARENT
            it.window.isStatusBarContrastEnforced = false
            it.window.isNavigationBarContrastEnforced = false
            WindowCompat.getInsetsController(it.window, root).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
        val navigation = WindowInsetsCompat.Type.navigationBars()
        val deadline = SystemClock.uptimeMillis() + 5000
        var ready = false
        var insetHeight = 0
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync {
                val insets = ViewCompat.getRootWindowInsets(root)
                insetHeight = insets?.getInsets(navigation)?.bottom ?: 0
                ready = activity.hasWindowFocus() && insets?.isVisible(navigation) == true && insetHeight > 0
            }
            if (!ready) SystemClock.sleep(25)
        }
        assertTrue("The control window did not receive a visible navigation inset", ready)
        val committed = CountDownLatch(1)
        val callback = Runnable { committed.countDown() }
        instrumentation.runOnMainSync {
            root.viewTreeObserver.registerFrameCommitCallback(callback)
            root.invalidate()
        }
        try { assertTrue("No committed platform frame", committed.await(5, TimeUnit.SECONDS)) }
        finally { instrumentation.runOnMainSync { root.viewTreeObserver.unregisterFrameCommitCallback(callback) } }
        automation.waitForIdle(300, 5000)
        val dir = File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: activity.getExternalFilesDir(null)!!.path, "platform-navigation-baseline").apply { mkdirs() }
        val image = requireNotNull(automation.takeScreenshot())
        try {
            File(dir, "plain-window.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            var state = ""
            instrumentation.runOnMainSync {
                val decor = activity.window.decorView
                state = "appearance=${decor.windowInsetsController?.systemBarsAppearance}\nlegacy=${decor.systemUiVisibility}\nnavHeight=$insetHeight\n"
            }
            File(dir, "client.txt").writeText(state)
            for ((name, command) in listOf(
                "window" to "dumpsys window",
                "systemui" to "dumpsys activity service com.android.systemui/.SystemUIService",
                "launcher" to "dumpsys activity service com.android.launcher3/com.android.quickstep.TouchInteractionService"
            )) {
                ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { input ->
                    File(dir, "$name.txt").outputStream().use { input.copyTo(it) }
                }
            }
            // Keep exactly the same pixel criterion as the app test. A failing control
            // is diagnostic evidence, not a reason to waive the app's acceptance check.
            assertNightNavigationGlyphs(image, insetHeight)
        } finally { image.recycle() }
    }
}
