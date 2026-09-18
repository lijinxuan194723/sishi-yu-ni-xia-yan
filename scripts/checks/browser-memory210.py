"""End-to-end automatic memory + recommendation preference path; synthetic model responses only."""
import json,traceback
from playwright.sync_api import sync_playwright
import runtime210 as r
OUT=r.OUT;CHECKS=[];ERRORS=[]
def check(name,ok,detail=None):
 CHECKS.append({'name':name,'passed':bool(ok),'detail':detail});print(('PASS ' if ok else 'FAIL ')+name,flush=True)
 if not ok:raise AssertionError(name)
def close(p):p.get_by_role('dialog').last.locator('[data-slot=dialog-close]').click();p.wait_for_timeout(320)
def run(p):
 # No click on 'organize': the existing worker should persist automatic source-grounded facts.
 p.wait_for_function("()=>JSON.parse(localStorage.getItem('luke-companion-v1')||'{}').memoryArchive?.facts?.some(f=>f.key==='音乐喜好')",timeout=16000)
 check('automatic memory writes a sourced fact without pressing the manual organize button',p.evaluate("()=>{const d=JSON.parse(localStorage.getItem('luke-companion-v1'));return d.messages.length===32&&d.memoryArchive.chapters.length>0&&d.memoryArchive.facts[0].sourceIndex===0&&d.messages[0].text.includes(d.memoryArchive.facts[0].quote)}"))
 r.nav(p,'他的此刻');p.get_by_role('tab',name='音乐',exact=True).click();p.wait_for_function("()=>JSON.parse(localStorage.getItem('luke-recommendation-history-v1')||'[]').length>=2",timeout=10000)
 check('book/song requests learn from confirmed memory without changing the chat transcript',p.evaluate("()=>{const requests=[...window.__fixture.calls,...window.__fixture.streams].filter(x=>x.body.includes('现在主动给用户推荐'));return requests.length>=2&&requests.every(x=>x.body.includes('confirmedFacts')&&x.body.includes('音乐喜好'))&&JSON.parse(localStorage.getItem('luke-companion-v1')).messages.length===32}"))
 p.get_by_role('button',name='推荐偏好',exact=True).click();d=p.get_by_role('dialog').last;d.get_by_label('喜欢或避开的音乐').fill('更喜欢钢琴独奏，不要过于嘈杂');d.get_by_label('喜欢或避开的书籍').fill('轻松的科普读物');d.get_by_role('button',name='保存偏好',exact=True).click();p.wait_for_timeout(280)
 before=p.evaluate("()=>JSON.parse(localStorage.getItem('luke-recommendation-history-v1')).filter(x=>x.kind==='song').length")
 # The manual regenerate makes a new real request; not just a fixed local carousel.
 p.locator('.music-moment .moment-footer button').filter(has_text='再推荐一次').click();p.wait_for_function("n=>JSON.parse(localStorage.getItem('luke-recommendation-history-v1')).filter(x=>x.kind==='song').length>n",arg=before,timeout=10000)
 check('fresh recommendation includes saved explicit preferences and avoids previous titles',p.evaluate("()=>{const q=JSON.parse([...window.__fixture.calls,...window.__fixture.streams].filter(x=>x.body.includes('现在主动给用户推荐')).at(-1).body).messages[1].content;const data=JSON.parse(q);return data.preferences.explicitPreferences.song.includes('钢琴独奏')&&data.previous.length>=1}"))
 # Favorites are real saved data, and can be managed through the unified folder/history entry.
 p.locator('.music-moment .recommendation-actions').get_by_role('button',name='收藏',exact=True).click();p.wait_for_timeout(120)
 check('recommendation favorite is persisted independently of generated history',p.evaluate("()=>JSON.parse(localStorage.getItem('luke-recommendation-favorites-v1')||'[]').length===1"))
 d=r.settings(p,'长期聊天记忆');d.get_by_role('switch',name='自动整理长期记忆').uncheck();p.wait_for_timeout(150)
 p.once('dialog',lambda dialog:dialog.accept());d.locator('.memory-fact').first.get_by_role('button',name='忘记',exact=True).click();p.wait_for_timeout(150)
 check('forgetting fact keeps original messages but blocks it from future context',p.evaluate("()=>{const d=JSON.parse(localStorage.getItem('luke-companion-v1'));return d.messages[0].text.includes('钢琴')&&d.memoryArchive.blockedKeys.includes('音乐喜好')&&d.memoryArchive.mutedSources.includes(0)&&!d.memoryArchive.facts.some(f=>f.key==='音乐喜好')}"))
 # Complete backup retains new directories while excluding model credentials.
 close(p);d=r.settings(p,'日常与数据');d.get_by_role('button',name='导出软件完整备份',exact=True).click();p.wait_for_timeout(120)
 check('full backup has memory/settings and no model API key',p.evaluate("()=>{const raw=window.__fixture.backup.at(-1).text,b=JSON.parse(raw),d=JSON.parse(b.storage['luke-companion-v1']);return b.format==='four-seasons-luke-full-backup'&&d.memoryArchive.blockedKeys.includes('音乐喜好')&&!raw.includes('local-test-only')&&b.storage['luke-recommendation-preferences-v206']}"))
 check('no application exceptions in memory/recommendation/backup flow',not ERRORS,ERRORS)
def main():
 s=r.seed();s['luke-companion-v1']['messages']=[{'who':'me' if i%2==0 else 'luke','text':'我喜欢安静的钢琴音乐' if i==0 else '这一段记录日常小事 '+str(i),**({'source':'model'} if i%2 else {}),'at':'2026-09-17T00:'+str(i).zfill(2)+':00Z'} for i in range(32)];s['luke-companion-v1']['memoryArchive']={'version':1,'enabled':True,'autoRevision':1,'chapters':[],'facts':[]};s.pop('luke-memory-retry-v205',None)
 with sync_playwright() as w:
  b=w.chromium.launch(executable_path='/usr/bin/chromium',args=['--no-sandbox']);c=b.new_context(viewport={'width':393,'height':820},is_mobile=True,has_touch=True,locale='zh-CN',timezone_id='Asia/Shanghai');p=r.boot(c,s,ERRORS)
  try:run(p)
  except Exception:CHECKS.append({'name':'suite completes','passed':False,'detail':traceback.format_exc()});print(traceback.format_exc());p.screenshot(path=str(OUT/'memory-failure.png'))
  report={'environment':'production offline replay; synthetic model extraction/recommendations, not model accuracy or native persistence validation','checks':CHECKS,'passed':sum(x['passed'] for x in CHECKS),'failed':sum(not x['passed'] for x in CHECKS),'errors':ERRORS};(OUT/'memory-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print('TOTAL',report['passed'],report['failed']);b.close()
 raise SystemExit(int(any(not c['passed'] for c in CHECKS)))
if __name__=='__main__':main()
