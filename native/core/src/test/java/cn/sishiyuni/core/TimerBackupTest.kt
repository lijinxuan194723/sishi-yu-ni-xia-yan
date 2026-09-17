package cn.sishiyuni.core

import cn.sishiyuni.core.backup.TimerBackup
import cn.sishiyuni.core.data.TimerEntity
import cn.sishiyuni.core.model.*
import cn.sishiyuni.core.timer.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TimerBackupTest {
    private val clock = object : TimeSource {
        var w = 1700000000000L; var e = 1000L
        override fun wall() = w; override fun elapsed() = e; override fun boot() = 1
        fun advance(ms: Long) {w += ms; e += ms}
    }
    @Test fun liveStudyExportIncludesCurrentIntervalWithoutPausingTheActualTimer() {
        val live=TimerTransitions.startStudy(null,"阅读",clock,"live")
        clock.advance(45000)
        val exported=TimerBackup.encode(live,clock)
        assertTrue(live.running); assertEquals("{}",live.raw)
        val restored=TimerBackup.decode(exported)
        assertFalse(restored.running); assertEquals(45000L,restored.remainingMs)
        assertEquals(45000L,obj(restored.raw).arr("segments").single().jsonObject.num("millis"))
        assertNotEquals(live.generation,restored.generation)
    }
    @Test fun pausedStudyAndItsEarlierSegmentsRoundTripUnchanged() {
        val live=TimerTransitions.startStudy(null,"阅读",clock,"live"); clock.advance(60000)
        val paused=TimerTransitions.pauseStudy(live,clock); clock.advance(10*60000)
        val restored=TimerBackup.decode(TimerBackup.encode(paused,clock))
        assertEquals(paused.remainingMs,restored.remainingMs); assertEquals(paused.raw,restored.raw)
    }
    @Test fun repeatedExportsDoNotDuplicateTheLiveSegment() {
        val live=TimerTransitions.startStudy(null,"阅读",clock,"live"); clock.advance(1000)
        TimerBackup.encode(live,clock); clock.advance(1000)
        val restored=TimerBackup.decode(TimerBackup.encode(live,clock))
        assertEquals(2000L,restored.remainingMs); assertEquals(1,obj(restored.raw).arr("segments").size)
    }
    @Test fun idleTimersStayIdleInsteadOfReceivingPhantomActivityIds() {
        for (timer in listOf(TimerEntity(),TimerEntity(id="study",kind="study"),
            TimerEntity(id=Pomodoro.ID,kind=Pomodoro.ID,durationMs=60000,remainingMs=60000))) {
            val restored=TimerBackup.decode(TimerBackup.encode(timer,clock))
            assertEquals("",restored.generation); assertFalse(restored.running)
        }
    }
    @Test fun countdownSnapshotKeepsRemainingTimeNotOriginalDuration() {
        val live=TimerEntity(durationMs=60000,remainingMs=60000,running=true,elapsedDeadline=clock.e+60000,
            wallDeadline=clock.w+60000,bootCount=1,generation="run")
        clock.advance(12345)
        val restored=TimerBackup.decode(TimerBackup.encode(live,clock))
        assertEquals(47655L,restored.remainingMs); assertFalse(restored.running)
    }
    @Test fun manualRestPhaseRoundTripsWithoutStartingOrLosingRoundCount() {
        var timer=Pomodoro.start(Pomodoro.ready(PomodoroConfig(),"阅读","语文","work"),clock)
        clock.advance(timer.durationMs)
        timer=requireNotNull(Pomodoro.complete(timer,clock,"rest")).next
        val restored=TimerBackup.decode(TimerBackup.encode(timer,clock))
        assertEquals(Pomodoro.state(timer),Pomodoro.state(restored)); assertFalse(restored.running)
    }
    @Test fun missingCurrentStudyIntervalIsRejectedBeforeImport() {
        val broken=buildJsonObject {
            put("id","study");put("kind","study");put("label","阅读");put("durationMs",0)
            put("remainingMs",60000);put("running",true);put("completed",false);put("generation","old")
            put("startedWall",clock.w);put("raw","{}")
        }
        assertTrue(runCatching {TimerBackup.decode(broken)}.isFailure)
    }
    @Test fun corruptMetadataIsNotSilentlyDroppedOnExport() {
        val broken=TimerEntity(id="study",kind="study",raw="{\"segments\":\"wrong\"}")
        assertTrue(runCatching {TimerBackup.encode(broken,clock)}.isFailure)
    }
    @Test fun impossibleRemainingTimeCannotBeImported() {
        val raw=TimerBackup.encode(TimerEntity(),clock).change("remainingMs" to JsonPrimitive(1000))
        assertTrue(runCatching {TimerBackup.decode(raw)}.isFailure)
    }
}
