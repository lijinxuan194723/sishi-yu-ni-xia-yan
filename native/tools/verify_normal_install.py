#!/usr/bin/env python3
"""Install and open the exact distributed APK on a disposable emulator, with NO adb -t bypass."""
import hashlib,json,pathlib,subprocess,sys,time,xml.etree.ElementTree as ET
apk=pathlib.Path(sys.argv[1]);out=pathlib.Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=True)
pkg='cn.sishiyuni.nativeapp.preview'

def adb(*args):
    p=subprocess.run(['adb',*map(str,args)],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,check=True)
    print(p.stdout);return p.stdout

installed=adb('install','--no-streaming',apk)
assert 'Success' in installed,installed
component=adb('shell','cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER',pkg)
assert 'cn.sishiyuni.app.MainActivity' in component,component
launch=adb('shell','am','start','-W','-S','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','-n',pkg+'/cn.sishiyuni.app.MainActivity')
assert 'Status: ok' in launch,launch
(out/'launch.txt').write_text(launch)
# Keep real motion enabled; observe the main screen instead of assuming am-start success implies usability.
last=''
for _ in range(20):
    time.sleep(1)
    adb('shell','uiautomator','dump','/sdcard/native-launch.xml')
    last=adb('shell','cat','/sdcard/native-launch.xml')
    if all(label in last for label in ['四时与你','悄悄话','时光手记','计时']):break
else: raise AssertionError('Launcher did not reach the expected navigation: '+last[:1500])
assert '无法打开本机数据' not in last and '本机记录尚未读取完成' not in last
pid=adb('shell','pidof',pkg).strip();assert pid
(out/'launcher.xml').write_text(last)
with (out/'launcher.png').open('wb') as f:subprocess.run(['adb','exec-out','screencap','-p'],stdout=f,check=True)
flags=adb('shell','dumpsys','package',pkg)
assert 'TEST_ONLY' not in flags
(out/'package.txt').write_text(flags)
# Check the SAME signed artifact can be reinstalled without uninstalling/clearing user storage.
updated=adb('install','--no-streaming','-r',apk);assert 'Success' in updated
adb('shell','am','start','-W','-S','-n',pkg+'/cn.sishiyuni.app.MainActivity')
time.sleep(2);assert adb('shell','pidof',pkg).strip()
result={'package':pkg,'sdk':adb('shell','getprop','ro.build.version.sdk').strip(),
 'apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'normal_install':True,'reinstall_without_clear':True,
 'launcher_resolved':True,'main_ui_visible':True,'test_only_bypass':False}
(out/'install-result.json').write_text(json.dumps(result,indent=2));print(json.dumps(result,indent=2))
