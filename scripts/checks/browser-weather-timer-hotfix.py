"""Hotfix regression against compiled assets.
Uses the existing documented offline renderer (MemoryStorage and synthetic Android/network).
Clicks are actual mouse/touch actions without force/DOM click injection. Visibility simulation
is specifically labeled, and it is not an Android keyboard, process or GPU test.
"""
import json,traceback,time,os
from pathlib import Path
from playwright.sync_api import sync_playwright
import runtime210 as r
OUT=r.ROOT/'work/weather-timer-hotfix';OUT.mkdir(parents=True,exist_ok=True)
RESULT=[];ERRORS=[]
def check(name,ok,detail=None):
 RESULT.append({'name':name,'passed':bool(ok),**({'detail':detail} if detail is not None else {})});print(('PASS ' if ok else 'FAIL ')+name,flush=True)
 if not ok:raise AssertionError(name+': '+str(detail)[:700])
def pause(p,ms=120):p.wait_for_timeout(ms)
def scale(p,value):
 p.evaluate('''value=>{const x=JSON.parse(localStorage.getItem('luke-display-v206')||'{}');x.scale=value;localStorage.setItem('luke-display-v206',JSON.stringify(x));dispatchEvent(new Event('luke-display-change'));}''',value);pause(p,80)
def close(p):p.get_by_role('dialog').last.locator('[data-slot=dialog-close]').tap();pause(p,380)
def hit(locator):
 return locator.evaluate('''e=>{const b=e.getBoundingClientRect(),points=[[.5,.5],[.18,.5],[.82,.5],[.5,.25],[.5,.75]];return {disabled:e.disabled,inert:!!e.closest('[inert]'),left:b.left,top:b.top,right:b.right,bottom:b.bottom,points:points.map(([x,y])=>{const h=document.elementFromPoint(b.x+b.width*x,b.y+b.height*y);return {ok:h===e||e.contains(h),target:h?.tagName+'.'+h?.className}}),visible:b.x>=-.6&&b.right<=innerWidth+.6&&b.y>=0&&b.bottom<=innerHeight+.6}}''')
def is_hit(d):return not d['disabled'] and not d['inert'] and d['visible'] and all(p['ok'] for p in d['points'])
NEEDLE=r'''()=>{
 const dial=document.querySelector('.mechanical-dial210'),svg=dial.querySelector('svg'),g=dial.querySelector('[data-hand=second]'),mat=g.getScreenCTM(),view=svg.getScreenCTM();
 const center=new DOMPoint(100,100).matrixTransform(view),points=[[100,20],[100,46],[100,119]].map(([x,y])=>new DOMPoint(x,y).matrixTransform(mat)),pivot=new DOMPoint(100,100).matrixTransform(mat),size=dial.getBoundingClientRect().width;
 const radii=points.map(p=>Math.hypot(p.x-center.x,p.y-center.y)/size),needle=g.getAttribute('transform');
 const readout=document.querySelector('.focus-readout-hf3'),clock=readout.querySelector('.focus-clock'),pendant=readout.querySelector('.focus-keepsake-pendant');
 return {needle,angle:needle.match(/rotate\(([-0-9.]+)/)?.[1],radii,ringSafe:Math.hypot(pivot.x-center.x,pivot.y-center.y)<.6&&radii[0]>.39&&radii[0]<.41&&radii.every(r=>r<.48),text:clock.textContent,clockX:clock.getBoundingClientRect().x,clockY:clock.getBoundingClientRect().y,playing:dial.dataset.playing,pendant:getComputedStyle(pendant).transform,css:pendant.getAnimations().map(a=>({name:a.animationName,time:a.currentTime,state:a.playState}))};}'''

