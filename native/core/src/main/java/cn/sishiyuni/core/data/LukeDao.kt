package cn.sishiyuni.core.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface LukeDao {
 @Query("SELECT * FROM sessions ORDER BY updatedAt DESC") fun sessions():Flow<List<SessionEntity>>
 @Query("SELECT * FROM sessions WHERE id=:id") suspend fun session(id:String):SessionEntity?
 @Query("SELECT * FROM sessions ORDER BY updatedAt DESC") suspend fun allSessions():List<SessionEntity>
 @Upsert suspend fun putSession(item:SessionEntity)
 @Query("UPDATE sessions SET draft=:draft WHERE id=:id") suspend fun draft(id:String,draft:String)
 @Query("UPDATE sessions SET title=:title WHERE id=:id") suspend fun renameSession(id:String,title:String):Int
 @Query("UPDATE sessions SET archived=:archived WHERE id=:id") suspend fun archiveSession(id:String,archived:Boolean):Int
 @Query("UPDATE sessions SET updatedAt=MAX(updatedAt,:at) WHERE id=:id") suspend fun touchSession(id:String,at:Long):Int
 @Query("SELECT * FROM messages WHERE sessionId=:session ORDER BY ordinal") fun messages(session:String):Flow<List<MessageEntity>>
 @Query("SELECT * FROM messages WHERE sessionId=:session ORDER BY ordinal") suspend fun messagesNow(session:String):List<MessageEntity>
 @Query("SELECT * FROM messages ORDER BY at,ordinal") suspend fun allMessages():List<MessageEntity>
 @Query("SELECT * FROM messages WHERE id=:id") suspend fun message(id:String):MessageEntity?
 @Query("SELECT COALESCE(MAX(ordinal),-1)+1 FROM messages WHERE sessionId=:session") suspend fun nextOrdinal(session:String):Long
 @Query("SELECT * FROM messages WHERE instr(text,:query)>0 ORDER BY at DESC LIMIT 100") suspend fun searchMessages(query:String):List<MessageEntity>
 @Query("SELECT * FROM messages WHERE favorite=1 ORDER BY at DESC") fun favoriteMessages():Flow<List<MessageEntity>>
 @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertMessage(item:MessageEntity)
 @Update(onConflict=OnConflictStrategy.ABORT) suspend fun replaceMessage(item:MessageEntity):Int
 /** The immutable identity includes its session and ordinal; a uniqueness conflict is never a successful no-op. */
 @Transaction suspend fun putMessage(item:MessageEntity) {
  val old=message(item.id)
  if(old==null)insertMessage(item)
  else {
   require(old.sessionId==item.sessionId&&old.ordinal==item.ordinal){"消息归属或顺序不能在更新时改变"}
   check(replaceMessage(item)==1){"消息已改变，请重新读取"}
  }
 }
 @Query("UPDATE messages SET text=:text,status=:status WHERE id=:id") suspend fun updateMessage(id:String,text:String,status:String)
 @Query("UPDATE messages SET favorite=NOT favorite WHERE id=:id") suspend fun starMessage(id:String)
 @Query("SELECT * FROM plans ORDER BY date,important DESC") fun plans():Flow<List<PlanEntity>>
 @Query("SELECT * FROM plans") suspend fun allPlans():List<PlanEntity>
 @Upsert suspend fun putPlan(item:PlanEntity)
 @Query("DELETE FROM plans WHERE id=:id") suspend fun deletePlan(id:String)
 @Query("SELECT * FROM folders ORDER BY createdAt") fun folders():Flow<List<FolderEntity>>
 @Query("SELECT * FROM folders") suspend fun allFolders():List<FolderEntity>
 @Upsert suspend fun putFolder(item:FolderEntity)
 @Query("DELETE FROM folders WHERE id=:id") suspend fun deleteFolder(id:String)
 @Query("UPDATE memos SET folderId=NULL,revision=revision+1 WHERE folderId=:id") suspend fun unfileMemos(id:String)
 @Query("SELECT * FROM memos ORDER BY pinnedAt DESC,updatedAt DESC") fun memos():Flow<List<MemoEntity>>
 @Query("SELECT * FROM memos") suspend fun allMemos():List<MemoEntity>
 @Query("SELECT * FROM memos WHERE id=:id") suspend fun memo(id:String):MemoEntity?
 @Upsert suspend fun putMemo(item:MemoEntity)
 @Query("UPDATE memos SET title=:title,body=:body,mood=:mood,updatedAt=:at,revision=revision+1 WHERE id=:id AND revision=:revision") suspend fun editMemo(id:String,title:String,body:String,mood:String,at:Long,revision:Long):Int
 @Query("DELETE FROM memos WHERE id=:id AND deletedAt IS NOT NULL") suspend fun purgeMemo(id:String)
 @Query("SELECT * FROM memory_facts ORDER BY locked DESC,updatedAt DESC") fun facts():Flow<List<FactEntity>>
 @Query("SELECT * FROM memory_facts") suspend fun allFacts():List<FactEntity>
 @Query("SELECT * FROM memory_facts WHERE key=:key") suspend fun fact(key:String):FactEntity?
 @Upsert suspend fun putFact(item:FactEntity)
 @Query("DELETE FROM memory_facts WHERE sourceMessageId=:messageId AND locked=0 AND blocked=0") suspend fun invalidateFacts(messageId:String)
 @Query("SELECT * FROM memory_chapters WHERE sessionId=:session ORDER BY toOrdinal DESC") suspend fun chapters(session:String):List<ChapterEntity>
 @Query("SELECT * FROM memory_chapters") suspend fun allChapters():List<ChapterEntity>
 @Upsert suspend fun putChapter(item:ChapterEntity)
 @Query("DELETE FROM memory_chapters WHERE sessionId=:session AND toOrdinal>=:ordinal") suspend fun invalidateChapters(session:String,ordinal:Long)
 @Query("SELECT * FROM subjects ORDER BY name") fun subjects():Flow<List<SubjectEntity>>
 @Query("SELECT * FROM subjects") suspend fun allSubjects():List<SubjectEntity>
 @Upsert suspend fun putSubject(item:SubjectEntity)
 @Query("SELECT * FROM focus_logs ORDER BY at DESC") fun focusLogs():Flow<List<FocusLogEntity>>
 @Query("SELECT * FROM focus_logs") suspend fun allFocusLogs():List<FocusLogEntity>
 @Upsert suspend fun putFocusLog(item:FocusLogEntity)
 @Query("DELETE FROM focus_logs WHERE id=:id") suspend fun deleteFocusLog(id:String)
 @Query("SELECT * FROM timers") fun timers():Flow<List<TimerEntity>>
 @Query("SELECT * FROM timers") suspend fun allTimers():List<TimerEntity>
 @Query("SELECT * FROM timers WHERE id=:id") suspend fun timer(id:String):TimerEntity?
 @Upsert suspend fun putTimer(item:TimerEntity)
 @Query("SELECT * FROM skills ORDER BY installedAt DESC") fun skills():Flow<List<SkillEntity>>
 @Query("SELECT * FROM skills") suspend fun allSkills():List<SkillEntity>
 @Query("SELECT * FROM skills WHERE id=:id") suspend fun skill(id:String):SkillEntity?
 @Upsert suspend fun putSkill(item:SkillEntity)
 @Query("DELETE FROM skills WHERE id=:id") suspend fun deleteSkill(id:String)
 @Query("UPDATE skills SET enabled=:enabled WHERE id=:id AND digest=:expectedDigest")
 suspend fun setSkillEnabledIfUnchanged(id:String,enabled:Boolean,expectedDigest:String):Int
 @Transaction suspend fun installReviewedSkill(item:SkillEntity,expectedDigest:String?):Boolean {
  val old=skill(item.id)
  if(old==null){
   if(expectedDigest!=null)return false
   putSkill(item.copy(enabled=false));return true
  }
  if(old.digest==item.digest)return true
  if(expectedDigest==null||old.digest!=expectedDigest)return false
  putSkill(item.copy(enabled=false,installedAt=old.installedAt));return true
 }
 @Query("SELECT * FROM records WHERE kind=:kind ORDER BY updatedAt DESC") fun records(kind:String):Flow<List<RecordEntity>>
 @Query("SELECT * FROM records WHERE kind=:kind") suspend fun recordsNow(kind:String):List<RecordEntity>
 @Query("SELECT * FROM records") suspend fun allRecords():List<RecordEntity>
 @Query("SELECT * FROM records WHERE kind=:kind AND id=:id") suspend fun record(kind:String,id:String):RecordEntity?
 @Upsert suspend fun putRecord(item:RecordEntity)
 @Query("DELETE FROM records WHERE kind=:kind AND id=:id") suspend fun deleteRecord(kind:String,id:String)
 @Query("SELECT * FROM imports") suspend fun allImports():List<ImportEntity>
 @Query("SELECT * FROM imports WHERE digest=:digest") suspend fun imported(digest:String):ImportEntity?
 @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun putImport(item:ImportEntity)
 @Query("SELECT COUNT(*) FROM messages") suspend fun messageCount():Int
 @Query("SELECT COUNT(*) FROM memos") suspend fun memoCount():Int
}
