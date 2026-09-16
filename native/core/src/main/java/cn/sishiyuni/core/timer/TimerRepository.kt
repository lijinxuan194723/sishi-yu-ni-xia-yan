package cn.sishiyuni.core.timer

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import cn.sishiyuni.core.data.*
import cn.sishiyuni.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

class AndroidTime(private val context:Context):TimeSource{
 override fun wall()=System.currentTimeMillis()
 override fun elapsed()=SystemClock.elapsedRealtime()
 override fun boot()=Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT,0)
}
class TimerRepository(private val context:Context,private val db:LukeDatabase,val time:TimeSource=AndroidTime(context)){
 private val mutex=Mutex();private val alarms=context.getSystemService(AlarmManager::class.java)
 val states=db.dao().timers()
 suspend fun startCountdown(minutes:Int,label:String="夏彦提醒你：休息一下",replaceExisting:Boolean=false)=mutex.withLock{
  require(minutes in 1..180)
  val old=db.dao().timer("countdown")
  TimerTransitions.requireCountdownReplacement(old,replaceExisting)
  val duration=minutes*60000L
  val t=TimerEntity(durationMs=duration,remainingMs=duration,elapsedDeadline=time.elapsed()+duration,wallDeadline=time.wall()+duration,bootCount=time.boot(),running=true,startedWall=time.wall(),generation=newId(),label=label.take(100))
  db.dao().putTimer(t)
  if(old!=null)cancelAlarm(old)
  schedule(t)
 }
 suspend fun pause(id:String="countdown")=mutex.withLock{
  val t=db.dao().timer(id)?:return@withLock
  if(!t.running)return@withLock
  val paused=if(t.kind=="study")TimerTransitions.pauseStudy(t,time)
   else t.copy(remainingMs=TimerMath.remaining(t,time),running=false)
  db.dao().putTimer(paused)
  cancelAlarm(t)
 }
 suspend fun resume(id:String="countdown")=mutex.withLock{
  val t=db.dao().timer(id)?:return@withLock
  if(t.running||t.completed||t.generation.isBlank())return@withLock
  val study=t.kind=="study"
  val resumed=t.copy(running=true,elapsedDeadline=if(study)time.elapsed()else time.elapsed()+t.remainingMs,wallDeadline=if(study)time.wall()else time.wall()+t.remainingMs,bootCount=time.boot())
  db.dao().putTimer(resumed)
  if(!study)schedule(resumed)
 }
 suspend fun reset(id:String="countdown",discardStudyConfirmed:Boolean=false)=mutex.withLock{
  val t=db.dao().timer(id)?:return@withLock
  val reset=TimerTransitions.reset(t,discardStudyConfirmed)
  db.dao().putTimer(reset)
  cancelAlarm(t)
  NotificationManagerCompat.from(context).cancel(id.hashCode())
 }
 suspend fun startStudy(subject:String)=mutex.withLock{
  val old=db.dao().timer("study")
  val next=TimerTransitions.startStudy(old,subject,time,newId())
  if(next!==old)db.dao().putTimer(next)
 }
 suspend fun finishStudy()=mutex.withLock{db.withTransaction{
  val t=db.dao().timer("study")?:return@withTransaction
  if(t.completed||!TimerTransitions.hasStudy(t))return@withTransaction
  val paused=TimerTransitions.pauseStudy(t,time)
  val segments=obj(paused.raw).arr("segments")
  segments.forEachIndexed{i,e->val s=e.jsonObject
   TimerMath.splitStudy(s.num("from"),s.num("millis").coerceIn(0,24*3600000L)).forEachIndexed{j,(at,minutes)->
    if(minutes>0)db.dao().putFocusLog(FocusLogEntity("${t.generation}:$i:$j",at,minutes,t.label,t.label,"study"))
   }
  }
  db.dao().putTimer(paused.copy(completed=true))
 }}
 private fun pending(t:TimerEntity,flags:Int=PendingIntent.FLAG_UPDATE_CURRENT):PendingIntent?=PendingIntent.getBroadcast(context,t.id.hashCode(),Intent(context,TimerReceiver::class.java).setAction("finish").setData(Uri.parse("luke-timer://${t.id}/${t.generation}")).putExtra("id",t.id).putExtra("generation",t.generation),flags or PendingIntent.FLAG_IMMUTABLE)
 private fun cancelAlarm(t:TimerEntity){pending(t,PendingIntent.FLAG_NO_CREATE)?.let{alarms.cancel(it);it.cancel()}}
 fun exactAllowed():Boolean=Build.VERSION.SDK_INT<31||alarms.canScheduleExactAlarms()
 private fun schedule(t:TimerEntity){
  if(t.kind=="study"||!t.running)return
  val intent=pending(t)?:return
  val trigger=time.elapsed()+TimerMath.remaining(t,time)
  try{if(exactAllowed())alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,trigger,intent)else alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,trigger,intent)}
  catch(_:SecurityException){alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,trigger,intent)}
 }
 suspend fun fire(id:String,generation:String){
  val fired=mutex.withLock{db.withTransaction{
   val t=db.dao().timer(id)?:return@withTransaction null
   if(t.kind=="study"||!t.running||t.completed||t.generation!=generation)return@withTransaction null
   if(TimerMath.remaining(t,time)>0){schedule(t);return@withTransaction null}
   db.dao().putTimer(t.copy(running=false,completed=true,remainingMs=0));t
  }}
  if(fired!=null)notify(fired)
 }
 suspend fun restore(){mutex.withLock{db.dao().allTimers().filter{it.running&&it.kind!="study"}.forEach{t->
  val remaining=TimerMath.remaining(t,time)
  val updated=if(t.bootCount!=time.boot())t.copy(bootCount=time.boot(),elapsedDeadline=time.elapsed()+remaining,wallDeadline=time.wall()+remaining)else t
  db.dao().putTimer(updated);schedule(updated)
 }}}
 suspend fun checkForeground(){db.dao().allTimers().filter{it.running&&it.kind!="study"&&TimerMath.remaining(it,time)==0L}.forEach{fire(it.id,it.generation)}}
 private fun notify(t:TimerEntity){
  val manager=context.getSystemService(NotificationManager::class.java)
  val channel=NotificationChannel("luke-timers","计时结束",NotificationManager.IMPORTANCE_HIGH).apply{description="倒计时到时提醒";enableVibration(true);setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM).build())}
  manager.createNotificationChannel(channel)
  if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
  val launch=context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply{flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP}
  val content=launch?.let{PendingIntent.getActivity(context,0,it,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)}
  val n=NotificationCompat.Builder(context,"luke-timers").setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("夏彦提醒你").setContentText(t.label).setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(content).build()
  NotificationManagerCompat.from(context).notify(t.id.hashCode(),n)
 }
}
class TimerReceiver:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){val pending=goAsync();CoroutineScope(SupervisorJob()+Dispatchers.IO).launch{try{val graph=(context.applicationContext as cn.sishiyuni.core.GraphOwner).graph;graph.timer.fire(intent.getStringExtra("id")?:"",intent.getStringExtra("generation")?:"")}finally{pending.finish()}}}}
class RestoreReceiver:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){val pending=goAsync();CoroutineScope(SupervisorJob()+Dispatchers.IO).launch{try{val graph=(context.applicationContext as cn.sishiyuni.core.GraphOwner).graph;graph.timer.restore()}finally{pending.finish()}}}}
