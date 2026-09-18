"""hotfix.2 production-bundle UI regressions plus a separately labelled storage-hook harness.
About:blank replay, synthetic Android network and MemoryStorage; no native-device/CSP/IDB claim.
Pointer movement/keyboard input is real browser input; blur and visibility interruptions are simulated.
"""
from pathlib import Path
import json,hashlib,traceback,sys
from playwright.sync_api import sync_playwright
import runtime210 as r
OUT=r.ROOT/'work/detail-hotfix2';OUT.mkdir(exist_ok=True,parents=True)
RESULTS=[];ERRORS=[]
def check(name,ok,detail=None):
 row={'name':name,'passed':bool(ok)}
 if detail is not None:row['detail']=detail
 RESULTS.append(row);print(('PASS ' if ok else 'FAIL ')+name,flush=True)
def wait(p,n=220):p.wait_for_timeout(n)
def close(p):
 p.get_by_role('dialog').last.locator('[data-slot=dialog-close]').click();wait(p,500)
def boot(ctx,setup=None,seed=None):
 p=ctx.new_page();p.set_default_timeout(8000);p.on('pageerror',lambda e:ERRORS.append(str(e)));p.expose_function('__testDigest',lambda data:list(hashlib.sha256(bytes(data)).digest()))
 p.set_content('<html lang="zh-CN"><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div></body></html>');images,css,js=r.assets();p.evaluate(r.SETUP,{'seed':seed or r.seed(),'images':images});p.evaluate(r.BRIDGE)
 if setup:p.evaluate(setup)
 p.add_style_tag(content=css);p.add_script_tag(content=js,type='module');p.locator('.main-nav').wait_for();wait(p,700);return p

def dialogs(ctx):
 p=boot(ctx);d=r.settings(p,'外观与字号');before=d.locator('[data-slot=dialog-title]').inner_text();p.evaluate('()=>window.__lukeBack()');wait(p,450)
 check('main settings subpage consumes Back only to return to its own index',p.get_by_role('dialog').count()==1 and p.get_by_role('dialog').get_by_role('tab',name='外观与字号',exact=True).is_visible(),before)
 p.evaluate('()=>window.__lukeBack()');wait(p,450);check('next Back closes main settings',p.get_by_role('dialog').count()==0)
 d=r.settings(p,'外观与字号');close(p);r.nav(p,'悄悄话');p.get_by_role('button',name='对话与技能',exact=True).click();wait(p);p.evaluate('()=>window.__lukeBack()');wait(p,500)
 check('Back closes conversation hub in one press despite last-used settings subpage',p.get_by_role('dialog').count()==0)
 p.get_by_role('button',name='对话与技能',exact=True).click();wait(p);p.get_by_role('button',name='Skills',exact=True).click();wait(p,400);p.evaluate('()=>window.__lukeBack()');wait(p,500)
 check('Back closes Skills without being swallowed by the old settings state',p.get_by_role('dialog').count()==0)
 # Current short draft survives an intervening panel and renderer animations.
 p.locator('.composer textarea').fill('还没发出的第二句话');p.get_by_role('button',name='对话与技能',exact=True).click();wait(p);p.evaluate('()=>window.__lukeBack()');wait(p,500)
 check('closing a tool panel never clears the unsent chat draft',p.locator('.composer textarea').input_value()=='还没发出的第二句话')
 p.screenshot(path=str(OUT/'chat-final.png'));p.close()

