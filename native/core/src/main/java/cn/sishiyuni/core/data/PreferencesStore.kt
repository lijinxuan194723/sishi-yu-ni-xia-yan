package cn.sishiyuni.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.time.LocalDateTime

private val Context.nativePreferences by preferencesDataStore("native-preferences")
data class AppPreferences(
 val name:String="华生",val since:String="2023-07-08",val birthday:String="",
 val season:String="auto",val period:String="auto",val scale:Float=.95f,val chatSize:Float=13f,
 val avatarMine:String="images/companions/dog.webp",val avatarLuke:String="images/companions/cat.webp",
 val bubbleMine:String="plain",val bubbleLuke:String="tea",val glass:Boolean=true,val effects:Boolean=true,
 val reduceMotion:Boolean=false,val haptics:Boolean=true,val preferHighRefresh:Boolean=true,
 val activeSession:String="legacy",val autoMemory:Boolean=true,val modelUrl:String="",val modelName:String="",
 val fallbackUrl:String="",val fallbackModel:String="",val weatherCity:String="",val latitude:Double=0.0,val longitude:Double=0.0,
 val weatherProvider:String="open-meteo",val holidayAuto:Boolean=true,val holidayFallback:Boolean=true,
 val songPreference:String="",val bookPreference:String="",val useRecommendationMemory:Boolean=true,
 val sharing:Map<String,Boolean> = mapOf("weather" to true,"reading" to true,"study" to true,"plans" to true,"notes" to true,"dates" to true),val photoIndex:Int=0
){
 fun resolvedSeason(now:LocalDateTime=LocalDateTime.now()):String=if(season in setOf("spring","summer","autumn","winter"))season else listOf("spring","summer","autumn","winter")[((now.monthValue+9)%12)/3]
 fun isNight(now:LocalDateTime=LocalDateTime.now()):Boolean=if(period=="auto")now.hour<6||now.hour>=19 else period in setOf("night","深夜","夜晚")
}
class PreferencesStore(private val store:DataStore<Preferences>,scope:CoroutineScope){
 constructor(context:Context,scope:CoroutineScope):this(context.applicationContext.nativePreferences,scope)
 val error=MutableStateFlow<String?>(null)
 val ready=MutableStateFlow(false)
 val state:StateFlow<AppPreferences> = store.data.retryWhen { e,attempt ->
  if(e is java.io.IOException){error.value="设置暂时无法读取，未覆盖原文件";delay((1000L*(attempt+1)).coerceAtMost(10000));true}else false
 }.map{decode(it).also{ready.value=true;error.value=null}}.stateIn(scope,SharingStarted.Eagerly,AppPreferences())
 private fun decode(p:Preferences):AppPreferences {
  fun s(k:String,d:String="")=p[stringPreferencesKey(k)]?:d
  fun b(k:String,d:Boolean=true)=p[booleanPreferencesKey(k)]?:d
  fun f(k:String,d:Float)=p[floatPreferencesKey(k)]?.takeIf{it.isFinite()}?:d
  return AppPreferences(name=s("name","华生"),since=s("since","2023-07-08"),birthday=s("birthday"),season=s("season","auto"),period=s("period","auto"),scale=f("scale",.95f).coerceIn(.75f,1.6f),chatSize=f("chatSize",13f).coerceIn(10f,32f),avatarMine=s("avatarMine","images/companions/dog.webp"),avatarLuke=s("avatarLuke","images/companions/cat.webp"),bubbleMine=s("bubbleMine","plain"),bubbleLuke=s("bubbleLuke","tea"),glass=b("glass"),effects=b("effects"),reduceMotion=b("reduceMotion",false),haptics=b("haptics"),preferHighRefresh=b("preferHighRefresh"),activeSession=s("activeSession","legacy"),autoMemory=b("autoMemory"),modelUrl=s("modelUrl"),modelName=s("modelName"),fallbackUrl=s("fallbackUrl"),fallbackModel=s("fallbackModel"),weatherCity=s("weatherCity"),latitude=p[doublePreferencesKey("latitude")]?.takeIf{it.isFinite()&&it in -90.0..90.0}?:0.0,longitude=p[doublePreferencesKey("longitude")]?.takeIf{it.isFinite()&&it in -180.0..180.0}?:0.0,weatherProvider="open-meteo",holidayAuto=b("holidayAuto"),holidayFallback=b("holidayFallback"),songPreference=s("songPreference"),bookPreference=s("bookPreference"),useRecommendationMemory=b("useRecommendationMemory"),sharing=sharingKeys.associateWith{b("sharing-$it")},photoIndex=(p[intPreferencesKey("photoIndex")]?:0).coerceIn(0,99))
 }
 suspend fun text(key:String,value:String){val checked=PreferenceRules.text(key,value);store.edit{it[stringPreferencesKey(key)]=checked}}
 suspend fun flag(key:String,value:Boolean){require(key in flagKeys||PreferenceRules.isSharingKey(key)){"未知的开关设置"};store.edit{it[booleanPreferencesKey(key)]=value}}
 suspend fun size(key:String,value:Float){val checked=PreferenceRules.size(key,value);store.edit{it[floatPreferencesKey(key)]=checked}}
 suspend fun photo(value:Int){store.edit{it[intPreferencesKey("photoIndex")]=value.coerceIn(0,99)}}
 suspend fun location(name:String,lat:Double,lon:Double){restore(buildJsonObject{put("weatherCity",name.take(120));put("latitude",lat);put("longitude",lon)})}
 suspend fun resetTypography(){store.edit{it[floatPreferencesKey("scale")]=.95f;it[floatPreferencesKey("chatSize")]=13f}}
 suspend fun snapshot():JsonObject=store.data.first().let{p->buildJsonObject{p.asMap().forEach{(k,v)->if(PreferenceRules.isKnown(k.name))put(k.name,when(v){is Boolean->JsonPrimitive(v);is Number->JsonPrimitive(v);else->JsonPrimitive(v.toString())})}}}
 suspend fun restore(value:JsonObject){
  val checked=PreferenceRules.normalize(value)
  store.edit{p->checked.forEach{(key,v)->val item=v.jsonPrimitive;when{
   key in textKeys->p[stringPreferencesKey(key)]=item.content
   key in flagKeys||PreferenceRules.isSharingKey(key)->p[booleanPreferencesKey(key)]=item.boolean
   key=="scale"||key=="chatSize"->p[floatPreferencesKey(key)]=item.float
   key in setOf("latitude","longitude")->p[doublePreferencesKey(key)]=item.double
   key=="photoIndex"->p[intPreferencesKey(key)]=item.int
  }}}
 }
 companion object {
  val textKeys=setOf("name","since","birthday","season","period","avatarMine","avatarLuke","bubbleMine","bubbleLuke","activeSession","modelUrl","modelName","fallbackUrl","fallbackModel","weatherCity","weatherProvider","songPreference","bookPreference")
  val flagKeys=setOf("glass","effects","reduceMotion","haptics","preferHighRefresh","autoMemory","holidayAuto","holidayFallback","useRecommendationMemory")
  val sharingKeys=setOf("weather","reading","study","plans","notes","dates")
 }
}
