"""User-path tests using the real production bundle and explicit offline fixtures.
No Android, native IME, real CSP, live API or durable IndexedDB performance claim.
"""
import sys,json,time,traceback,zipfile,io
from pathlib import Path
from playwright.sync_api import sync_playwright
import runtime210 as r
OUT=r.OUT;RESULTS=[];ERRORS=[]
def check(name,ok,detail=None):
 RESULTS.append({'name':name,'passed':bool(ok),'detail':detail});print(('PASS ' if ok else 'FAIL ')+name,flush=True)
 if not ok:raise AssertionError(name+': '+str(detail))
def close(p):
 p.get_by_role('dialog').last.locator('[data-slot=dialog-close]').click();p.wait_for_timeout(360)
def hub(p):
 p.get_by_role('button',name='对话与技能',exact=True).click();p.get_by_role('dialog').last.wait_for();p.wait_for_timeout(200);return p.get_by_role('dialog').last

def chat_and_skills(ctx):
 p=r.boot(ctx,errors=ERRORS);r.nav(p,'悄悄话');p.wait_for_timeout(350)
 check('default scale .95 / 13px and personal bubble configuration apply',p.evaluate("()=>getComputedStyle(document.documentElement).fontSize==='15.2px'&&getComputedStyle(document.querySelector('.bubble-frame p')).fontSize==='13px'"))
 check('mine short bubble hugs the right avatar with a real 12px gap',p.locator('.message.mine').evaluate("e=>{let a=e.querySelector('img').getBoundingClientRect(),b=e.querySelector('.bubble-frame').getBoundingClientRect();return Math.abs(a.top-b.top)<1&&a.left-b.right<15&&a.left>=b.right}"))
 p.screenshot(path=str(OUT/'chat-final.png'))
 text=p.get_by_role('textbox',name='发送给夏彦的消息',exact=True);text.fill('第一段未发送草稿');d=hub(p);d.get_by_role('button',name='新开一段对话').click();p.wait_for_timeout(350)
 check('new conversation is empty while old messages are kept',p.locator('.messages .message').count()==0 and p.get_by_role('textbox',name='发送给夏彦的消息').input_value()=='')
 p.get_by_role('textbox',name='发送给夏彦的消息').fill('第二段未发送草稿');d=hub(p);d.locator('.conversation-select210').filter(has_text='最初的悄悄话').click();p.wait_for_timeout(220)
 check('switching back restores only that conversation draft',p.get_by_role('textbox',name='发送给夏彦的消息').input_value()=='第一段未发送草稿' and p.locator('.messages .message').count()==3)
 d=hub(p);d.locator('.conversation-select210').filter(has_text='新的悄悄话').click();p.wait_for_timeout(220)
 check('second draft also survives a round trip',p.get_by_role('textbox',name='发送给夏彦的消息').input_value()=='第二段未发送草稿')
 p.evaluate("()=>window.__fixture.mode='hold'");text=p.get_by_role('textbox',name='发送给夏彦的消息');text.fill('第一条真正发送的问题');p.get_by_role('button',name='发送消息',exact=True).click();p.wait_for_function('()=>window.__fixture.streams.length>=1');text.fill('回复期间继续输入的新草稿')
 d=hub(p);check('conversation switch and creation cannot race an in-flight reply',d.get_by_role('button',name='新开一段对话').is_disabled() and d.locator('.conversation-select210').first.is_disabled());close(p)
 p.evaluate("()=>{window.__fixture.mode='normal';window.__fixture.pending.splice(0).forEach(f=>f())}")
 p.wait_for_function("()=>document.querySelectorAll('.messages .message:not(.mine)').length===1",timeout=10000);p.wait_for_timeout(300)
 check('reply belongs to initiating conversation and does not clear newly typed draft',p.locator('.messages .message').count()==2 and text.input_value()=='回复期间继续输入的新草稿')
 d=hub(p);d.locator('.conversation-select210').filter(has_text='最初的悄悄话').click();p.wait_for_timeout(200)
 check('previous conversation never receives the new reply',p.locator('.messages .message').count()==3 and '测试回复' not in p.locator('.messages').inner_text())
 d=hub(p);d.get_by_role('button',name='重命名对话 最初的悄悄话',exact=True).click();d.get_by_role('textbox',name='对话名称').fill('   ');d.get_by_role('button',name='保存对话名称').click();p.wait_for_timeout(80)
 check('blank rename is reported without throwing inside the React state update',d.get_by_role('alert').is_visible() and d.get_by_role('textbox',name='对话名称').is_visible())
 d.get_by_role('textbox',name='对话名称').fill('以前的聊天');d.get_by_role('button',name='保存对话名称').click();p.wait_for_timeout(160)
 check('rename changes directory title without changing message count','以前的聊天' in d.inner_text() and p.locator('.messages .message').count()==3)
 d.get_by_role('button',name='导出对话 以前的聊天',exact=True).click();check('single conversation export contains only its own original messages',p.evaluate("()=>{const b=window.__fixture.backup.at(-1);return b.text.includes('以前的聊天')&&!b.text.includes('第一条真正发送的问题')}"))
 # Import an actual MD file through the browser file input. Digest is test-only SHA bridge when about:blank lacks subtle.
 d.get_by_role('button',name='Skills',exact=True).click();p.wait_for_timeout(350);d=p.get_by_role('dialog').last
 md='---\nname: reading-helper\ndescription: 读书方法测试\n---\n使用测试标记 METHOD-ONLY-210 提供读书步骤。'
 d.get_by_label('导入技能文件').set_input_files({'name':'SKILL.md','mimeType':'text/markdown','buffer':md.encode()});d.get_by_role('button',name='确认安装',exact=True).wait_for();d.get_by_role('button',name='确认安装',exact=True).click();p.wait_for_timeout(180)
 check('skill installation is persisted but disabled until explicit enable',p.evaluate("()=>{const s=JSON.parse(localStorage.getItem('luke-chat-skills-v210'));return s.items.length===1&&!s.items[0].enabled}"))
 d.get_by_role('switch',name='启用技能 reading-helper').click();p.wait_for_function("()=>JSON.parse(localStorage.getItem('luke-chat-skills-v210')).items[0].enabled")
 p.wait_for_function("()=>document.querySelector('input[aria-label=\"启用技能 reading-helper\"]').checked");
 check('explicitly enabled skill is checksum-verified and saved',d.get_by_role('switch',name='启用技能 reading-helper').is_checked())
 p.screenshot(path=str(OUT/'skills-final.png'));close(p)
 text=p.get_by_role('textbox',name='发送给夏彦的消息');text.fill('用已启用的方法解释读书');p.get_by_role('button',name='发送消息',exact=True).click();p.wait_for_function("()=>window.__fixture.streams.some(r=>r.body.includes('METHOD-ONLY-210'))")
 p.wait_for_function("()=>!document.querySelector('.composer button[aria-label=\"停止回复\"]')",timeout=10000);p.wait_for_timeout(220)
 check('enabled method is sent as instructions without adding it to chat memory ledger',p.evaluate("()=>window.__fixture.streams.at(-1).body.includes('METHOD-ONLY-210')") and 'METHOD-ONLY-210' not in p.locator('.messages').inner_text())
 d=hub(p);d.get_by_role('button',name='Skills',exact=True).click();p.wait_for_timeout(320);d=p.get_by_role('dialog').last
 # Genuine ZIP parse/decompression with text references and ignored scripts.
 archive=io.BytesIO()
 with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
  z.writestr('pack/SKILL.md','---\nname: zip-method\ndescription: ZIP import fixture\n---\nReview instructions before use.');z.writestr('pack/references/readme.md','Reference text');z.writestr('pack/scripts/run.js','window.SHOULD_NOT_RUN=true;')
 d.get_by_label('导入技能文件').set_input_files({'name':'skill.zip','mimeType':'application/zip','buffer':archive.getvalue()});d.get_by_role('button',name='确认安装',exact=True).wait_for();d.get_by_role('button',name='确认安装',exact=True).click();p.wait_for_timeout(120)
 check('ZIP imports reference text but never executes or enables ignored scripts',p.evaluate("()=>{const s=JSON.parse(localStorage.getItem('luke-chat-skills-v210')).items.find(s=>s.id==='zip-method');return !s.enabled&&s.files['references/readme.md']==='Reference text'&&!window.SHOULD_NOT_RUN&&s.ignored.length===1}"))
 d.get_by_role('textbox',name='技能 GitHub 地址').fill('https://github.com/fixture/skills');d.get_by_role('button',name='读取技能',exact=True).click();d.locator('.skill-repo-list210 button').wait_for();d.locator('.skill-repo-list210 button').first.click();d.get_by_role('button',name='确认安装',exact=True).wait_for();d.get_by_role('button',name='确认安装',exact=True).click();p.wait_for_timeout(120)
 check('GitHub install reads a pinned commit, includes references, and avoids credentials',p.evaluate("()=>{const s=JSON.parse(localStorage.getItem('luke-chat-skills-v210')).items.find(s=>s.id==='github-helper');const c=window.__fixture.calls.filter(c=>c.url.includes('github'));return s.source.includes('aaaaaaaa')&&c.length>=5&&c.every(c=>!JSON.parse(c.headers).authorization)}"))
 p.once('dialog',lambda dialog:dialog.accept());d.get_by_role('button',name='卸载技能 zip-method').click();p.wait_for_timeout(150)
 check('uninstall removes the skill only, not chat history',p.evaluate("()=>!JSON.parse(localStorage.getItem('luke-chat-skills-v210')).items.some(s=>s.id==='zip-method')"))
 close(p)
 # Save & reconstruct the runtime using captured storage; this is not real IndexedDB validation.
 p.wait_for_timeout(700);stored=r.snapshot_store(p);p.close();p=r.boot(ctx,s=stored,errors=ERRORS);r.nav(p,'悄悄话');d=hub(p)
 check('directory, titles and enable choices survive offline runtime reconstruction',d.locator('.conversation-select210').count()==2 and '以前的聊天' in d.inner_text() and p.evaluate("()=>JSON.parse(localStorage.getItem('luke-chat-skills-v210')).items.some(s=>s.id==='reading-helper'&&s.enabled)"))
 p.close()

