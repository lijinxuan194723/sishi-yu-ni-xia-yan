package cn.sishiyuni.uitesthost

import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.sishiyuni.core.AppGraph
import cn.sishiyuni.core.data.AppPreferences
import cn.sishiyuni.designsystem.LukeTheme
import cn.sishiyuni.feature.settings.SettingsScreen
import cn.sishiyuni.feature.settings.SettingsViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

class SettingsUiTest {
    @get:Rule val rule=createAndroidComposeRule<TestActivity>()
    private lateinit var graph:AppGraph
    private lateinit var original:AppPreferences
    @Before fun initialize() {
        graph=(rule.activity.application as TestApplication).graph
        runBlocking {
            withTimeout(15000){graph.ready.first{it}}
            original=graph.prefs.state.value
            graph.prefs.restore(buildJsonObject {
                put("name","华生");put("since","2023-07-08");put("birthday","")
                put("scale",.95f);put("chatSize",13f);put("season","spring");put("period","day")
                put("effects",false);put("reduceMotion",true);put("sharing-notes",true)
            })
            withTimeout(10000){graph.prefs.state.first{it.name=="华生" && it.scale==.95f && it.chatSize==13f && it.reduceMotion && !it.effects}}
        }
    }
    @After fun restore() {
        runBlocking {
            graph.prefs.restore(buildJsonObject {
                put("name",original.name);put("since",original.since);put("birthday",original.birthday)
                put("scale",original.scale);put("chatSize",original.chatSize);put("season",original.season);put("period",original.period)
                put("effects",original.effects);put("reduceMotion",original.reduceMotion);put("sharing-notes",original.sharing["notes"]?:true)
            })
        }
    }
    @Composable private fun Content() {
        val p by graph.prefs.state.collectAsStateWithLifecycle()
        LukeTheme(p,now=LocalDateTime.of(2026,9,16,12,0)){SettingsScreen(graph,{},{})}
    }
    private fun screen() { rule.setContent { Content() } }
    private fun vm()=ViewModelProvider(rule.activity).get("settings",SettingsViewModel::class.java)
    private fun group(id:String,search:String="") {
        if(search.isNotEmpty()) rule.onNodeWithTag("settings-search").performTextReplacement(search)
        rule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-group-$id"))
        rule.onNodeWithTag("settings-group-$id").performClick()
    }
    @Test fun searchIsNotAutomaticallyFocusedAndFiltersSettings() {
        screen()
        rule.onNodeWithTag("settings-search").assertIsNotFocused().performTextInput("字体")
        rule.onNodeWithTag("settings-group-typography").assertExists()
        rule.onNodeWithTag("settings-group-appearance").assertDoesNotExist()
    }
    @Test fun invalidProfileKeepsTheDialogAndDoesNotPartiallySave() {
        screen();group("identity")
        rule.onNodeWithTag("identity-name").performClick()
        rule.onNodeWithTag("profile-name").performTextReplacement("新称呼")
        rule.onNodeWithTag("profile-since").performTextReplacement("2026-02-30")
        rule.onNodeWithTag("settings-form-save").performClick()
        rule.waitUntil(10000){vm().error.value!=null && vm().pending.value==0}
        rule.onNodeWithTag("profile-name").assertTextContains("新称呼")
        rule.onNodeWithTag("profile-since").assertTextContains("2026-02-30")
        assertEquals("华生",graph.prefs.state.value.name)
        assertEquals("2023-07-08",graph.prefs.state.value.since)
    }
    @Test fun correctingAnInvalidProfileSavesAndClosesOnlyThatForm() {
        screen();group("identity");rule.onNodeWithTag("identity-name").performClick()
        rule.onNodeWithTag("profile-name").performTextReplacement("小鹿")
        rule.onNodeWithTag("profile-since").performTextReplacement("2026-99-99")
        rule.onNodeWithTag("settings-form-save").performClick()
        rule.waitUntil(10000){vm().pending.value==0 && vm().error.value!=null}
        rule.onNodeWithTag("profile-since").performTextReplacement("2026-09-16")
        rule.onNodeWithTag("settings-form-save").performClick()
        rule.waitUntil(10000){graph.prefs.state.value.name=="小鹿" && vm().pending.value==0}
        rule.onNodeWithTag("profile-name").assertDoesNotExist()
        assertEquals("2026-09-16",graph.prefs.state.value.since)
    }
    @Test fun bothTypographyControlsPersistIndependentlyAndResetTogether() {
        screen();group("typography")
        rule.onNodeWithTag("size-scale").performSemanticsAction(SemanticsActions.SetProgress){assertTrue(it(85f))}
        rule.waitUntil(10000){graph.prefs.state.value.scale==.85f && vm().pending.value==0}
        assertEquals(13f,graph.prefs.state.value.chatSize)
        rule.onNodeWithTag("size-chatSize").performSemanticsAction(SemanticsActions.SetProgress){assertTrue(it(19f))}
        rule.waitUntil(10000){graph.prefs.state.value.chatSize==19f && vm().pending.value==0}
        assertEquals(.85f,graph.prefs.state.value.scale)
        rule.onNodeWithTag("settings-section-typography").performScrollToNode(hasTestTag("reset-typography"))
        rule.onNodeWithTag("reset-typography").performClick()
        rule.waitUntil(10000){graph.prefs.state.value.scale==.95f && graph.prefs.state.value.chatSize==13f && vm().pending.value==0}
    }
    @Test fun rapidExplicitThemeChoicesAreWrittenInOrderAndFinishAtLastSelection() {
        screen();group("appearance")
        rule.runOnUiThread { vm().text("season","summer");vm().text("season","autumn");vm().text("season","winter") }
        rule.waitUntil(10000){graph.prefs.state.value.season=="winter" && vm().pending.value==0}
        rule.onNodeWithTag("choice-winter").assertIsSelected()
    }
    @Test fun sharingToggleOnlyChangesTheSelectedCategory() {
        screen();group("privacy","分享")
        // The lazy item is deliberately not composed until its parent is scrolled.
        rule.onNodeWithTag("settings-section-privacy").performScrollToNode(hasTestTag("setting-sharing-notes"))
        rule.onNodeWithTag("setting-sharing-notes").assertIsDisplayed().performClick()
        rule.waitUntil(10000){graph.prefs.state.value.sharing["notes"]==false && vm().pending.value==0}
        assertEquals(original.sharing["plans"],graph.prefs.state.value.sharing["plans"])
        assertEquals(original.sharing["weather"],graph.prefs.state.value.sharing["weather"])
    }
    @Test fun subpageScrollKeepsHeaderFixedAndReturningRetainsSearch() {
        screen();group("privacy","分享")
        val before=rule.onNodeWithTag("glass-header").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("settings-section-privacy").performTouchInput{swipeUp()}
        val after=rule.onNodeWithTag("glass-header").fetchSemanticsNode().boundsInRoot
        assertEquals(before.top,after.top,.5f);assertEquals(before.bottom,after.bottom,.5f)
        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNodeWithTag("settings-search").assertTextContains("分享").assertIsNotFocused()
    }
    @Test fun invalidModelAddressDoesNotDismissTheFormOrEraseTypedFields() {
        screen();group("connection","模型")
        rule.onNodeWithTag("model-primary").performClick()
        rule.onNodeWithTag("model-url").performTextReplacement("invalid-address")
        rule.onNodeWithTag("model-name").performTextReplacement("test-fixture")
        rule.onNodeWithTag("settings-form-save").performClick()
        rule.waitUntil(10000){vm().error.value!=null && vm().pending.value==0}
        rule.onNodeWithTag("model-url").assertTextContains("invalid-address")
        rule.onNodeWithTag("model-name").assertTextContains("test-fixture")
    }
    @Test fun calendarYearSelectionSurvivesSavedStateRestoration() {
        val restoration=StateRestorationTester(rule)
        restoration.setContent { Content() }
        group("calendar","日历")
        rule.onNodeWithContentDescription("上一年").performClick()
        rule.onNodeWithText("2025 年").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("2025 年").assertExists()
    }
    @Test fun unfinishedProfileTextSurvivesSavedStateRestorationWithoutSavingIt() {
        val restoration=StateRestorationTester(rule)
        restoration.setContent { Content() }
        group("identity");rule.onNodeWithTag("identity-name").performClick()
        rule.onNodeWithTag("profile-name").performTextReplacement("未保存")
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("profile-name").assertTextContains("未保存")
        assertEquals("华生",graph.prefs.state.value.name)
    }
    @Test fun acceptedSettingWriteFinishesAfterItsScreenViewModelIsCleared() {
        var visible by mutableStateOf(true)
        rule.setContent { if(visible) Content() }
        val writer=vm()
        rule.runOnUiThread {
            writer.text("name","离开后保存")
            visible=false
            rule.activity.viewModelStore.clear()
        }
        rule.waitUntil(10000){graph.prefs.state.value.name=="离开后保存" && writer.pending.value==0}
        assertNull(writer.error.value)
    }
}
