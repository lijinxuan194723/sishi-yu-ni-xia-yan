package cn.sishiyuni.core.skills

import android.content.Context
import android.net.Uri
import cn.sishiyuni.core.data.*
import cn.sishiyuni.core.model.*
import cn.sishiyuni.core.network.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64
import java.util.zip.ZipInputStream

data class SkillCandidate(val skill:SkillEntity,val warnings:List<String>)
object SkillParser {
 const val MAX_ARCHIVE=10*1024*1024
 fun utf8(bytes:ByteArray):String=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
 fun safePath(path:String):Boolean=path.isNotBlank()&&path.length<=500&&!path.startsWith('/')&&!path.startsWith('\\')&&!Regex("^[A-Za-z]:").containsMatchIn(path)&&path.replace('\\','/').split('/').none{it==".."||it=="."}&&!path.contains('\u0000')
 fun parse(text:String,source:String,fallback:String="本地技能",files:String="[]"):SkillCandidate {
  bounded(text,64000,"技能说明");require(text.isNotBlank()){"技能说明为空"}
  val front=if(text.startsWith("---\n")||text.startsWith("---\r\n"))text.substringAfter('\n').substringBefore("\n---").trim()else ""
  fun field(key:String):String=Regex("(?m)^$key:\\s*(.+)$").find(front)?.groupValues?.get(1)?.trim()?.trim('\"','\'')?:""
  val name=field("name").ifBlank{Regex("(?m)^# +(.+)$").find(text)?.groupValues?.get(1)?:fallback}.take(80)
  val desc=field("description").let{if(it in setOf("|",">","|-",">-"))front.lineSequence().dropWhile{!it.startsWith("description:")}.drop(1).takeWhile{it.startsWith(" ")}.joinToString(" "){it.trim()}else it}.take(500)
  val digest=text.sha256();val identity=if(source=="local")name.lowercase()else source
  val hasExecution=Regex("(?i)\\b(bash|powershell|python|node|sudo|curl)\\b|scripts/").containsMatchIn(text)
  return SkillCandidate(SkillEntity(identity.sha256(),name,desc,text,source,digest,false,files=files),buildList{add("安装后默认停用；确认启用后，仅向模型提供技能说明。");if(hasExecution)add("这个技能提及脚本或命令。本应用不执行脚本，不授予设备控制权限。")})
 }
 fun zip(bytes:ByteArray):List<SkillCandidate>{
  require(bytes.size<=MAX_ARCHIVE){"ZIP 不得超过 10 MB"}
  val files=linkedMapOf<String,String>();var total=0;var count=0
  ZipInputStream(bytes.inputStream()).use{zip->while(true){val e=zip.nextEntry?:break;count++;require(count<=512){"ZIP 文件数量过多"};require(safePath(e.name)){"ZIP 包含不安全路径"};if(e.isDirectory){zip.closeEntry();continue}
   require(e.size<=2_000_000){"ZIP 单文件过大"};val content=zip.readLimited(2_000_000);total+=content.size;require(total<=8_000_000){"ZIP 解压总量超过限制"}
   val path=e.name.replace('\\','/');require(!files.containsKey(path)){"ZIP 路径重复"}
   if(path.endsWith(".md",true)||path.endsWith(".txt",true)||path.endsWith(".json",true)){files[path]=utf8(content)}else files[path]=""
   zip.closeEntry()
  }}
  val names=files.keys.filter{it.substringAfterLast('/').equals("SKILL.md",true)};require(names.isNotEmpty()&&names.size<=50){"ZIP 中没有 SKILL.md，或技能数量过多"}
  return names.map{path->val parent=path.substringBeforeLast('/',"");val refs=files.filter{(k,v)->k!=path&&k.startsWith(if(parent.isEmpty())"" else "$parent/")&&v.isNotEmpty()}.entries.take(20)
   val encoded=buildJsonArray{refs.forEach{(name,value)->add(buildJsonObject{put("path",name.removePrefix("$parent/"));put("text",value.take(12000))})}}.toString()
   parse(files.getValue(path),"local",parent.substringAfterLast('/').ifBlank{"本地技能"},encoded)
  }
 }
}
class SkillInstaller(private val context:Context,private val http:Http,private val dao:LukeDao){
 suspend fun inspect(uri:Uri):List<SkillCandidate> = withContext(Dispatchers.IO){val bytes=context.contentResolver.openInputStream(uri).use{requireNotNull(it){"无法打开技能文件"}.readLimited(SkillParser.MAX_ARCHIVE)};if(bytes.size>=4&&bytes[0]==0x50.toByte()&&bytes[1]==0x4b.toByte())SkillParser.zip(bytes)else listOf(SkillParser.parse(SkillParser.utf8(bytes),"local"))}
 suspend fun github(value:String):List<SkillCandidate>{
  val url=httpsUrl(value);require(url.host in setOf("github.com","raw.githubusercontent.com")&&url.query==null){"仅支持公开 GitHub 仓库或 SKILL.md 地址"}
  val p=url.pathSegments.filter{it.isNotEmpty()};require(p.size>=2&&p.take(2).all{Regex("[A-Za-z0-9_.-]+").matches(it)}){"GitHub 地址格式无效"}
  val owner=p[0];val repo=p[1].removeSuffix(".git")
  suspend fun content(path:String,ref:String):String{
   require(SkillParser.safePath(path))
   val api=httpsUrl("https://api.github.com/repos/$owner/$repo/contents").newBuilder().addPathSegments(path).addQueryParameter("ref",ref).build()
   val data=http.json(api.toString(),1_000_000).jsonObject;require(data.str("encoding")=="base64"&&data.num("size")<=64000){"技能文件编码或大小不受支持"}
   return SkillParser.utf8(Base64.getMimeDecoder().decode(data.str("content")))
  }
  if(url.host=="raw.githubusercontent.com"||p.getOrNull(2)=="blob"){
   val offset=if(url.host=="raw.githubusercontent.com")2 else 3;require(p.size>offset+1){"请提供完整的 SKILL.md 链接"};val ref=p[offset];val path=p.drop(offset+1).joinToString("/");require(path.substringAfterLast('/').equals("SKILL.md",true)){"请选择 SKILL.md 文件"}
   val canonical="https://github.com/$owner/$repo/blob/$ref/$path";return listOf(SkillParser.parse(content(path,ref),canonical))
  }
  require(p.size==2){"目录链接请改用仓库首页，或完整 SKILL.md 文件链接"}
  val metadata=http.json("https://api.github.com/repos/$owner/$repo",100000).jsonObject
  val ref=metadata.str("default_branch");require(ref.isNotBlank()){"仓库默认分支不可用"}
  val treeUrl=httpsUrl("https://api.github.com/repos/$owner/$repo/git/trees").newBuilder().addPathSegment(ref).addQueryParameter("recursive","1").build()
  val tree=http.json(treeUrl.toString(),2_000_000).jsonObject;require(!tree.flag("truncated")){"仓库过大，请使用单个 SKILL.md 链接"}
  val paths=tree.arr("tree").map{it.jsonObject}.filter{it.str("type")=="blob"&&it.str("path").substringAfterLast('/').equals("SKILL.md",true)}
  require(paths.size in 1..20){"未找到技能，或仓库含超过 20 个技能；请使用单个文件链接"}
  return paths.map{e->currentCoroutineContext().ensureActive();val path=e.str("path");SkillParser.parse(content(path,ref),"https://github.com/$owner/$repo/blob/$ref/$path")}
 }
 suspend fun install(candidate:SkillCandidate){require(candidate.skill.content.sha256()==candidate.skill.digest);dao.putSkill(candidate.skill.copy(enabled=false))}
 suspend fun setEnabled(id:String,enabled:Boolean){val skill=dao.allSkills().find{it.id==id}?:error("技能不存在");dao.putSkill(skill.copy(enabled=enabled))}
 suspend fun uninstall(id:String){dao.deleteSkill(id)}
}
