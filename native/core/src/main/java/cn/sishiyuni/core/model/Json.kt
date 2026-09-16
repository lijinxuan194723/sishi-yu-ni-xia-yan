package cn.sishiyuni.core.model

import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

val JsonCodec=Json { ignoreUnknownKeys=true; isLenient=false }
fun obj(text:String):JsonObject=JsonCodec.parseToJsonElement(text) as? JsonObject ?: error("内容必须是 JSON 对象")
fun JsonObject.str(key:String,default:String="")=(get(key) as? JsonPrimitive)?.contentOrNull ?: default
fun JsonObject.num(key:String,default:Long=0)=(get(key) as? JsonPrimitive)?.longOrNull ?: default
fun JsonObject.dec(key:String,default:Double=0.0)=(get(key) as? JsonPrimitive)?.doubleOrNull?.takeIf{it.isFinite()} ?: default
fun JsonObject.flag(key:String,default:Boolean=false)=(get(key) as? JsonPrimitive)?.booleanOrNull ?: default
fun JsonObject.arr(key:String)=(get(key) as? JsonArray) ?: JsonArray(emptyList())
fun JsonObject.child(key:String)=(get(key) as? JsonObject) ?: JsonObject(emptyMap())
fun JsonObject.change(vararg pairs:Pair<String,JsonElement?>):JsonObject=JsonObject(toMutableMap().apply{pairs.forEach{(k,v)->if(v==null)remove(k)else put(k,v)}})
fun ByteArray.sha256():String=MessageDigest.getInstance("SHA-256").digest(this).joinToString(""){"%02x".format(it)}
fun String.sha256():String=toByteArray(Charsets.UTF_8).sha256()
fun newId():String=UUID.randomUUID().toString()
fun validDate(value:String):Boolean=value.length==10&&runCatching{LocalDate.parse(value).year in 1900..2100}.getOrDefault(false)
fun parseTime(value:String):Long=runCatching{Instant.parse(value).toEpochMilli()}.getOrDefault(0L)
fun nowIso():String=Instant.now().toString()
fun bounded(value:String,max:Int,label:String):String {require(value.length<=max&&!value.contains('\u0000')){"$label 内容过长或含无效字符"};return value}
fun java.io.InputStream.readLimited(limit:Int):ByteArray {val out=java.io.ByteArrayOutputStream();val b=ByteArray(8192);while(true){val n=read(b);if(n<0)break;require(out.size()+n<=limit){"文件过大"};out.write(b,0,n)};return out.toByteArray()}