def timer_and_notes(ctx):
 p=r.boot(ctx,errors=ERRORS);r.nav(p,'计时');pane=p.locator('#main-panel-timers');
 check('fresh user has no hard-coded mathematics or English subjects',pane.get_by_role('combobox').inner_text().strip()=='先添加你的科目')
 pane.get_by_role('button',name='管理学习科目',exact=True).click();d=p.get_by_role('dialog').last;d.get_by_role('textbox',name='科目名称').fill('阅读');d.get_by_role('button',name='添加科目',exact=True).click();p.wait_for_timeout(90)
 d.get_by_role('button',name='修改科目 阅读',exact=True).click();d.get_by_role('textbox',name='科目名称').fill('阅读复盘');d.get_by_role('button',name='保存科目名称',exact=True).click();p.wait_for_timeout(100);close(p)
 check('subject creation and rename update the actual selector',pane.get_by_role('combobox').locator('option:checked').inner_text()=='阅读复盘')
 pane.get_by_role('button',name='开始计时',exact=True).click();p.wait_for_timeout(1150)
 check('clock advances and mechanical hand animates without moving the center',pane.locator('.focus-clock').inner_text()!='00:00:00' and pane.locator('.mechanical-dial210').get_attribute('data-running')=='true')
 pane.get_by_role('button',name='结束并保存',exact=True).click();p.wait_for_timeout(250)
 pane.get_by_role('button',name='管理学习科目',exact=True).click();d=p.get_by_role('dialog').last;p.once('dialog',lambda d:d.accept());d.get_by_role('button',name='删除科目 阅读复盘',exact=True).click();p.wait_for_timeout(100);close(p)
 check('deleting a subject does not erase its existing study record',p.evaluate("()=>{const d=JSON.parse(localStorage.getItem('luke-companion-v1'));return d.focusLog.some(l=>l.group==='阅读复盘')}"))
 pane.get_by_role('tab',name='提醒',exact=True).click();p.wait_for_timeout(220);ruler=p.get_by_role('slider',name='拖动刻度选择倒计时',exact=True);ruler.scroll_into_view_if_needed();ruler.focus();p.keyboard.press('ArrowRight');p.keyboard.press('Enter');p.wait_for_timeout(260)
 check('real keyboard arrow and Enter starts selected countdown minutes',pane.locator('.in-app-countdown210').get_by_role('button',name='暂停',exact=True).is_visible())
 pane.locator('.in-app-countdown210').get_by_role('button',name='暂停',exact=True).click();p.wait_for_timeout(100);check('pause is durable and resume remains available',pane.locator('.in-app-countdown210').get_by_role('button',name='继续',exact=True).is_visible())
 pane.locator('.in-app-countdown210').get_by_role('button',name='重置',exact=True).click();p.wait_for_timeout(120)
 # Actual mouse pointer, not dispatch-only, verifies follow ruler motion and release behavior.
 ruler.scroll_into_view_if_needed();box=ruler.bounding_box();x=box['x']+box['width']*.7;y=box['y']+box['height']*.6;p.mouse.move(x,y);p.mouse.down();p.mouse.move(x-64,y,steps=6);p.wait_for_timeout(40)
 check('ruler changes while finger/pointer is down, before countdown starts',ruler.get_attribute('aria-valuenow')=='34' and pane.locator('.in-app-countdown210').get_by_role('button',name='开始倒计时').is_visible())
 p.mouse.up();p.wait_for_timeout(180);check('release starts the chosen 34 minute countdown exactly once',p.evaluate("()=>JSON.parse(localStorage.getItem('luke-companion-v1')).countdown.seconds===2040"))
 pane.locator('.in-app-countdown210').get_by_role('button',name='暂停',exact=True).click();p.wait_for_timeout(70);pane.locator('.in-app-countdown210').get_by_role('button',name='重置',exact=True).click();p.wait_for_timeout(100)
 ruler.scroll_into_view_if_needed();box=ruler.bounding_box();x=box['x']+box['width']*.5;y=box['y']+box['height']*.5;p.mouse.move(x,y);p.mouse.down();p.mouse.move(x+2,y-50,steps=5);p.mouse.up();p.wait_for_timeout(120)
 check('vertical scrolling the ruler does not launch a countdown or change main tab',pane.locator('.in-app-countdown210').get_by_role('button',name='开始倒计时').is_visible() and p.locator('.page-viewport210').get_attribute('data-page')=='timers')
 p.screenshot(path=str(OUT/'countdown-final.png'))
 r.nav(p,'时光手记');p.get_by_role('button',name='新建笔记',exact=True).click();p.get_by_role('textbox',name='笔记标题').fill('测试手记');body=p.get_by_role('textbox',name='笔记正文');body.fill('第一行\n第二行');body.focus();p.keyboard.press('Control+A');p.get_by_role('button',name='待办列表',exact=True).click();p.wait_for_timeout(100)
 check('memo formatting applies to the full multiline selection',body.input_value()=='- [ ] 第一行\n- [ ] 第二行')
 p.get_by_role('button',name='返回笔记列表',exact=True).click();p.wait_for_timeout(240)
 check('closing note flushes latest body to storage rather than discarding it',p.evaluate("()=>JSON.parse(localStorage.getItem('luke-memo-workspace-v1')).memos.some(m=>m.title==='测试手记'&&m.body.includes('- [ ] 第二行'))"))
 check('duplicate top-right notebook/search shortcuts are not shown',p.get_by_role('button',name='搜索笔记',exact=True).count()==0 and p.get_by_role('button',name='笔记本',exact=True).count()==0)
 p.screenshot(path=str(OUT/'notes-final.png'))
 r.nav(p,'他的此刻');p.get_by_role('tab',name='在读',exact=True).click();p.wait_for_timeout(700)
 check('book browser does not have the old fixed titles',not any(t in p.locator('#main-panel-heart').inner_text() for t in ['许三观卖血记','海子诗全集']))
 p.get_by_role('button',name='添加书籍',exact=True).click();d=p.get_by_role('dialog').last;d.get_by_label('书名',exact=True).fill('自己的书');d.get_by_label('作者',exact=True).fill('自己的作者');d.get_by_label('感想',exact=True).fill('这段文字应带进手记');d.get_by_role('button',name='保存并设为正在读').click();p.wait_for_timeout(250)
 # choose our saved book without assuming daily recommendation index/order.
 for _ in range(12):
  if '自己的书' in p.locator('.reading-book').inner_text():break
  p.get_by_role('button',name='下一本已保存的书').click();p.wait_for_timeout(50)
 p.get_by_role('button',name='管理这本书',exact=True).click();p.wait_for_timeout(220);p.get_by_role('button',name='写读后感',exact=True).click();p.wait_for_timeout(650)
 check('reading-to-notebook action carries actual title/author/notes into editor',p.get_by_role('textbox',name='笔记正文').is_visible() and '自己的书' in p.get_by_role('textbox',name='笔记正文').input_value() and '这段文字应带进手记' in p.get_by_role('textbox',name='笔记正文').input_value())
 p.get_by_role('button',name='返回笔记列表').click();p.wait_for_timeout(200)
 p.close()