def ruler(ctx):
 seed=r.seed();seed['luke-companion-v1']['countdown']={'seconds':180,'remainingMs':80000};p=boot(ctx,seed=seed);r.nav(p,'计时');p.get_by_role('tab',name='提醒',exact=True).click();wait(p)
 field=p.locator('.countdown-setting210 input');slider=p.get_by_role('slider',name='拖动刻度选择倒计时')
 check('restored paused countdown initializes its ruler from saved duration, not a fixed 25 minutes',field.input_value()=='3' and slider.get_attribute('aria-valuenow')=='3')
 panel=p.locator('.in-app-countdown210');panel.get_by_role('button',name='重置',exact=True).click();wait(p);field.fill('4');wait(p,80)
 def begin():
  slider.scroll_into_view_if_needed();bb=slider.bounding_box();x=bb['x']+bb['width']*.65;y=bb['y']+bb['height']*.6;p.mouse.move(x,y);p.mouse.down();p.mouse.move(x-64,y,steps=8);wait(p,60);return x,y
 def running():return p.evaluate("()=>!!JSON.parse(localStorage.getItem('luke-companion-v1')).countdown?.endsAt")
 begin();p.evaluate('()=>dispatchEvent(new Event("blur"))');p.mouse.up();wait(p,200)
 check('window blur during an actual ruler drag cancels instead of auto-starting',not running() and field.input_value()=='4')
 begin();p.evaluate("()=>{Object.defineProperty(document,'hidden',{configurable:true,get:()=>true});document.dispatchEvent(new Event('visibilitychange'))}");p.mouse.up();wait(p,150)
 check('backgrounding while dragging cancels and restores the starting selection',not running() and field.input_value()=='4')
 p.evaluate("()=>{delete document.hidden;document.dispatchEvent(new Event('visibilitychange'))}");wait(p)
 begin();p.evaluate("()=>{const e=document.querySelector('.countdown-ruler210').closest('[role=tabpanel]');e.setAttribute('aria-hidden','true')}");wait(p,80);p.mouse.up();wait(p,180)
 check('hiding a retained section cancels its unconfirmed ruler gesture',not running() and field.input_value()=='4')
 p.evaluate("()=>document.querySelector('.countdown-ruler210').closest('[role=tabpanel]').removeAttribute('aria-hidden')")
 # Add a second contact outside the ruler; this path used to evade local pointer tracking.
 begin();p.evaluate("()=>document.querySelector('.sidebar').dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,pointerId:91,pointerType:'touch',isPrimary:false}))");p.mouse.up();p.evaluate("()=>window.dispatchEvent(new PointerEvent('pointerup',{pointerId:91,pointerType:'touch'}))");wait(p,200)
 check('a second contact outside the ruler cannot commit the first drag',not running() and field.input_value()=='4')
 begin();p.mouse.up();wait(p,220)
 check('the next uninterrupted physical drag still starts exactly once',running() and p.evaluate("()=>JSON.parse(localStorage.getItem('luke-companion-v1')).countdown.seconds===12*60"))
 panel.get_by_role('button',name='暂停',exact=True).click();wait(p);remaining=p.locator('.countdown-value210').inner_text();r.nav(p,'回到身边');r.nav(p,'计时');wait(p,300)
 check('paused time survives page switching without silently restarting',not running() and p.locator('.countdown-value210').inner_text()==remaining)
 panel.get_by_role('button',name='重置',exact=True).click();wait(p);slider.focus();p.keyboard.press('Home');p.keyboard.press('ArrowRight');p.keyboard.press('Enter');wait(p,150)
 check('keyboard Home/right/Enter starts the selected one-minute duration',p.evaluate("()=>JSON.parse(localStorage.getItem('luke-companion-v1')).countdown.seconds===60"))
 p.screenshot(path=str(OUT/'reminder-final.png'));p.close()

def swipe(ctx):
 p=boot(ctx);p.locator('#main-panel-home .section-scroll210').evaluate('e=>e.scrollTop=e.scrollHeight');wait(p,100)
 q=p.locator('#main-panel-home .quote-card p');bb=q.bounding_box();x=bb['x']+bb['width']*.8;y=bb['y']+bb['height']*.5
 p.mouse.move(x,y);p.mouse.down();p.mouse.move(x-95,y+1,steps=10);wait(p,50)
 check('page responds during a real pointer drag before testing interruption',-160<p.locator('#main-panel-home').bounding_box()['x']< -25)
 p.evaluate('()=>dispatchEvent(new Event("blur"))');p.mouse.up();wait(p,400)
 check('interrupted page drag returns to its original page and indicator',p.locator('.page-viewport210').get_attribute('data-page')=='home' and abs(p.locator('.page-viewport210').evaluate("e=>document.querySelector('#main-panel-home').getBoundingClientRect().left-e.getBoundingClientRect().left"))<1)
 # A normal click ends any mouse text selection produced during the interrupted gesture.
 p.mouse.click(bb['x']+4,bb['y']-4);wait(p,70)
 # Reject an input pointer and finish it OUTSIDE the page, as happens with platform UI.
 p.evaluate("()=>document.querySelector('.page-viewport210 input, .page-viewport210 button').dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,pointerId:77,pointerType:'touch',isPrimary:true,button:0,clientX:180,clientY:700}))")
 p.evaluate("()=>window.dispatchEvent(new PointerEvent('pointerup',{pointerId:77,pointerType:'touch'}))")
 p.mouse.move(x,y);p.mouse.down();p.mouse.move(x-130,y+1,steps=10);wait(p,40);mid=p.locator('#main-panel-home').bounding_box()['x'];p.mouse.up();wait(p,650)
 check('an excluded-control contact ending outside the page does not disable later follow-finger navigation',mid< -30 and p.locator('.page-viewport210').get_attribute('data-page')=='chat',{'mid':mid,'page':p.locator('.page-viewport210').get_attribute('data-page'),'selection':p.evaluate('()=>window.getSelection()?.type')})
 check('no extra focus scroll remains on the page carousel',p.locator('.page-viewport210').evaluate('e=>e.scrollLeft===0'))
 p.close()

