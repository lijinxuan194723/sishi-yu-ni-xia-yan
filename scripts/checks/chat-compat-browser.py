"""Explicit offline replay of built CSS/JS. Native requests and storage are fixtures.
Does not validate the production CSP, real IndexedDB, gallery, IME or Android GPU.
"""
from pathlib import Path
import json,base64,re,sys,traceback
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[2];WEB=ROOT/'work/mobile-web';OUT=ROOT/'work/chat-compat';OUT.mkdir(parents=True,exist_ok=True)
rows=[];errors=[]
def check(name,value,detail=None):
 rows.append({'name':name,'passed':bool(value),'detail':detail});print(('PASS ' if value else 'FAIL ')+name,flush=True)
 if not value:raise AssertionError(name+': '+str(detail))
def nav(page,name):page.locator('.main-nav').get_by_role('tab',name=name,exact=True).click();page.wait_for_timeout(300)
def settings(page):
 nav(page,'回到身边');page.locator('main>header').get_by_role('button',name='打开设置',exact=True).click();page.get_by_role('dialog').wait_for();page.get_by_role('tab',name='头像与聊天样式',exact=True).click();page.wait_for_timeout(250)
def close(page):page.get_by_role('dialog').locator('[data-slot=dialog-close]').click();page.wait_for_timeout(300)
seed={'luke-companion-v1':{'name':'测试','since':'2024-01-01','messages':[{'who':'luke','text':'今天，也想听你说说身边的小事。','at':'2026-09-17T01:00:00Z'},{'who':'me','text':'早安','at':'2026-09-17T01:01:00Z'},{'who':'luke','text':'早安。等你忙完，我们再慢慢聊。','at':'2026-09-17T01:02:00Z'}],'tasks':[],'notes':[],'checks':[],'memoryArchive':{'version':1,'enabled':False,'autoRevision':1,'chapters':[],'facts':[]}},'luke-appearance-v1':{'season':'spring','period':'正午','effects':True},'luke-backup-confirmed':'9999999999999','luke-display-v205':{'version':1,'scale':1,'chatSize':16,'bubble':'tea','avatar':'/images/companions/dog.webp','lukeAvatar':'cat'},'luke-display-v206':{'version':2,'scale':.95,'chatSize':13,'bubbleMine':'plain','bubbleLuke':'sunflower','avatarMine':'/images/companions/dog.webp','avatarLuke':'/images/companions/toast.webp','extra209':{'retain':True}}}
SETUP=r'''({seed,images})=>{
 if(!crypto.randomUUID)crypto.randomUUID=()=>{const b=crypto.getRandomValues(new Uint8Array(16));b[6]=(b[6]&15)|64;b[8]=(b[8]&63)|128;const h=[...b].map(x=>x.toString(16).padStart(2,'0')).join('');return h.slice(0,8)+'-'+h.slice(8,12)+'-'+h.slice(12,16)+'-'+h.slice(16,20)+'-'+h.slice(20)};
 class MemoryStorage{constructor(){this.data=new Map}get length(){return this.data.size}getItem(k){return this.data.get(k)??null}setItem(k,v){this.data.set(String(k),String(v))}removeItem(k){this.data.delete(k)}clear(){this.data.clear()}key(i){return [...this.data.keys()][i]??null}}
 const store=new MemoryStorage();Object.defineProperty(window,'localStorage',{configurable:true,value:store});Object.defineProperty(window,'sessionStorage',{configurable:true,value:new MemoryStorage});Object.defineProperty(window,'indexedDB',{configurable:true,value:undefined});
 for(const [k,v] of Object.entries(seed))store.setItem(k,typeof v==='string'?v:JSON.stringify(v));
 const desc=Object.getOwnPropertyDescriptor(HTMLImageElement.prototype,'src');Object.defineProperty(HTMLImageElement.prototype,'src',{...desc,set(v){desc.set.call(this,images[v]??v)}});
 const original=Element.prototype.setAttribute;Element.prototype.setAttribute=function(name,value){return original.call(this,name,this instanceof HTMLImageElement&&name==='src'?(images[value]??value):value)};
 window.LukeAndroid={pageReady(){delete document.documentElement.dataset.nativeLaunching},systemTheme(){},haptic(){},cancel(){},request(id){queueMicrotask(()=>window.__lukeNetwork?.(id,503,'{}'))}};
}'''
images={'/'+p.relative_to(WEB).as_posix():'data:image/webp;base64,'+base64.b64encode(p.read_bytes()).decode() for p in (WEB/'images').rglob('*.webp')}
for k,v in list(images.items()):
 for suffix in ['.png','.jpg','.jpeg']:images[k[:-5]+suffix]=v
