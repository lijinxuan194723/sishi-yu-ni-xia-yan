"""Actual signed APK: eight seasonal cold starts and real native UI actions."""
import re,subprocess,time,xml.etree.ElementTree as ET
from motion_observation import wait_rotation
from splash_pixels import verify_splash_pixels

def verify_seasonal_android(adb,tap,dump,wait_home,wait_text,launch,record,out,pkg):
    adb('shell','settings','put','system','user_rotation','0');wait_rotation(dump,0)
    tap('回到身边');wait_home()
    def visible(label):
        for _ in range(5):
            xml=dump()
            candidates=[n for n in ET.fromstring(xml).iter('node') if n.get('text')==label or n.get('content-desc')==label]
            for n in candidates:
                b=list(map(int,re.findall(r'\d+',n.get('bounds',''))))
                if len(b)==4 and 40<b[1]<1120 and b[3]>b[1] and b[2]>b[0]:
                    tap(label);return
            adb('shell','input','swipe','560','1000','560','500','350');time.sleep(.4)
        raise AssertionError('Control unavailable after scrolling: '+label)
    for label,season in [('春','spring'),('夏','summer'),('秋','autumn'),('冬','winter')]:
        for period,mode in [('正午','day'),('深夜','night')]:
            tap('打开设置');tap('四季与昼夜');visible(label);visible(period);tap('关闭');time.sleep(.4)
            adb('shell','am','force-stop',pkg);adb('logcat','-c')
            name='season-'+season+'-'+mode
            remote='/sdcard/'+name+'.mp4'
            recorder=subprocess.Popen(['adb','shell','screenrecord','--size','720x1280','--bit-rate','2000000','--time-limit','20',remote],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
            time.sleep(.3)
            try:
                launch();time.sleep(3);wait_home()
                text=adb('logcat','-d','-v','brief','LukeSeason:I','LukeRenderer:E','AndroidRuntime:E','chromium:F','libc:F','*:S')
                (out/(name+'.log')).write_text(text)
                record('native cold launch restores '+season+' '+mode,'launch season='+season+' mode='+mode in text and all(marker not in text for marker in ['FATAL EXCEPTION','terminated crashed=','Fatal signal']),text)
            finally:
                (out/(name+'-system.log')).write_text(adb('logcat','-d','-v','threadtime'))
                subprocess.run(['adb','shell','pkill','-2','screenrecord'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
                try:recorder.wait(timeout=6)
                except subprocess.TimeoutExpired:recorder.terminate();recorder.wait(timeout=6)
                subprocess.run(['adb','pull',remote,str(out/(name+'.mp4'))],check=False)
            verify_splash_pixels(out/(name+'.mp4'),record,out)
    tap('打开设置');tap('生日与纪念日');visible('看看生日祝福')
    xml=wait_text('收下这份祝福','关闭')
    record('birthday greeting is reachable on native device','收下这份祝福' in xml and '我们的小小设定' not in xml)
    (out/'birthday.png').write_bytes(adb('exec-out','screencap','-p',binary=True))
    tap('关闭');wait_text('生日与纪念日','我们的小小设定')
    record('birthday closes back to settings without a stuck layer','收下这份祝福' not in dump())
    tap('四季与昼夜');visible('关闭触感');tap('关闭');tap('打开设置');tap('四季与昼夜');adb('logcat','-c')
    visible('夏');time.sleep(.2)
    log=adb('logcat','-d','-v','brief','LukeFeedback:D','*:S')
    record('disabled native feedback remains silent','kind=' not in log)
    visible('轻柔反馈');adb('logcat','-c');time.sleep(.1);visible('试试轻柔触感');time.sleep(.2)
    log=adb('logcat','-d','-v','brief','LukeFeedback:D','*:S')
    record('one feedback test click routes once to native selection',log.count('kind=selection')==1,log)
    tap('关闭');tap('打开设置');tap('四季与昼夜');visible('随日期');tap('关闭')
