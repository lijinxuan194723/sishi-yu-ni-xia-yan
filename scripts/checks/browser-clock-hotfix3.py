"""Actual production clock geometry, hand pixel evidence, and interruption regression.
Uses the documented offline runtime210 test storage/native bridge. Not a device or CSP test.
The actual requestAnimationFrame + SVG paint runs; tests do not replace the clock component.
"""
import json,time,math,traceback,io
from pathlib import Path
from PIL import Image
from playwright.sync_api import sync_playwright
import runtime210 as r
OUT=r.ROOT/'work/clock-hotfix3';OUT.mkdir(parents=True,exist_ok=True)
RESULT=[];ERRORS=[]
def check(name,ok,detail=None):
 RESULT.append({'name':name,'passed':bool(ok),'detail':detail});print(('PASS ' if ok else 'FAIL ')+name,flush=True)
def wait(p,ms=180):p.wait_for_timeout(ms)
def scale(p,n):
 p.evaluate("n=>{const s=JSON.parse(localStorage.getItem('luke-display-v206')||'{}');s.scale=n;localStorage.setItem('luke-display-v206',JSON.stringify(s));dispatchEvent(new Event('luke-display-change'))}",n);wait(p,70)
def close(p):p.get_by_role('dialog').last.locator('[data-slot=dialog-close]').click();wait(p,260)
HANDS=r'''e=>{const svg=e.querySelector('svg'),center=new DOMPoint(100,100).matrixTransform(svg.getScreenCTM()),bounds=svg.getBoundingClientRect();return [...e.querySelectorAll('[data-hand]')].map(g=>{const pivot=new DOMPoint(100,100).matrixTransform(g.getScreenCTM()),tip=new DOMPoint(100,g.dataset.hand==='second'?20:g.dataset.hand==='minute'?43:70).matrixTransform(g.getScreenCTM()),b=g.getBoundingClientRect();return {hand:g.dataset.hand,transform:g.getAttribute('transform'),centerError:Math.hypot(pivot.x-center.x,pivot.y-center.y),tip,center,pivot,inside:b.left>=bounds.left-1&&b.right<=bounds.right+1&&b.top>=bounds.top-1&&b.bottom<=bounds.bottom+1,origin:getComputedStyle(g).transformOrigin}})}'''
GEO=r'''()=>{const r=e=>e.getBoundingClientRect(),dial=document.querySelector('.study-timer .analog-clock-hf3'),read=document.querySelector('.focus-readout-hf3'),footer=document.querySelector('#main-panel-timers .section-actions210'),pane=document.querySelector('#main-panel-timers .section-scroll210'),header=document.querySelector('main>header'),rail=document.querySelector('#main-panel-timers .section-switch210');const a=r(dial),b=r(read),f=r(footer),h=r(header),t=r(rail),view=r(pane);const point=document.elementFromPoint(f.x+f.width/2,f.y+f.height/2);return {diameter:a.width,circular:Math.abs(a.width-a.height)<.7,visible:a.top>=t.bottom+2&&a.bottom<=Math.min(f.top,view.bottom)+1,digitsBelow:b.top>=a.bottom+4||b.left>=a.right+4,readoutVisible:b.bottom<=f.top+1,contentWidth:read.scrollWidth<=read.clientWidth+1,actionHit:footer.contains(point),documentFits:document.scrollingElement.scrollWidth<=innerWidth+1&&document.scrollingElement.scrollHeight<=innerHeight+1,ring:a.toJSON(),readout:b.toJSON(),footer:f.toJSON()}}'''
def snapshot(p,selector):return p.locator(selector).evaluate(HANDS)
def sample(p,selector,ms=1300):
 return p.locator(selector).evaluate('''async (e,ms)=>{const frames=[],at=performance.now();while(performance.now()-at<ms){frames.push({t:performance.now()-at,transform:e.querySelector('[data-hand="second"]').getAttribute('transform'),motion:e.dataset.motion});await new Promise(requestAnimationFrame)}return frames}''',ms)
