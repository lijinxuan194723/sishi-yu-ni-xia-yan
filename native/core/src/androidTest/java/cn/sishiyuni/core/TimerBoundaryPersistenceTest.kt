package cn.sishiyuni.core

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cn.sishiyuni.core.data.*
import cn.sishiyuni.core.timer.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TimerBoundaryPersistenceTest {
    private lateinit var db: LukeDatabase
    private lateinit var repo: TimerRepository
    private val clock = object : TimeSource {
        var w=1700000000000L; var e=1000L; var b=1
        override fun wall()=w; override fun elapsed()=e; override fun boot()=b
    }
    private val alarms = object : TimerAlarms {
        var notifications=0
        override fun exactAllowed()=false
        override fun schedule(timer: TimerEntity) {}
        override fun cancel(timer: TimerEntity) {}
        override fun dismiss(id: String) {}
        override fun notifyFinished(timer: TimerEntity) {notifications++}
    }
    @Before fun setup() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        db=Room.inMemoryDatabaseBuilder(context,LukeDatabase::class.java).build()
        repo=TimerRepository(context,db,clock,alarms)
    }
    @After fun close() {db.close()}
    @Test fun overdueRebootPreservesOriginalCompletionDate()=runBlocking {
        repo.startPomodoro(PomodoroConfig(1,1,2,2),"阅读")
        val timer=requireNotNull(db.dao().timer(Pomodoro.ID))
        clock.w+=48*3600000L; clock.b++; clock.e=0
        repo.restore()
        assertEquals(timer.wallDeadline,db.dao().allFocusLogs().single().at)
        assertFalse(requireNotNull(db.dao().timer(Pomodoro.ID)).running)
    }
    @Test fun cancellationAtDeadlineKeepsTheCompletedWorkRecord()=runBlocking {
        repo.startPomodoro(PomodoroConfig(1,1,2,2),"阅读")
        clock.e+=60000; clock.w+=60000
        repo.cancelPomodoro(true)
        assertEquals(1,db.dao().allFocusLogs().size)
        assertFalse(Pomodoro.isActive(db.dao().timer(Pomodoro.ID)))
    }
    @Test fun phasePresetIsSavedWithoutStartingAnAlarm()=runBlocking {
        val preset=PomodoroPreset(PomodoroConfig(42,7,18,3),"算法练习","计算机")
        repo.savePomodoroPreset(preset)
        assertNull(db.dao().timer(Pomodoro.ID))
        assertEquals(preset,PomodoroPreset.decode(requireNotNull(db.dao().record("timer-config",Pomodoro.ID)).payload))
        assertEquals(0,alarms.notifications)
    }
    @Test fun changingPresetCannotAlterAnActiveOrPausedPhase()=runBlocking {
        repo.startPomodoro(PomodoroConfig(),"阅读")
        val old=requireNotNull(db.dao().timer(Pomodoro.ID))
        try {repo.savePomodoroPreset(PomodoroPreset(PomodoroConfig(50,10,20,2)));fail("active preset update must be rejected")}
        catch (_:IllegalStateException) {}
        assertEquals(old,db.dao().timer(Pomodoro.ID))
        repo.pause(Pomodoro.ID)
        try {repo.savePomodoroPreset(PomodoroPreset());fail("paused cycle must also be protected")}
        catch (_:IllegalStateException) {}
    }
    @Test fun duplicateStartWithDifferentSubjectIsRejectedRatherThanSilentlyRelabelled()=runBlocking {
        repo.startPomodoro(PomodoroConfig(),"阅读","语文")
        val old=db.dao().timer(Pomodoro.ID)
        try {repo.startPomodoro(PomodoroConfig(),"阅读","历史");fail("different subject requires ending the first cycle")}
        catch (_:IllegalStateException) {}
        assertEquals(old,db.dao().timer(Pomodoro.ID))
    }
}
