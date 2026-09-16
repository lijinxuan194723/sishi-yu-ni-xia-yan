package cn.sishiyuni.core.network

import cn.sishiyuni.core.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class Http {
 val client=OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(45,TimeUnit.SECONDS).callTimeout(100,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
 suspend fun bytes(url:String,limit:Int=2_000_000):ByteArray=execute(Request.Builder().url(httpsUrl(url)).get().build(),limit)
 suspend fun json(url:String,limit:Int=2_000_000):JsonElement=JsonCodec.parseToJsonElement(bytes(url,limit).toString(Charsets.UTF_8))
 suspend fun execute(request:Request,limit:Int):ByteArray=suspendCancellableCoroutine{continuation->
  val call=client.newCall(request);continuation.invokeOnCancellation{call.cancel()}
  call.enqueue(object:Callback {
   override fun onFailure(call:Call,e:IOException){if(continuation.isActive)continuation.resumeWithException(IOException("网络连接失败，请检查网络后重试",e))}
   override fun onResponse(call:Call,response:Response){try{response.use{
    if(!it.isSuccessful)throw IOException("服务返回 HTTP ${it.code}")
    val body=it.body?:throw IOException("响应为空");require(body.contentLength()<=limit){"响应过大"}
    val out=java.io.ByteArrayOutputStream();val b=ByteArray(8192)
    body.byteStream().use{input->while(continuation.isActive){val n=input.read(b);if(n<0)break;require(out.size()+n<=limit){"响应过大"};out.write(b,0,n)}}
    if(continuation.isActive)continuation.resume(out.toByteArray())
   }}catch(e:Exception){if(continuation.isActive)continuation.resumeWithException(e)}}
  })
 }
}
fun httpsUrl(value:String):HttpUrl {val u=value.trim().toHttpUrl();require(u.isHttps&&u.username.isEmpty()&&u.password.isEmpty()&&u.fragment==null){"请使用不含账号及片段的 HTTPS 地址"};return u}
fun completionUrl(value:String):HttpUrl {val u=httpsUrl(value);require(u.query==null){"模型地址不能含查询参数"};val p=u.encodedPath.trimEnd('/');return if(p.endsWith("/chat/completions"))u else u.newBuilder().encodedPath("$p/chat/completions").build()}
data class ModelConnection(val url:String,val model:String,val key:String){fun validate(){completionUrl(url);require(model.isNotBlank()&&model.length<=200);require(key.isNotBlank()){ "请先填写模型密钥" }}}
data class ModelTurn(val role:String,val content:String,val image:String?=null)
class SseDecoder {
 private val lines=mutableListOf<String>()
 fun line(value:String):String?{if(value.isEmpty())return finish();if(value.startsWith("data:"))lines+=value.substring(5).removePrefix(" ");return null}
 fun finish():String?=if(lines.isEmpty())null else lines.joinToString("\n").also{lines.clear()}
}
class ModelClient(private val http:Http){
 private fun request(c:ModelConnection,system:String,turns:List<ModelTurn>,stream:Boolean):Request {
  c.validate();require(system.length<=40000&&turns.sumOf{it.content.length}<=100000){"上下文超过限制"}
  val body=buildJsonObject{put("model",c.model);put("stream",stream);put("temperature",.7);putJsonArray("messages"){
   add(buildJsonObject{put("role","system");put("content",system)})
   turns.forEach{t->require(t.role in setOf("user","assistant"));add(buildJsonObject{put("role",t.role);if(t.image==null)put("content",t.content)else putJsonArray("content"){
    add(buildJsonObject{put("type","text");put("text",t.content)})
    add(buildJsonObject{put("type","image_url");putJsonObject("image_url"){put("url",t.image)}})
   }})}
  }}
  return Request.Builder().url(completionUrl(c.url)).header("Authorization","Bearer ${c.key}").header("Accept",if(stream)"text/event-stream" else "application/json").post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
 }
 suspend fun answer(c:ModelConnection,system:String,turns:List<ModelTurn>):String {
  val raw=http.execute(request(c,system,turns,false),1_000_000).toString(Charsets.UTF_8)
  val text=obj(raw).arr("choices").firstOrNull()?.jsonObject?.child("message")?.str("content")
  require(!text.isNullOrBlank()&&text.length<=20000){"模型未返回有效内容"};return text
 }
 fun stream(c:ModelConnection,system:String,turns:List<ModelTurn>):Flow<String> = callbackFlow {
  val call=http.client.newCall(request(c,system,turns,true))
  call.enqueue(object:Callback {
   override fun onFailure(call:Call,e:IOException){close(IOException("模型连接中断",e))}
   override fun onResponse(call:Call,response:Response){try{response.use{
    if(!it.isSuccessful)throw IOException("模型返回 HTTP ${it.code}")
    val body=it.body?:throw IOException("模型响应为空")
    if(!body.contentType().toString().contains("event-stream")){
     require(body.contentLength()<=1_000_000){"模型响应过大"}
     val raw=body.byteStream().use{it.readLimited(1_000_000)}.toString(Charsets.UTF_8)
     val text=obj(raw).arr("choices").firstOrNull()?.jsonObject?.child("message")?.str("content")?:""
     require(text.isNotBlank()&&text.length<=20000){"模型未返回有效内容"};trySendBlocking(text);close();return
    }
    val decoder=SseDecoder();var chars=0;var finished=false;var readChars=0
    fun event(data:String?){
     if(data.isNullOrBlank())return
     if(data.trim()=="[DONE]"){finished=true;return}
     val e=obj(data);if(e.containsKey("error"))throw IOException("模型返回错误")
     val choice=e.arr("choices").firstOrNull()?.jsonObject?:return
     val delta=choice.child("delta").str("content")
     if(delta.isNotEmpty()){chars+=delta.length;require(chars<=20000){"回复达到长度限制"};if(trySendBlocking(delta).isFailure)throw IOException("请求已取消")}
     if(choice.str("finish_reason")=="length")throw IOException("回复被模型长度限制截断")
     if(choice.str("finish_reason").isNotEmpty())finished=true
    }
    body.charStream().use{reader->val line=StringBuilder();val b=CharArray(2048)
     while(!call.isCanceled()&&!finished){val n=reader.read(b);if(n<0)break;readChars+=n;require(readChars<=1_000_000){"模型响应过大"}
      for(i in 0 until n){val ch=b[i];if(ch=='\n'){event(decoder.line(line.toString().removeSuffix("\r")));line.setLength(0)}else line.append(ch);if(finished)break}
     }
     if(!finished&&line.isNotEmpty())event(decoder.line(line.toString().removeSuffix("\r")))
     if(!finished)event(decoder.finish())
    }
    require(finished&&chars>0){"回复流未完整结束"};close()
   }}catch(e:Exception){close(e)}}
  });awaitClose{call.cancel()}
 }
}