WEATHER=r'''()=>{
 const e=document.querySelector('.weather-card210'),q=s=>e.querySelector(s),rect=x=>x.getBoundingClientRect(),inside=(a,b)=>a.left>=b.left-.8&&a.right<=b.right+.8&&a.top>=b.top-.8&&a.bottom<=b.bottom+.8;
 const order=['.weather-head210','.weather-summary210','.weather-condition210','.weather-whisper210','.weather-time210'].map(s=>rect(q(s))),sky=rect(q('.weather-sky210')),t=rect(q('.temperature-window210')),number=rect(q('.temperature-window210>strong')),art=rect(q('.weather-art210'));
 const dayChecks=[...e.querySelectorAll('.weather-forecast210>div')].map(day=>{let labels=[...day.children].map(rect);return {text:day.textContent,ordered:labels.every((b,i)=>i===0||b.top>=labels[i-1].bottom-.8),width:day.scrollWidth<=day.clientWidth+1}});
 const buttons=[...e.querySelectorAll('.weather-head210 button')].map(b=>rect(b));
 return {orderSafe:order.every((b,i)=>inside(b,sky)&&(i===0||b.top>=order[i-1].bottom-.8)),numberInside:inside(number,t),separateArt:t.right<=art.left+.8,headerFits:buttons.every(b=>inside(b,rect(q('.weather-head210'))))&&rect(q('.weather-head210>span')).right<=buttons[0].left+.8,cardFits:rect(e).left>=-.8&&rect(e).right<=innerWidth+.8,onlyOneNumber:e.querySelectorAll('.temperature-window210>strong').length===1,dayChecks,metricsFit:q('.weather-metrics210').scrollWidth<=q('.weather-metrics210').clientWidth+1,temperature:q('.temperature-window210').textContent};}'''
