package cn.sishiyuni.uitesthost

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import cn.sishiyuni.core.data.AppPreferences
import cn.sishiyuni.designsystem.LukeTheme
import cn.sishiyuni.feature.settings.SettingsScreen
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

/** Captures actual emulator windows. These are review images, not golden-image approvals. */
class SettingsVisualTest {
    @get:Rule val rule=createAndroidComposeRule<TestActivity>()
    private fun screen(scale:Float=.95f,systemScale:Float=1f,night:Boolean=false) {
        val graph=(rule.activity.application as TestApplication).graph
        runBlocking { withTimeout(15000){graph.ready.first{it}} }
        rule.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,systemScale)) {
                LukeTheme(AppPreferences(scale=scale,effects=false,reduceMotion=true,season="spring",period=if(night)"night" else "day"),
                    now=LocalDateTime.of(2026,9,16,12,0)) { SettingsScreen(graph,{},{}) }
            }
        }
    }
    private fun capture(name:String) {
        rule.waitForIdle()
        val directory=File(requireNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")) {
            "Enable additional Android test output to retain visual review evidence"
        },"screenshots")
        assertTrue(directory.isDirectory || directory.mkdirs())
        val bitmap=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try { File(directory,"$name.png").outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))} }
        finally { bitmap.recycle() }
    }
    @Test fun defaultSettingsAndTypographyAreVisibleAndCaptured() {
        screen();rule.onNodeWithTag("settings-search").assertIsDisplayed();capture("settings-day")
        rule.onNodeWithTag("settings-group-typography").performClick()
        rule.onNodeWithTag("size-scale").assertIsDisplayed()
        rule.onNodeWithTag("size-chatSize").assertIsDisplayed()
        capture("settings-typography")
    }
    @Test fun nightSettingsKeepTheSameGeometryAndSeasonalSurfaces() {
        screen(night=true);capture("settings-night")
        rule.onNodeWithTag("settings-group-appearance").performClick()
        rule.onNodeWithTag("choice-winter").assertIsDisplayed()
        capture("settings-appearance-night")
    }
    @Test fun enlargedProfileCanScrollFieldsWhileKeepingItsSaveActionVisible() {
        screen(scale=1.6f,systemScale=1.5f)
        rule.onNodeWithTag("settings-group-identity").performClick()
        rule.onNodeWithTag("identity-name").performClick()
        rule.onNodeWithTag("settings-form-save").assertIsDisplayed()
        rule.onNodeWithTag("profile-birthday").performScrollTo()
        rule.onNodeWithTag("profile-birthday").assertIsDisplayed()
        capture("settings-profile-large")
    }
}
