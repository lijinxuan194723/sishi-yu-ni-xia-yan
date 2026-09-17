package cn.sishiyuni.core

import cn.sishiyuni.core.data.TimerEntity
import cn.sishiyuni.core.timer.*
import org.junit.Assert.*
import org.junit.Test

class PomodoroTest {
    private class Clock : TimeSource {
        var w = 1_700_000_000_000L; var e = 1000L; var b = 1
        override fun wall() = w
        override fun elapsed() = e
        override fun boot() = b
        fun advance(ms: Long) { w += ms; e += ms }
    }
    private fun ready(c: PomodoroConfig = PomodoroConfig()) = Pomodoro.ready(c, "读一章", "阅读", "phase-0")
    private inline fun rejected(block: () -> Unit) {
        try { block(); fail("Expected validation failure") } catch (_: IllegalArgumentException) { }
    }
    @Test fun fourRoundsKeepBreaksManualAndOnlyCountWork() {
        val clock = Clock(); var timer = ready(); var total = 0.0; var logs = 0
        for (round in 1..4) {
            timer = Pomodoro.start(timer, clock)
            clock.advance(timer.durationMs)
            val result = requireNotNull(Pomodoro.complete(timer, clock, "break-$round"))
            total += requireNotNull(result.log).minutes; logs++
            timer = result.next
            assertFalse(timer.running); assertEquals(0L, timer.startedWall)
            assertEquals(if(round == 4) "long" else "short", Pomodoro.state(timer).phase)
            assertEquals(round, Pomodoro.state(timer).completed)
            assertNull(Pomodoro.complete(timer, clock, "unused"))
            timer = Pomodoro.start(timer, clock); clock.advance(timer.durationMs)
            val rest = requireNotNull(Pomodoro.complete(timer, clock, "work-$round"))
            assertNull(rest.log); timer = rest.next
            assertEquals("work", Pomodoro.state(timer).phase)
            assertEquals(if(round == 4) 0 else round, Pomodoro.state(timer).completed)
            assertFalse(timer.running)
        }
        assertEquals(4, logs); assertEquals(100.0, total, .0001)
    }
    @Test fun customRoundOneGoesDirectlyToLongRest() {
        val clock = Clock(); val timer = Pomodoro.start(ready(PomodoroConfig(40,8,20,1)), clock)
        clock.advance(timer.durationMs)
        val result = requireNotNull(Pomodoro.complete(timer, clock, "next"))
        assertEquals(40.0, result.log!!.minutes, .0001)
        assertEquals(20 * 60000L, result.next.durationMs)
        assertEquals("long", Pomodoro.state(result.next).phase)
    }
    @Test fun repeatedStartPreservesDeadlineAndGeneration() {
        val clock = Clock(); val timer = Pomodoro.start(ready(), clock); clock.advance(9000)
        assertSame(timer, Pomodoro.start(timer, clock))
    }
    @Test fun earlyCallbackDoesNotAdvanceOrLog() {
        val clock = Clock(); val timer = Pomodoro.start(ready(), clock); clock.advance(timer.durationMs - 1)
        assertNull(Pomodoro.complete(timer, clock, "next"))
    }
    @Test fun pausedPhaseNeverFinishesFromElapsedWallTime() {
        val clock = Clock(); val running = Pomodoro.start(ready(), clock); clock.advance(4000)
        val paused = running.copy(running=false, remainingMs=TimerMath.remaining(running,clock))
        clock.advance(10 * running.durationMs)
        assertNull(Pomodoro.complete(paused, clock, "next"))
        val resumed = Pomodoro.start(paused, clock)
        assertEquals(paused.remainingMs, TimerMath.remaining(resumed, clock))
        assertEquals(running.generation, resumed.generation)
    }
    @Test fun delayedAlarmDoesNotCountTimeInUnstartedBreaks() {
        val clock = Clock(); val timer = Pomodoro.start(ready(), clock); val deadline = timer.wallDeadline
        clock.advance(10 * timer.durationMs)
        val result = requireNotNull(Pomodoro.complete(timer, clock, "next"))
        assertEquals(deadline, result.log!!.at)
        assertEquals(5 * 60000L, result.next.remainingMs)
        assertEquals(1, Pomodoro.state(result.next).completed)
    }
    @Test fun wallClockChangeDoesNotEndSameBootTimerEarly() {
        val clock = Clock(); val timer = Pomodoro.start(ready(), clock)
        clock.w += 24 * 3600000L
        assertEquals(timer.durationMs, TimerMath.remaining(timer, clock))
        assertNull(Pomodoro.complete(timer, clock, "next"))
    }
    @Test fun rebootUsesStoredWallDeadline() {
        val clock = Clock(); val timer = Pomodoro.start(ready(), clock)
        clock.b++; clock.e = 0; clock.w += timer.durationMs
        assertNotNull(Pomodoro.complete(timer, clock, "next"))
    }
    @Test fun finishedPhaseNeedsFreshGeneration() {
        val clock = Clock(); val timer = Pomodoro.start(ready(),clock); clock.advance(timer.durationMs)
        rejected { Pomodoro.complete(timer,clock,timer.generation) }
        rejected { Pomodoro.complete(timer,clock,"") }
    }
    @Test fun configurationBoundsRejectInvalidOrExtremeValues() {
        listOf(PomodoroConfig(work=0),PomodoroConfig(work=181),PomodoroConfig(short=0),PomodoroConfig(short=61),
            PomodoroConfig(long=0),PomodoroConfig(long=121),PomodoroConfig(rounds=0),PomodoroConfig(rounds=13),PomodoroConfig(work=Int.MAX_VALUE))
            .forEach { rejected { it.validate() } }
    }
    @Test fun inconsistentPhaseCountsAreRejected() {
        listOf(PomodoroState(phase="other"),PomodoroState(phase="short",completed=0),PomodoroState(phase="long",completed=3),
            PomodoroState(phase="work",completed=4),PomodoroState(phase="short",completed=4)).forEach { rejected { it.validate() } }
    }
    @Test fun metadataRoundTripPreservesAllTimingParameters() {
        val timer = ready(PomodoroConfig(32,7,18,5))
        val state = Pomodoro.state(timer)
        assertEquals(PomodoroConfig(32,7,18,5),state.config)
        assertEquals("阅读",state.group); assertEquals("读一章",timer.label)
    }
    @Test fun mismatchedDurationIsNotSilentlyReset() {
        rejected { Pomodoro.state(ready().copy(durationMs=60000L)) }
        rejected { Pomodoro.state(ready().copy(remainingMs=-1L)) }
    }
    @Test fun idleAndDiscardedTimersAreNotActive() {
        assertFalse(Pomodoro.isActive(null)); assertFalse(Pomodoro.isActive(TimerEntity()))
        assertFalse(Pomodoro.isActive(ready().copy(generation="")))
        assertTrue(Pomodoro.isActive(ready()))
    }
}