def timer(p):
 r.nav(p,'计时');start=p.get_by_role('button',name='开始计时',exact=True)
 check('fresh install start button accepts a real touch (no silently disabled missing-subject state)',is_hit(hit(start)),hit(start));start.tap()
 check('first tap offers a real subject-and-start flow',p.get_by_role('dialog').get_by_role('button',name='添加并开始计时',exact=True).is_visible())
 close(p);check('canceling first-use setup creates no timer record',not p.evaluate("()=>JSON.parse(localStorage.getItem('luke-companion-v1')).study"))
 start.tap();p.get_by_role('textbox',name='科目名称',exact=True).fill('阅读复盘');p.get_by_role('button',name='添加并开始计时',exact=True).tap();pause(p,450)
 check('explicit add-and-start commits both subject and active study',p.evaluate("()=>JSON.parse(localStorage.getItem('luke-companion-v1')).study?.subject==='阅读复盘'&&JSON.parse(localStorage.getItem('luke-study-subjects-v206')).items.length===1"))
 frames=[]
 for _ in range(28):pause(p,70);frames.append(p.evaluate(NEEDLE))
 check('running second hand produces actual intermediate SVG transforms',len(set(f['needle'] for f in frames))>=5,{'unique':len(set(f['needle'] for f in frames))})
 check('centered needle reaches the outer ticks without leaving the clock while moving',all(f['ringSafe'] for f in frames),frames)
 check('character animation visibly changes its transform while running',len(set(f['pendant'] for f in frames))>=8)
 check('timer advances independently of decorative animation',len(set(f['text'] for f in frames))>=2)
 (OUT/'needle-first-seconds.json').write_text(json.dumps(frames,ensure_ascii=False,indent=2))
 # A retained inactive panel must not keep its animation eligible.
 r.nav(p,'回到身边');pause(p,250);check('leaving timer page suspends its ornament animation',p.locator('.mechanical-dial210').get_attribute('data-playing')=='false')
 r.nav(p,'计时');pause(p,250);check('returning to timer resumes animation without restarting the study',p.locator('.mechanical-dial210').get_attribute('data-playing')=='true')
 p.get_by_role('tab',name='统计',exact=True).tap();pause(p,120);check('inactive inner timer panel suspends animation',p.locator('.mechanical-dial210').get_attribute('data-playing')=='false')
 p.get_by_role('tab',name='专注',exact=True).tap();pause(p,140);check('reopening focus panel resumes the actual animation',p.locator('.mechanical-dial210').get_attribute('data-playing')=='true')
 p.emulate_media(reduced_motion='reduce');pause(p,100);check('system reduced-motion still takes precedence over decoration',p.locator('.mechanical-dial210').get_attribute('data-playing')=='false');before=p.locator('.focus-clock').inner_text();pause(p,1100);check('clock still advances with reduced motion',p.locator('.focus-clock').inner_text()!=before)
 p.emulate_media(reduced_motion='no-preference');pause(p,140);check('clearing reduced motion restores animation',p.locator('.mechanical-dial210').get_attribute('data-playing')=='true')
 # Synthetic visibility changes exercise lifecycle code, not Android process survival.
 p.evaluate("()=>{Object.defineProperty(document,'hidden',{configurable:true,get:()=>true});dispatchEvent(new Event('visibilitychange'));document.dispatchEvent(new Event('visibilitychange'))}");pause(p,100)
 check('simulated hidden document suspends animation',p.locator('.mechanical-dial210').get_attribute('data-playing')=='false')
 p.evaluate("()=>{delete document.hidden;document.dispatchEvent(new Event('visibilitychange'))}");pause(p,140);check('simulated foreground resumes the same timer animation',p.locator('.mechanical-dial210').get_attribute('data-playing')=='true')
 end=p.get_by_role('button',name='结束并保存',exact=True);check('stop button is the actual hit target at five points',is_hit(hit(end)),hit(end));end.tap();pause(p,140)
 check('stop saves a single study entry and clears active study',p.evaluate("()=>{const d=JSON.parse(localStorage.getItem('luke-companion-v1'));return !d.study&&d.focusLog.filter(x=>x.group==='阅读复盘').length===1}"))
 # Matrix: verify actual hit tests, then perform start and end through touch at each size.
 matrix=[]
 for width in [320,360,393,430]:
  for height in [568,640,820]:
   for size in [.75,.95,1.3,1.6]:
    p.set_viewport_size({'width':width,'height':height});scale(p,size)
    button=p.get_by_role('button',name='开始计时',exact=True);d=hit(button)
    if not is_hit(d):check(f'timer start hit at {width}x{height}/{size}',False,d)
    button.tap();pause(p,40);end=p.get_by_role('button',name='结束并保存',exact=True);stop=hit(end)
    if not is_hit(stop):check(f'timer stop hit at {width}x{height}/{size}',False,stop)
    end.tap();pause(p,40);matrix.append({'width':width,'height':height,'scale':size,'startHit':is_hit(d),'endHit':is_hit(stop),'saved':not p.evaluate("()=>!!JSON.parse(localStorage.getItem('luke-companion-v1')).study")})
 check('48 viewport/font combinations: real touch start and stop, no covering layer',len(matrix)==48 and all(x['startHit'] and x['endHit'] and x['saved'] for x in matrix));(OUT/'timer-touch-matrix.json').write_text(json.dumps(matrix,indent=2))
 p.set_viewport_size({'width':393,'height':820});scale(p,.95)
 p.get_by_role('button',name='开始计时',exact=True).tap();pause(p,2180);p.screenshot(path=str(OUT/'timer-running.png'));p.get_by_role('button',name='结束并保存',exact=True).tap();pause(p,80)
 # Reminder footer/ruler controls also remain touchable after switching among subpanels.
 p.get_by_role('tab',name='提醒',exact=True).tap();pause(p,180);panel=p.locator('.in-app-countdown210');a=panel.get_by_role('button',name='开始倒计时',exact=True);a.scroll_into_view_if_needed();a.tap();pause(p,80)
 check('reminder countdown starts via a physical tap, not dispatchEvent',panel.get_by_role('button',name='暂停',exact=True).is_visible());panel.get_by_role('button',name='暂停',exact=True).tap();pause(p,70);panel.get_by_role('button',name='继续',exact=True).tap();pause(p,70);panel.get_by_role('button',name='暂停',exact=True).tap();pause(p,70);panel.get_by_role('button',name='重置',exact=True).tap()
 check('reminder pause/resume/reset remains interactive',panel.get_by_role('button',name='开始倒计时',exact=True).is_visible())

