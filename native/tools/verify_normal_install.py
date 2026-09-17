#!/usr/bin/env python3
"""Install the exact signed artifact, exercise UI and retain a draft across reinstall.
Only run on a disposable emulator. No testOnly (-t), downgrade or data-clear bypass.
"""
import hashlib,json,pathlib,re,subprocess,sys,time,xml.etree.ElementTree as ET
apk=pathlib.Path(sys.argv[1]);out=pathlib.Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=True)
pkg='cn.sishiyuni.nativeapp.preview'
component=pkg+'/cn.sishiyuni.app.MainActivity'
marker='install_retention_902064'

def adb(*args):
    p=subprocess.run(['adb',*map(str,args)],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=60,check=True)
    return p.stdout

def screenshot(name):
    with (out/(name+'.png')).open('wb') as f:
        subprocess.run(['adb','exec-out','screencap','-p'],stdout=f,check=True,timeout=20)

def hierarchy():
    adb('shell','uiautomator','dump','/sdcard/native-install-check.xml')
    text=adb('shell','cat','/sdcard/native-install-check.xml')
    return text,ET.fromstring(text)

def wait_for(predicate,description,timeout=35):
    deadline=time.monotonic()+timeout
    last=''
    while time.monotonic()<deadline:
        try:
            last,root=hierarchy()
            found=predicate(root)
            if found is not None and found is not False:return found
        except (ET.ParseError,subprocess.SubprocessError) as exc:last=str(exc)
        time.sleep(.15)
    (out/'last-hierarchy.xml').write_text(last,encoding='utf-8')
    raise AssertionError('Timed out: '+description)

def visible(node):
    numbers=[int(x) for x in re.findall(r'\d+',node.get('bounds',''))]
    return len(numbers)==4 and numbers[2]>numbers[0] and numbers[3]>numbers[1]

def find(root,label):
    return next((n for n in root.iter('node') if visible(n) and label in [n.get('text'),n.get('content-desc')]),None)

def tap_node(node):
    assert node.get('enabled')=='true','Control is disabled'
    l,t,r,b=[int(x) for x in re.findall(r'\d+',node.attrib['bounds'])]
    adb('shell','input','tap',(l+r)//2,(t+b)//2)

def tap(label):tap_node(wait_for(lambda r:find(r,label),'visible control '+label))

def editor(root):
    return next((n for n in root.iter('node') if n.get('class')=='android.widget.EditText' and n.get('enabled')=='true' and visible(n)),None)

def open_app(name):
    result=adb('shell','am','start','-W','-S','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','-n',component)
    (out/(name+'.txt')).write_text(result)
    assert 'Status: ok' in result,result
    wait_for(lambda r:all(find(r,s) is not None for s in ['四时与你','悄悄话','时光手记','计时']),'main application navigation')
    assert adb('shell','pidof',pkg).strip()

try:
    assert adb('shell','getprop','ro.kernel.qemu').strip()=='1','This script is for disposable Android emulators only'
    installed=adb('install','--no-streaming',apk);assert 'Success' in installed,installed
    (out/'normal-install.txt').write_text(installed)
    resolved=adb('shell','cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER',pkg)
    assert 'cn.sishiyuni.app.MainActivity' in resolved,resolved
    open_app('first-launch');screenshot('launcher')
    flags=adb('shell','dumpsys','package',pkg);assert 'TEST_ONLY' not in flags
    (out/'package.txt').write_text(flags)
    tap('悄悄话');tap_node(wait_for(editor,'enabled chat input'))
    adb('shell','input','text',marker)
    wait_for(lambda r:next((n for n in r.iter('node') if marker in n.get('text','')),None),'typed draft echoed')
    screenshot('release-chat-keyboard')
    adb('shell','input','keyevent','KEYCODE_BACK')
    tap('计时');wait_for(lambda r:find(r,'开始计时'),'timer main action')
    screenshot('release-timer');tap('回到身边')
    # Same exact bytes and signature; do NOT uninstall or clear storage between these steps.
    updated=adb('install','--no-streaming','-r',apk);assert 'Success' in updated,updated
    (out/'reinstall.txt').write_text(updated)
    open_app('after-reinstall');tap('悄悄话')
    wait_for(lambda r:next((n for n in r.iter('node') if marker in n.get('text','')),None),'draft after full process restart and reinstall')
    screenshot('release-retained-draft')
    result={'package':pkg,'sdk':adb('shell','getprop','ro.build.version.sdk').strip(),
      'apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'normal_install':True,'reinstall_without_clear':True,
      'launcher_resolved':True,'main_ui_visible':True,'test_only_bypass':False,'release_chat_input':True,
      'release_timer_visible':True,'draft_retained_after_reinstall':True}
    (out/'install-result.json').write_text(json.dumps(result,indent=2));print(json.dumps(result,indent=2))
except BaseException:
    try:
        screenshot('install-failure')
        (out/'failure-logcat.txt').write_text(adb('logcat','-d','-t','400'),encoding='utf-8')
    except Exception:pass
    raise
