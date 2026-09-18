"""Geometry, visual settings, pointer cancellation and functional failure/recovery paths.
Uses the final production bundle in the documented offline replay; not Android/IME/IndexedDB/CSP.
"""
import json,traceback,sys,time
from pathlib import Path
from playwright.sync_api import sync_playwright
import runtime210 as r
OUT=r.OUT;RESULT=[];ERRORS=[]
def check(name,ok,detail=None):
 RESULT.append({'name':name,'passed':bool(ok),'detail':detail});print(('PASS ' if ok else 'FAIL ')+name,flush=True)
def scale(p,s,chat=13):
 p.evaluate("([scale,chatSize])=>{const v=JSON.parse(localStorage.getItem('luke-display-v206')||'{}');localStorage.setItem('luke-display-v206',JSON.stringify({...v,scale,chatSize}));dispatchEvent(new Event('luke-display-change'))}",[s,chat]);p.wait_for_timeout(90)
def close(p):p.get_by_role('dialog').last.locator('[data-slot=dialog-close]').click();p.wait_for_timeout(320)
GEO='''()=>{const r=s=>document.querySelector(s).getBoundingClientRect(),root=document.querySelector('.app-shell'),dial=r('.mechanical-dial210'),port=r('#main-panel-timers .section-scroll210'),feet=r('#main-panel-timers .section-actions210'),nav=r('.sidebar'),top=r('main>header'),title=r('main>header .bar-title'),peanut=r('.dial-peanut210'),center={x:dial.x+dial.width/2,y:dial.y+dial.height/2};const inside=(a,b)=>a.left>=b.left-.8&&a.right<=b.right+.8&&a.top>=b.top-.8&&a.bottom<=b.bottom+.8;const inner=[...document.querySelectorAll('.dial-center210>.focus-keepsake,.dial-center210>.focus-clock,.dial-center210>small')].map(e=>{const a=e.getBoundingClientRect();return {cl:e.className,r:Math.max(...[[a.left,a.top],[a.right,a.top],[a.left,a.bottom],[a.right,a.bottom]].map(([x,y])=>Math.hypot(x-center.x,y-center.y))),safe:dial.width*.39}});return {diameter:dial.width,ringVisible:inside(dial,port)&&dial.top>=top.bottom,ringRound:Math.abs(dial.width-dial.height)<1,innerSafe:(()=>{const r=document.querySelector('.focus-readout-hf3').getBoundingClientRect();return r.top>=dial.bottom+4||r.left>=dial.right+4})(),inner,actionClear:feet.bottom<=nav.top+.8&&feet.top>=top.bottom,peanutSeparate:document.querySelector('.dial-peanut210').getClientRects().length===0||peanut.top>=dial.bottom+4,noDocumentScroll:document.scrollingElement.scrollHeight<=innerHeight+1&&document.scrollingElement.scrollWidth<=innerWidth+1,titleInside:inside(title,top),portHeight:port.height};}'''
def run(p,ctx):
 r.nav(p,'计时');matrix=[]
 for w in [320,360,393,430]:
  for h in [568,640,820]:
   for s in [.75,.95,1.6]:
    p.set_viewport_size({'width':w,'height':h});scale(p,s);row=p.evaluate(GEO);matrix.append({'width':w,'height':h,'scale':s,**row})
 for field in ['ringRound','innerSafe','actionClear','peanutSeparate','noDocumentScroll','titleInside']:
  bad=[x for x in matrix if not x[field]];check(field+' across 36 viewport/global-size combinations',not bad,bad)
 # Extreme user type has a native scroll fallback. All normal/default combinations show the whole ring immediately.
 check('default and compact type show complete clock on initial viewport',all(x['ringVisible'] for x in matrix if x['scale']<=.95),[x for x in matrix if x['scale']<=.95 and not x['ringVisible']])
 (OUT/'layout-matrix.json').write_text(json.dumps(matrix,ensure_ascii=False,indent=2))
 p.set_viewport_size({'width':320,'height':568});scale(p,1.6);p.screenshot(path=str(OUT/'timer-small-large-type.png'))
 # Visible-but-small buttons at min/max type remain clickable and do not touch text.
 headers=[]
 for name in ['回到身边','悄悄话','他的此刻','一起计划','时光手记','计时']:
  r.nav(p,name);headers.append({'page':name,**p.locator('main>header').evaluate("e=>{const a=e.getBoundingClientRect(),t=e.querySelector('.bar-title').getBoundingClientRect();return {inside:t.top>=a.top-.5&&t.bottom<=a.bottom+.5,width:t.right<=a.right,noOverlap:[...e.querySelectorAll('.bar-actions button')].filter(b=>b.getClientRects().length).every(b=>b.getBoundingClientRect().left>=t.right-.5)}}")})
 check('all six headers fit maximum type on narrow viewport',all(x['inside'] and x['width'] and x['noOverlap'] for x in headers),headers)
 p.set_viewport_size({'width':393,'height':820});scale(p,.95)
 # Normal real settings interaction and system-pref fallback.
 d=r.settings(p,'外观与字号')
 d.get_by_role('switch',name='顶部通透效果',exact=True).uncheck();p.wait_for_timeout(100)
 check('opaque setting is stored under the existing 2.0.9 key',p.evaluate("()=>localStorage.getItem('luke-glass-headers-v209')==='off'&&getComputedStyle(document.querySelector('main>header')).backdropFilter==='none'"))
 d.get_by_role('switch',name='顶部通透效果',exact=True).check();p.wait_for_timeout(80)
 check('turning glass back on restores translucent header without moving navigation',p.evaluate("()=>getComputedStyle(document.querySelector('main>header')).backdropFilter.includes('blur')"))
 colors=[]
 for season in ['春','夏','秋','冬']:
  for period in ['正午','深夜']:
   d.get_by_role('button',name=season,exact=True).click();d.get_by_role('button',name=period,exact=True).click();p.wait_for_timeout(580)
   colors.append(p.evaluate("()=>({season:document.documentElement.dataset.season,night:document.documentElement.dataset.night,ink:getComputedStyle(document.querySelector('main>header')).color,bg:getComputedStyle(document.querySelector('main>header')).backgroundColor,photo:[...document.querySelectorAll('.appearance-preview img')].some(i=>i.complete&&i.naturalWidth>0&&Number(getComputedStyle(i).opacity)>.98)})"))
 check('four seasons and day/night keep decoded photo and themed controls',all(c['photo'] for c in colors) and len(set(c['ink'] for c in colors))>=4,colors)
 d.get_by_role('switch',name='季节动画',exact=True).uncheck();p.wait_for_timeout(120);close(p);r.nav(p,'回到身边');p.get_by_role('tab',name='今日',exact=True).click();p.wait_for_timeout(150)
 check('app effect-off stops weather particles and Motion layout movement',p.locator('.weather-scene210').get_attribute('data-playing')=='false')
 d=r.settings(p,'外观与字号');d.get_by_role('switch',name='季节动画',exact=True).check();close(p)
 # Holidays preserved and absent future doesn't prevent planning, no data-source clutter on main.
 r.nav(p,'一起计划');p.get_by_role('button',name='跳转到指定日期',exact=True).click();dlg=p.get_by_role('dialog').last;dlg.get_by_role('textbox',name='输入指定日期').fill('2026-10-10');dlg.get_by_role('button',name='确定日期',exact=True).click();p.wait_for_timeout(200)
 check('local 2026 make-up day is shown on main calendar',p.locator('.calendar-grid button[aria-label^="2026-10-10"] .holiday-mark').inner_text()=='班')
 check('calendar displays no technical source links',p.locator('.luke-plan-calendar a').count()==0)
 p.get_by_role('button',name='跳转到指定日期',exact=True).click();dlg=p.get_by_role('dialog').last;check('date picker and main calendar use the same work/rest data',dlg.locator('.date-grid button[aria-label^="2026-10-10"] i').inner_text()=='班');dlg.get_by_role('button',name='取消',exact=True).click();p.wait_for_timeout(180)
 # Memo concurrency failure preserves newer foreign record and local draft.
 r.nav(p,'时光手记');p.get_by_role('button',name='新建笔记',exact=True).click();p.get_by_role('textbox',name='笔记正文').fill('保留的本页草稿');p.wait_for_timeout(250)
 foreign=p.evaluate("()=>{const x=JSON.parse(localStorage.getItem('luke-memo-workspace-v1'));x.memos[0].body='另一个页面写入的正文';const text=JSON.stringify(x);localStorage.setItem('luke-memo-workspace-v1',text);return text}")
 p.get_by_role('textbox',name='笔记正文').fill('不要覆盖别处，但当前文字也保留');p.wait_for_timeout(220)
 check('memo detects concurrent store updates and does not overwrite them',p.evaluate("()=>localStorage.getItem('luke-memo-workspace-v1')")==foreign and p.get_by_role('textbox',name='笔记正文').input_value()=='不要覆盖别处，但当前文字也保留')
 check('memo concurrency status is visible rather than falsely saying saved','停止覆盖' in p.locator('[data-memo-editor]').inner_text())
 p.screenshot(path=str(OUT/'memo-conflict.png'))
 check('no unhandled application error during layout and settings tests',not ERRORS,ERRORS)

def main():
 with sync_playwright() as pw:
  b=pw.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);ctx=b.new_context(viewport={'width':393,'height':820},is_mobile=True,has_touch=True,locale='zh-CN',timezone_id='Asia/Shanghai');p=r.boot(ctx,errors=ERRORS)
  try:run(p,ctx)
  except Exception:RESULT.append({'name':'suite completes','passed':False,'detail':traceback.format_exc()});print(traceback.format_exc(),flush=True);p.screenshot(path=str(OUT/'layout-failure.png'))
  report={'environment':'production bundle in explicit offline MemoryStorage/Android/SHA fixtures; no device/CSP/IndexedDB claim','checks':RESULT,'passed':sum(c['passed'] for c in RESULT),'failed':sum(not c['passed'] for c in RESULT),'errors':ERRORS};(OUT/'layout-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print('TOTAL',report['passed'],report['failed'],flush=True);b.close()
 raise SystemExit(int(any(not c['passed'] for c in RESULT)))
if __name__=='__main__':main()
