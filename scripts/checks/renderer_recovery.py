"""Fault injection only in an isolated Android emulator, not in the shipped app.
Terminate only its uniquely bound sandboxed WebView renderer after normal checks, then verify
manual recovery and existing saved-note retention. Never auto-dismiss a real fault.
"""
import re,time
from motion_observation import bound_renderer

def verify_renderer_recovery(adb,tap,dump,wait_home,wait_text,record,out,pkg,observer,verify_note):
    if adb('shell','getprop','ro.kernel.qemu').strip()!='1':raise RuntimeError('Renderer injection requires a disposable emulator')
    wait_home()
    host=adb('shell','pidof',pkg).strip()
    if not re.fullmatch(r'[0-9]+',host):raise AssertionError('Expected one host process')
    root=adb('root');adb('wait-for-device');time.sleep(.5)
    adb('forward','tcp:'+str(observer.port),'tcp:'+str(observer.port))
    uid=adb('shell','id','-u').strip()
    (out/'recovery-environment.txt').write_text(root+'\nuid='+uid+'\nScope: disposable emulator only, no production debug access enabled.\n')
    if uid!='0':raise AssertionError('Fault injection requires a rootable test image')
    processes=adb('shell','ps','-A','-o','PID,NAME');(out/'recovery-processes.txt').write_text(processes)
    services=adb('shell','dumpsys','activity','services');(out/'recovery-services.txt').write_text(services)
    # Other system applications can have their own renderer. Prove this process
    # belongs exclusively to the tested host through ActivityManager bindings.
    target=bound_renderer(services,processes,host,pkg)
    record('fault injection isolates exactly one host-bound WebView renderer',target is not None,{'host':host,'renderer':target})
    # Re-read the binding immediately before the one targeted fault injection.
    record('renderer binding remains unchanged before fault injection',
           bound_renderer(adb('shell','dumpsys','activity','services'),adb('shell','ps','-A','-o','PID,NAME'),host,pkg)==target)
    adb('logcat','-c');adb('shell','kill','-9',target)
    xml=wait_text('页面显示已中断','重新载入页面')
    record('renderer termination shows explicit recovery without host exit',adb('shell','pidof',pkg).strip()==host and '未保存的输入可能需要重新填写' in xml)
    (out/'renderer-recovery.png').write_bytes(adb('exec-out','screencap','-p',binary=True))
    time.sleep(1)
    record('renderer recovery does not silently loop or auto-reload','重新载入页面' in dump())
    tap('暂时返回桌面')
    adb('shell','am','start','-n',pkg+'/com.luke.summer.MainActivity');wait_text('页面显示已中断')
    record('recovery surface survives background and resume','重新载入页面' in dump())
    tap('重新载入页面');wait_home()
    record('manual renderer recovery returns to home','页面显示已中断' not in dump())
    verify_note('renderer recovery retains exact previously saved note')
    log=adb('logcat','-d','-v','threadtime','LukeRenderer:E','AndroidRuntime:E','chromium:F','libc:F','*:S')
    (out/'renderer-recovery.log').write_text(log)
    record('injected renderer termination is handled once with no host crash',log.count('terminated crashed=')==1 and 'FATAL EXCEPTION' not in log and 'Fatal signal' not in log,log)
