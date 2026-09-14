"""Runtime tests on isolated Android emulators using the exact signed APK.
No model credentials or personal records are loaded. A disposable note tests upgrade.
"""
import hashlib,json,pathlib,re,subprocess,time,xml.etree.ElementTree as ET
from seasonal_android import verify_seasonal_android
from android_observer import AndroidObserver
from renderer_recovery import verify_renderer_recovery
from scoped_android_log import after_marker
import uuid
from motion_observation import choose_control, wait_rotation
from motion_device_environment import prepare, check_visible_close
out=pathlib.Path('work/motion-android');out.mkdir(parents=True,exist_ok=True)
pkg='com.luke.summer.preview';component=pkg+'/com.luke.summer.MainActivity'
apk=next(pathlib.Path('work/motion-candidate').glob('*.apk'));results=[];observer=None

def adb(*args,binary=False,timeout=30):
 return subprocess.check_output(['adb',*args],text=not binary,timeout=timeout)
def record(name,ok,detail=None):
 results.append({'name':name,'passed':bool(ok),'detail':detail});save();print(('PASS ' if ok else 'FAIL ')+name,flush=True)
 if not ok:raise AssertionError(name)
def save():
 (out/'results.json').write_text(json.dumps({'sdk':sdk,'apkSha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'results':results},ensure_ascii=False,indent=2))
def dump():return observer.dump()
def wait_text(*terms):
 end=time.monotonic()+12;last=''
 while time.monotonic()<end:
  last=dump()
  if all(term in last for term in terms):return last
  time.sleep(.3)
 (out/'missing-content.xml').write_text(last)
 raise AssertionError('Content did not become accessible: '+str(terms))
def wait_home():
 end=time.monotonic()+35;last=''
 while time.monotonic()<end:
  try:
   last=dump()
   if '启动暂未完成' in last or '页面显示已中断' in last:raise AssertionError('Native startup fallback shown instead of app')
   if '悄悄话' in last and '回到身边' in last and '打开设置' in last:return last
  except subprocess.SubprocessError:pass
  time.sleep(.5)
 (out/'last-window.xml').write_text(last);raise AssertionError('Home did not become accessible')
def tap_node(node):
 x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
 if x2<=x1 or y2<=y1:raise AssertionError('Control has empty bounds')
 adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.5)
def editor_values():
 tree=ET.fromstring(dump());fields=[e for e in tree.iter('node') if e.get('class')=='android.widget.EditText']
 fields.sort(key=lambda e:int(re.findall(r'\d+',e.get('bounds'))[1]));return [e.get('text','') for e in fields]
def tap(label):
 end=time.monotonic()+35;last='';previous=None
 while time.monotonic()<end:
  began=time.monotonic();last=dump();tree=ET.fromstring(last);node=choose_control(tree,label)
  with (out/'touch-samples.jsonl').open('a') as stream:stream.write(json.dumps({'label':label,'readSeconds':time.monotonic()-began,'rotation':tree.get('rotation'),'bounds':node.get('bounds') if node is not None else None},ensure_ascii=False)+'\n')
  if node is not None:
   state=(tree.get('rotation'),tree.get('width'),tree.get('height'),node.get('bounds'))
   if state==previous:
    with (out/'touch-actions.jsonl').open('a') as stream:stream.write(json.dumps({'label':label,'state':state,'time':time.monotonic()},ensure_ascii=False)+'\n')
    tap_node(node);return
   previous=state
  else:previous=None
  time.sleep(.2)
 (out/'missing-control.xml').write_text(last);raise AssertionError('Cannot find stable visible accessible control: '+label)
def launch():adb('shell','am','start','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','-f','0x10200000','-n',component)
def scales(value):
 for key in ['window_animation_scale','transition_animation_scale','animator_duration_scale']:adb('shell','settings','put','global',key,str(value))
def logs():return adb('logcat','-d','-v','threadtime','LukeMotion:I','LukeRenderer:E','AndroidRuntime:E','chromium:F','LukeReturn:I','LukeCase:I','libc:F','*:S')
def mark_case(name):
 token='case-'+name+'-'+uuid.uuid4().hex
 adb('shell','log','-p','i','-t','LukeCase',token)
 deadline=time.monotonic()+4
 while time.monotonic()<deadline:
  if token in logs():break
  time.sleep(.1)
 else:raise AssertionError('Test log marker was not recorded')
 with (out/'case-markers.jsonl').open('a') as f:f.write(json.dumps({'case':name,'token':token})+'\n')
 return token
def case_log(token):return after_marker(logs(),token)
def cold(name,night=False,reduced=False):
 adb('shell','am','force-stop',pkg);scales(0 if reduced else 1);adb('shell','cmd','uimode','night','yes' if night else 'no')
 time.sleep(1);adb('logcat','-c');remote='/sdcard/motion-'+name+'.mp4'
 recorder=subprocess.Popen(['adb','shell','screenrecord','--size','720x1280','--bit-rate','2500000','--time-limit','30',remote],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 time.sleep(.5)
 try:
  launch()
  if reduced:time.sleep(3)
  else:
   deadline=time.monotonic()+18
   while time.monotonic()<deadline:
    if 'exit-complete' in logs():break
    time.sleep(.2)
  wait_home();time.sleep(.6);text=logs();(out/(name+'.log')).write_text(text)
  record(name+' reaches home without native crash','FATAL EXCEPTION' not in text and 'terminated crashed=' not in text and 'Fatal signal' not in text)
  matches=re.findall(r'(system|legacy) exit-complete ms=(\d+) frames=(\d+)',text)
  if reduced:record(name+' respects disabled system animations','exit-start' not in text)
  else:
   record(name+' exit spans multiple rendered animation callbacks',bool(matches) and all(int(ms)>=300 and int(frames)>=6 for _,ms,frames in matches),matches)
   record(name+' uses the expected native path',bool(matches) and matches[-1][0]==('system' if int(sdk)>=31 else 'legacy'))
  (out/(name+'.png')).write_bytes(adb('exec-out','screencap','-p',binary=True))
 finally:
  (out/(name+'-system.log')).write_text(adb('logcat','-d','-v','threadtime'))
  subprocess.run(['adb','shell','pkill','-2','screenrecord'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
  try:recorder.wait(timeout=6)
  except subprocess.TimeoutExpired:recorder.terminate();recorder.wait(timeout=6)
  subprocess.run(['adb','pull',remote,str(out/(name+'.mp4'))],check=False)
def warm_checks():
 recorder=subprocess.Popen(['adb','shell','screenrecord','--size','720x1280','--bit-rate','2500000','--time-limit','40','/sdcard/motion-warm.mp4'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 try:
  for n in range(3):
   token=mark_case('warm-'+str(n))
   adb('shell','input','keyevent','3');time.sleep(.5);launch();wait_home()
   end=time.monotonic()+10
   while time.monotonic()<end:
    text=case_log(token)
    if 'LukeReturn' in text and 'finished' in text:break
    time.sleep(.2)
   (out/('warm-'+str(n)+'.log')).write_text(text)
   (out/('warm-'+str(n)+'-unfiltered.log')).write_text(logs())
   record('warm '+str(n)+' does not replay cold startup','exit-start' not in text)
   record('warm '+str(n)+' has a completed return transition','LukeReturn' in text and 'show ' in text and 'finished' in text,text)
   counts=re.findall(r'LukeReturn: complete frames=(\d+) ms=(\d+)',text)
   record('warm '+str(n)+' rendered multiple return frames',bool(counts) and all(int(count)>=6 for count,_ in counts),counts)
   record('warm '+str(n)+' has no renderer failure',all(m not in text for m in ['FATAL EXCEPTION','terminated crashed=','Fatal signal']))
  scales(0)
  actual={key:adb('shell','settings','get','global',key).strip() for key in ['window_animation_scale','transition_animation_scale','animator_duration_scale']}
  record('disabled animation scales are applied',all(float(value)==0 for value in actual.values()),actual)
  time.sleep(.3);token=mark_case('warm-reduced')
  adb('shell','input','keyevent','3');time.sleep(.5);launch();wait_home();time.sleep(.8)
  text=case_log(token);(out/'warm-reduced.log').write_text(text);(out/'warm-reduced-unfiltered.log').write_text(logs())
  record('warm disabled animations skip the cover','LukeReturn' not in text,text);scales(1)
 finally:
  subprocess.run(['adb','shell','pkill','-2','screenrecord'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
  try:recorder.wait(timeout=6)
  except subprocess.TimeoutExpired:recorder.terminate();recorder.wait(timeout=6)
  subprocess.run(['adb','pull','/sdcard/motion-warm.mp4',str(out/'warm-return.mp4')],check=False)
def seed_note():
 tap('时光手记');tap('新建笔记');xml=wait_text('返回笔记列表','android.widget.EditText');tree=ET.fromstring(xml)
 fields=[e for e in tree.iter('node') if e.get('class')=='android.widget.EditText']
 record('baseline editor exposes title and body fields','返回笔记列表' in xml and len(fields)==2)
 fields.sort(key=lambda e:int(re.findall(r'\d+',e.get('bounds'))[1]))
 tap_node(fields[0]);adb('shell','input','text','MotionUpgrade902002');time.sleep(.4)
 record('baseline note editor contains the exact fixture title',editor_values()[0]=='MotionUpgrade902002')
 (out/'baseline-note.png').write_bytes(adb('exec-out','screencap','-p',binary=True))
 adb('shell','input','keyevent','4');time.sleep(.4);tap('返回笔记列表')
def verify_upgrade_note(label='in-place upgrade retains exact saved note title'):
 tap('时光手记');opened=False
 # A redesigned list may put the fixture below the viewport. Use real swipes,
 # and exclude the separate More button even when WebView exposes aria-label as text.
 for _ in range(6):
  xml=dump();tree=ET.fromstring(xml)
  candidates=[]
  for e in tree.iter('node'):
   text=e.get('text','') or e.get('content-desc','')
   if 'MotionUpgrade902002' not in text or text.startswith('手记操作：') or e.get('clickable')!='true':continue
   b=list(map(int,re.findall(r'\d+',e.get('bounds',''))))
   if len(b)==4 and b[2]>b[0] and b[3]>b[1] and b[1]>=160 and b[3]<=1040:candidates.append(e)
  if candidates:
   tap_node(candidates[0]);opened=True;break
  adb('shell','input','swipe','360','940','360','460','450');time.sleep(.5)
 (out/'upgraded-list.png').write_bytes(adb('exec-out','screencap','-p',binary=True))
 record('saved note can be opened with an on-screen touch',opened)
 wait_text('返回笔记列表','android.widget.EditText');values=editor_values()
 record(label,bool(values) and values[0]=='MotionUpgrade902002',values)
 (out/'upgraded-note.png').write_bytes(adb('exec-out','screencap','-p',binary=True))
 tap('返回笔记列表');tap('回到身边')

sdk=adb('shell','getprop','ro.build.version.sdk').strip()
try:
 if adb('shell','getprop','ro.kernel.qemu').strip()!='1':raise RuntimeError('Test requires a disposable emulator')
 observer=AndroidObserver(adb,out);observer.start()
 adb('shell','wm','size','720x1280');adb('shell','wm','density','320')
 (out/'display.txt').write_text(adb('shell','wm','size')+adb('shell','wm','density'))
 (out/'webview-provider.txt').write_text(adb('shell','dumpsys','webviewupdate'))
 prepare(adb,dump,out,pkg)
 previous=list(pathlib.Path('work/motion-previous').glob('*.apk'))
 if previous:adb('install','-r',str(previous[0]),timeout=90);scales(1);launch();wait_home();seed_note()
 install=adb('install','-r',str(apk),timeout=90);record('signed candidate installs without uninstall','Success' in install,install.strip())
 cold('cold-light');warm_checks()
 if previous:verify_upgrade_note()
 cold('cold-dark',night=True)
 cold('cold-reduced',reduced=True);scales(1)
 tap('打开设置');xml=wait_text('日常与数据');record('settings opens in Android WebView','日常与数据' in xml);check_visible_close(xml,record,'settings');tap('关闭')
 tap('他的此刻');tap('开始情景对话');xml=wait_text('查看话题背景','会话记录','android.widget.EditText')
 record('topic dialog is usable in Android WebView','查看话题背景' in xml and '会话记录' in xml and 'android.widget.EditText' in xml);check_visible_close(xml,record,'topic dialog');tap('关闭')
 tap('计时');tap('学习科目');xml=wait_text('输入其他科目')
 record('animated subject menu is usable','输入其他科目' in xml);tap('学习科目')
 adb('shell','settings','put','system','accelerometer_rotation','0');adb('shell','settings','put','system','user_rotation','1');wait_rotation(dump,1)
 record('rotation does not show a failed startup','启动暂未完成' not in dump())
 record('no native crash during interaction smoke tests',all(marker not in logs() for marker in ['FATAL EXCEPTION','terminated crashed=','Fatal signal']))
 verify_seasonal_android(adb,tap,dump,wait_home,wait_text,launch,record,out,pkg)
 verify_renderer_recovery(adb,tap,dump,wait_home,wait_text,record,out,pkg,observer,verify_upgrade_note)
except Exception as e:
 results.append({'name':'runtime failure','passed':False,'detail':str(e)});save()
 try:(out/'failure.png').write_bytes(adb('exec-out','screencap','-p',binary=True));(out/'failure.log').write_text(adb('logcat','-d','-v','threadtime'));(out/'failure.xml').write_text(dump())
 except Exception:pass
 raise
finally:
 save()
 if observer is not None:observer.close()
