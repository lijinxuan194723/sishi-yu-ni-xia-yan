package cn.sishiyuni.feature.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.sishiyuni.core.AppGraph
import cn.sishiyuni.core.model.newId
import cn.sishiyuni.designsystem.*
import kotlinx.serialization.json.*
import kotlin.math.roundToInt

private data class SettingsGroup(val id:String,val title:String,val detail:String,val icon:ImageVector,val keywords:String)
private val groups = listOf(
    SettingsGroup("identity","我们的日常","称呼、相伴日期与生日",Icons.Outlined.FavoriteBorder,"昵称 姓名 日期"),
    SettingsGroup("appearance","四季与昼夜","主题、通透效果与动态反馈",Icons.Outlined.WbSunny,"季节 春夏秋冬 夜间 透明 动画"),
    SettingsGroup("typography","字号与阅读","全局与聊天分别调整",Icons.Outlined.TextFields,"字体 大小 缩放"),
    SettingsGroup("chat","聊天外观","分别更换双方头像",Icons.Outlined.Face,"照片 相册 我的头像 夏彦头像"),
    SettingsGroup("connection","模型连接","主模型与备用模型",Icons.Outlined.Link,"API 密钥 服务 地址"),
    SettingsGroup("privacy","记忆与分享","自动整理与上下文分享",Icons.Outlined.Lock,"长期记忆 隐私 天气 手记 计划 学习"),
    SettingsGroup("calendar","日历与节假日","本地优先，在线补充",Icons.Outlined.CalendarMonth,"放假 调休 休班 自动更新"),
    SettingsGroup("about","关于与说明","数据来源、许可与免责声明",Icons.Outlined.Info,"天气 来源 版权 Open-Meteo")
)

