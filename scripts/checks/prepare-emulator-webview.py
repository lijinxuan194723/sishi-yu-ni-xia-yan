"""Only for disposable CI emulators: pin the official AOSP test WebView.
The image's Android 11 WebView 83 cannot parse the app's existing ES2022 bundle.
This old prebuilt is for isolated, network-free app tests, not for user devices.
"""
import base64,hashlib,json,pathlib,subprocess,urllib.request
out=pathlib.Path('work/motion-android');out.mkdir(parents=True,exist_ok=True)
def adb(*args):return subprocess.check_output(['adb',*args],text=True)
original=adb('shell','dumpsys','webviewupdate');(out/'original-webview.txt').write_text(original)
if adb('shell','getprop','ro.build.version.sdk').strip()=='30':
 # Immutable tree in the official Android source repository, version 128.0.6613.88.
 url='https://android.googlesource.com/platform/external/chromium-webview/+/616bc0ca72d135d718d4ac64247067c6187f60b2/webview.apk?format=TEXT'
 data=base64.b64decode(urllib.request.urlopen(url,timeout=180).read(),validate=True)
 apk=pathlib.Path('work/aosp-webview-128.apk');apk.write_bytes(data)
 subprocess.run(['adb','install','-r',str(apk)],check=True,timeout=120)
 change=adb('shell','cmd','webviewupdate','set-webview-implementation','com.android.webview')
 if 'Success' not in change:raise SystemExit(change)
 (out/'test-webview.json').write_text(json.dumps({'source':url,'version':'128.0.6613.88','sha256':hashlib.sha256(data).hexdigest(),'scope':'disposable emulator only'},indent=2))