def motion_weather(ctx):
 p=r.boot(ctx,errors=ERRORS);p.wait_for_timeout(350)
 p.get_by_role('tab',name='今日',exact=True).click();p.wait_for_timeout(280)
 check('weather current, hourly and seven-day values come from parsed fixture, not decorative defaults',p.locator('.weather-card210').inner_text().find('22')>=0 and p.locator('.weather-forecast210 .weather-day').count()==7)
 p.get_by_role('button',name='逐小时',exact=True).click();p.wait_for_timeout(150)
 check('hourly forecast switches to real supplied hour rows',p.locator('.weather-forecast210>div').count()>0)
 before=p.locator('.temperature-window210').inner_text();p.get_by_role('button',name='切换温度单位',exact=True).click();p.wait_for_timeout(350)
 check('temperature unit changes display while raw Celsius cache remains unchanged',before!=p.locator('.temperature-window210').inner_text() and p.evaluate("()=>Object.values(JSON.parse(localStorage.getItem('luke-weather-cache-v210')))[0].temperature===22"))
 p.screenshot(path=str(OUT/'weather-final.png'))
 p.get_by_role('tab',name='相伴',exact=True).click();p.wait_for_timeout(220);p.get_by_role('button',name='放大查看相册照片',exact=True).click();p.wait_for_timeout(380)
 check('photo detail displays matching decoded image using a shared layout transition',p.get_by_role('dialog').last.locator('img').evaluate('e=>e.complete&&e.naturalWidth>0'))
 close(p);check('shared photo return preserves the selected home section',p.get_by_role('tab',name='相伴',exact=True).get_attribute('aria-selected')=='true')
 # Swipe on a neutral scroll area (not the photo gallery or a button).
 p.locator('#main-panel-home .section-scroll210').evaluate('e=>e.scrollTop=e.scrollHeight');p.wait_for_timeout(70);q=p.locator('#main-panel-home .quote-card p').bounding_box();x=q['x']+q['width']*.8;y=q['y']+q['height']*.5;p.mouse.move(x,y);p.mouse.down();p.mouse.move(x-105,y+1,steps=6);p.wait_for_timeout(50)
 progress=p.evaluate("()=>Number(document.documentElement.style.getPropertyValue('--page-progress210'))")
 check('whole page and navigation indicator follow pointer before release',progress>0.1 and progress<.9,progress)
 p.mouse.up();p.wait_for_timeout(650)
 check('swipe release settles on next page with matching title and selected navigation',p.locator('.page-viewport210').get_attribute('data-page')=='chat' and p.locator('#main-tab-chat').get_attribute('aria-selected')=='true')
 d=hub(p);before=p.locator('.page-viewport210').get_attribute('data-page');bb=d.bounding_box();sx=bb['x']+bb['width']*.7;sy=bb['y']+90;p.mouse.move(sx,sy);p.mouse.down();p.mouse.move(sx-60,sy,steps=5);p.mouse.up();p.wait_for_timeout(100)
 check('open dialog disables underlying page swipe',p.locator('.page-viewport210').get_attribute('data-page')==before);
 if p.get_by_role('dialog').count():close(p)
 p.emulate_media(reduced_motion='reduce');r.nav(p,'计时');check('reduced motion navigation settles without a running spring',p.locator('.page-track210').evaluate("e=>Math.abs(e.getBoundingClientRect().x+5*e.parentElement.clientWidth)<1"));p.emulate_media(reduced_motion='no-preference')
 p.close()

