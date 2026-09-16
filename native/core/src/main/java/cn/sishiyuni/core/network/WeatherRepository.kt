package cn.sishiyuni.core.network

import cn.sishiyuni.core.data.*
import cn.sishiyuni.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val WEATHER_TTL=30*60*1000L
data class WeatherHour(val time:String,val temperature:Double,val code:Int,val rain:Double?)
data class WeatherDay(val date:String,val min:Double,val max:Double,val code:Int,val rain:Double?)
data class WeatherReport(val city:String,val temperature:Double,val feelsLike:Double?,val humidity:Double?,val wind:Double?,val code:Int,val day:Boolean,val time:String,val hours:List<WeatherHour>,val days:List<WeatherDay>,val fetchedAt:Long,val cached:Boolean=false)
data class CityResult(val name:String,val label:String,val latitude:Double,val longitude:Double)
data class WeatherState(val report:WeatherReport?=null,val loading:Boolean=false,val error:String?=null)

object WeatherParser {
 fun parse(root:JsonObject,city:String,at:Long):WeatherReport {
  val c=root.child("current");val temperature=c["temperature_2m"]?.jsonPrimitive?.doubleOrNull
  require(temperature!=null&&temperature.isFinite()&&temperature in -100.0..70.0){"天气响应缺少有效温度"}
  val code=c["weather_code"]?.jsonPrimitive?.intOrNull?:error("天气状态缺失")
  fun optional(o:JsonObject,k:String)=o[k]?.jsonPrimitive?.doubleOrNull?.takeIf{it.isFinite()}
  val h=root.child("hourly");val d=root.child("daily")
  fun number(a:JsonArray,i:Int)=a.getOrNull(i)?.jsonPrimitive?.doubleOrNull?.takeIf{it.isFinite()}
  val now=c.str("time")
  val hours=h.arr("time").mapIndexedNotNull{i,t->
   val temp=number(h.arr("temperature_2m"),i);val hc=number(h.arr("weather_code"),i)
   if(temp==null||hc==null||t.jsonPrimitive.content<now.take(13))null else WeatherHour(t.jsonPrimitive.content,temp,hc.toInt(),number(h.arr("precipitation_probability"),i))
  }.take(24)
  val days=d.arr("time").mapIndexedNotNull{i,t->val lo=number(d.arr("temperature_2m_min"),i);val hi=number(d.arr("temperature_2m_max"),i);val dc=number(d.arr("weather_code"),i);if(lo==null||hi==null||dc==null||lo>hi)null else WeatherDay(t.jsonPrimitive.content,lo,hi,dc.toInt(),number(d.arr("precipitation_probability_max"),i))}.take(10)
  return WeatherReport(city,temperature,optional(c,"apparent_temperature"),optional(c,"relative_humidity_2m"),optional(c,"wind_speed_10m"),code,c.num("is_day",1)==1L,now,hours,days,at)
 }
 fun label(code:Int):String=when(code){0->"晴";1,2->"多云";3->"阴";45,48->"雾";51,53,55,56,57->"细雨";61,63,65,66,67,80,81,82->"雨";71,73,75,77,85,86->"雪";95,96,99->"雷雨";else->"天气变化中"}
 fun scene(code:Int):String=when(code){0->"sun";1,2,3->"cloud";45,48->"fog";71,73,75,77,85,86->"snow";95,96,99->"thunder";else->"rain"}
}
class WeatherRepository(private val http:Http,private val dao:LukeDao,private val prefs:PreferencesStore){
 val state=MutableStateFlow(WeatherState());private val mutex=Mutex()
 suspend fun search(query:String):List<CityResult>{
  require(query.trim().length in 1..80)
  val url=httpsUrl("https://geocoding-api.open-meteo.com/v1/search").newBuilder().addQueryParameter("name",query.trim()).addQueryParameter("count","15").addQueryParameter("language","zh").build()
  return http.json(url.toString()).jsonObject.arr("results").mapNotNull{e->val c=e.jsonObject;val lat=c["latitude"]?.jsonPrimitive?.doubleOrNull;val lon=c["longitude"]?.jsonPrimitive?.doubleOrNull;if(lat==null||lon==null||lat !in -90.0..90.0||lon !in -180.0..180.0)null else CityResult(c.str("name"),listOf(c.str("name"),c.str("admin1"),c.str("country")).filter{it.isNotBlank()}.distinct().joinToString(" · "),lat,lon)}
 }
 suspend fun refresh(force:Boolean=false)=mutex.withLock{
  val p=prefs.state.value;if(p.weatherCity.isBlank()){state.value=WeatherState(error="先选择一个城市");return@withLock}
  val key="open-meteo:${p.latitude}:${p.longitude}";val cached=dao.record("weather",key)
  val cachedReport=cached?.let{runCatching{WeatherParser.parse(obj(it.payload),p.weatherCity,it.updatedAt).copy(cached=true)}.getOrNull()}
  state.value=WeatherState(cachedReport,loading=true)
  if(!force&&cachedReport!=null&&System.currentTimeMillis()-cachedReport.fetchedAt in 0..WEATHER_TTL){state.value=WeatherState(cachedReport.copy(cached=false));return@withLock}
  try{
   val u=httpsUrl("https://api.open-meteo.com/v1/forecast").newBuilder().addQueryParameter("latitude",p.latitude.toString()).addQueryParameter("longitude",p.longitude.toString()).addQueryParameter("current","temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code,is_day").addQueryParameter("hourly","temperature_2m,weather_code,precipitation_probability").addQueryParameter("daily","weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max").addQueryParameter("timezone","auto").addQueryParameter("forecast_days","7").build()
   val raw=http.json(u.toString()).jsonObject;val at=System.currentTimeMillis();val report=WeatherParser.parse(raw,p.weatherCity,at)
   if(p.latitude!=prefs.state.value.latitude||p.longitude!=prefs.state.value.longitude)return@withLock
   dao.putRecord(RecordEntity("weather",key,raw.toString(),at));state.value=WeatherState(report)
  }catch(e:CancellationException){state.value=WeatherState(cachedReport);throw e}catch(e:Exception){state.value=WeatherState(cachedReport,false,if(cachedReport!=null)"更新失败，继续显示上次记录" else (e.message?:"天气暂时不可用"))}
 }
}
