"""Offline production-resource renderer. Documented test memory and Android/network bridge.
The local HTTP attempt was blocked by policy; this is not an IndexedDB/CSP/device test.
"""
from pathlib import Path
import ast,json,re,base64,hashlib,time
ROOT=Path(__file__).resolve().parents[2];WEB=ROOT/'work/mobile-web';OUT=ROOT/'work/verify210';OUT.mkdir(parents=True,exist_ok=True)
module=ast.parse((ROOT/'scripts/checks/chat-compat-browser.py').read_text())
SETUP=next(ast.literal_eval(n.value) for n in module.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='SETUP' for t in n.targets))
BASE_SEED=next(ast.literal_eval(n.value) for n in module.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='seed' for t in n.targets))
CACHE=None
BRIDGE=r'''()=>{
 window.__fixture={calls:[],streams:[],canceled:[],mode:'normal',pending:[],backup:[],delay:65,recommendations:0};
 const f=window.__fixture;
 if(!crypto.subtle)Object.defineProperty(crypto,'subtle',{configurable:true,value:{digest:async(name,data)=>new Uint8Array(await window.__testDigest(Array.from(new Uint8Array(data)))).buffer}});
 window.LukeAndroid={pageReady(){delete document.documentElement.dataset.nativeLaunching},systemTheme(){},haptic(){},saveBackup(name,text){f.backup.push({name,text})},cancel(id){f.canceled.push(id)},request(id,url,method,headers,body){f.calls.push({id,url,method,headers,body});
  let status=200,data={};
  if(url.includes('api.open-meteo.com')){const t=Math.floor(Date.now()/1000);data={utc_offset_seconds:28800,current:{time:t,temperature_2m:22,weather_code:61,apparent_temperature:21,relative_humidity_2m:72,wind_speed_10m:9,is_day:1},daily:{time:Array.from({length:7},(_,i)=>t+i*86400),weather_code:[61,2,0,0,3,71,1],temperature_2m_max:[24,26,27,26,22,18,24],temperature_2m_min:[18,19,20,18,17,14,16]},hourly:{time:Array.from({length:24},(_,i)=>t+i*3600),temperature_2m:Array.from({length:24},(_,i)=>22-i%5),weather_code:Array.from({length:24},()=>61),precipitation_probability:Array.from({length:24},()=>60)}};}
  else if(url.includes('holiday')){status=503;}
  else if(url==='https://api.github.com/repos/fixture/skills'){data={private:false,default_branch:'main'};}
  else if(url.includes('api.github.com/repos/fixture/skills/commits/')){data={sha:'a'.repeat(40)};}
  else if(url.includes('api.github.com/repos/fixture/skills/git/trees/')){data={truncated:false,tree:[{path:'reading-helper/SKILL.md',size:140,mode:'100644',type:'blob'},{path:'reading-helper/references/reading.md',size:80,mode:'100644',type:'blob'},{path:'reading-helper/scripts/run.py',size:100,mode:'100644',type:'blob'}]};}
  else if(url.includes('raw.githubusercontent.com/fixture/skills/')){const txt=url.endsWith('SKILL.md')?'---\nname: github-helper\ndescription: A fixture only\n---\nUse clear explanations.': 'reading example from pinned commit';setTimeout(()=>{if(!f.canceled.includes(id))window.__lukeNetwork?.(id,200,txt);},f.delay);return;}
  else if(url.includes('test.model.invalid')){let content='测试回复：我在这里，慢慢说。';const q=JSON.parse(body);if(q.messages?.[0]?.content?.includes('你是聊天记忆整理器')){const batch=JSON.parse(q.messages[1].content),src=batch.records.find(m=>m.who==='me'&&m.text.includes('钢琴'));content=JSON.stringify({summary:'用户明确喜欢安静的钢琴音乐。',facts:src?[{key:'音乐喜好',value:'喜欢安静的钢琴音乐',quote:'我喜欢安静的钢琴音乐',sourceIndex:src.index}]:[]});}else if(q.messages?.[0]?.content?.includes('现在主动给用户推荐')){const book=q.messages[0].content.includes('一本真实出版');f.recommendations++;content=JSON.stringify({title:(book?'测试书籍':'测试歌曲')+f.recommendations,creator:'测试作者',thought:'这是受控数据，不是真实推荐；一起看看适合你的特点。',about:'测试简介',bookKind:'测试'});}data={choices:[{message:{content}}]};}
  else {status=503;}
  setTimeout(()=>{if(!f.canceled.includes(id))window.__lukeNetwork?.(id,status,JSON.stringify(data));},f.delay);
 },requestStream(id,url,method,headers,body){f.streams.push({id,url,method,headers,body});const publish=()=>{if(f.canceled.includes(id))return;let content='测试回复：今天也在这里陪你。';try{const q=JSON.parse(body);if(q.messages?.[0]?.content?.includes('你是聊天记忆整理器')){const batch=JSON.parse(q.messages[1].content);const src=batch.records.find(m=>m.who==='me'&&m.text.includes('钢琴'));content=JSON.stringify({summary:'用户明确喜欢安静的钢琴音乐。',facts:src?[{key:'音乐喜好',value:'喜欢安静的钢琴音乐',quote:'我喜欢安静的钢琴音乐',sourceIndex:src.index}]:[]});}else if(q.messages?.[0]?.content?.includes('现在主动给用户推荐')){const book=q.messages[0].content.includes('一本真实出版');f.recommendations++;content=JSON.stringify({title:(book?'测试书籍':'测试歌曲')+f.recommendations,creator:'测试作者',thought:'这是受控数据，不是真实推荐；一起看看适合你的特点。',about:'测试简介',bookKind:'测试'});}}catch{}window.__lukeStreaming?.(id,200,'text/event-stream','data: '+JSON.stringify({choices:[{delta:{content:content.slice(0,7)}}]})+'\n\n',false);setTimeout(()=>{if(!f.canceled.includes(id))window.__lukeStreaming?.(id,200,'text/event-stream','data: '+JSON.stringify({choices:[{delta:{content:content.slice(7)}}]})+'\n\ndata: [DONE]\n\n',true);},90);};
  if(f.mode==='hold')f.pending.push(publish);else setTimeout(publish,f.delay);
 }};
}'''
def assets():
 global CACHE
 if CACHE:return CACHE
 images={'/'+p.relative_to(WEB).as_posix():'data:image/webp;base64,'+base64.b64encode(p.read_bytes()).decode() for p in (WEB/'images').rglob('*.webp')}
 for k,v in list(images.items()):
  for ext in ['.png','.jpg','.jpeg']:images[k[:-5]+ext]=v
 css='\n'.join(p.read_text() for p in (WEB/'assets').glob('*.css'));css=re.sub(r'''url\((['"]?)(/images/[^)'"\s]+)\1\)''',lambda m:'url("'+images.get(m[2],m[2])+'")',css)
 js=next((WEB/'assets').glob('*.js')).read_text();CACHE=(images,css,js);return CACHE

