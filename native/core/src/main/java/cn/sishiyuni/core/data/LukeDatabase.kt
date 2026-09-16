package cn.sishiyuni.core.data

import android.content.Context
import androidx.room.*

@Database(entities=[SessionEntity::class,MessageEntity::class,PlanEntity::class,FolderEntity::class,MemoEntity::class,FactEntity::class,ChapterEntity::class,SubjectEntity::class,FocusLogEntity::class,TimerEntity::class,SkillEntity::class,RecordEntity::class,ImportEntity::class],version=1,exportSchema=true)
abstract class LukeDatabase:RoomDatabase(){
 abstract fun dao():LukeDao
 companion object { fun open(context:Context):LukeDatabase=Room.databaseBuilder(context.applicationContext,LukeDatabase::class.java,"four-seasons-native.db").setJournalMode(JournalMode.WRITE_AHEAD_LOGGING).build() }
}
