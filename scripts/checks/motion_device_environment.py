"""Preflight for the disposable emulator only; never ship this recovery in the app.
A known launcher ANR before the app launches may be recovered once and is recorded.
No app failure or dialog during an application test is dismissed or hidden.
"""
import re
import time
import xml.etree.ElementTree as ET


def prepare(adb, dump, out, pkg):
    adb('shell', 'am', 'force-stop', pkg)
    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    adb('shell', 'wm', 'dismiss-keyguard')
    adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
    adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
    adb('shell', 'cmd', 'uimode', 'night', 'no')
    adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
    time.sleep(4)  # Let the image finish its first density/configuration change.
    deadline = time.monotonic() + 45
    recovered = False
    while time.monotonic() < deadline:
        xml = dump()
        tree = ET.fromstring(xml)
        nodes = list(tree.iter('node'))
        title = next((e.get('text', '') for e in nodes if e.get('resource-id') == 'android:id/alertTitle'), '')
        if "Pixel Launcher isn't responding" in title or 'Pixel Launcher is not responding' in title:
            if recovered:
                raise AssertionError('Emulator launcher repeatedly unresponsive before app test')
            (out / 'environment-launcher-anr.xml').write_text(xml)
            (out / 'environment-launcher-anr.log').write_text(adb('logcat', '-d', '-v', 'threadtime'))
            (out / 'environment-launcher-anr.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
            close = next((e for e in nodes if e.get('resource-id') == 'android:id/aerr_close'), None)
            if close is None:
                raise AssertionError('Known launcher ANR has no safe close control')
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', close.get('bounds', '')))
            adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
            recovered = True
            adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
            time.sleep(4)
            continue
        if any('launcher' in e.get('package', '').lower() for e in nodes) and not title:
            (out / 'environment-ready.xml').write_text(xml)
            (out / 'environment.txt').write_text('Launcher preflight ready. Recovered prior system launcher ANR: ' + str(recovered))
            return
        time.sleep(1)
    (out / 'environment-failure.xml').write_text(xml)
    raise AssertionError('Emulator did not reach a responsive launcher before app testing')


def check_visible_close(xml, record, label):
    nodes = list(ET.fromstring(xml).iter('node'))
    close = [n for n in nodes if n.get('text') == '关闭' or n.get('content-desc') == '关闭']
    bounds = [list(map(int, re.findall(r'\d+', n.get('bounds', '')))) for n in close]
    visible = any(len(b) == 4 and b[2] - b[0] >= 40 and b[3] - b[1] >= 40 for b in bounds)
    record(label + ' exposes a visible close button (not an offscreen node)', visible, bounds)