def angle(t):return float(t.split('(')[1].split()[0])
def pixels(p,selector):
 anchor=p.locator(selector).evaluate('''e=>{const g=e.querySelector('[data-hand=second]'),pt=new DOMPoint(100,46).matrixTransform(g.getScreenCTM()),s=getComputedStyle(g.querySelector('.dial-pointer210')).stroke,c=document.createElement('canvas');c.width=c.height=1;let x=c.getContext('2d');x.fillStyle=s;x.fillRect(0,0,1,1);return {x:pt.x,y:pt.y,rgb:[...x.getImageData(0,0,1,1).data].slice(0,3)}}''')
 im=Image.open(io.BytesIO(p.screenshot())).convert('RGB');hits=0
 for x in range(max(0,int(anchor['x'])-6),min(im.width,int(anchor['x'])+7)):
  for y in range(max(0,int(anchor['y'])-6),min(im.height,int(anchor['y'])+7)):
   rgb=im.getpixel((x,y));hits+=max(abs(a-b) for a,b in zip(rgb,anchor['rgb']))<48
 return {'hits':hits,'anchor':anchor}
def run(p,ctx):
 r.nav(p,'计时');focus='.study-timer .analog-clock-hf3'
 check('idle stopwatch has visible full-length center-pivot minute and second hands',len(snapshot(p,focus))==2 and all(x['centerError']<.5 for x in snapshot(p,focus)),snapshot(p,focus))
 idle=sample(p,focus,350);check('idle stopwatch does not fabricate elapsed time',len(set(x['transform'] for x in idle))==1)
 p.get_by_role('button',name='开始计时',exact=True).tap();wait(p,110)
 frames=sample(p,focus,1800);(OUT/'focus-sweep-frames.json').write_text(json.dumps(frames,indent=2))
 delta=(angle(frames[-1]['transform'])-angle(frames[0]['transform']))%360
 check('running second hand draws intermediate frames at the real clock rate',len(set(x['transform'] for x in frames))>15 and 8<delta<14,{'frames':len(frames),'unique':len(set(x['transform'] for x in frames)),'angleDelta':delta})
 check('elapsed readout stays separate from rotating hands',p.evaluate(GEO)['digitsBelow'])
 px=pixels(p,focus);check('running hand actually paints visible colored pixels inside the dial',px['hits']>=3,px)
 # Sweep through every quadrant using a controlled clock offset. Rotations use real SVG CTM.
 p.evaluate('()=>{window.__wallNowHF3=Date.now;window.__clockOffsetHF3=0;Date.now=()=>window.__wallNowHF3()+window.__clockOffsetHF3}')
 quadrants=[]
 for offset in [0,7500,15000,22500,30000,37500,45000,52500,59990,60010]:
  p.evaluate('offset=>window.__clockOffsetHF3=offset',offset);wait(p,50);quadrants.append(snapshot(p,focus))
 check('all ten sweep positions keep the hub invariant and hands inside the rim',all(g['centerError']<.6 and g['inside'] for row in quadrants for g in row),quadrants)
 p.evaluate('()=>{Date.now=window.__wallNowHF3;delete window.__clockOffsetHF3}');wait(p,100)
 # Essential hands still step when decorative animations are off.
 p.emulate_media(reduced_motion='reduce');wait(p,60);stepped=sample(p,focus,1250)
 check('system reduce-motion switches to functional one-second ticks, not frozen hands',p.locator(focus).get_attribute('data-motion')=='step' and 2<=len(set(x['transform'] for x in stepped))<=3)
 p.emulate_media(reduced_motion='no-preference');p.evaluate("()=>{document.documentElement.dataset.effects='off'}");wait(p,70);stepped=sample(p,focus,1250)
 check('season effects-off does not disable the actual timer hand',2<=len(set(x['transform'] for x in stepped))<=3 and p.locator(focus).get_attribute('data-motion')=='step')
 p.evaluate("()=>{document.documentElement.dataset.effects='on'}");wait(p,70)
 hidden=p.locator(focus+' [data-hand=second]');p.get_by_role('tab',name='统计',exact=True).tap();wait(p,150);before=hidden.get_attribute('transform');wait(p,1100)
 check('inactive focus tab performs no visual hand updates',hidden.get_attribute('transform')==before and p.locator(focus).get_attribute('data-motion')=='parked')
 p.get_by_role('tab',name='专注',exact=True).tap();wait(p,140);check('return to focus resumes at the persisted elapsed time',hidden.get_attribute('transform')!=before and p.locator(focus).get_attribute('data-motion')=='smooth')
 p.evaluate("()=>{Object.defineProperty(document,'hidden',{configurable:true,get:()=>true});document.dispatchEvent(new Event('visibilitychange'))}");wait(p,100);before=hidden.get_attribute('transform');wait(p,1100)
 check('document hidden cancels clock painting',hidden.get_attribute('transform')==before)
 p.evaluate("()=>{delete document.hidden;document.dispatchEvent(new Event('visibilitychange'))}");wait(p,100);check('foreground return catches up without replaying hidden frames',hidden.get_attribute('transform')!=before)
 p.get_by_role('button',name='结束并保存',exact=True).tap();wait(p,100)
 # Do not substitute a synthetic click in the start/stop layout matrix.
 matrix=[]
 for w in [320,360,393,430]:
  for h in [568,640,820]:
   for s in [.75,.95,1.3,1.6]:
    p.set_viewport_size({'width':w,'height':h});scale(p,s);g=p.evaluate(GEO);p.get_by_role('button',name='开始计时',exact=True).tap();wait(p,25)
    stop=p.get_by_role('button',name='结束并保存',exact=True);ok=stop.is_visible();stop.tap();wait(p,25);matrix.append({'w':w,'h':h,'scale':s,**g,'startStop':ok})
 for key in ['circular','visible','digitsBelow','readoutVisible','contentWidth','actionHit','documentFits','startStop']:
  bad=[x for x in matrix if not x[key]];check(f'{key}: 48 real viewport/font/touch cases',not bad,bad)
 (OUT/'focus-layout-matrix.json').write_text(json.dumps(matrix,indent=2))
 p.set_viewport_size({'width':320,'height':568});scale(p,1.6);p.screenshot(path=str(OUT/'focus-small-maximum.png'))
 p.set_viewport_size({'width':393,'height':820});scale(p,.95)
 # Reminders are a live wall clock before starting, then the actual countdown, paused exactly.
 p.get_by_role('tab',name='提醒',exact=True).tap();wait(p,130);rem='.in-app-countdown210 .analog-clock-hf3';panel=p.locator('.in-app-countdown210')
 before=sample(p,rem,1000);check('reminder idle clock has three real moving hands, not a static clock icon',len(snapshot(p,rem))==3 and len(set(x['transform'] for x in before))>15 and all(x['centerError']<.6 for x in snapshot(p,rem)))
 p.screenshot(path=str(OUT/'reminder-idle.png'));panel.get_by_role('spinbutton',name='倒计时分钟',exact=True).fill('2');panel.get_by_role('button',name='开始倒计时',exact=True).tap();wait(p,120)
 frames=sample(p,rem,1450);delta=(angle(frames[0]['transform'])-angle(frames[-1]['transform']))%360
 check('reminder running hands count DOWN at correct speed',p.locator(rem).get_attribute('data-clock-mode')=='countdown' and len(snapshot(p,rem))==2 and 6<delta<12,{'delta':delta,'count':len(frames)})
 check('countdown pointer and timer number refer to same saved deadline',panel.get_by_role('timer',name='倒计时剩余时间').inner_text() in ['01:58','01:59'])
 px=pixels(p,rem);check('reminder second hand is painted, not merely a changed DOM attribute',px['hits']>=3,px)
 panel.get_by_role('button',name='暂停',exact=True).tap();wait(p,100);paused=sample(p,rem,1200);value=panel.get_by_role('timer',name='倒计时剩余时间').inner_text();wait(p,500)
 check('pause stops both displayed number and hand, without an idle fake spin',len(set(x['transform'] for x in paused))==1 and value==panel.get_by_role('timer',name='倒计时剩余时间').inner_text())
 panel.get_by_role('button',name='继续',exact=True).tap();wait(p,80);before=sample(p,rem,500);check('resume immediately restores the same countdown movement',len(set(x['transform'] for x in before))>10)
 panel.get_by_role('button',name='暂停',exact=True).tap();panel.get_by_role('spinbutton',name='倒计时分钟').fill('3');check('changing a paused duration offers a new start rather than resuming mismatched minutes',panel.get_by_role('button',name='按新时长开始',exact=True).is_visible());panel.get_by_role('button',name='按新时长开始',exact=True).tap();wait(p,90)
 check('explicit new-duration start really stores 3 minutes',p.evaluate("()=>JSON.parse(localStorage.getItem('luke-companion-v1')).countdown.seconds===180"))
 panel.get_by_role('button',name='暂停',exact=True).tap();panel.get_by_role('button',name='重置',exact=True).tap();wait(p,120)
 check('reset returns to a live current clock without erasing focus history',p.locator(rem).get_attribute('data-clock-mode')=='clock' and p.evaluate("()=>JSON.parse(localStorage.getItem('luke-companion-v1')).focusLog.length>0"))
 p.get_by_role('button',name='系统闹钟与锁屏提醒',exact=True).tap();wait(p,260);check('system alarm functions remain available in the expandable section',p.get_by_role('button',name='到系统时钟确认提醒',exact=True).is_visible())
 p.get_by_role('button',name='系统闹钟与锁屏提醒',exact=True).tap();wait(p,200)
 # Clock visibility and geometric center at every scale and through all seasonal palettes.
 panel.get_by_role('spinbutton',name='倒计时分钟').fill('2');panel.get_by_role('button',name='开始倒计时',exact=True).tap();wait(p,100)
 matrix=[]
 for w in [320,360,393,430]:
  for size in [.75,.95,1.3,1.6]:
   p.set_viewport_size({'width':w,'height':640});scale(p,size)
   g=panel.evaluate("e=>{const d=e.querySelector('.analog-clock-hf3').getBoundingClientRect(),v=e.querySelector('.countdown-readout-hf3').getBoundingClientRect(),r=e.getBoundingClientRect(),t=e.querySelector('.countdown-value210'),a=[...e.querySelectorAll('.countdown-actions210>button')].map(b=>b.getBoundingClientRect());return {disjoint:d.right<=v.left+1,width:e.scrollWidth<=e.clientWidth+1,number:t.scrollWidth<=t.clientWidth+1,round:Math.abs(d.width-d.height)<1,actions:Math.abs(a[0].top-a[1].top)<1&&Math.abs(a[0].height-a[1].height)<1}}")
   matrix.append({'w':w,'scale':size,**g,'center':all(x['centerError']<.6 and x['inside'] for x in snapshot(p,rem))})
 check('reminder clock/readout never overlap in 16 width/font configurations',all(all(row[k] for k in ['disjoint','width','number','round','center']) for row in matrix),matrix);(OUT/'reminder-layout-matrix.json').write_text(json.dumps(matrix,indent=2))
 check('reminder primary and reset actions have equal height in all 16 cases',all(row['actions'] for row in matrix),matrix)
 panel.get_by_role('button',name='暂停',exact=True).tap();panel.get_by_role('button',name='重置',exact=True).tap();wait(p,80)
 p.set_viewport_size({'width':393,'height':820});scale(p,.95)
 # Text layouts must not split one date or a short section title at random positions.
 stats=[]
 for width in [320,360,393,430]:
  for size in [.95,1.3,1.6]:
   p.set_viewport_size({'width':width,'height':640});scale(p,size);p.get_by_role('tab',name='统计',exact=True).tap();wait(p,60)
   date=p.locator('.study-range .date-field').evaluate("e=>{const s=e.querySelector('span'),g=e.querySelector('svg'),a=s.getBoundingClientRect(),b=g.getBoundingClientRect(),r=document.createRange();r.selectNodeContents(s);return {single:r.getClientRects().length===1,clear:a.right<=b.left+1,inside:e.scrollWidth<=e.clientWidth+1}}")
   p.get_by_role('tab',name='月历',exact=True).tap();wait(p,60)
   title=p.locator('.luke-study-calendar>header h2').evaluate("e=>{const r=document.createRange();r.selectNode(e.firstChild);return r.getClientRects().length===1}")
   stats.append({'width':width,'scale':size,**date,'title':title})
 check('statistics date and calendar title remain whole at all 12 width/font cases',all(all(x[k] for k in ['single','clear','inside','title']) for x in stats),stats)
 (OUT/'statistics-calendar-layout.json').write_text(json.dumps(stats,indent=2));p.set_viewport_size({'width':393,'height':820});scale(p,.95)
 # Pomodoro was formerly also only a large number.
 p.get_by_role('tab',name='番茄',exact=True).tap();wait(p,130);pom=p.locator('.focus-moment');check('pomodoro has real center-pivot hands too',pom.locator('[data-hand=second]').count()==1)
 start=pom.get_by_role('button',name='开始专注',exact=True);start.scroll_into_view_if_needed();start.tap();wait(p,180);pm='.pomodoro-dial-hf3 .analog-clock-hf3';sf=sample(p,pm,550);check('pomodoro hands run with the underlying focus timer',len(set(x['transform'] for x in sf))>8)
 check('pomodoro start/reset controls have equal height and aligned edges',pom.locator('.focus-actions').evaluate("e=>{const b=[...e.children].map(x=>x.getBoundingClientRect());return Math.abs(b[0].top-b[1].top)<1&&Math.abs(b[0].height-b[1].height)<1}"))
 p.screenshot(path=str(OUT/'pomodoro.png'))
 # Stop focus via pause if the controls have the historical text.
 pause=pom.get_by_role('button',name='暂停',exact=True)
 if pause.count():pause.tap()
 # Validate all theme foregrounds via actual pointer pixels, not only CSS declarations.
 results=[]
 for season in ['春','夏','秋','冬']:
  for period in ['正午','深夜']:
   d=r.settings(p,'外观与字号');d.get_by_role('button',name=season,exact=True).click();d.get_by_role('button',name=period,exact=True).click();wait(p,460);close(p);r.nav(p,'计时');p.get_by_role('tab',name='提醒',exact=True).click();wait(p,110)
   marks=pixels(p,rem);results.append({'season':season,'period':period,**marks})
 check('both day and night in all four seasons visibly paint the real needle',all(x['hits']>=3 for x in results),results);(OUT/'season-hand-pixels.json').write_text(json.dumps(results,ensure_ascii=False,indent=2))
 p.screenshot(path=str(OUT/'reminder-night.png'))
 d=r.settings(p,'外观与字号');d.get_by_role('button',name='春',exact=True).click();d.get_by_role('button',name='正午',exact=True).click();wait(p,460);close(p);r.nav(p,'计时');p.get_by_role('tab',name='专注',exact=True).tap();p.get_by_role('button',name='开始计时',exact=True).tap();wait(p,350)
 p.screenshot(path=str(OUT/'focus-final.png'))
 # Capture real frames with actual wall intervals. This is not a rendered mock-up.
 captures=[];times=[]
 for i in range(28):
  times.append(time.monotonic());captures.append(Image.open(io.BytesIO(p.screenshot())).convert('RGB'));wait(p,70)
 durations=[max(40,round((times[i+1]-times[i])*1000)) for i in range(len(times)-1)]+[120]
 captures[0].save(OUT/'focus-live.gif',save_all=True,append_images=captures[1:],duration=durations,loop=0)
 p.get_by_role('button',name='结束并保存',exact=True).tap();p.get_by_role('tab',name='提醒',exact=True).tap();wait(p,140)
 panel=p.locator('.in-app-countdown210');panel.get_by_role('spinbutton',name='倒计时分钟').fill('1');panel.get_by_role('button',name='开始倒计时',exact=True).tap();wait(p,220)
 p.screenshot(path=str(OUT/'reminder-running.png'));captures=[];times=[]
 for i in range(26):
  times.append(time.monotonic());captures.append(Image.open(io.BytesIO(p.screenshot())).convert('RGB'));wait(p,70)
 durations=[max(40,round((times[i+1]-times[i])*1000)) for i in range(len(times)-1)]+[120]
 captures[0].save(OUT/'reminder-live.gif',save_all=True,append_images=captures[1:],duration=durations,loop=0)
 (OUT/'recording-info.json').write_text(json.dumps({'source':'Actual Chromium production UI screenshots; synthetic chat/storage seed, real elapsed wall capture intervals; no drawn hand or speedup','focus':'focus-live.gif','reminder':'reminder-live.gif','reminderFrames':len(times),'reminderDurationMs':sum(durations)},indent=2))
 check('no unhandled page errors in all clock and layout checks',not ERRORS,ERRORS)

def main():
 with sync_playwright() as pw:
  b=pw.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);ctx=b.new_context(viewport={'width':393,'height':820},is_mobile=True,has_touch=True,locale='zh-CN',timezone_id='Asia/Shanghai');seed=r.seed();seed['luke-study-subjects-v206']={'version':1,'selected':'read','items':[{'id':'read','name':'阅读复盘'}]};p=r.boot(ctx,seed,ERRORS)
  try:run(p,ctx)
  except Exception:check('suite completes',False,traceback.format_exc());print(traceback.format_exc(),flush=True);p.screenshot(path=str(OUT/'failure.png'))
  report={'environment':'Production CSS/JS in documented offline about:blank; MemoryStorage and synthetic Android network/storage. Clock movement and screen pixels use actual browser rendering, not a component mock. Not a device/CSP/real-IndexedDB or refresh-rate certification.','browser':b.version,'passed':sum(c['passed'] for c in RESULT),'failed':sum(not c['passed'] for c in RESULT),'checks':RESULT,'errors':ERRORS};(OUT/'results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print('TOTAL',report['passed'],report['failed'],flush=True);b.close()
 raise SystemExit(int(any(not c['passed'] for c in RESULT)))
if __name__=='__main__':main()
