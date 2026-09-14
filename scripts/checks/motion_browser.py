"""Frame-by-frame interaction tests against the real compiled web bundle.
CI uses a real localhost origin. --embedded is an offline fallback with explicitly
mocked storage and local data-URL images; neither mode claims Android coverage.
"""
from pathlib import Path
import argparse, base64, functools, http.server, io, json, os, threading, traceback
from playwright.sync_api import sync_playwright
from motion_accessibility import verify_reduced_dialogs

parser=argparse.ArgumentParser()
parser.add_argument('--root',default='work/mobile-web')
parser.add_argument('--out',default='work/motion-browser')
parser.add_argument('--embedded',action='store_true')
args=parser.parse_args();root=Path(args.root).resolve();out=Path(args.out).resolve();out.mkdir(parents=True,exist_ok=True)
results=[];traces={};errors=[];server=None
css='\n'.join(p.read_text() for p in (root/'assets').glob('*.css'))
js=next((root/'assets').glob('*.js')).read_text()
if args.embedded:
 from PIL import Image
 for file in sorted((root/'images').rglob('*'),key=lambda p:len(str(p)),reverse=True):
  if file.is_file():
   im=Image.open(file);im.thumbnail((720,720));buffer=io.BytesIO();im.save(buffer,format='WEBP',quality=80)
   url='data:image/webp;base64,'+base64.b64encode(buffer.getvalue()).decode()
   name='/'+str(file.relative_to(root));css=css.replace(name,url);js=js.replace(name,url)
else:
 class QuietHandler(http.server.SimpleHTTPRequestHandler):
  def log_message(self,*a):pass
 server=http.server.ThreadingHTTPServer(('127.0.0.1',0),functools.partial(QuietHandler,directory=str(root)))
 threading.Thread(target=server.serve_forever,daemon=True).start()

SEED={'luke-appearance-v1':json.dumps({'season':'spring','period':'正午','effects':True},ensure_ascii=False)}
TRACE='''({selector,action,ms})=>new Promise(resolve=>{
 const frames=[],start=performance.now();
 function sample(){const node=document.querySelector(selector);const style=node&&getComputedStyle(node);const rect=node?.getBoundingClientRect();
 frames.push({t:Math.round(performance.now()-start),exists:!!node,opacity:style?Number(style.opacity):null,
 translate:style?.translate,transform:style?.transform,scale:style?.scale,x:rect?.x,y:rect?.y,right:rect?.right,bottom:rect?.bottom,
 inert:node?.inert,images:node?[...node.querySelectorAll('img')].map(i=>({opacity:Number(getComputedStyle(i).opacity),decoded:i.complete&&i.naturalWidth>0})):[]});
 if(performance.now()-start<ms)requestAnimationFrame(sample);else resolve(frames);}
 (new Function(action))();requestAnimationFrame(sample);
})'''

def save():
 (out/'results.json').write_text(json.dumps({'mode':'embedded/mocked-storage' if args.embedded else 'http/real-storage','results':results,'pageErrors':errors},ensure_ascii=False,indent=2))
 (out/'frames.json').write_text(json.dumps(traces,ensure_ascii=False,indent=2))
def check(name,condition,detail=None):
 results.append({'name':name,'passed':bool(condition),'detail':detail});save();print(('PASS ' if condition else 'FAIL ')+name,flush=True)
 if not condition:raise AssertionError(name)
def trace(page,name,selector,action,ms=800):
 frames=page.evaluate(TRACE,{'selector':selector,'action':action,'ms':ms});traces[name]=frames;save();return frames
def smooth(name,frames,exit=False):
 mid={round(f['opacity'],3) for f in frames if f['exists'] and 0.01<f['opacity']<.99}
 alive=[f for f in frames if f['exists']]
 check(name,len(mid)>=5 and len(alive)>=8,{'intermediateOpacities':len(mid),'frames':len(alive)})
 if exit:check(name+' completes before removal',not frames[-1]['exists'] and alive[-1]['opacity']<.12,{'lastOpacity':alive[-1]['opacity']})
 else:check(name+' reaches stable state',frames[-1]['exists'] and frames[-1]['opacity']>=.99)
def button(label,scope='document'):
 return f"[...{scope}.querySelectorAll('button')].find(b=>(b.getAttribute('aria-label')=== {json.dumps(label,ensure_ascii=False)} || b.textContent.trim()=== {json.dumps(label,ensure_ascii=False)}) && b.getBoundingClientRect().width).click();"
