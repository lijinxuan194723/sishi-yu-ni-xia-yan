package cn.sishiyuni.core.network

import android.content.Context
import cn.sishiyuni.core.data.*
import cn.sishiyuni.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.time.LocalDate

data class Holiday(val date:String,val name:String,val off:Boolean)
data class HolidayYear(val year:Int,val entries:List<Holiday>,val source:String,val checkedAt:Long=0)
object HolidayParser {
 fun static(root:JsonObject,year:Int):List<Holiday>{
  require(year in 2000..2100&&root.num("year")==year.toLong()){ "节假日年份不符" }
  val days=root.arr("days");require(days.size in 10..120){"年度数据不完整"}
  val entries=days.map{e->val d=e.jsonObject;val date=d.str("date");require(validDate(date));val parsed=LocalDate.parse(date);require(parsed>=LocalDate.of(year-1,12,1)&&parsed<=LocalDate.of(year,12,31)){"节假日日期越界"};val off=(d["isOffDay"] as? JsonPrimitive)?.booleanOrNull?:error("休班标志无效");Holiday(date,bounded(d.str("name"),30,"节日名称"),off)}
  validate(entries);return entries
 }
 fun backup(root:JsonObject,year:Int):List<Holiday>{
  require(root.num("code",-1)==0L);val days=root.child("holiday");require(days.size in 10..120)
  val entries=days.map{(k,e)->val d=e.jsonObject;val date=d.str("date","$year-$k");require(validDate(date)&&date.startsWith("$year-"));require(k==date.takeLast(5)||k==date);val off=(d["holiday"] as? JsonPrimitive)?.booleanOrNull?:error("休班标志无效");Holiday(date,bounded(d.str("name"),30,"节日名称"),off)}
  validate(entries);return entries
 }
 private fun validate(entries:List<Holiday>){require(entries.map{it.date}.distinct().size==entries.size&&entries.any{!it.off}){"年度数据冲突或不完整"};require(listOf("元旦","春节","清明","劳动","端午","中秋","国庆").all{name->entries.any{it.name.contains(name)}}){"年度数据缺少法定节日"}}
 fun encode(year:Int,items:List<Holiday>)=buildJsonObject{put("year",year);putJsonArray("days"){items.forEach{h->add(buildJsonObject{put("date",h.date);put("name",h.name);put("isOffDay",h.off)})}}.toString()
}
class HolidayRepository(private val context:Context,private val dao:LukeDao,private val http:Http,private val prefs:PreferencesStore){
 val years=MutableStateFlow<Map<Int,HolidayYear>>(emptyMap());val status=MutableStateFlow("");private val mutex=Mutex()
 suspend fun load(year:Int,force:Boolean=false)=withContext(Dispatchers.IO){mutex.withLock{
  require(year in 1900..2100)
  val cached=dao.record("holiday",year.toString());val source=dao.record("holiday-source",year.toString())?.payload?:"本地"
  var valid=cached?.let{runCatching{HolidayYear(year,HolidayParser.static(obj(it.payload),year),source,it.updatedAt)}.getOrNull()}
  if(valid==null)valid=runCatching{val raw=context.assets.open("holidays/$year.json").use{it.readLimited(500000)}.toString(Charsets.UTF_8);HolidayYear(year,HolidayParser.static(obj(raw),year),"内置静态数据")}.getOrNull()
  if(valid!=null)years.update{it+(year to valid!!)}
  if(!force&&!prefs.state.value.holidayAuto)return@withLock
  val attempted=dao.record("holiday-attempt",year.toString())?.updatedAt?:0
  val interval=if(valid!=null)7*86400000L else 6*3600000L
  if(!force&&System.currentTimeMillis()-attempted in 0..interval)return@withLock
  if(year !in 2000..2100){status.value="这个年份尚无可用的节假日安排";return@withLock}
  dao.putRecord(RecordEntity("holiday-attempt",year.toString(),"{}"));status.value="正在检查 $year 年安排"
  try{
   val raw=http.json("https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master/$year.json",500000).jsonObject
   val entries=HolidayParser.static(raw,year);save(year,entries,"holiday-cn 静态 JSON")
  }catch(e:CancellationException){throw e}catch(e:Exception){
   if(valid==null&&prefs.state.value.holidayFallback){try{val entries=HolidayParser.backup(http.json("https://timor.tech/api/holiday/year/$year",500000).jsonObject,year);save(year,entries,"Timor 年度接口")}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){status.value="$year 年数据暂不可用；不推测休班"}}
   else status.value=if(valid!=null)"已保留本地 $year 年安排" else "$year 年数据暂不可用；不推测休班"
  }
 }} }
 private suspend fun save(year:Int,entries:List<Holiday>,source:String){val at=System.currentTimeMillis();dao.putRecord(RecordEntity("holiday",year.toString(),HolidayParser.encode(year,entries),at));dao.putRecord(RecordEntity("holiday-source",year.toString(),source,at));years.update{it+(year to HolidayYear(year,entries,source,at))};status.value="$year 年安排已更新"}
 fun on(date:LocalDate):Holiday?=years.value[date.year]?.entries?.find{it.date==date.toString()}?:years.value[date.year+1]?.entries?.find{it.date==date.toString()}
}
