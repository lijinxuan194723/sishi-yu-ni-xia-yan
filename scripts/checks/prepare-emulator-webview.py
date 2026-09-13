"""Install an official AOSP test WebView only inside the disposable API30 emulator.
The system image's WebView 83 predates the app's existing ES2022 bundle.
This test engine is not shipped to users or used for general web browsing.
"""
import base64,hashlib,json,pathlib,subprocess,traceback,urllib.request
out=pathlib.Path('work/motion-android');out.mkdir(parents=True,exist_ok=True)
def adb(*args):return subprocess.check_output(['adb',*args],text=True)
original=adb('shell','dumpsys','webviewupdate');(out/'original-webview.txt').write_text(original)
try:
 if adb('shell','getprop','ro.build.version.sdk').strip()=='30':
  repository='https://android.googlesource.com/platform/external/chromium-webview'
  revision=subprocess.check_output(['git','ls-remote',repository,'refs/heads/main'],text=True,timeout=60).split()[0]
  if len(revision)!=40:raise RuntimeError('Cannot pin AOSP revision')
  # Gitiles requires a commit revision, not the directory's tree object hash.
  url=f'{repository}/+/{revision}/128.0.6613.88/x86_64/webview.apk?format=TEXT'
  data=base64.b64decode(urllib.request.urlopen(url,timeout=180).read())
  if not data.startswith(b'PK'):raise RuntimeError('AOSP download was not an APK')
  apk=pathlib.Path('work/aosp-webview-128.apk');apk.write_bytes(data)
  subprocess.run(['adb','install','-r',str(apk)],check=True,timeout=120)
  change=adb('shell','cmd','webviewupdate','set-webview-implementation','com.android.webview')
  if 'Success' not in change:raise RuntimeError(change)
  (out/'test-webview.json').write_text(json.dumps({'source':url,'revision':revision,'version':'128.0.6613.88','sha256':hashlib.sha256(data).hexdigest(),'scope':'disposable emulator only'},indent=2))
except Exception:
 (out/'webview-preparation-error.txt').write_text(traceback.format_exc());raise
