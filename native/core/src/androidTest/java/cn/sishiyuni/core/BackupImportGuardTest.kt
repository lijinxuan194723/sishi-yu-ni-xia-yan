package cn.sishiyuni.core

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import cn.sishiyuni.core.backup.*
import cn.sishiyuni.core.data.*
import cn.sishiyuni.core.timer.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BackupImportGuardTest {
    private lateinit var db: LukeDatabase
    private lateinit var context: Context
    @Before fun open() {
        context=ApplicationProvider.getApplicationContext()
        db=Room.inMemoryDatabaseBuilder(context,LukeDatabase::class.java).build()
    }
    @After fun close() {db.close()}
    private suspend fun assertRefused() {
        try {db.withTransaction {BackupImportGuard.requireEmpty(db.dao())};fail("Existing user data must not be overwritten")}
        catch (_:IllegalStateException) {}
    }
    @Test fun freshBootstrapAndKnownCachesAllowImport()=runBlocking {
        db.dao().putSession(SessionEntity("legacy"))
        for (kind in listOf("weather","holiday","holiday-source","holiday-attempt"))
            db.dao().putRecord(RecordEntity(kind,"test","{}"))
        db.withTransaction {BackupImportGuard.requireEmpty(db.dao())}
    }
    @Test fun evenOneUnsentDraftPreventsImport()=runBlocking {
        db.dao().putSession(SessionEntity("legacy",draft="还没说完的话"))
        assertRefused()
        assertEquals("还没说完的话",db.dao().session("legacy")!!.draft)
    }
    @Test fun aNamedEmptyConversationIsStillUserData()=runBlocking {
        db.dao().putSession(SessionEntity("legacy",title="我的对话"))
        assertRefused(); assertEquals("我的对话",db.dao().session("legacy")!!.title)
    }
    @Test fun aNewEmptyConversationIsNotMistakenForBootstrap()=runBlocking {
        db.dao().putSession(SessionEntity("new-session"))
        assertRefused(); assertNotNull(db.dao().session("new-session"))
    }
    @Test fun subjectsAloneProtectTheirExistingIdentifiers()=runBlocking {
        db.dao().putSubject(SubjectEntity("my-subject","阅读"))
        assertRefused(); assertEquals("my-subject",db.dao().allSubjects().single().id)
    }
    @Test fun emptyNotebookStillPreventsSilentReplacement()=runBlocking {
        db.dao().putFolder(FolderEntity("notebook","读书笔记"))
        assertRefused(); assertEquals("读书笔记",db.dao().allFolders().single().name)
    }
    @Test fun runningTimersMustNotBeOverwrittenBeforeAnyStudyLogExists()=runBlocking {
        val timer=Pomodoro.ready(PomodoroConfig(),"阅读","语文","keep-me").copy(running=true)
        db.dao().putTimer(timer)
        assertRefused(); assertEquals(timer,db.dao().timer(Pomodoro.ID))
    }
    @Test fun aSavedPresetIsUserDataNotDisposableCache()=runBlocking {
        val preset=PomodoroPreset(PomodoroConfig(40,8,20,3),"算法练习").encode()
        db.dao().putRecord(RecordEntity("timer-config",Pomodoro.ID,preset))
        assertRefused(); assertEquals(preset,db.dao().record("timer-config",Pomodoro.ID)!!.payload)
    }
    @Test fun unknownRecordKindsFailClosed()=runBlocking {
        db.dao().putRecord(RecordEntity("future-personal-data","my-record","keep"))
        assertRefused(); assertEquals("keep",db.dao().allRecords().single().payload)
    }
    @Test fun restoredStudyCanActuallyBeFinishedIntoOneAccurateRoomLog()=runBlocking {
        val clock=object:TimeSource {
            var w=1700000000000L;var e=1000L
            override fun wall()=w;override fun elapsed()=e;override fun boot()=1
        }
        val alarms=object:TimerAlarms {
            override fun exactAllowed()=false
            override fun schedule(timer:TimerEntity) {};override fun cancel(timer:TimerEntity) {}
            override fun dismiss(id:String) {};override fun notifyFinished(timer:TimerEntity) {}
        }
        val original=TimerTransitions.startStudy(null,"阅读",clock,"old-device")
        clock.w+=45000;clock.e+=45000
        val restored=TimerBackup.decode(TimerBackup.encode(original,clock))
        db.withTransaction {BackupImportGuard.requireEmpty(db.dao());db.dao().putTimer(restored)}
        val repository=TimerRepository(context,db,clock,alarms)
        repository.finishStudy();repository.finishStudy()
        val log=db.dao().allFocusLogs().single()
        assertEquals(.75,log.minutes,.000001)
        assertEquals("阅读",log.title)
        assertTrue(db.dao().timer("study")!!.completed)
    }
}
