package cn.sishiyuni.core.model

import cn.sishiyuni.core.data.FocusLogEntity
import cn.sishiyuni.core.data.MemoEntity
import cn.sishiyuni.core.data.PlanEntity
import cn.sishiyuni.core.data.RecordEntity
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Legacy togetherDays counted the first day as day one, independent of DST and current hour. */
fun togetherDays(since: LocalDate, today: LocalDate): Long =
    (ChronoUnit.DAYS.between(since, today) + 1L).coerceAtLeast(1L)

data class CompanionWeek(val checkDays: Int, val completedPlans: Int, val notes: Int, val minutes: Double)

fun companionWeek(
    today: LocalDate,
    zone: ZoneId,
    checks: List<RecordEntity>,
    plans: List<PlanEntity>,
    memos: List<MemoEntity>,
    logs: List<FocusLogEntity>,
): CompanionWeek {
    val first = today.minusDays(6)
    val start = first.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    fun inWeek(at: Long) = at >= start && at < end
    return CompanionWeek(
        checks.asSequence().mapNotNull { runCatching { LocalDate.parse(it.id) }.getOrNull() }
            .filter { it >= first && it <= today }.distinct().count(),
        plans.count { it.done && it.date in first.toString()..today.toString() },
        memos.count { it.deletedAt == null && inWeek(it.createdAt) },
        logs.asSequence().filter { inWeek(it.at) && it.minutes.isFinite() && it.minutes >= 0 }
            .sumOf { it.minutes },
    )
}
