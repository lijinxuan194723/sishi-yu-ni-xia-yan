package cn.sishiyuni.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import cn.sishiyuni.core.data.*
import cn.sishiyuni.core.model.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

/** The full source text is kept for rollback, not sent to the model. */
data class ImportPlan(
 val digest:String,val source:String,val preferences:JsonObject,
 val sessions:List<SessionEntity>,val messages:List<MessageEntity>,val plans:List<PlanEntity>,
 val memos:List<MemoEntity>,val folders:List<FolderEntity>,val facts:List<FactEntity>,
 val chapters:List<ChapterEntity>,val subjects:List<SubjectEntity>,val focusLogs:List<FocusLogEntity>,
 val records:List<RecordEntity>,val warnings:List<String>,val skills:List<SkillEntity> = emptyList(),
 val timers:List<TimerEntity> = emptyList(),val originals:List<ImportEntity> = emptyList()
){
 // Validate at inspection/construction time, before any Room transaction. Otherwise
 // a valid message import with invalid settings could leave initialization blocked.
 init { PreferenceRules.normalize(preferences) }
 val summary get()="${messages.size} 条消息 · ${plans.size} 项计划 · ${memos.size} 篇手记 · ${facts.size} 条记忆"
}

object BackupDecoder {
 const val MAX_BYTES=60_000_000
 fun decode(text:String):ImportPlan{require(text.toByteArray().size<=MAX_BYTES){"备份超过 60 MB"};val root=obj(text);return if(root.str("format")=="four-seasons-luke-native-backup")native(text,root)else legacy(text,root)}
 private fun unique(ids:List<String>){require(ids.all{it.isNotBlank()&&it.length<=200}&&ids.distinct().size==ids.size){"备份含重复或无效编号"}}
 private fun native(text:String,r:JsonObject):ImportPlan{
  require(r.num("version")==1L){"备份版本不受支持"}
  fun rows(key:String,max:Int=50000):List<JsonObject>{val a=r[key] as? JsonArray?:error("缺少 $key 数据");require(a.size<=max){"$key 数量超限"};return a.map{it as? JsonObject?:error("$key 格式错误")}}
  fun nullableLong(o:JsonObject,k:String)=(o[k] as? JsonPrimitive)?.longOrNull
  fun nullableText(o:JsonObject,k:String)=(o[k] as? JsonPrimitive)?.contentOrNull
  val sessions=rows("sessions",5000).map{s->SessionEntity(s.str("id"),bounded(s.str("title"),100,"对话标题"),s.num("createdAt"),s.num("updatedAt"),bounded(s.str("draft"),20000,"草稿"),s.flag("archived"))};unique(sessions.map{it.id});val sids=sessions.map{it.id}.toSet()
  val messages=rows("messages",100000).map{m->require(m.str("sessionId") in sids&&m.str("who") in setOf("me","luke")&&m.num("ordinal",-1)>=0){"消息会话或顺序无效"};val status=if(m.str("status")=="streaming")"interrupted" else m.str("status");require(status in setOf("complete","interrupted","error","cancelled"));MessageEntity(m.str("id"),m.str("sessionId"),m.num("ordinal"),m.str("who"),bounded(m.str("text"),20000,"消息"),m.num("at"),m.str("source"),m.flag("favorite"),status,m.flag("muted"),bounded(m.str("raw","{}"),800000,"附件"))};unique(messages.map{it.id});unique(messages.map{it.sessionId+"/"+it.ordinal})
  val folders=rows("folders",100).map{FolderEntity(it.str("id"),bounded(it.str("name"),30,"笔记本"),it.num("createdAt"))};unique(folders.map{it.id});val fids=folders.map{it.id}.toSet()
  val memos=rows("memos",3000).map{m->val folder=nullableText(m,"folderId");require(folder==null||folder in fids);require(m.num("createdAt")>=0&&m.num("updatedAt")>=m.num("createdAt")&&m.num("revision")>=0);MemoEntity(m.str("id"),bounded(m.str("title"),300,"标题"),bounded(m.str("body"),100000,"正文"),m.num("createdAt"),m.num("updatedAt"),nullableLong(m,"pinnedAt"),folder,m.flag("starred"),nullableLong(m,"deletedAt"),bounded(m.str("mood"),50,"心情"),m.num("revision"),m.str("raw","{}"))};unique(memos.map{it.id})
  val plans=rows("plans").map{p->require(validDate(p.str("date")));PlanEntity(p.str("id"),p.str("date"),bounded(p.str("text"),150,"计划"),p.flag("done"),p.flag("important"),p.str("raw","{}"))};unique(plans.map{it.id})
  val facts=rows("facts",3000).map{f->FactEntity(f.str("key"),bounded(f.str("value"),5000,"记忆"),bounded(f.str("quote"),600,"引用"),f.str("sourceMessageId"),f.num("updatedAt"),f.flag("locked"),f.flag("blocked"))};unique(facts.map{it.key})
  val chapters=rows("chapters",10000).map{c->require(c.str("sessionId") in sids&&c.num("fromOrdinal")>=0&&c.num("toOrdinal")>=c.num("fromOrdinal"));ChapterEntity(c.str("id"),c.str("sessionId"),c.num("fromOrdinal"),c.num("toOrdinal"),c.str("fingerprint"),bounded(c.str("summary"),5000,"摘要"),c.num("updatedAt"))};unique(chapters.map{it.id})
  val subjects=rows("subjects",500).map{SubjectEntity(it.str("id"),bounded(it.str("name"),30,"科目"),it.flag("deleted"))};unique(subjects.map{it.id})
  val logs=rows("focusLogs").map{l->require(l.dec("minutes")>0&&l.dec("minutes")<=1500);FocusLogEntity(l.str("id"),l.num("at"),l.dec("minutes"),bounded(l.str("title"),100,"记录"),bounded(l.str("group"),30,"科目"),l.str("kind"),l.str("raw","{}"))};unique(logs.map{it.id})
  val skills=rows("skills",100).map{s->val content=bounded(s.str("content"),64000,"技能");require(content.sha256()==s.str("digest"));SkillEntity(s.str("id"),bounded(s.str("name"),80,"技能名称"),bounded(s.str("description"),500,"简介"),content,s.str("source"),s.str("digest"),false,s.num("installedAt"),bounded(s.str("files","[]"),2_000_000,"参考资料"))};unique(skills.map{it.id})
  val records=rows("records").map{e->val kind=e.str("kind");require(kind!="migration"&&!Regex("secret|password|credential|authorization",RegexOption.IGNORE_CASE).containsMatchIn(kind));RecordEntity(kind,e.str("id"),e.str("payload"),e.num("updatedAt"))};unique(records.map{it.kind+"/"+it.id})
  val timers=rows("timers",20).map{t->require(t.num("durationMs") in 0..86_400_000&&t.num("remainingMs") in 0..86_400_000);TimerEntity(t.str("id"),t.str("kind"),bounded(t.str("label"),100,"提醒标签"),t.num("durationMs"),t.num("remainingMs"),0,0,0,false,t.flag("completed"),t.num("startedWall"),newId(),t.str("raw","{}"))};unique(timers.map{it.id})
  val originals=(r["originals"] as? JsonArray).orEmpty().map{val o=it.jsonObject;require(o.str("original").sha256()==o.str("digest"));ImportEntity(o.str("digest"),o.str("original"),o.num("importedAt"),o.str("report"))};unique(originals.map{it.digest})
  val pref=r.child("preferences");require(validDate(pref.str("since","2023-07-08"))&&pref.str("name","华生").length in 1..12);require(pref.dec("latitude") in -90.0..90.0&&pref.dec("longitude") in -180.0..180.0)
  return ImportPlan(text.sha256(),text,pref,sessions,messages,plans,memos,folders,facts,chapters,subjects,logs,records,listOf("导入的技能默认停用，请重新确认。","进行中的计时以暂停状态恢复，不自动响铃。","模型密钥不在备份内，请重新填写。"),skills,timers,originals)
 }
 private fun legacy(text:String,root:JsonObject):ImportPlan{
  val storage:Map<String,String>;val main:JsonObject
  if(root.str("format")=="four-seasons-luke-full-backup"){
   require(root.num("version")==1L);storage=root.child("storage").mapValues{(_,v)->require(v is JsonPrimitive&&v.isString);v.content}
   require(storage.keys.all{it.startsWith("luke-")&&!Regex("api[-_]?key|token|secret|password|credential|authorization",RegexOption.IGNORE_CASE).containsMatchIn(it)}){"请从旧版重新导出不含密钥的完整备份"}
   main=obj(storage["luke-companion-v1"]?:error("缺少主存档"))
  }else{require(root["messages"] is JsonArray){"不是旧存档或完整备份"};main=root;storage=mapOf("luke-companion-v1" to text)}
  require(main.str("name").length in 1..12&&validDate(main.str("since"))){"称呼或相伴日期无效"}
  listOf("messages","tasks","notes","checks").forEach{require(main[it] is JsonArray){"缺少 $it 数据"}}
  val digest=text.sha256();val sid="legacy-${digest.take(16)}"
  val archive=main.child("memoryArchive");val muted=archive.arr("mutedSources").map{it.jsonPrimitive.long}.toSet();val blocked=archive.arr("blockedKeys").map{it.jsonPrimitive.content}.toSet()
  val messages=main.arr("messages").mapIndexed{i,e->val m=e.jsonObject;require(m.str("who") in setOf("me","luke"));MessageEntity("$sid-$i",sid,i.toLong(),m.str("who"),bounded(m.str("text"),20000,"消息"),parseTime(m.str("at")),m.str("source","legacy-unknown"),m.flag("favorite"),if(m.flag("interrupted"))"interrupted" else "complete",i.toLong() in muted,m.toString())}
  val sessions=listOf(SessionEntity(sid,"导入的悄悄话",messages.firstOrNull()?.at?:0,messages.lastOrNull()?.at?:0,main.str("draft")))
  val plans=main.arr("tasks").map{e->val p=e.jsonObject;require(validDate(p.str("date"))&&(p["done"] as? JsonPrimitive)?.booleanOrNull!=null);PlanEntity(p.str("id"),p.str("date"),bounded(p.str("text"),150,"计划"),p.flag("done"),p.flag("important"),p.toString())};unique(plans.map{it.id})
  val records=storage.filterKeys{it!="luke-companion-v1"}.map{(k,v)->RecordEntity("legacy-settings",k,v,0)}.toMutableList();records+=RecordEntity("legacy-main",digest,main.toString(),0)
  main.arr("checks").forEach{require(validDate(it.jsonPrimitive.content));records+=RecordEntity("check",it.jsonPrimitive.content,it.toString(),0)}
  main.arr("anniversaries").forEach{val a=it.jsonObject;require(validDate(a.str("date")));records+=RecordEntity("anniversary",a.str("id").ifBlank{it.toString().sha256()},it.toString(),0)}
  main.arr("books").forEach{val b=it.jsonObject;require(b.str("title").isNotBlank());records+=RecordEntity("book",(b.str("title")+"|"+b.str("author")).sha256(),it.toString(),parseTime(b.str("updatedAt")))}
  if(main["reading"] is JsonObject)records+=RecordEntity("reading","current",main.child("reading").toString(),0)
  for(key in listOf("focus","countdown","study","focusTasks","memory","memoryArchive","contextSharing"))if(main.containsKey(key))records+=RecordEntity("legacy-state",key,main.getValue(key).toString(),0)
  val workspace=storage["luke-memo-workspace-v1"]?.let(::obj)?:buildJsonObject{put("version",1);put("folders",JsonArray(emptyList()));put("memos",JsonArray(emptyList()))}
  require(workspace.num("version")==1L&&workspace.arr("memos").size<=3000&&workspace.arr("folders").size<=100)
  val folders=workspace.arr("folders").map{val f=it.jsonObject;FolderEntity(f.str("id"),bounded(f.str("name"),30,"笔记本"),f.num("createdAt"))};unique(folders.map{it.id})
  val memos=workspace.arr("memos").map{val m=it.jsonObject;val folder=(m["folderId"] as? JsonPrimitive)?.contentOrNull;require(folder==null||folders.any{it.id==folder});require(m.num("createdAt")>=0&&m.num("updatedAt")>=m.num("createdAt"));MemoEntity(m.str("id"),bounded(m.str("title"),300,"标题"),bounded(m.str("body"),100000,"正文"),m.num("createdAt"),m.num("updatedAt"),(m["pinnedAt"] as? JsonPrimitive)?.longOrNull,folder,m.flag("starred"),(m["deletedAt"] as? JsonPrimitive)?.longOrNull,m.str("mood"),raw=m.toString())}.toMutableList();unique(memos.map{it.id})
  main.arr("notes").forEachIndexed{i,e->val n=e.jsonObject;require(validDate(n.str("date")));val at=java.time.LocalDate.parse(n.str("date")).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();memos+=MemoEntity("$sid-note-$i",n.str("date"),bounded(n.str("text"),5000,"旧手记"),at,at,mood=n.str("mood"),raw=n.toString())}
  val facts=archive.arr("facts").map{val f=it.jsonObject;val i=f.num("sourceIndex",-1);FactEntity(bounded(f.str("key"),80,"记忆键"),bounded(f.str("value"),600,"记忆"),bounded(f.str("quote"),240,"引用"),messages.getOrNull(i.toInt())?.id?:"",parseTime(f.str("updatedAt")),f.flag("locked"),f.str("key") in blocked||i in muted)}.toMutableList()
  blocked.filter{b->facts.none{it.key==b}}.forEach{facts+=FactEntity(it,"",blocked=true)}
  main.child("memory").str("pinned").takeIf{it.isNotBlank()}?.let{facts+=FactEntity("用户固定记忆",bounded(it,5000,"固定记忆"),locked=true)}
  val subjectRaw=storage.entries.firstOrNull{it.key.contains("subject")&&(it.key.contains("study")||it.key.contains("focus"))}?.value
  val subjectJson=subjectRaw?.let{JsonCodec.parseToJsonElement(it)}
  val subjectArray=when(subjectJson){is JsonArray->subjectJson;is JsonObject->subjectJson.arr("subjects");else->JsonArray(emptyList())}
  val subjects=subjectArray.map{if(it is JsonPrimitive)SubjectEntity(it.content.sha256(),bounded(it.content,30,"科目"))else it.jsonObject.let{s->SubjectEntity(s.str("id",s.str("name").sha256()),bounded(s.str("name"),30,"科目"),s.flag("deleted"))}}
  val logs=main.arr("focusLog").mapIndexed{i,e->val l=e.jsonObject;require(l.dec("minutes")>0&&l.dec("minutes")<=1500);FocusLogEntity("$sid-focus-$i",parseTime(l.str("at")),l.dec("minutes"),l.str("title"),l.str("group"),l.str("kind"),l.toString())}
  val display=storage["luke-display-v206"]?.let(::obj)?:storage["luke-display-v205"]?.let(::obj)?:JsonObject(emptyMap())
  val appearance=storage["luke-appearance-v1"]?.let(::obj)?:JsonObject(emptyMap())
  val recommendation=storage["luke-recommendation-preferences-v206"]?.let(::obj)?:JsonObject(emptyMap())
  val prefs=buildJsonObject{
   put("name",main.str("name"));put("since",main.str("since"));put("birthday",main.str("birthday"));put("activeSession",sid)
   put("scale",display.dec("scale",.95).coerceIn(.75,1.6));put("chatSize",display.dec("chatSize",13.0).coerceIn(10.0,32.0))
   for(k in listOf("bubbleMine","bubbleLuke"))put(k,display.str(k,display.str("bubble",if(k=="bubbleMine")"plain" else "tea")))
   put("avatarMine",display.str("avatarMine",display.str("avatar","images/companions/dog.webp")));put("avatarLuke",display.str("avatarLuke","images/companions/cat.webp"))
   put("season",appearance.str("season","auto"));put("period",appearance.str("period","auto"));put("effects",appearance.flag("effects",true))
   put("autoMemory",archive.flag("enabled",true));put("songPreference",recommendation.str("song"));put("bookPreference",recommendation.str("book"));put("useRecommendationMemory",recommendation.flag("useMemory",true))
   main.child("contextSharing").forEach{(k,v)->if(k in PreferencesStore.sharingKeys&&v.jsonPrimitive.booleanOrNull!=null)put("sharing-$k",v)}
  }
  return ImportPlan(digest,text,prefs,sessions,messages,plans,memos,folders,facts,emptyList(),subjects,logs,records,buildList{add("完整原文已保留，可导出回退。模型密钥需重新填写。");if(archive.arr("chapters").isNotEmpty())add("旧摘要保留在快照中；原生索引按消息重新核对，不直接信任旧索引。");if(listOf("focus","countdown","study").any{main[it] is JsonObject})add("旧版进行中的计时状态已保留，不自动启动或响铃。")})
 }
}

