package cn.sishiyuni.uitesthost

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cn.sishiyuni.core.data.AppPreferences
import cn.sishiyuni.core.model.obj
import cn.sishiyuni.core.network.HolidayParser
import cn.sishiyuni.designsystem.LukeTheme
import cn.sishiyuni.feature.plans.NativeCalendar
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarUiTest {
    @get:Rule val rule = createAndroidComposeRule<TestActivity>()
    private fun holidays(year: Int) = rule.activity.assets.open("holidays/$year.json").bufferedReader().use { HolidayParser.static(obj(it.readText()), year) }.associateBy { it.date }

    @Test fun packagedYearsKeepActualMakeupDaysAndCrossYearDate() {
        assertEquals(listOf(34, 36, 33, 39), (2023..2026).map { holidays(it).size })
        assertTrue(holidays(2023).getValue("2022-12-31").off)
        assertFalse(holidays(2026).getValue("2026-01-04").off)
        assertFalse(holidays(2026).getValue("2026-09-20").off)
        assertTrue(holidays(2026).getValue("2026-09-25").off)
        assertFalse(holidays(2024).containsKey("2024-06-08")) // no invented holiday entries for ordinary weekends
        assertTrue(rule.activity.assets.open("holidays/LICENSE.txt").bufferedReader().readText().contains("Copyright (c) 2019 NateScarlet"))
    }
    @Test fun selectingDateReportsTheExactLocalDate() {
        val selected = mutableStateOf(LocalDate.of(2026, 1, 1))
        val entries = holidays(2026)
        rule.setContent { LukeTheme(AppPreferences(reduceMotion = true)) {
            NativeCalendar(YearMonth.of(2026, 1), selected.value, LocalDate.of(2026, 1, 1), entries, emptyMap(), {}, { selected.value = it })
        } }
        rule.onNodeWithTag("day-2026-01-04").performClick()
        rule.runOnIdle { assertEquals(LocalDate.of(2026, 1, 4), selected.value) }
        rule.onNodeWithTag("day-2026-01-04").assertIsSelected()
    }
    @Test fun todayAvatarReplacesNumberRatherThanAddingAnotherRow() {
        val today = LocalDate.of(2026, 1, 1); val entries = holidays(2026)
        rule.setContent { LukeTheme(AppPreferences(reduceMotion = true)) {
            NativeCalendar(YearMonth.from(today), today, today, entries, emptyMap(), {}, {})
        } }
        rule.onNodeWithTag("today-avatar", true).assertExists()
        rule.onNodeWithTag("day-number-2026-01-01", true).assertDoesNotExist()
        val avatar = rule.onNodeWithTag("today-avatar", true).fetchSemanticsNode().boundsInRoot
        val badge = rule.onNodeWithTag("holiday-2026-01-01", true).fetchSemanticsNode().boundsInRoot
        assertTrue("Holiday badge overlaps today avatar", badge.bottom <= avatar.top)
    }
    @Test fun dayNumbersAndHolidayBadgesDoNotIntersectAtLargeFonts() {
        val global = mutableStateOf(.95f); val system = mutableStateOf(1f)
        val width = mutableStateOf(300); val entries = holidays(2026)
        rule.setContent {
            val original = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(original.density, system.value)) {
                LukeTheme(AppPreferences(scale = global.value, reduceMotion = true)) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        NativeCalendar(YearMonth.of(2026, 1), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), entries,
                            emptyMap(), {}, {}, Modifier.width(width.value.dp))
                    }
                }
            }
        }
        for (w in listOf(260, 280, 300)) for (g in listOf(.75f, .95f, 1.6f)) for (s in listOf(1f, 1.5f, 2f)) {
            rule.runOnIdle { width.value = w; global.value = g; system.value = s }
            rule.waitForIdle()
            val number = rule.onNodeWithTag("day-number-2026-01-04", true).fetchSemanticsNode().boundsInRoot
            val badge = rule.onNodeWithTag("holiday-2026-01-04", true).fetchSemanticsNode().boundsInRoot
            val cell = rule.onNodeWithTag("day-2026-01-04").fetchSemanticsNode().boundsInRoot
            assertTrue("Badge collision: width=$w global=$g system=$s", badge.bottom <= number.top + 1f)
            assertTrue("Number exceeds cell", number.left >= cell.left && number.right <= cell.right)
        }
    }
    @Test fun unavailableYearDoesNotReceiveInventedRestOrWorkBadges() {
        rule.setContent { LukeTheme(AppPreferences(reduceMotion = true)) {
            NativeCalendar(YearMonth.of(2030, 10), LocalDate.of(2030, 10, 1), LocalDate.of(2026, 1, 1), emptyMap(), emptyMap(), {}, {})
        } }
        rule.onNodeWithTag("holiday-2030-10-01", true).assertDoesNotExist()
        rule.onNodeWithTag("day-number-2030-10-01", true).assertExists()
    }
    @Test fun navigationAtLimitsDoesNotProduceOutOfRangeYears() {
        rule.setContent { LukeTheme(AppPreferences(reduceMotion = true)) {
            NativeCalendar(YearMonth.of(1900, 1), LocalDate.of(1900, 1, 1), LocalDate.of(2026, 1, 1), emptyMap(), emptyMap(), {}, {})
        } }
        rule.onNodeWithContentDescription("上个月").assertIsNotEnabled()
        rule.onNodeWithContentDescription("下个月").assertIsEnabled()
    }
}