def weather(p):
 r.nav(p,'回到身边');p.get_by_role('tab',name='今日',exact=True).tap();pause(p,300)
 # Real pointer action during the last part of a page spring used to focus-scroll
 # the outer carousel by ~110px. Repeating it catches the double-offset regression.
 seq=[]
 for _ in range(3):
  p.locator('#main-tab-timers').tap();pause(p,160);p.locator('#main-tab-home').tap();pause(p,300);p.get_by_role('tab',name='今日',exact=True).tap();pause(p,550)
  seq.append(p.evaluate("()=>({scroll:document.querySelector('.page-viewport210').scrollLeft,left:document.querySelector('#main-panel-home').getBoundingClientRect().left})"))
 check('rapid page -> subsection taps do not introduce a second horizontal scroll offset',all(abs(x['left'])<.6 and x['scroll']==0 for x in seq),seq)
 p.locator('.page-viewport210').evaluate('e=>{e.scrollLeft=90;e.dispatchEvent(new Event("scroll"))}');pause(p,40);check('outer viewport cannot be focus-scrolled while inner content still scrolls',p.locator('.page-viewport210').evaluate('e=>e.scrollLeft===0'))
 # Original app setter, but controlled weather data. No unvalidated live endpoints.
 p.evaluate('''()=>{const original=LukeAndroid.request;window.__wxOriginal=original;window.__wxHotfix={value:22};LukeAndroid.request=(id,url,...rest)=>{if(!url.includes('api.open-meteo.com'))return original(id,url,...rest);const t=Math.floor(Date.now()/1000),n=window.__wxHotfix.value,p={utc_offset_seconds:28800,current:{time:t,temperature_2m:n,weather_code:61,apparent_temperature:n,relative_humidity_2m:100,wind_speed_10m:200,is_day:1},daily:{time:Array.from({length:7},(_,i)=>t+i*86400),weather_code:[61,95,0,3,71,65,45],temperature_2m_max:Array(7).fill(n),temperature_2m_min:Array(7).fill(n)},hourly:{time:Array.from({length:24},(_,i)=>t+i*3600),temperature_2m:Array(24).fill(n),weather_code:Array(24).fill(61),precipitation_probability:Array(24).fill(100)}};setTimeout(()=>window.__lukeNetwork(id,200,JSON.stringify(p)),35)}}''')
 d=r.settings(p,'天气与位置');d.get_by_role('textbox',name='地点名称',exact=True).fill('内蒙古自治区呼伦贝尔市额尔古纳市测试地点');d.get_by_role('button',name='保存天气设置',exact=True).tap();close(p);p.get_by_role('tab',name='今日',exact=True).tap();pause(p,150)
 matrix=[]
 for temp in [-100,22,70]:
  p.evaluate('n=>window.__wxHotfix.value=n',temp);p.get_by_role('button',name='刷新天气',exact=True).tap();pause(p,140)
  for unit in ['C','F']:
   if (unit=='F')!=('°F' in p.get_by_role('button',name='切换温度单位',exact=True).inner_text().split('/')[0]):p.get_by_role('button',name='切换温度单位',exact=True).tap();pause(p,380)
   for width in [320,360,393,430]:
    for size in [.75,.95,1.3,1.6]:
     p.set_viewport_size({'width':width,'height':820});scale(p,size);g=p.evaluate(WEATHER)
     safe=all(g[k] for k in ['orderSafe','numberInside','separateArt','headerFits','cardFits','onlyOneNumber','metricsFit']) and all(x['ordered'] and x['width'] for x in g['dayChecks']);matrix.append({'width':width,'scale':size,'unit':unit,'temperature':temp,'passed':safe,**g})
     if not safe:check('weather matrix',False,matrix[-1])
 check('96 weather size/temperature/unit combinations: text, digits, icons and controls do not overlap',len(matrix)==96 and all(x['passed'] for x in matrix));(OUT/'weather-matrix.json').write_text(json.dumps(matrix,ensure_ascii=False,indent=2))
 p.set_viewport_size({'width':393,'height':820});scale(p,.95);p.evaluate('()=>{window.__wxHotfix.value=22;LukeAndroid.request=window.__wxOriginal}');p.get_by_role('button',name='刷新天气',exact=True).tap();pause(p,200)
 if p.get_by_role('button',name='切换温度单位').inner_text().startswith('°F'):p.get_by_role('button',name='切换温度单位').tap();pause(p,380)
 for _ in range(6):p.get_by_role('button',name='切换温度单位').tap();pause(p,25);check('temperature replacement never stacks old/new digits',p.locator('.temperature-window210>strong').count()==1)
 pause(p,380);p.screenshot(path=str(OUT/'weather-default.png'))
 motion=[]
 for _ in range(12):pause(p,70);motion.append(p.locator('.weather-cloud210').first.evaluate('e=>getComputedStyle(e).transform'))
 check('visible weather cloud has changing actual transforms',len(set(motion))>5)
 rail=p.locator('.weather-forecast210');rail.scroll_into_view_if_needed();pause(p,80);box=rail.bounding_box();p.mouse.move(box['x']+box['width']*.5,box['y']+40);p.mouse.wheel(240,0);pause(p,200)
 check('forecast horizontal scroll remains functional and does not switch pages',rail.evaluate('e=>e.scrollLeft>0') and p.locator('.page-viewport210').get_attribute('data-page')=='home')
 p.get_by_role('button',name='逐小时',exact=True).tap();check('hourly forecast still shows parsed rows',p.locator('.weather-forecast210>div').count()>1)
 p.get_by_role('button',name='未来七天',exact=True).tap();check('daily forecast still has all seven supplied days',p.locator('.weather-day').count()==7)
 p.locator('#main-panel-home .section-scroll210').evaluate('e=>e.scrollTop=0');p.set_viewport_size({'width':320,'height':640});scale(p,1.6);p.screenshot(path=str(OUT/'weather-small-large-type.png'))
 # Restore default theme and exercise controls that used to be difficult to hit.
 p.set_viewport_size({'width':393,'height':820});scale(p,.95);d=r.settings(p,'外观与字号');d.get_by_role('switch',name='季节动画',exact=True).uncheck();close(p);p.get_by_role('tab',name='今日',exact=True).tap();pause(p,150)
 check('explicit app effects-off is respected, not silently overwritten by hotfix',p.locator('.weather-scene210').get_attribute('data-playing')=='false')
 d=r.settings(p,'外观与字号');d.get_by_role('switch',name='季节动画',exact=True).check();close(p);p.get_by_role('tab',name='今日',exact=True).tap();pause(p,150)
 check('reenabling app effects resumes weather motion',p.locator('.weather-scene210').get_attribute('data-playing')=='true')