WEATHER_SETUP=r'''()=>{
 window.__detail2={online:true,pending:[],mode:'hold',calls:0};Object.defineProperty(navigator,'onLine',{configurable:true,get:()=>__detail2.online});Object.defineProperty(document,'hidden',{configurable:true,get:()=>true});
 const previous=LukeAndroid.request;
 LukeAndroid.request=(id,url,...args)=>{
  if(!url.includes('api.open-meteo.com'))return previous(id,url,...args);
  __detail2.calls++;const t=Math.floor(Date.now()/1000),lon=new URL(url).searchParams.get('longitude'),value=lon==='121'?19:31;
  const data={utc_offset_seconds:28800,current:{time:t,temperature_2m:value,weather_code:61,humidity:50},daily:{time:[t-86400,t,t+86400],weather_code:[0,61,0],temperature_2m_max:[value,value,value],temperature_2m_min:[12,13,14]},hourly:{time:[t,t+3600],temperature_2m:[value,value],weather_code:[61,0]}};
  const deliver=()=>window.__lukeNetwork(id,200,JSON.stringify(data));
  if(__detail2.mode==='hold')__detail2.pending.push({id,url,deliver});else setTimeout(deliver,35);
 };
}'''
def weather(ctx):
 p=boot(ctx,setup=WEATHER_SETUP)
 check('hidden app startup does not begin a weather request',p.evaluate('()=>__detail2.calls')==0)
 p.evaluate("()=>{delete document.hidden;document.dispatchEvent(new Event('visibilitychange'))}");wait(p,100)
 check('foregrounding starts just one pending weather request',p.evaluate('()=>__detail2.calls')==1)
 p.evaluate("()=>{__detail2.online=false;dispatchEvent(new Event('offline'))}");wait(p,100)
 p.evaluate('()=>__detail2.pending[0].deliver()');wait(p,100);r.nav(p,'回到身边');p.get_by_role('tab',name='今日',exact=True).click();wait(p,250)
 check('late response after an offline cancellation cannot populate the weather UI',p.locator('.temperature-window210').count()==0)
 before=p.evaluate('()=>__detail2.calls');p.get_by_role('button',name='刷新天气',exact=True).click();wait(p,80)
 check('explicit offline refresh does not generate a network request',p.evaluate('()=>__detail2.calls')==before)
 p.evaluate("()=>{__detail2.mode='normal';__detail2.online=true;dispatchEvent(new Event('online'))}");wait(p,220)
 check('reconnection recovers the weather without reopening the app',p.locator('.temperature-window210').inner_text().startswith('31'))
 p.get_by_role('button',name='未来七天',exact=True).click();first=p.locator('.weather-day time').first.inner_text()
 check('missing optional weather metrics do not leave an empty spacer row',p.locator('.weather-metrics210').count()==0)
 check('yesterday is not mislabeled 今天 in a cached forecast',first!='今天' and p.locator('.weather-day time').nth(1).inner_text()=='今天',first)
 # A pending old-city refresh must not win after the new location was saved.
 p.evaluate("()=>__detail2.mode='hold'");p.get_by_role('button',name='刷新天气',exact=True).click();wait(p,100)
 old=p.evaluate('()=>__detail2.pending.length-1');d=r.settings(p,'天气与位置');d.get_by_role('spinbutton',name='经度',exact=True).fill('121');d.get_by_role('textbox',name='地点名称',exact=True).fill('另一个测试地点');p.evaluate("()=>__detail2.mode='normal'");d.get_by_role('button',name='保存天气设置',exact=True).click();wait(p,150);close(p);p.get_by_role('tab',name='今日',exact=True).click();wait(p,200)
 p.evaluate('i=>__detail2.pending[i].deliver()',old);wait(p,100)
 check('an old-city response cannot replace newer location data',p.locator('.temperature-window210').inner_text().startswith('19') and p.locator('.weather-head210>span').inner_text()=='另一个测试地点')
 check('both city caches remain under separate coordinate keys',p.evaluate("()=>{const cache=JSON.parse(localStorage.getItem('luke-weather-cache-v210'));return cache['open-meteo:30.0000,120.0000'].temperature===31&&cache['open-meteo:30.0000,121.0000'].temperature===19}"))
 p.screenshot(path=str(OUT/'weather-final.png'));p.close()