def seed():
 s=json.loads(json.dumps(BASE_SEED));s.pop('luke-display-v205',None);s.pop('luke-display-v206',None)
 s['luke-model-credentials-v1']={'baseUrl':'https://test.model.invalid/v1','model':'fixture','key':'local-test-only'}
 s['luke-connections-v1']={'model':{'baseUrl':'https://test.model.invalid/v1','model':'fixture','key':'local-test-only'},'weather':{'provider':'open-meteo','lat':'30','lon':'120','place':'测试城市','effects':True,'key':''}}
 return s

def boot(ctx,s=None,errors=None):
 p=ctx.new_page();p.set_default_timeout(8000)
 if errors is not None:p.on('pageerror',lambda e:errors.append(str(e)))
 p.expose_function('__testDigest',lambda data:list(hashlib.sha256(bytes(data)).digest()))
 p.set_content('<html lang="zh-CN"><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div></body></html>')
 images,css,js=assets();p.evaluate(SETUP,{'seed':s or seed(),'images':images});p.evaluate(BRIDGE);p.add_style_tag(content=css);p.add_script_tag(content=js,type='module');p.locator('.main-nav').wait_for();p.wait_for_timeout(700);return p

def nav(p,name):p.locator('.main-nav').get_by_role('tab',name=name,exact=True).click();p.wait_for_timeout(380)
def settings(p,name=None):
 nav(p,'回到身边');p.locator('main>header').get_by_role('button',name='打开设置',exact=True).click();p.get_by_role('dialog').wait_for()
 if name:p.get_by_role('dialog').get_by_role('tab',name=name,exact=True).click()
 p.wait_for_timeout(280);return p.get_by_role('dialog').last

def snapshot_store(p):return p.evaluate('()=>Object.fromEntries(Array.from({length:localStorage.length},(_,i)=>{const k=localStorage.key(i);return [k,localStorage.getItem(k)]}))')
