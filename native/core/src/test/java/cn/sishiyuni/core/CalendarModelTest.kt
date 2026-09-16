package cn.sishiyuni.core

import cn.sishiyuni.core.plans.CalendarModel
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarModelTest {
    @Test fun leapDayIsStrictlyValidated() {
        assertNotNull(CalendarModel.parse("2024-02-29")); assertNull(CalendarModel.parse("2026-02-29"))
        assertNull(CalendarModel.parse("2026-2-01")); assertNull(CalendarModel.parse("2101-01-01"))
    }
    @Test fun monthHasSixCompleteMondayFirstRows() {
        for (year in listOf(1900, 2024, 2025, 2026, 2100)) for (month in 1..12) {
            val target = YearMonth.of(year, month); val cells = CalendarModel.cells(target)
            assertEquals(42, cells.size); assertEquals(42, cells.distinct().size)
            assertEquals(1, cells.first().dayOfWeek.value)
            assertEquals(target.lengthOfMonth(), cells.count { YearMonth.from(it) == target })
            cells.zipWithNext().forEach { (a, b) -> assertEquals(a.plusDays(1), b) }
        }
    }
    @Test fun monthNavigationStopsAtBothBounds() {
        assertEquals(YearMonth.of(1900, 1), CalendarModel.shift(YearMonth.of(1900, 1), -1))
        assertEquals(YearMonth.of(2100, 12), CalendarModel.shift(YearMonth.of(2100, 12), 1))
    }
    @Test fun decemberIncludesNextYearWithoutChangingDateStrings() {
        val values = CalendarModel.cells(YearMonth.of(2026, 12))
        assertTrue(LocalDate.of(2027, 1, 1) in values); assertEquals("2027-01-01", values.first { it.year == 2027 }.toString())
    }
    @Test fun anniversaryClampsLeapBirthdayInNonLeapYear() {
        assertEquals(LocalDate.of(2025, 2, 28), CalendarModel.annual(LocalDate.of(2024, 2, 29), 2025))
        assertEquals(LocalDate.of(2028, 2, 29), CalendarModel.annual(LocalDate.of(2024, 2, 29), 2028))
    }
}
