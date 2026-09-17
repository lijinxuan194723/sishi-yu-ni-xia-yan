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

class StudyLedgerPersistenceTest {
    private lateinit var db:LukeDatabase
    private lateinit var repository:TimerRepository
    private val clock=object:TimeSource {override fun wall()=1700000000000L;override fun elapsed()=1000L;override fun boot()=1}
    @Before fun setup() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        db=Room.inMemoryDatabaseBuilder(context,LukeDatabase::class.java).build()
        val alarms=object:TimerAlarms {
            override fun exactAllowed()=false;override fun schedule(timer:TimerEntity) {};override fun cancel(timer:TimerEntity) {}
            override fun dismiss(id:String) {};override fun notifyFinished(timer:TimerEntity) {}
        }
        repository=TimerRepository(context,db,clock,alarms)
    }
    @After fun close() {db.close()}
    @Test fun finishingCorruptPausedDataDoesNotConsumeTheRecord()=runBlocking {
        val old=TimerEntity(id="study",kind="study",generation="keep",remainingMs=60000,raw="{\"segments\":false}")
        db.dao().putTimer(old)
        assertTrue(runCatching {repository.finishStudy()}.isFailure)
        assertEquals(old,db.dao().timer("study"));assertTrue(db.dao().allFocusLogs().isEmpty())
    }
    @Test fun pausingCorruptRunningDataPreservesItForRecovery()=runBlocking {
        val old=TimerEntity(id="study",kind="study",generation="keep",running=true,bootCount=1,
            elapsedDeadline=clock.elapsed(),wallDeadline=clock.wall(),raw="{\"segments\":[{}]}")
        db.dao().putTimer(old)
        assertTrue(runCatching {repository.pause("study")}.isFailure)
        assertEquals(old,db.dao().timer("study"));assertTrue(db.dao().allFocusLogs().isEmpty())
    }
    @Test fun finishingMismatchedTotalRollsBackInsteadOfLoggingOnlyTheReadablePart()=runBlocking {
        val old=TimerEntity(id="study",kind="study",generation="keep",remainingMs=120000,
            raw="{\"segments\":[{\"from\":1700000000000,\"millis\":60000}]}")
        db.dao().putTimer(old)
        assertTrue(runCatching {repository.finishStudy()}.isFailure)
        assertEquals(old,db.dao().timer("study"));assertTrue(db.dao().allFocusLogs().isEmpty())
    }
}