def nav(label):return button(label,"document.querySelector('.main-nav')")
def open_settings(page):
 page.evaluate(button('打开设置'));page.locator('.settings').wait_for();page.wait_for_timeout(420)
def close_settings(page):
 page.evaluate("document.querySelector('.settings [data-slot=dialog-close]').click()");page.locator('.settings').wait_for(state='detached')

with sync_playwright() as p:
 kwargs={'args':['--no-sandbox']}
 if os.environ.get('CHROMIUM_PATH'):kwargs['executable_path']=os.environ['CHROMIUM_PATH']
 elif args.embedded:kwargs['executable_path']='/usr/bin/chromium'
 browser=p.chromium.launch(**kwargs)
 def boot(width=390,height=844,reduced='no-preference'):
  page=browser.new_page(viewport={'width':width,'height':height},reduced_motion=reduced)
  page.set_default_timeout(10000);page.on('pageerror',lambda e:errors.append(str(e)))
  if args.embedded:
   page.set_content('<!doctype html><html lang="zh-CN"><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="root"></div></body></html>')
   page.evaluate('''seed=>{for(const prop of ['localStorage','sessionStorage']){const map=new Map(Object.entries(prop==='localStorage'?seed:{}));Object.defineProperty(window,prop,{configurable:true,value:{getItem:k=>map.get(String(k))??null,setItem:(k,v)=>map.set(String(k),String(v)),removeItem:k=>map.delete(String(k)),clear:()=>map.clear(),key:i=>[...map.keys()][i]??null,get length(){return map.size}}});}}''',SEED)
   page.add_style_tag(content=css);page.add_script_tag(content=js,type='module')
  else:
   page.add_init_script('for(const [k,v] of Object.entries('+json.dumps(SEED,ensure_ascii=False)+'))localStorage.setItem(k,v)')
   page.route('**/api/**',lambda route:route.abort())
   page.goto(f'http://127.0.0.1:{server.server_port}/')
  page.get_by_role('button',name='打开设置',exact=True).first.wait_for();page.wait_for_timeout(750)
  return page
 try:
  page=boot()
  f=trace(page,'settings-in','.settings',button('打开设置'));smooth('settings entry has real intermediate frames',f)
  f=trace(page,'settings-out','.settings',"document.querySelector('.settings [data-slot=dialog-close]').click()");smooth('settings exit',f,True)
  open_settings(page)
  f=trace(page,'settings-reverse','.settings',"document.querySelector('.settings [data-slot=dialog-close]').click();setTimeout(()=>{"+button('打开设置')+"},80)")
  check('rapid dialog reversal remains visible',f[-1]['exists'] and f[-1]['opacity']>=.99)
  page.get_by_role('tab',name='四季与昼夜',exact=True).click();page.locator('.appearance-preview img').first.wait_for();page.wait_for_timeout(500)
  f=trace(page,'season-photo','.appearance-preview .season-photo',button('冬'),1100)
  mids=[im['opacity'] for row in f for im in row['images'] if .01<im['opacity']<.99]
  check('decoded seasonal photograph has multiple blend frames',len(set(round(v,3) for v in mids))>=6)
  check('season photo never blanks during crossfade',all(sum(im['opacity'] for im in row['images'])>=.99 for row in f))
  f=trace(page,'light','.appearance-preview .light-night',button('深夜'),1100);smooth('day/night lighting interpolates',f)
  page.evaluate(button('春'));page.wait_for_timeout(70);page.evaluate(button('秋'));page.wait_for_timeout(70);page.evaluate(button('夏'));page.wait_for_timeout(1500)
  check('rapid theme selections settle on latest target',page.locator('.settings').get_attribute('data-season')=='summer')
  close_settings(page);page.wait_for_timeout(1100)
  check('gallery theme handoff removes outgoing cover',page.locator('.hero-season-cover').count()==0)
  f=trace(page,'gallery-next','.hero-track',"document.querySelector('.hero-gallery').dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowRight',bubbles:true}))",1300)
  check('gallery scroll contains real intermediate positions',len({round(v['x'],1) for v in f if v['exists']})>=8)
  check('gallery settles on second image',page.locator('.hero-gallery').get_attribute('data-photo-index')=='1')
  for label in ['他的此刻','一起计划','时光手记','计时','悄悄话','回到身边']:
   f=trace(page,'page-'+label,'.app-tabs main > [data-slot=tabs-content]:not([hidden])',nav(label),700)
   smooth('page '+label,f)
   check('page '+label+' has no horizontal overflow',page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'))
  page.evaluate(nav('他的此刻'));page.wait_for_timeout(400)
  f=trace(page,'topic-in','.topic-chat',button('开始情景对话'));smooth('topic entry',f)
  page.get_by_label('独立对话消息',exact=True).fill('保留这个草稿')
  f=trace(page,'topic-out','.topic-chat',"document.querySelector('.topic-chat [data-slot=dialog-close]').click()");smooth('topic exit retains DOM until transparent',f,True)
  page.evaluate(button('开始情景对话'));page.get_by_label('独立对话消息',exact=True).wait_for()
  check('topic draft survives animated close/reopen',page.get_by_label('独立对话消息',exact=True).input_value()=='保留这个草稿')
  page.wait_for_timeout(400)
  page.evaluate("document.querySelector('.topic-chat [data-slot=dialog-close]').click()");page.locator('.topic-chat').wait_for(state='detached')
  page.evaluate(nav('时光手记'));page.wait_for_timeout(400)
  f=trace(page,'note-in','[data-memo-editor=true]',button('新建笔记'));smooth('note editor entry',f)
  page.get_by_label('笔记标题',exact=True).fill('Motion QA retained note')
  f=trace(page,'note-out','[data-memo-editor=true]',button('返回笔记列表'));smooth('note editor exit',f,True)
  check('note is preserved after animated close',page.get_by_text('Motion QA retained note',exact=True).count()>0)
  f=trace(page,'notebooks-in','[data-memo-notebooks=true]',button('打开笔记本'));smooth('notebook panel entry',f)
  f=trace(page,'notebooks-out','[data-memo-notebooks=true]',button('关闭笔记本'));smooth('notebook panel exit',f,True)
  page.evaluate(nav('计时'));page.wait_for_timeout(400)
  f=trace(page,'subject-in','.study-popover',button('学习科目'));smooth('subject menu entry',f)
  f=trace(page,'subject-reverse','.study-popover',button('学习科目')+'setTimeout(()=>{'+button('学习科目')+'},80)')
  check('subject menu reversal keeps a continuous frame',f[-1]['exists'] and f[-1]['opacity']>=.99 and max(abs(a['opacity']-b['opacity']) for a,b in zip(f,f[1:]) if a['exists'] and b['exists'])<.45)
  f=trace(page,'subject-out','.study-popover',button('学习科目'));smooth('subject menu exit',f,True)
  page.screenshot(path=str(out/'mobile.png'))
  page.evaluate(nav('回到身边'));page.wait_for_timeout(400)
  client=page.context.new_cdp_session(page);client.send('Emulation.setCPUThrottlingRate',{'rate':4})
  f=trace(page,'cpu4-settings','.settings',button('打开设置'),1400);smooth('CPU4 dialog still produces intermediate frames',f)
  close_settings(page);client.send('Emulation.setCPUThrottlingRate',{'rate':1});client.detach()
  for width,height in [(320,568),(412,915),(768,1024),(844,390)]:
   page.set_viewport_size({'width':width,'height':height});open_settings(page)
   rect=page.locator('.settings').bounding_box()
   check(f'dialog bounds {width}x{height}',rect['x']>=-1 and rect['y']>=-1 and rect['x']+rect['width']<=width+1 and rect['y']+rect['height']<=height+1,rect)
   close_settings(page)
  page.close()
  page=boot(reduced='reduce')
  f=trace(page,'reduce-settings','.settings',button('打开设置'),450)
  check('reduced motion skips dialog animation',all(not v['exists'] or v['opacity']==1 for v in f))
  close_settings(page);page.evaluate(nav('时光手记'));page.wait_for_timeout(100);page.evaluate(button('新建笔记'));page.locator('[data-memo-editor=true]').wait_for()
  f=trace(page,'reduce-note-out','[data-memo-editor=true]',button('返回笔记列表'),200)
  check('reduced motion removes editor without a stranded layer',not any(v['exists'] for v in f))
  check('reduced motion leaves no running UI animation',page.evaluate("document.getAnimations().filter(a=>a.playState==='running').length") == 0)
  verify_reduced_dialogs(page, check)
  from memo_polish_browser import verify_memo_polish
  verify_memo_polish(page,check,out)
  page.close();check('no uncaught browser exceptions',not errors,errors)
 except Exception as exc:
  results.append({'name':'uncaught test failure','passed':False,'detail':str(exc)});save()
  try:page.screenshot(path=str(out/'failure.png'));(out/'failure.html').write_text(page.content())
  except Exception:pass
  traceback.print_exc();raise
 finally:
  browser.close()
  if server:server.shutdown()
  save()
