#!/usr/bin/env bash
# Disposable review channel only. NEVER reads the 2.0.4 keystore or its cache.
set -euo pipefail
: "${ANDROID_HOME:?}" "${RUNNER_TEMP:?}"
JAR="$ANDROID_HOME/platforms/android-35/android.jar"
TOOLS="$ANDROID_HOME/build-tools/35.0.0"
STAGE="$RUNNER_TEMP/luke-review205"
mkdir -p "$STAGE/assets/web" "$STAGE/classes" "$STAGE/dex" "$STAGE/native" outputs/review205 work/check205
cp -R work/mobile-web/. "$STAGE/assets/web/"
cp mobile/android/MainActivity.java "$STAGE/native/"
cp -R mobile/android/res "$STAGE/native/res"
cp mobile/android/AndroidManifest.xml "$STAGE/native/AndroidManifest.xml"
python3 - "$STAGE/native/AndroidManifest.xml" <<'PY'
from pathlib import Path
import sys
p=Path(sys.argv[1]);s=p.read_text()
assert 'package="com.luke.summer"' in s
s=s.replace('package="com.luke.summer"','package="com.luke.summer.review205"').replace('android:name=".MainActivity"','android:name="com.luke.summer.MainActivity"')
s=s.replace("android:label='四时与你'", "android:label='四时与你·体验'").replace('android:label="四时与你"', 'android:label="四时与你·体验"')
p.write_text(s)
PY
javac -encoding UTF-8 --release 8 -classpath "$JAR" -d "$STAGE/classes" "$STAGE/native/MainActivity.java"
mapfile -d '' CLASSES < <(find "$STAGE/classes" -name '*.class' -print0)
"$TOOLS/d8" --lib "$JAR" --min-api 26 --output "$STAGE/dex" "${CLASSES[@]}"
"$TOOLS/aapt" package -f -M "$STAGE/native/AndroidManifest.xml" -S "$STAGE/native/res" -A "$STAGE/assets" -I "$JAR" -F "$STAGE/unsigned.apk"
(cd "$STAGE/dex" && "$TOOLS/aapt" add "$STAGE/unsigned.apk" classes.dex)
"$TOOLS/zipalign" -f -p 4 "$STAGE/unsigned.apk" "$STAGE/aligned.apk"
KEY="$RUNNER_TEMP/independent-review205.p12"
PASSWORD="$(openssl rand -hex 24)"
trap 'rm -f "$KEY"' EXIT
keytool -genkeypair -alias review205 -keyalg RSA -keysize 3072 -validity 365 -dname 'CN=Four Seasons Independent Review' -storetype PKCS12 -keystore "$KEY" -storepass "$PASSWORD" -keypass "$PASSWORD" >/dev/null 2>&1
APK="$GITHUB_WORKSPACE/outputs/review205/Four-Seasons-Luke-2.0.5-independent.apk"
"$TOOLS/apksigner" sign --ks "$KEY" --ks-key-alias review205 --ks-pass "pass:$PASSWORD" --key-pass "pass:$PASSWORD" --out "$APK" "$STAGE/aligned.apk"
"$TOOLS/apksigner" verify --verbose --print-certs "$APK" > work/check205/apk-signature.txt
"$TOOLS/zipalign" -c 4 "$APK"
"$TOOLS/aapt" dump badging "$APK" > work/check205/apk-badging.txt
python3 - <<'PY'
from pathlib import Path
import re,hashlib,json,os,zipfile,io
from PIL import Image
root=Path('outputs/review205'); apk=root/'Four-Seasons-Luke-2.0.5-independent.apk'
b=Path('work/check205/apk-badging.txt').read_text();s=Path('work/check205/apk-signature.txt').read_text()
m=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",b)
assert m and m.groups()==('com.luke.summer.review205','902010','2.0.5')
assert 'application-debuggable' not in b and "application-label:'四时与你·体验'" in b
cert=re.search(r'certificate SHA-256 digest: ([a-f0-9]{64})',s)[1]
assert cert!='be727012f0f00f93ea7578f1ab37b5c22c06652997a6797c0e19cef4f1b13e69'
art={}
with zipfile.ZipFile(apk) as z:
    assert z.testzip() is None
    assert not any(n.endswith('personal-model.json') or n.endswith('.p12') for n in z.namelist())
    for category,names in [('chat-skins',['tea','sunflower','hearts']),('companions',['dog','toast','cat'])]:
        for name in names:
            path=f'images/{category}/{name}.webp'; data=z.read('assets/web/'+path)
            image=Image.open(io.BytesIO(data));image.load()
            assert min(image.size)>32
            assert data==Path('public/'+path).read_bytes()
            art[path]={'size':image.size,'sha256':hashlib.sha256(data).hexdigest()}
h=hashlib.sha256(apk.read_bytes()).hexdigest()
(root/'Four-Seasons-Luke-2.0.5-independent.sha256').write_text(h+'  '+apk.name+'\n')
(root/'build205.json').write_text(json.dumps({'commit':os.environ['SOURCE_SHA'],'run':os.environ['GITHUB_RUN_ID'],'branch':'release/v2.0.5-complete','package':m[1],'versionCode':int(m[2]),'versionName':m[3],'sha256':h,'size':apk.stat().st_size,'certificateSha256':cert,'baseline':'2.0.4','channel':'independent-review','updatesOriginalApp':False,'originalSigningMaterialUsed':False,'artwork':art},ensure_ascii=False,indent=2))
print((root/'build205.json').read_text())
PY
