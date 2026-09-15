"""Smoke-test only the independent APK in a disposable Android emulator."""
from pathlib import Path
import subprocess,time,json,hashlib,xml.etree.ElementTree as ET,re,traceback
out=Path('work/native205');out.mkdir(parents=True,exist_ok=True)
pkg='com.luke.summer.review205';apk=Path('outputs/review205/Four-Seasons-Luke-2.0.5-independent.apk');results=[]
def adb(*args):return subprocess.check_output(['adb',*args],text=True,timeout=45)
def record(name,ok):
    results.append({'name':name,'passed':bool(ok)});(out/'results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2))
    if not ok:raise AssertionError(name)
def tree():
    adb('shell','uiautomator','dump','/sdcard/review205.xml')
    return ET.fromstring(adb('shell','cat','/sdcard/review205.xml'))
def find(label):
    root=tree()
    for node in root.iter('node'):
        if node.get('text')==label or node.get('content-desc')==label:
            coords=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
            if len(coords)==4 and coords[2]>coords[0] and coords[3]>coords[1]:return coords
    return None
def wait(label,seconds=40):
    until=time.monotonic()+seconds
    while time.monotonic()<until:
        pos=find(label)
        if pos:return pos
        time.sleep(1)
    raise TimeoutError('Native accessibility label not found: '+label)
def click(label):
    x1,y1,x2,y2=wait(label);adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.7)
def screenshot(name):
    data=subprocess.check_output(['adb','exec-out','screencap','-p'],timeout=30);assert data.startswith(b'\x89PNG');(out/(name+'.png')).write_bytes(data)
try:
    manifest=json.loads(Path('outputs/review205/build205.json').read_text())
    record('native test uses exactly the delivered APK',hashlib.sha256(apk.read_bytes()).hexdigest()==manifest['sha256'])
    (out/'environment.txt').write_text(adb('shell','getprop','ro.build.version.sdk')+adb('shell','dumpsys','webviewupdate'))
    installed=adb('install',str(apk));record('independent APK installs on Android emulator','Success' in installed)
    adb('logcat','-c');adb('shell','am','start','-W','-n',pkg+'/com.luke.summer.MainActivity');wait('回到身边');screenshot('native-home');record('cold native launch renders bottom navigation',True)
    for label,shot in [('悄悄话','native-chat'),('一起计划','native-calendar'),('计时','native-timer')]:
        click(label);screenshot(shot);record('native tab opens '+label,pkg in adb('shell','dumpsys','activity','activities'))
    click('回到身边');click('打开设置');wait('搜索设置');screenshot('native-settings');record('native settings opens accessible search',True)
    adb('shell','input','keyevent','4');time.sleep(.8);record('Android back keeps the app alive',bool(adb('shell','pidof',pkg).strip()))
    adb('shell','am','force-stop',pkg);adb('shell','am','start','-W','-n',pkg+'/com.luke.summer.MainActivity');wait('回到身边');record('native process restart renders again',True)
    crashes=adb('logcat','-b','crash','-d');(out/'crash-log.txt').write_text(crashes);record('no native application crash was logged',pkg not in crashes)
except Exception:
    (out/'failure.txt').write_text(traceback.format_exc());results.append({'name':'native smoke suite completed','passed':False});
    try:screenshot('native-failure');(out/'ui-failure.xml').write_text(adb('shell','cat','/sdcard/review205.xml'))
    except Exception:pass
finally:
    (out/'results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2))
print(json.dumps(results,ensure_ascii=False,indent=2))
raise SystemExit(0 if results and all(r['passed'] for r in results) else 1)
