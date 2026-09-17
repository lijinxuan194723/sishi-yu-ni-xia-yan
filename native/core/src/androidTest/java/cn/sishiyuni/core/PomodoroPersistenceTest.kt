package cn.sishiyuni.core

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cn.sishiyuni.core.data.*
import cn.sishiyuni.core.timer.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class PomodoroPersistenceTest {
    private lateinit var context: Context
    private lateinit var db: LukeDatabase
    private lateinit var file: String
    private val clock = Clock()
    private val alarms = Signals()
    private fun repo() = TimerRepository(context, db, clock, alarms)
    private class Clock : TimeSource {
        var w = 1_700_000_000_000L; var e = 1000L; var b = 1
        override fun wall() = w
        override fun elapsed() = e
        override fun boot() = b
        fun advance(ms: Long) { w += ms; e += ms }
    }
    private class Signals : TimerAlarms {
        val shown = mutableListOf<TimerEntity>()
        var schedules = 0; var broken = false
        override fun exactAllowed() = false
        override fun schedule(timer: TimerEntity) { if(broken) throw SecurityException("test denied"); schedules++ }
        override fun cancel(timer: TimerEntity) { }
        override fun dismiss(id: String) { }
        override fun notifyFinished(timer: TimerEntity) { shown += timer }
    }
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        file = "pomodoro-test-${UUID.randomUUID()}.db"
        db = Room.databaseBuilder(context, LukeDatabase::class.java, file).build()
    }
    @After fun close() { db.close(); context.deleteDatabase(file) }
    private suspend fun timer() = requireNotNull(db.dao().timer(Pomodoro.ID))
    private suspend fun begin(repository: TimerRepository = repo()) {
        repository.startPomodoro(PomodoroConfig(1,1,2,2),"阅读","语文")
    }
    @Test fun alarmAndForegroundRaceCreatesOneLogAndOneNextPhase() = runBlocking {
        val repository=repo(); begin(repository); val first=timer(); clock.advance(first.durationMs)
        coroutineScope { List(12) { async(Dispatchers.Default) { repository.fire(first.id,first.generation) } }.awaitAll() }
        assertEquals(1,db.dao().allFocusLogs().size)
        assertEquals(1,alarms.shown.size)
        assertFalse(timer().running); assertEquals("short",Pomodoro.state(timer()).phase)
        assertNotEquals(first.generation,timer().generation)
    }
    @Test fun pauseAtExactDeadlineAdvancesRatherThanStrandingZeroSeconds() = runBlocking {
        val repository=repo(); begin(repository); clock.advance(timer().durationMs)
        repository.pause(Pomodoro.ID)
        assertEquals(1,db.dao().allFocusLogs().size)
        assertEquals("short",Pomodoro.state(timer()).phase)
        assertTrue(timer().remainingMs>0); assertFalse(timer().running)
    }
    @Test fun pauseReopenAndResumeDoesNotConsumePausedTime() = runBlocking {
        val repository=repo(); begin(repository); clock.advance(10000); repository.pause(Pomodoro.ID)
        val remaining=timer().remainingMs; val generation=timer().generation
        db.close(); clock.advance(600000)
        db=Room.databaseBuilder(context,LukeDatabase::class.java,file).build()
        val restored=repo(); restored.restore()
        assertFalse(timer().running); assertEquals(remaining,timer().remainingMs)
        restored.resume(Pomodoro.ID)
        assertEquals(remaining,TimerMath.remaining(timer(),clock)); assertEquals(generation,timer().generation)
    }
    @Test fun overdueReopenCompletesOnlyOnePhaseAndDoesNotAutostartRest() = runBlocking {
        begin(); db.close(); clock.advance(5*3600000L); clock.b++; clock.e=1000
        db=Room.databaseBuilder(context,LukeDatabase::class.java,file).build()
        repo().restore()
        assertEquals(1,db.dao().allFocusLogs().size)
        assertFalse(timer().running); assertEquals("short",Pomodoro.state(timer()).phase)
    }
    @Test fun restFinishingNeverInflatesFocusStatistics() = runBlocking {
        val repository=repo(); begin(repository); clock.advance(timer().durationMs); repository.checkForeground()
        repository.resume(Pomodoro.ID); clock.advance(timer().durationMs); repository.checkForeground()
        assertEquals(1,db.dao().allFocusLogs().size)
        assertEquals("work",Pomodoro.state(timer()).phase)
        assertFalse(timer().running)
    }
    @Test fun confirmedCancelKeepsCompletedLogsAndIgnoresLateCallback() = runBlocking {
        val repository=repo(); begin(repository); clock.advance(timer().durationMs); repository.checkForeground()
        repository.resume(Pomodoro.ID); val old=timer(); repository.cancelPomodoro(true)
        clock.advance(old.durationMs); repository.fire(old.id,old.generation)
        assertFalse(Pomodoro.isActive(timer())); assertEquals(1,db.dao().allFocusLogs().size)
        assertEquals(1,alarms.shown.size)
    }
    @Test fun unconfirmedCancelAndGenericResetCannotDiscardLiveCycle() = runBlocking {
        val repository=repo(); begin(repository); val old=timer()
        try { repository.cancelPomodoro(false); fail("confirmation required") } catch (_:IllegalStateException) { }
        try { repository.reset(Pomodoro.ID); fail("generic reset must not discard cycle") } catch (_:IllegalStateException) { }
        assertEquals(old,timer())
    }
    @Test fun duplicateStartDoesNotRestartOrLoseElapsedTime() = runBlocking {
        val repository=repo(); begin(repository); val old=timer(); clock.advance(12000); begin(repository)
        assertEquals(old,timer()); assertEquals(48000L,TimerMath.remaining(timer(),clock))
    }
    @Test fun deniedAlarmDoesNotDiscardClockOrStatistics() = runBlocking {
        val repository=repo(); alarms.broken=true; begin(repository)
        assertTrue(timer().running); assertNotNull(repository.reminderWarning.value)
        alarms.broken=false; repository.restore()
        assertNull(repository.reminderWarning.value)
        clock.advance(timer().durationMs); repository.checkForeground()
        assertEquals(1,db.dao().allFocusLogs().size)
    }
    @Test fun countdownAndPomodoroRemainIndependent() = runBlocking {
        val repository=repo(); begin(repository); repository.startCountdown(2)
        clock.advance(60000L); repository.checkForeground()
        assertEquals("short",Pomodoro.state(timer()).phase)
        assertTrue(requireNotNull(db.dao().timer("countdown")).running)
        assertEquals(1,db.dao().allFocusLogs().size)
    }
}
