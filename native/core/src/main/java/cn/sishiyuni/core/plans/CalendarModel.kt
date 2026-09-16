package cn.sishiyuni.core.plans

import java.time.LocalDate
import java.time.YearMonth

object CalendarModel {
    val first: LocalDate = LocalDate.of(1900, 1, 1)
    val last: LocalDate = LocalDate.of(2100, 12, 31)
    fun parse(value: String): LocalDate? {
        if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(value)) return null
        return runCatching { LocalDate.parse(value) }.getOrNull()?.takeIf { it in first..last }
    }
    fun cells(month: YearMonth): List<LocalDate> {
        require(month.year in 1900..2100)
        val start = month.atDay(1).minusDays((month.atDay(1).dayOfWeek.value - 1).toLong())
        return List(42) { start.plusDays(it.toLong()) }
    }
    fun shift(month: YearMonth, offset: Int): YearMonth = month.plusMonths(offset.toLong())
        .coerceIn(YearMonth.from(first), YearMonth.from(last))
    fun annual(source: LocalDate, year: Int): LocalDate {
        val month = YearMonth.of(year, source.month)
        return month.atDay(minOf(source.dayOfMonth, month.lengthOfMonth()))
    }
}