def restore_hook(ctx):
 # Real React hook lifecycle, with an explicitly injected deterministic storage adapter.
 script=(OUT/'restore-harness.js').read_text();p=ctx.new_page();p.set_default_timeout(8000);p.on('pageerror',lambda e:ERRORS.append(str(e)));p.set_content('<div id="root"></div>');p.evaluate("()=>{window.__restoreFixture={failLoad:true,failRestore:false,hold:false,writes:0,restores:0};if(!crypto.randomUUID)crypto.randomUUID=()=>{const b=crypto.getRandomValues(new Uint8Array(16));b[6]=(b[6]&15)|64;b[8]=(b[8]&63)|128;const h=[...b].map(x=>x.toString(16).padStart(2,'0')).join('');return h.slice(0,8)+'-'+h.slice(8,12)+'-'+h.slice(12,16)+'-'+h.slice(16,20)+'-'+h.slice(20)};}");p.add_script_tag(content=script);wait(p,150)
 check('storage-hook fixture reproduces a failed first load, not an already-ready session',p.get_by_role('status').inner_text().startswith('只读'))
 p.evaluate("async()=>{const d={...__restoreHarness.data,name:'恢复成功'};await __restoreHarness.restore(d)}");wait(p,120)
 check('successful restore from a read error leaves the app editable',p.get_by_role('status').inner_text()=='可编辑 · 恢复成功' and p.get_by_role('textbox',name='草稿').is_enabled())
 p.get_by_role('textbox',name='草稿').fill('恢复后继续输入');wait(p,250)
 check('the first post-restore edit is actually persisted by the hook',p.evaluate('()=>__restoreFixture.writes')==1)
 p.evaluate("async()=>{__restoreFixture.failRestore=true;try{await __restoreHarness.restore({...__restoreHarness.data,name:'不应覆盖'})}catch{}} ");wait(p,100)
 check('failed restore keeps the previous editable data',p.get_by_role('status').inner_text()=='可编辑 · 恢复成功' and p.get_by_role('textbox',name='草稿').input_value()=='恢复后继续输入')
 p.evaluate("()=>{__restoreFixture.failRestore=false;__restoreFixture.hold=true;window.__firstRestore=__restoreHarness.restore({...__restoreHarness.data,name:'第一次请求'})}");wait(p,80)
 msg=p.evaluate("async()=>{try{await __restoreHarness.restore({...__restoreHarness.data,name:'第二次请求'});return ''}catch(e){return e.message}}")
 check('concurrent restore is rejected before it can overwrite the in-flight data', '正在恢复另一份备份' in msg and p.evaluate('()=>__restoreFixture.restores')==3,msg)
 p.evaluate('async()=>{__restoreFixture.release();await __firstRestore}');wait(p,120)
 check('after a conflicting restore attempt the original import still completes',p.get_by_role('status').inner_text()=='可编辑 · 第一次请求')
 p.close()

def main():
 with sync_playwright() as pw:
  browser=pw.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
  context=browser.new_context(viewport={'width':393,'height':820},device_scale_factor=1,is_mobile=True,has_touch=True,reduced_motion='no-preference')
  for name,fn in [('dialogs',dialogs),('ruler',ruler),('swipe',swipe),('weather',weather),('restore-hook',restore_hook)]:
   try:fn(context)
   except Exception as exc:
    check(name+' suite completes',False,traceback.format_exc());(OUT/(name+'-failure.txt')).write_text(traceback.format_exc())
    if context.pages:
     try:context.pages[-1].screenshot(path=str(OUT/(name+'-failure.png')))
     except Exception:pass
    for p in context.pages:p.close()
  check('no unhandled JavaScript exceptions in production UI or the isolated hook harness',not ERRORS,ERRORS)
  result={'environment':'Compiled production UI in offline about:blank, MemoryStorage and controlled Android transport. Separate restore-hook tests use real React/useLocalData with an explicit storage adapter. No device/CSP/real IndexedDB verification.','checks':RESULTS,'passed':sum(x['passed'] for x in RESULTS),'failed':sum(not x['passed'] for x in RESULTS),'errors':ERRORS,'browser':browser.version}
  (OUT/'results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));print(json.dumps({'passed':result['passed'],'failed':result['failed']},ensure_ascii=False));browser.close()
 raise SystemExit(int(any(not r['passed'] for r in RESULTS)))
if __name__=='__main__':main()
