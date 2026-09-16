package cn.sishiyuni.core

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.sishiyuni.core.backup.BackupService
import cn.sishiyuni.core.data.*
import cn.sishiyuni.core.memory.MemoryRepository
import cn.sishiyuni.core.network.*
import cn.sishiyuni.core.skills.SkillInstaller
import cn.sishiyuni.core.timer.TimerRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

interface GraphOwner { val graph:AppGraph }
class AppGraph(val context:Context){
 val errors=MutableStateFlow<String?>(null)
 val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate+CoroutineExceptionHandler{_,e->errors.value=e.message?:"操作失败，请重试"})
 val db=LukeDatabase.open(context);val dao=db.dao();val prefs=PreferencesStore(context,scope)
 val drafts=ChatDraftRepository({id->dao.session(id)?.draft},{id,text->dao.draft(id,text)},scope)
 val vault=SecretVault(context);val http=Http();val model=ModelClient(http);val images=ImageStore(context)
 val weather=WeatherRepository(http,dao,prefs);val holidays=HolidayRepository(context,dao,http,prefs)
 val timer=TimerRepository(context,db);val skills=SkillInstaller(context,http,dao);val backup=BackupService(context,db,prefs)
 val memory=MemoryRepository(this);val chat=ChatRepository(this);val moments=MomentsRepository(this)
 val ready=MutableStateFlow(false)
 init{scope.launch(Dispatchers.IO){
  try{prefs.ready.filter{it}.first();backup.finishPendingSettings()
   if(dao.allSessions().isEmpty())dao.putSession(SessionEntity("legacy"))
   if(dao.session(prefs.state.value.activeSession)==null)prefs.text("activeSession",dao.allSessions().first().id)
   dao.allMessages().filter{it.status=="streaming"}.forEach{dao.updateMessage(it.id,it.text,"interrupted")}
   timer.restore();ready.value=true
  }catch(e:Exception){errors.value="本机数据初始化失败，未清除数据：${e.message}"}
 }}
 suspend fun connection(fallback:Boolean=false):ModelConnection=withContext(Dispatchers.IO){
  val p=prefs.state.value;val url=if(fallback)p.fallbackUrl else p.modelUrl;val model=if(fallback)p.fallbackModel else p.modelName
  val bound=vault.read(if(fallback)"fallback" else "primary");val origin=bound.substringBefore('\n');val key=bound.substringAfter('\n',"")
  require(origin==completionUrl(url).toString()&&key.isNotBlank()){ "请先在设置中保存这个服务的模型密钥" };ModelConnection(url,model,key).also{it.validate()}
 }
 suspend fun saveConnection(url:String,model:String,key:String,fallback:Boolean=false)=withContext(Dispatchers.IO){
  val endpoint=completionUrl(url).toString();require(model.isNotBlank()&&model.length<=200);require(!key.contains('\n')&&!key.contains('\r'))
  val slot=if(fallback)"fallback" else "primary";val existing=vault.read(slot)
  val value=if(key.isNotBlank())key else existing.takeIf{it.substringBefore('\n')==endpoint}?.substringAfter('\n',"")?:error("更换服务地址时请填写密钥")
  vault.save("$endpoint\n$value",slot)
  prefs.restore(buildJsonObject{put(if(fallback)"fallbackUrl" else "modelUrl",url);put(if(fallback)"fallbackModel" else "modelName",model)})
 }
 suspend fun clearConnection(fallback:Boolean=false)=withContext(Dispatchers.IO){vault.save("",if(fallback)"fallback" else "primary");prefs.restore(buildJsonObject{put(if(fallback)"fallbackUrl" else "modelUrl","");put(if(fallback)"fallbackModel" else "modelName","")})}
}
open class CoreViewModel(protected val graph:AppGraph):ViewModel(){
 val error=MutableStateFlow<String?>(null)
 protected fun task(block:suspend()->Unit)=viewModelScope.launch{try{block()}catch(e:CancellationException){throw e}catch(e:Exception){error.value=e.message?:"操作失败，请重试"}}
 fun clearError(){error.value=null}
}