class BackupService(private val context:Context,private val db:LukeDatabase,private val prefs:PreferencesStore){
 suspend fun inspect(uri:Uri):ImportPlan=withContext(Dispatchers.IO){val bytes=context.contentResolver.openInputStream(uri).use{requireNotNull(it){"无法打开备份"}.readLimited(BackupDecoder.MAX_BYTES)};BackupDecoder.decode(bytes.toString(Charsets.UTF_8))}
 suspend fun import(plan:ImportPlan)=withContext(Dispatchers.IO){
  // Validate again at the persistence boundary; retain the original backup unchanged.
  val checkedPreferences=PreferenceRules.normalize(plan.preferences)
  db.withTransaction{
   val d=db.dao();if(d.imported(plan.digest)!=null)return@withTransaction
   check(d.messageCount()==0&&d.memoCount()==0&&d.allPlans().isEmpty()&&d.allFocusLogs().isEmpty()&&d.allFacts().isEmpty()&&d.allSkills().isEmpty()){"本机已有原生数据，本次未覆盖。请先导出备份。"}
   plan.sessions.forEach{d.putSession(it)};plan.messages.forEach{d.putMessage(it)};plan.plans.forEach{d.putPlan(it)};plan.folders.forEach{d.putFolder(it)};plan.memos.forEach{d.putMemo(it)}
   plan.facts.forEach{d.putFact(it)};plan.chapters.forEach{d.putChapter(it)};plan.subjects.forEach{d.putSubject(it)};plan.focusLogs.forEach{d.putFocusLog(it)};plan.skills.forEach{d.putSkill(it)};plan.timers.forEach{d.putTimer(it)};plan.records.forEach{d.putRecord(it)}
   plan.originals.forEach{if(d.imported(it.digest)==null)d.putImport(it)};if(d.imported(plan.digest)==null)d.putImport(ImportEntity(plan.digest,plan.source,report=plan.summary))
   d.putRecord(RecordEntity("migration","pending-preferences",checkedPreferences.toString()))
  };finishPendingSettings()
 }
 suspend fun finishPendingSettings(){val pending=db.dao().record("migration","pending-preferences")?:return;prefs.restore(obj(pending.payload));db.dao().deleteRecord("migration","pending-preferences")}
 suspend fun exportRollback(uri:Uri)=withContext(Dispatchers.IO){val source=db.dao().allImports().firstOrNull{runCatching{obj(it.original).str("format")!="four-seasons-luke-native-backup"}.getOrDefault(false)}?.original?:error("没有旧版原始快照");write(uri,source)}
 private fun write(uri:Uri,text:String){context.contentResolver.openOutputStream(uri,"wt").use{requireNotNull(it){"无法写入文件"}.write(text.toByteArray(Charsets.UTF_8))}}
 suspend fun export(uri:Uri)=withContext(Dispatchers.IO){
  val pref=prefs.snapshot();val text=db.withTransaction{val d=db.dao();buildJsonObject{
   put("format","four-seasons-luke-native-backup");put("version",1);put("exportedAt",nowIso());put("preferences",pref)
   fun table(k:String,items:List<JsonObject>){put(k,JsonArray(items))}
   table("sessions",d.allSessions().map{buildJsonObject{put("id",it.id);put("title",it.title);put("createdAt",it.createdAt);put("updatedAt",it.updatedAt);put("draft",it.draft);put("archived",it.archived)}})
   table("messages",d.allMessages().map{buildJsonObject{put("id",it.id);put("sessionId",it.sessionId);put("ordinal",it.ordinal);put("who",it.who);put("text",it.text);put("at",it.at);put("source",it.source);put("favorite",it.favorite);put("status",it.status);put("muted",it.muted);put("raw",it.raw)}})
   table("plans",d.allPlans().map{buildJsonObject{put("id",it.id);put("date",it.date);put("text",it.text);put("done",it.done);put("important",it.important);put("raw",it.raw)}})
   table("folders",d.allFolders().map{buildJsonObject{put("id",it.id);put("name",it.name);put("createdAt",it.createdAt)}})
   table("memos",d.allMemos().map{buildJsonObject{put("id",it.id);put("title",it.title);put("body",it.body);put("createdAt",it.createdAt);put("updatedAt",it.updatedAt);put("pinnedAt",it.pinnedAt?.let(::JsonPrimitive)?:JsonNull);put("folderId",it.folderId?.let(::JsonPrimitive)?:JsonNull);put("starred",it.starred);put("deletedAt",it.deletedAt?.let(::JsonPrimitive)?:JsonNull);put("mood",it.mood);put("revision",it.revision);put("raw",it.raw)}})
   table("facts",d.allFacts().map{buildJsonObject{put("key",it.key);put("value",it.value);put("quote",it.quote);put("sourceMessageId",it.sourceMessageId);put("updatedAt",it.updatedAt);put("locked",it.locked);put("blocked",it.blocked)}})
   table("chapters",d.allChapters().map{buildJsonObject{put("id",it.id);put("sessionId",it.sessionId);put("fromOrdinal",it.fromOrdinal);put("toOrdinal",it.toOrdinal);put("fingerprint",it.fingerprint);put("summary",it.summary);put("updatedAt",it.updatedAt)}})
   table("subjects",d.allSubjects().map{buildJsonObject{put("id",it.id);put("name",it.name);put("deleted",it.deleted)}})
   table("focusLogs",d.allFocusLogs().map{buildJsonObject{put("id",it.id);put("at",it.at);put("minutes",it.minutes);put("title",it.title);put("group",it.group);put("kind",it.kind);put("raw",it.raw)}})
   table("skills",d.allSkills().map{buildJsonObject{put("id",it.id);put("name",it.name);put("description",it.description);put("content",it.content);put("source",it.source);put("digest",it.digest);put("enabled",false);put("installedAt",it.installedAt);put("files",it.files)}})
   table("records",d.allRecords().filter{it.kind!="migration"}.map{buildJsonObject{put("kind",it.kind);put("id",it.id);put("payload",it.payload);put("updatedAt",it.updatedAt)}})
   val time=cn.sishiyuni.core.timer.AndroidTime(context)
   table("timers",d.allTimers().map{t->val remaining=if(t.kind=="study")cn.sishiyuni.core.timer.TimerMath.studyElapsed(t,time)else cn.sishiyuni.core.timer.TimerMath.remaining(t,time);buildJsonObject{put("id",t.id);put("kind",t.kind);put("label",t.label);put("durationMs",t.durationMs);put("remainingMs",remaining);put("completed",t.completed);put("running",t.running);put("startedWall",t.startedWall);put("generation",t.generation);put("raw",t.raw)}})
   table("originals",d.allImports().filter{runCatching{obj(it.original).str("format")!="four-seasons-luke-native-backup"}.getOrDefault(false)}.map{buildJsonObject{put("digest",it.digest);put("original",it.original);put("importedAt",it.importedAt);put("report",it.report)}})
  }}.toString();require(text.toByteArray().size<=BackupDecoder.MAX_BYTES){"备份超过 60 MB"};write(uri,text)
 }
 suspend fun exportText(uri:Uri,text:String)=withContext(Dispatchers.IO){write(uri,bounded(text,2000000,"导出内容"))}