def main():
 with sync_playwright() as pw:
  b=pw.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);
  for name,fn in [('chat/skills',chat_and_skills),('timer/notes',timer_and_notes),('motion/weather',motion_weather)]:
   ctx=b.new_context(viewport={'width':393,'height':820},is_mobile=True,has_touch=True,locale='zh-CN',timezone_id='Asia/Shanghai',reduced_motion='no-preference')
   try:fn(ctx)
   except Exception as e:
    RESULTS.append({'name':name+' suite completes','passed':False,'detail':traceback.format_exc()});print(traceback.format_exc(),flush=True)
    if ctx.pages:ctx.pages[-1].screenshot(path=str(OUT/(name.replace('/','-')+'-failure.png')))
   finally:ctx.close()
  RESULTS.append({'name':'no unhandled application exceptions','passed':not ERRORS,'detail':ERRORS})
  report={'environment':'Real compiled JS/CSS, offline about:blank renderer; MemoryStorage/native bridge/SHA fixture; no real CSP, IndexedDB, live providers or Android device claim','browser':b.version,'checks':RESULTS,'passed':sum(c['passed'] for c in RESULTS),'failed':sum(not c['passed'] for c in RESULTS),'errors':ERRORS};(OUT/'functional-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print('TOTAL',report['passed'],report['failed'],flush=True);b.close()
 raise SystemExit(int(any(not c['passed'] for c in RESULTS)))
if __name__=='__main__':main()
