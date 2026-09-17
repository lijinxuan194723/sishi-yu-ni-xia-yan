package cn.sishiyuni.core

import cn.sishiyuni.core.data.TimerEntity
import cn.sishiyuni.core.model.*
import cn.sishiyuni.core.timer.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class StudyLedgerTest {
    private val clock=object:TimeSource {override fun wall()=1700000000000L;override fun elapsed()=1000L;override fun boot()=1}
    private fun timer(raw:String,remaining:Long=0,running:Boolean=false)=TimerEntity(id="study",kind="study",remainingMs=remaining,
        running=running,generation="study",wallDeadline=clock.wall(),elapsedDeadline=clock.elapsed(),bootCount=1,raw=raw)
    @Test fun wrongArrayTypeIsRejectedInBothRunningAndPausedStates() {
        for(running in listOf(true,false)) assertTrue(runCatching {TimerTransitions.pauseStudy(timer("{\"segments\":\"not-an-array\"}",running=running),clock)}.isFailure)
    }
    @Test fun missingOrNonNumericIntervalFieldsDoNotBecomeZeroLength() {
        for(row in listOf("{}","{\"from\":100}","{\"from\":100,\"millis\":\"abc\"}"))
            assertTrue(runCatching {TimerTransitions.pauseStudy(timer("{\"segments\":[$row]}"),clock)}.isFailure)
    }
    @Test fun cumulativeDurationMustMatchItsIntervals() {
        assertTrue(runCatching {TimerTransitions.pauseStudy(timer("{}",remaining=9000),clock)}.isFailure)
        assertTrue(runCatching {TimerTransitions.pauseStudy(timer("{\"segments\":[{\"from\":100,\"millis\":9000}]}"),clock)}.isFailure)
    }
    @Test fun negativeAndOverflowingIntervalTimesAreRejected() {
        for(row in listOf("{\"from\":-1,\"millis\":0}","{\"from\":1,\"millis\":-1}","{\"from\":9223372036854775807,\"millis\":1}"))
            assertTrue(runCatching {TimerTransitions.pauseStudy(timer("{\"segments\":[$row]}"),clock)}.isFailure)
    }
    @Test fun validIntervalsAndUnknownFieldsSurviveIdempotentPause() {
        val saved=timer("{\"segments\":[{\"from\":100,\"millis\":9000,\"future\":true}],\"note\":\"keep\"}",remaining=9000)
        val result=TimerTransitions.pauseStudy(saved,clock)
        assertSame(saved,result);assertEquals("keep",obj(result.raw).str("note"))
        assertTrue(obj(result.raw).arr("segments").single().jsonObject.flag("future"))
    }
}