css='\n'.join(p.read_text() for p in (WEB/'assets').glob('*.css'));css=re.sub(r'''url\((['"]?)(/images/[^)'"\s]+)\1\)''',lambda m:'url("'+images.get(m[2],m[2])+'")',css);js=next((WEB/'assets').glob('*.js')).read_text()
with sync_playwright() as pw:
 b=pw.chromium.launch(executable_path='/usr/bin/chromium',args=['--no-sandbox'],headless=True)
 ctx=b.new_context(viewport={'width':393,'height':820},is_mobile=True,has_touch=True,locale='zh-CN',timezone_id='Asia/Shanghai');p=ctx.new_page();p.on('pageerror',lambda e:errors.append(str(e)))
 try:
  p.set_content('<html lang="zh-CN"><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div></body></html>');p.evaluate(SETUP,{'seed':seed,'images':images});p.add_style_tag(content=css);p.add_script_tag(content=js,type='module');p.locator('.main-nav').wait_for();p.wait_for_timeout(700)
  nav(p,'悄悄话');check('2.0.9-shaped preferences applied without being overwritten',p.evaluate("()=>getComputedStyle(document.documentElement).fontSize==='15.2px'&&localStorage.getItem('luke-display-v205').includes('tea')"))
  check('each side uses its saved independent bubble',p.locator('.message.mine .bubble-frame').first.get_attribute('data-skin')=='plain' and p.locator('.message:not(.mine) .bubble-frame').first.get_attribute('data-skin')=='sunflower')
  check('both avatar and bubble tops align',p.locator('.message').first.evaluate("e=>Math.abs(e.querySelector('img').getBoundingClientRect().top-e.querySelector('.bubble-frame').getBoundingClientRect().top)<1"))
  check('short bubble shrinks to its text rather than filling the row',p.locator('.message.mine .bubble-frame').first.bounding_box()['width']<140)
  p.screenshot(path=str(OUT/'chat.png'))
  settings(p);check('two participant settings exist with correct selection semantics',p.get_by_role('button',name='我的外观',exact=True).get_attribute('aria-pressed')=='true')
  p.get_by_role('button',name='我的气泡：心意',exact=True).click();check('changing mine leaves Luke untouched',p.evaluate("()=>{const x=JSON.parse(localStorage.getItem('luke-display-v206'));return x.bubbleMine==='hearts'&&x.bubbleLuke==='sunflower'&&x.extra209.retain}"))
  p.get_by_role('button',name='夏彦的外观',exact=True).click();p.get_by_role('button',name='夏彦的气泡：暖茶',exact=True).click()
  p.locator('input[aria-label="选择夏彦的头像"]').set_input_files(str(ROOT/'public/images/companions/cat.webp'));p.wait_for_function("()=>JSON.parse(localStorage.getItem('luke-display-v206')).avatarLuke.startsWith('data:image/')")
  check('album upload is processed for Luke without changing my avatar',p.evaluate("()=>{const x=JSON.parse(localStorage.getItem('luke-display-v206'));return x.avatarMine==='/images/companions/dog.webp'&&x.avatarLuke.length<300000}"))
  p.get_by_role('button',name='我的外观',exact=True).click();p.locator('input[aria-label="选择我的头像"]').set_input_files(str(ROOT/'public/images/companions/toast.webp'));p.wait_for_function("()=>JSON.parse(localStorage.getItem('luke-display-v206')).avatarMine.startsWith('data:image/')")
  check('both uploaded avatars are retained',p.evaluate("()=>{const x=JSON.parse(localStorage.getItem('luke-display-v206'));return x.avatarMine.startsWith('data:')&&x.avatarLuke.startsWith('data:')}"))
  p.screenshot(path=str(OUT/'appearance.png'));close(p);nav(p,'悄悄话')
  check('header now also uses configured Luke photo',p.locator('.chat-head>.chat-avatar').get_attribute('data-personal-photo')=='true')
  check('uploads receive round photo presentation, cutouts do not gain a background',p.locator('.message.mine>.chat-avatar').first.evaluate("e=>getComputedStyle(e).borderRadius==='50%'&&getComputedStyle(e).padding==='0px'"))
  states=[]
  for width in [320,393,430]:
   for scale,size in [(.75,10),(.95,13),(1.6,32)]:
    p.set_viewport_size({'width':width,'height':820});p.evaluate("([scale,chatSize])=>{const x=JSON.parse(localStorage.getItem('luke-display-v206'));localStorage.setItem('luke-display-v206',JSON.stringify({...x,scale,chatSize}));dispatchEvent(new Event('luke-display-change'))}",[scale,size]);p.wait_for_timeout(100)
    result=p.evaluate("()=>({clear:[...document.querySelectorAll('.message')].every(e=>{const a=e.querySelector('img').getBoundingClientRect(),b=e.querySelector('.bubble-frame').getBoundingClientRect();return Math.abs(a.top-b.top)<1&&(a.right<=b.left+1||a.left>=b.right-1)&&b.left>=-1&&b.right<=innerWidth+1}),size:getComputedStyle(document.querySelector('.bubble-frame p')).fontSize})")
    states.append({'width':width,'scale':scale,'chatSize':size,**result});check(f'avatar/bubble geometry and independent type {width}px {scale}/{size}',result['clear'] and result['size']==str(size)+'px',result)
  (OUT/'matrix.json').write_text(json.dumps(states,indent=2))
  p.set_viewport_size({'width':393,'height':820});settings(p);p.get_by_role('slider',name='聊天字号',exact=True).focus();p.keyboard.press('End');check('keyboard End reaches expanded 32px maximum',p.evaluate("()=>JSON.parse(localStorage.getItem('luke-display-v206')).chatSize===32"));p.get_by_role('button',name='恢复默认字号').click();check('reset chat restores 13px without resetting explicit global scale',p.evaluate("()=>{const x=JSON.parse(localStorage.getItem('luke-display-v206'));return x.chatSize===13&&x.scale===1.6}"))
  check('no unhandled JavaScript errors',not errors,errors)
 except Exception:
  traceback.print_exc();rows.append({'name':'suite completes','passed':False,'detail':traceback.format_exc()});p.screenshot(path=str(OUT/'failure.png'))
 finally:
  result={'environment':'Built production CSS/JS offline replay in Chromium; simulated localStorage and native bridge; no production CSP/IndexedDB/native Android claim','passed':sum(x['passed'] for x in rows),'failed':sum(not x['passed'] for x in rows),'checks':rows,'errors':errors};(OUT/'results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));print(json.dumps(result,ensure_ascii=False,indent=2));b.close()
sys.exit(1 if result['failed'] else 0)
