package cn.sishiyuni.core.timer

import cn.sishiyuni.core.data.TimerEntity
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

interface TimeSource { fun wall():Long; fun elapsed():Long; fun boot():Int }
object TimerMath {
 fun remaining(t:TimerEntity,time:TimeSource):Long {
  if(!t.running)return t.remainingMs.coerceAtLeast(0)
  val amount=if(t.bootCount==time.boot())t.elapsedDeadline-time.elapsed()else t.wallDeadline-time.wall()
  return amount.coerceIn(0,t.durationMs.coerceAtLeast(0))
 }
 fun studyElapsed(t:TimerEntity,time:TimeSource):Long {
  val current=if(!t.running)0 else if(t.bootCount==time.boot())(time.elapsed()-t.elapsedDeadline).coerceAtLeast(0)else (time.wall()-t.wallDeadline).coerceIn(0,24*3600000L)
  return (t.remainingMs+current).coerceIn(0,24*3600000L)
 }
 fun releaseMinutes(position:Float,velocityMinutesPerSecond:Float):Int=(position+velocityMinutesPerSecond.coerceIn(-20f,20f)*.12f).roundToInt().coerceIn(1,180)
 fun rulerResistance(value:Float,min:Float=0f,max:Float=180f):Float=when{value<min->min-(min-value)*.18f;value>max->max+(value-max)*.18f;else->value}
 fun duration(ms:Long):String {val s=(ms.coerceAtLeast(0)+999)/1000;return "%02d:%02d:%02d".format(s/3600,s/60%60,s%60)}
 fun splitStudy(from:Long,millis:Long,zone:ZoneId=ZoneId.systemDefault()):List<Pair<Long,Double>> {
  require(millis in 0..24*3600000L);if(millis==0L)return emptyList()
  var start=from;val end=from+millis;val result=mutableListOf<Pair<Long,Double>>()
  while(start<end){val next=Instant.ofEpochMilli(start).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();val stop=minOf(next,end);result+=start to (stop-start)/60000.0;start=stop}
  return result
 }
}