@Composable
fun SettingsScreen(graph:AppGraph,onBack:()->Unit,onSkills:()->Unit) {
    val vm = nativeViewModel(graph,"settings") { SettingsViewModel(graph) }
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val readError by vm.readError.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val completed by vm.completed.collectAsStateWithLifecycle()
    val holidayBusy by vm.holidayBusy.collectAsStateWithLifecycle()
    val holidayStatus by vm.holidayStatus.collectAsStateWithLifecycle()
    val years by vm.holidayYears.collectAsStateWithLifecycle()
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var dialogId by rememberSaveable { mutableStateOf("") }
    var avatarTarget by rememberSaveable { mutableStateOf("avatarMine") }
    var calendarYear by rememberSaveable { mutableIntStateOf(2026) }
    val now = LocalAppTime.current
    LaunchedEffect(Unit) { calendarYear=now.year.coerceIn(1900,2100) }
    val stateHolder = rememberSaveableStateHolder()
    val reduced = LocalLukeMotion.current.reduced
    fun open(kind:String) { vm.clearError(); dialogId=newId(); dialog=kind }
    fun back() { if(group!=null) group=null else onBack() }
    BackHandler(group!=null && dialog==null) { group=null }
    LaunchedEffect(completed,dialogId) { if(completed==dialogId && dialogId.isNotEmpty()) dialog=null }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if(uri!=null) vm.avatar(avatarTarget,uri)
    }
    val title=groups.find { it.id==group }?.title?:"设置"
    NativeScreenFrame(title,onBack=::back) { padding ->
        AnimatedContent(group,label="settings-section",transitionSpec={
            if(reduced) EnterTransition.None togetherWith ExitTransition.None
            else (fadeIn(tween(140))+slideInHorizontally(spring(dampingRatio=1f,stiffness=550f)){it/14}) togetherWith fadeOut(tween(90))
        }) { section ->
            stateHolder.SaveableStateProvider(section?:"index") {
                if(section==null) {
                    val found=remember(query) { val key=query.trim(); groups.filter { key.isEmpty() || (it.title+it.detail+it.keywords).contains(key,ignoreCase=true) } }
                    LayeredContent(padding,header={
                        OutlinedTextField(query,{query=it.take(100)},Modifier.fillMaxWidth().testTag("settings-search"),singleLine=true,
                            placeholder={Text("搜索设置")},leadingIcon={Icon(Icons.Outlined.Search,null)},
                            trailingIcon={if(query.isNotEmpty()) IconButton(onClick={query=""}){Icon(Icons.Outlined.Close,"清除搜索")}},shape=RoundedCornerShape(24.dp))
                        ErrorNotice(error,vm::clearError)
                        if(readError!=null) Text(readError.orEmpty(),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                    }) { insets ->
                        LazyColumn(Modifier.fillMaxSize().testTag("settings-list"),contentPadding=insets,verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            items(found,key={it.id}) { item ->
                                LukeCard(Modifier.fillMaxWidth().clickable(enabled=ready){group=item.id}.testTag("settings-group-${item.id}"),padding=14.dp) {
                                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                                        Icon(item.icon,null,Modifier.size(24.dp),tint=LocalSeason.current.accent)
                                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                                            Text(item.title,style=MaterialTheme.typography.titleSmall)
                                            Text(item.detail,style=MaterialTheme.typography.bodySmall,color=LocalSeason.current.muted)
                                        }
                                        Icon(Icons.AutoMirrored.Outlined.ArrowForwardIos,null,Modifier.size(14.dp),tint=LocalSeason.current.muted)
                                    }
                                }
                            }
                            if(query.isBlank() || "skills 技能".contains(query.trim(),ignoreCase=true)) item {
                                SettingsAction("Skills","安装与管理聊天技能",onSkills,"settings-skills")
                            }
                            if(found.isEmpty() && !"skills 技能".contains(query.trim(),ignoreCase=true)) item { Text("没有匹配的设置",style=MaterialTheme.typography.bodyMedium) }
                        }
                    }
                } else {
                    val direction=LocalLayoutDirection.current
                    val insets=PaddingValues(start=padding.calculateStartPadding(direction)+16.dp,end=padding.calculateEndPadding(direction)+16.dp,
                        top=padding.calculateTopPadding()+8.dp,bottom=padding.calculateBottomPadding()+24.dp)
                    LazyColumn(Modifier.fillMaxSize().consumeWindowInsets(padding).testTag("settings-section-$section"),contentPadding=insets,
                        verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        if(error!=null) item { ErrorNotice(error,vm::clearError) }
                        when(section) {
                            "identity" -> {
                                item { SettingsAction("称呼",prefs.name,{open("identity")},"identity-name") }
                                item { SettingsAction("相伴日期",prefs.since,{open("identity")},"identity-since") }
                                item { SettingsAction("生日",prefs.birthday.ifBlank{"未设置"},{open("identity")},"identity-birthday") }
                            }
                            "appearance" -> {
                                item { SettingsChoices("四季",listOf("auto" to "随时序","spring" to "春","summer" to "夏","autumn" to "秋","winter" to "冬"),prefs.season){vm.text("season",it)} }
                                item { SettingsChoices("昼夜",listOf("auto" to "随时间","day" to "日间","night" to "夜间"),prefs.period){vm.text("period",it)} }
                                item { SettingsSwitch("通透效果","透明渐隐的顶部与底部",prefs.glass,"glass"){vm.flag("glass",it)} }
                                item { SettingsSwitch("场景动态","天气与四季的轻量动画",prefs.effects,"effects"){vm.flag("effects",it)} }
                                item { SettingsSwitch("减少动态效果","使用简短、直接的状态切换",prefs.reduceMotion,"reduceMotion"){vm.flag("reduceMotion",it)} }
                                item { SettingsSwitch("触感反馈","选择与操作时的轻触感",prefs.haptics,"haptics"){vm.flag("haptics",it)} }
                            }
                            "typography" -> {
                                item { SizeSetting("全局字号","scale",prefs.scale*100f,75f..160f,84,"%",pending>0){vm.size("scale",it/100f)} }
                                item { SizeSetting("聊天字号","chatSize",prefs.chatSize,10f..32f,21,"",pending>0){vm.size("chatSize",it)} }
                                item { LukeCard(Modifier.fillMaxWidth()) {
                                    Text("把日子慢慢写下来",style=MaterialTheme.typography.titleMedium)
                                    Text("文字随字号变化，按钮保留舒适的点击范围。",style=MaterialTheme.typography.bodyMedium)
                                } }
                                item { OutlinedButton(onClick=vm::resetTypography,modifier=Modifier.fillMaxWidth().testTag("reset-typography"),enabled=pending==0){Text("恢复默认字号")} }
                            }
                            "chat" -> {
                                item { AvatarSetting("夏彦的头像",prefs.avatarLuke,"avatarLuke") { avatarTarget="avatarLuke";photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
                                item { AvatarSetting("我的头像",prefs.avatarMine,"avatarMine") { avatarTarget="avatarMine";photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
                                item { SettingsAction("恢复内置头像","只恢复头像，不改变气泡与字号",{open("reset-avatars")},"reset-avatars") }
                            }
                            "connection" -> {
                                item { SettingsAction("主模型",prefs.modelName.ifBlank{"未配置"},{open("primary")},"model-primary") }
                                item { SettingsAction("备用模型",prefs.fallbackModel.ifBlank{"未配置"},{open("fallback")},"model-fallback") }
                                item { Text("密钥加密保存在本机，不随备份导出。更换服务地址时需要重新填写。",style=MaterialTheme.typography.bodySmall,color=LocalSeason.current.muted) }
                            }
                            "privacy" -> {
                                item { SettingsSwitch("自动长期记忆","聊天后自动整理，原对话保留",prefs.autoMemory,"autoMemory"){vm.flag("autoMemory",it)} }
                                item { SettingsSwitch("推荐参考记忆","歌曲与书籍推荐结合已有喜好",prefs.useRecommendationMemory,"useRecommendationMemory"){vm.flag("useRecommendationMemory",it)} }
                                item { Text("聊天中的上下文分享",style=MaterialTheme.typography.titleMedium) }
                                items(listOf("weather" to "天气","reading" to "共读","study" to "学习记录","plans" to "一起计划","notes" to "时光手记","dates" to "重要日期"),key={it.first}) { (key,label) ->
                                    SettingsSwitch(label,"允许在相关聊天中使用",prefs.sharing[key]?:true,"sharing-$key"){vm.flag("sharing-$key",it)}
                                }
                            }
                            "calendar" -> {
                                item { SettingsSwitch("自动检查更新","优先使用已保存的年度安排",prefs.holidayAuto,"holidayAuto"){vm.flag("holidayAuto",it)} }
                                item { SettingsSwitch("在线备用","缺少年度安排时尝试备用来源",prefs.holidayFallback,"holidayFallback"){vm.flag("holidayFallback",it)} }
                                item { LukeCard(Modifier.fillMaxWidth()) {
                                    Row(verticalAlignment=Alignment.CenterVertically) {
                                        IconButton(onClick={calendarYear--},enabled=calendarYear>1900 && !holidayBusy){Icon(Icons.Outlined.ChevronLeft,"上一年")}
                                        Text("$calendarYear 年",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                                        IconButton(onClick={calendarYear++},enabled=calendarYear<2100 && !holidayBusy){Icon(Icons.Outlined.ChevronRight,"下一年")}
                                    }
                                    Text(years[calendarYear]?.let{"已收录 ${it.entries.size} 条休班安排"}?:if(calendarYear in 2023..2026)"已内置年度安排" else "尚未载入年度安排",style=MaterialTheme.typography.bodyMedium)
                                    if(holidayStatus.isNotBlank()) Text(holidayStatus,style=MaterialTheme.typography.bodySmall,color=LocalSeason.current.muted)
                                    Button(onClick={vm.refreshCalendar(calendarYear)},enabled=!holidayBusy,modifier=Modifier.fillMaxWidth()){Text(if(holidayBusy)"正在检查" else "检查这个年份")}
                                } }
                            }
                            "about" -> {
                                item { LukeCard(Modifier.fillMaxWidth()) {
                                    Text("四时与你",style=MaterialTheme.typography.titleMedium)
                                    Text("非官方同人项目，与原作权利方无隶属关系。角色及相关素材权利归各自权利人。",style=MaterialTheme.typography.bodyMedium)
                                    Text("模型回复可能不准确。天气与节假日信息仅供日常参考，重要安排请另行核实。",style=MaterialTheme.typography.bodyMedium)
                                } }
                                item { SourceLink("天气数据与许可","Open-Meteo","https://open-meteo.com/en/licence") }
                                item { SourceLink("节假日静态数据","holiday-cn · MIT","https://github.com/NateScarlet/holiday-cn") }
                                item { SourceLink("节假日在线备用","Timor 年度接口","https://timor.tech/api/holiday") }
                            }
                        }
                    }
                }
            }
        }
    }
    val kind=dialog
    if(kind!=null) key(dialogId) {
        when(kind) {
            "identity" -> {
                var name by rememberSaveable { mutableStateOf(prefs.name) }
                var since by rememberSaveable { mutableStateOf(prefs.since) }
                var birthday by rememberSaveable { mutableStateOf(prefs.birthday) }
                SettingsForm("我们的日常",error,pending>0,{dialog=null},vm::clearError,onSave={
                    vm.change(buildJsonObject { put("name",name);put("since",since);put("birthday",birthday) },dialogId)
                }) {
                    OutlinedTextField(name,{name=it},Modifier.fillMaxWidth().testTag("profile-name"),label={Text("称呼")},singleLine=true)
                    OutlinedTextField(since,{since=it},Modifier.fillMaxWidth().testTag("profile-since"),label={Text("相伴日期")},placeholder={Text("YYYY-MM-DD")},singleLine=true)
                    OutlinedTextField(birthday,{birthday=it},Modifier.fillMaxWidth().testTag("profile-birthday"),label={Text("生日（可留空）")},placeholder={Text("YYYY-MM-DD")},singleLine=true)
                }
            }
            "primary","fallback" -> {
                val fallback=kind=="fallback"
                var url by rememberSaveable { mutableStateOf(if(fallback)prefs.fallbackUrl else prefs.modelUrl) }
                var model by rememberSaveable { mutableStateOf(if(fallback)prefs.fallbackModel else prefs.modelName) }
                var secret by remember { mutableStateOf("") } // Never place a plaintext key in saved instance state.
                SettingsForm(if(fallback)"备用模型" else "主模型",error,pending>0,{dialog=null},vm::clearError,
                    onSave={vm.saveConnection(dialogId,url,model,secret,fallback)}) {
                    OutlinedTextField(url,{url=it},Modifier.fillMaxWidth().testTag("model-url"),label={Text("服务地址")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri))
                    OutlinedTextField(model,{model=it},Modifier.fillMaxWidth().testTag("model-name"),label={Text("模型名称")},singleLine=true)
                    OutlinedTextField(secret,{secret=it},Modifier.fillMaxWidth().testTag("model-key"),label={Text("密钥")},placeholder={Text("同一服务留空保留原密钥")},singleLine=true,
                        visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password))
                    TextButton(onClick={open(if(fallback)"clear-fallback" else "clear-primary")},enabled=pending==0){Text("移除此连接")}
                }
            }
            "clear-primary","clear-fallback" -> NativeDialog("移除模型连接",{dialog=null}) {
                ErrorNotice(error,vm::clearError)
                Text("只移除这个连接的地址、模型名与密钥，聊天和记忆不会删除。",style=MaterialTheme.typography.bodyMedium)
                Button(onClick={vm.clearConnection(dialogId,kind=="clear-fallback")},enabled=pending==0){Text("确认移除")}
            }
            "reset-avatars" -> NativeDialog("恢复内置头像",{dialog=null}) {
                ErrorNotice(error,vm::clearError)
                Text("双方头像恢复为内置图片，聊天、气泡与字号保持不变。",style=MaterialTheme.typography.bodyMedium)
                Button(onClick={vm.change(buildJsonObject { put("avatarMine","images/companions/dog.webp");put("avatarLuke","images/companions/cat.webp") },dialogId)},enabled=pending==0){Text("确认恢复")}
            }
        }
    }
}

@Composable private fun SettingsAction(title:String,detail:String,onClick:()->Unit,tag:String) {
    LukeCard(Modifier.fillMaxWidth().clickable(onClick=onClick).testTag(tag),padding=16.dp) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(title,style=MaterialTheme.typography.titleSmall)
                Text(detail,style=MaterialTheme.typography.bodySmall,color=LocalSeason.current.muted,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForwardIos,null,Modifier.size(14.dp),tint=LocalSeason.current.muted)
        }
    }
}
@Composable private fun SettingsSwitch(title:String,detail:String,value:Boolean,tag:String,onChange:(Boolean)->Unit) {
    LukeCard(Modifier.fillMaxWidth(),padding=14.dp) {
        Row(Modifier.fillMaxWidth().heightIn(min=52.dp).toggleable(value=value,role=Role.Switch,onValueChange=onChange).testTag("setting-$tag"),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(title,style=MaterialTheme.typography.titleSmall)
                Text(detail,style=MaterialTheme.typography.bodySmall,color=LocalSeason.current.muted)
            }
            Switch(value,onCheckedChange=null)
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun SettingsChoices(title:String,choices:List<Pair<String,String>>,selected:String,onSelect:(String)->Unit) {
    LukeCard(Modifier.fillMaxWidth()) {
        Text(title,style=MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            choices.forEach { (id,label) -> FilterChip(selected=selected==id,onClick={onSelect(id)},label={Text(label)},modifier=Modifier.heightIn(min=48.dp).testTag("choice-$id")) }
        }
    }
}
@Composable private fun SizeSetting(title:String,key:String,value:Float,range:ClosedFloatingPointRange<Float>,steps:Int,suffix:String,busy:Boolean,onCommit:(Float)->Unit) {
    var chosen by rememberSaveable(key) { mutableFloatStateOf(value) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(value) { if(!dragging) chosen=value }
    LukeCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            Text("${chosen.roundToInt()}$suffix",style=MaterialTheme.typography.labelLarge)
        }
        Slider(value=chosen.coerceIn(range),onValueChange={dragging=true;chosen=it},valueRange=range,steps=steps,
            onValueChangeFinished={dragging=false;onCommit(chosen.roundToInt().toFloat())},modifier=Modifier.testTag("size-$key"))
        if(!dragging && kotlin.math.abs(chosen-value)>.05f && !busy) TextButton(onClick={onCommit(chosen.roundToInt().toFloat())}){Text("保存字号")}
    }
}
@Composable private fun AvatarSetting(title:String,path:String,tag:String,onPick:()->Unit) {
    LukeCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            AssetImage(path,title,Modifier.size(64.dp).testTag("settings-$tag"))
            Column(Modifier.weight(1f)) {
                Text(title,style=MaterialTheme.typography.titleSmall)
                TextButton(onClick=onPick,modifier=Modifier.testTag("pick-$tag")){Text("从相册选择")}
            }
        }
    }
}
@Composable private fun SettingsForm(title:String,error:String?,busy:Boolean,onDismiss:()->Unit,onClear:()->Unit,onSave:()->Unit,fields:@Composable ColumnScope.()->Unit) {
    NativeDialog(title,onDismiss) {
        ErrorNotice(error,onClear)
        Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).testTag("settings-form-fields"),verticalArrangement=Arrangement.spacedBy(12.dp),content=fields)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick=onDismiss,modifier=Modifier.weight(1f)){Text("取消")}
            Button(onClick=onSave,enabled=!busy,modifier=Modifier.weight(1f).testTag("settings-form-save")){Text(if(busy)"保存中" else "保存")}
        }
    }
}
@Composable private fun SourceLink(title:String,detail:String,url:String) {
    val opener=LocalUriHandler.current
    var failed by remember { mutableStateOf(false) }
    SettingsAction(title,detail,{failed=runCatching { opener.openUri(url) }.isFailure},"source-${title.hashCode()}")
    if(failed) Text("暂时无法打开浏览器",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
}