def full_orbit(ctx):
 rows=[]
 for seconds in [0,14,29,44,58,59,60,3599,3600]:
  seed=r.seed();seed['luke-companion-v1']['study']={'subject':'旋转回归','startedAt':int(time.time()*1000)-seconds*1000};p=r.boot(ctx,seed,ERRORS);r.nav(p,'计时');pause(p,150)
  for _ in range(4):pause(p,50);g=p.evaluate(NEEDLE);rows.append({'seedSeconds':seconds,**g});
  if not all(x['ringSafe'] for x in rows):check('full orbit uses the real viewBox center',False,rows[-4:])
  p.close()
 check('full 360-degree orbit, minute wrap and restored long timer stay on the same circumference',all(x['ringSafe'] for x in rows),{'samples':len(rows)});(OUT/'full-orbit.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2))
def main():
 with sync_playwright() as pw:
  browser=pw.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
  for name,fn in [('timer',timer),('weather',weather)]:
   if os.environ.get('HOTFIX_CASE') and os.environ['HOTFIX_CASE']!=name:continue
   ctx=browser.new_context(viewport={'width':393,'height':820},is_mobile=True,has_touch=True,reduced_motion='no-preference',locale='zh-CN',timezone_id='Asia/Shanghai');p=r.boot(ctx,errors=ERRORS)
   try:fn(p)
   except Exception:RESULT.append({'name':name+' suite completes','passed':False,'detail':traceback.format_exc()});print(traceback.format_exc(),flush=True);p.screenshot(path=str(OUT/(name+'-failure.png')))
   finally:ctx.close()
  ctx=browser.new_context(viewport={'width':393,'height':820},is_mobile=True,has_touch=True,reduced_motion='no-preference',locale='zh-CN')
  try:
   if not os.environ.get('HOTFIX_CASE'):full_orbit(ctx)
  except Exception:RESULT.append({'name':'full orbit suite completes','passed':False,'detail':traceback.format_exc()});print(traceback.format_exc(),flush=True)
  finally:ctx.close()
  check('no unhandled JavaScript exceptions in hotfix regression',not ERRORS,ERRORS)
  report={'environment':'Production bundle replay in Chromium about:blank; documented MemoryStorage and synthetic Android/weather fixtures; real touch/mouse actions; synthetic visibility lifecycle; not Android, real IME, real CSP, actual IndexedDB or device-GPU validation','browser':browser.version,'checks':RESULT,'passed':sum(c['passed'] for c in RESULT),'failed':sum(not c['passed'] for c in RESULT),'errors':ERRORS};(OUT/'results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print('TOTAL',report['passed'],report['failed'],flush=True);browser.close()
 raise SystemExit(int(any(not c['passed'] for c in RESULT)))
if __name__=='__main__':main()
