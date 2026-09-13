"""Runtime tests on an isolated Android emulator using the real signed APK.
No model credentials or user records are loaded. A disposable note tests upgrade safety.
"""
import hashlib,json,os,pathlib,re,subprocess,time,xml.etree.ElementTree as ET
out=pathlib.Path('work/motion-android');out.mkdir(parents=True,exist_ok=True)
pkg='com.luke.summer.preview';component=pkg+'/com.luke.summer.MainActivity'
apk=next(pathlib.Path('work/motion-candidate').glob('*.apk'));results=[]

def adb(*args,binary=False,timeout=30):
 return subprocess.check_output(['adb',*args],text=not binary,timeout=timeout)
def record(name,ok,detail=None):
 results.append({'name':name,'passed':bool(ok),'detail':detail});save();print(('PASS ' if ok else 'FAIL ')+name,flush=True)
 if not ok:raise AssertionError(name)
def save():
 (out/'results.json').write_text(json.dumps({'sdk':sdk,'apkSha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'results':results},ensure_ascii=False,indent=2))
def dump():
 adb('shell','uiautomator','dump','/sdcard/motion-window.xml',timeout=20)
 return adb('shell','cat','/sdcard/motion-window.xml')
def wait_home():
 end=time.monotonic()+35;last=''
 while time.monotonic()<end:
  try:
   last=dump()
   if '启动暂未完成' in last:raise AssertionError('Native startup fallback shown instead of app')
   if '悄悄话' in last and '回到身边' in last:return last
  except subprocess.SubprocessError:pass
  time.sleep(.5)
 (out/'last-window.xml').write_text(last);raise AssertionError('Home did not become accessible')
def tap(label):
 tree=ET.fromstring(dump())
 nodes=[e for e in tree.iter('node') if e.get('text')==label or e.get('content-desc')==label]
 if not nodes:raise AssertionError('Cannot find accessible control: '+label)
 node=nodes[-1];x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
 adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.5)
def launch():
 adb('shell','am','start','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','-f','0x10200000','-n',component)
def scales(value):
 for key in ['window_animation_scale','transition_animation_scale','animator_duration_scale']:
  adb('shell','settings','put','global',key,str(value))
def logs():return adb('logcat','-d','-v','threadtime','LukeMotion:I','AndroidRuntime:E','*:S')
def cold(name,night=False,reduced=False):
 scales(0 if reduced else 1);adb('shell','cmd','uimode','night','yes' if night else 'no')
 adb('shell','am','force-stop',pkg);adb('logcat','-c')
 remote='/sdcard/motion-'+name+'.mp4'
 recorder=subprocess.Popen(['adb','shell','screenrecord','--size','720x1280','--bit-rate','2500000','--time-limit','30',remote],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 time.sleep(.5)
 try:
  launch()
  # Do not walk the accessibility tree on the UI thread during the measured fade.
  # Poll logcat (outside the app) until animation completion, then inspect controls.
  if reduced:time.sleep(3)
  else:
   deadline=time.monotonic()+18
   while time.monotonic()<deadline:
    if 'exit-complete' in logs():break
    time.sleep(.2)
  wait_home();time.sleep(.6)
  text=logs();(out/(name+'.log')).write_text(text)
  record(name+' reaches home without native crash','FATAL EXCEPTION' not in text)
  matches=re.findall(r'(system|legacy) exit-complete ms=(\d+) frames=(\d+)',text)
  if reduced:record(name+' respects disabled system animations','exit-start' not in text)
  else:
   record(name+' exit spans multiple rendered animation callbacks',bool(matches) and all(int(ms)>=300 and int(frames)>=6 for _,ms,frames in matches),matches)
   record(name+' uses the expected native path',bool(matches) and matches[-1][0]==('system' if int(sdk)>=31 else 'legacy'))
  (out/(name+'.png')).write_bytes(adb('exec-out','screencap','-p',binary=True))
 finally:
  subprocess.run(['adb','shell','pkill','-2','screenrecord'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
  try:recorder.wait(timeout=6)
  except subprocess.TimeoutExpired:recorder.terminate();recorder.wait(timeout=6)
  subprocess.run(['adb','pull',remote,str(out/(name+'.mp4'))],check=False)

def seed_note():
 tap('时光手记');tap('新建笔记');tap('笔记标题');adb('shell','input','text','MotionUpgrade902002');
 adb('shell','input','keyevent','4');time.sleep(.4);tap('返回笔记列表')
 record('baseline contains disposable upgrade note','MotionUpgrade902002' in dump())

sdk=adb('shell','getprop','ro.build.version.sdk').strip()
try:
 adb('shell','wm','size','720x1280');adb('shell','wm','density','320')
 (out/'display.txt').write_text(adb('shell','wm','size')+adb('shell','wm','density'))
 (out/'webview-provider.txt').write_text(adb('shell','dumpsys','webviewupdate'))
 previous=list(pathlib.Path('work/motion-previous').glob('*.apk'))
 if previous:
  adb('install','-r',str(previous[0]),timeout=90);scales(1);launch();wait_home();seed_note()
 install=adb('install','-r',str(apk),timeout=90);record('signed candidate installs without uninstall','Success' in install,install.strip())
 cold('cold-light');cold('cold-dark',night=True)
 # Warm resume must not recreate the launch cover.
 before=len(re.findall('exit-start',logs()));adb('shell','input','keyevent','3');time.sleep(.5);launch();wait_home();time.sleep(.5)
 record('warm resume does not replay startup',len(re.findall('exit-start',logs()))==before)
 if previous:
  tap('时光手记');record('in-place upgrade retains the existing note','MotionUpgrade902002' in dump());tap('回到身边')
 cold('cold-reduced',reduced=True)
 scales(1)
 tap('打开设置');record('settings opens in Android WebView','日常与数据' in dump());tap('关闭')
 tap('他的此刻');tap('开始情景对话');record('topic dialog is usable in Android WebView','想和你聊聊' in dump() or '独立对话消息' in dump());tap('关闭')
 tap('计时');tap('学习科目');record('animated subject menu is usable','选择学习科目' in dump() or '输入其他科目' in dump());tap('学习科目')
 adb('shell','settings','put','system','accelerometer_rotation','0');adb('shell','settings','put','system','user_rotation','1');time.sleep(1)
 record('rotation does not show a failed startup','启动暂未完成' not in dump())
 record('no native crash during interaction smoke tests','FATAL EXCEPTION' not in logs())
except Exception as e:
 results.append({'name':'runtime failure','passed':False,'detail':str(e)});save()
 try:(out/'failure.png').write_bytes(adb('exec-out','screencap','-p',binary=True));(out/'failure.log').write_text(logs());(out/'failure.xml').write_text(dump())
 except Exception:pass
 raise
finally:save()
