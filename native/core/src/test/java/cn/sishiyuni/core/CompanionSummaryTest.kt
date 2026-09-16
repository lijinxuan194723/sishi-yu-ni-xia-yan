package cn.sishiyuni.core

import cn.sishiyuni.core.data.*
import cn.sishiyuni.core.model.*
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CompanionSummaryTest {
    @Test fun firstDayAndFutureDatesKeepLegacyInclusiveCount() {
        val today = LocalDate.parse("2026-09-16")
        assertEquals(1L, togetherDays(today, today))
        assertEquals(2L, togetherDays(today.minusDays(1), today))
        assertEquals(1L, togetherDays(today.plusDays(1), today))
    }
    @Test fun leapYearsDoNotLoseADay() {
        assertEquals(3L, togetherDays(LocalDate.parse("2024-02-28"), LocalDate.parse("2024-03-01")))
    }
    @Test fun recentWeekUsesLocalDateBoundariesNotAllTimeTotals() {
        val today = LocalDate.parse("2026-09-16")
        val zone = ZoneId.of("Asia/Shanghai")
        val start = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val notes = listOf(
            MemoEntity("old", "", "", start - 1, start),
            MemoEntity("new", "", "", start, start),
            MemoEntity("future", "", "", end, end),
            MemoEntity("trash", "", "", start + 1, start + 1, deletedAt = end - 1),
        )
        val result = companionWeek(today, zone,
            listOf(RecordEntity("check", "2026-09-10", "{}"), RecordEntity("check", "2026-09-16", "{}"),
                RecordEntity("check", "2026-09-09", "{}"), RecordEntity("check", "invalid", "{}")),
            listOf(PlanEntity("a", "2026-09-10", "one", done = true), PlanEntity("b", "2026-09-16", "two", done = false),
                PlanEntity("c", "2026-09-09", "old", done = true)), notes,
            listOf(FocusLogEntity("a", start, 10.0), FocusLogEntity("b", end - 1, 2.5),
                FocusLogEntity("c", start - 1, 400.0), FocusLogEntity("d", end, 500.0)),
        )
        assertEquals(2, result.checkDays); assertEquals(1, result.completedPlans)
        assertEquals(1, result.notes); assertEquals(12.5, result.minutes, .0001)
    }
}
