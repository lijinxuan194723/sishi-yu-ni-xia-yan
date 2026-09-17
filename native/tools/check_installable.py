#!/usr/bin/env python3
"""Signature/manifest/ABI checks for the exact single-APK sideload artifact, not test APKs."""
import hashlib,json,os,pathlib,re,subprocess,sys,zipfile
from audit_native_app import dex_strings

def execute(args):
    result=subprocess.run([str(x) for x in args],check=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    print(result.stdout)
    return result.stdout

apk=pathlib.Path(sys.argv[1]);out=pathlib.Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=True)
sdk=pathlib.Path(os.environ['ANDROID_HOME'])
tools=sorted((sdk/'build-tools').iterdir(),key=lambda x:[int(v) if v.isdigit() else 0 for v in x.name.split('.')])[-1]
signing=execute([tools/'apksigner','verify','--verbose','--print-certs','--min-sdk-version','26',apk])
(out/'signature.txt').write_text(signing)
alignment=execute([tools/'zipalign','-c','-P','16','-v','4',apk]);(out/'alignment.txt').write_text(alignment)
badging=execute([tools/'aapt','dump','badging',apk]);(out/'badging.txt').write_text(badging)
manifest=execute([tools/'aapt','dump','xmltree',apk,'AndroidManifest.xml']);(out/'manifest.txt').write_text(manifest)
assert "package: name='cn.sishiyuni.nativeapp.preview'" in badging
assert "launchable-activity: name='cn.sishiyuni.app.MainActivity'" in badging
assert "sdkVersion:'26'" in badging
assert not re.search(r'testOnly[^\n]*(?:0xffffffff|true)',manifest),'testOnly APK cannot be sideloaded normally'
assert 'E: instrumentation' not in manifest,'Do not distribute an instrumentation APK'
assert 'uses-split' not in badging and 'split=' not in badging.splitlines()[0],'Standalone APK required'
assert 'application-debuggable' not in badging,'Installable variant should use release optimizations'
with zipfile.ZipFile(apk) as archive:
    assert archive.testzip() is None
    names=archive.namelist()
    assert any(n.startswith('lib/arm64-v8a/') for n in names),'Missing physical-phone ABI'
    assert any(n.startswith('lib/x86_64/') for n in names),'Missing emulator ABI'
    for n in ['assets/images/luke-blossom.webp','assets/images/companions/cat.webp','assets/holidays/2026.json']:
        assert n in names,f'Missing application asset: {n}'
    web_assets=[n for n in names if n.startswith('assets/') and n.lower().endswith(('.js','.html','.css'))]
    assert not web_assets,f'Legacy web runtime assets still packaged: {web_assets}'
    strings=set()
    for n in names:
        if re.fullmatch(r'classes\d*\.dex',n):strings.update(dex_strings(archive.read(n)))
    refs=sorted(s.decode('ascii') for s in strings if s in [b'Landroid/webkit/WebView;',b'Landroid/webkit/WebViewClient;',b'Landroid/webkit/WebChromeClient;',b'Lcom/facebook/react/ReactRootView;'])
    # Findings remain explicit until dependency bytecode is inspected. This job verifies INSTALLABILITY,
    # not the separate zero-WebView acceptance job, whose failure is not suppressed or removed.
    result={'commit':os.environ.get('GITHUB_SHA'),'file':apk.name,'bytes':apk.stat().st_size,
        'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'package':'cn.sishiyuni.nativeapp.preview',
        'min_sdk':26,'launcher':'cn.sishiyuni.app.MainActivity','test_only':False,'standalone':True,
        'signed':True,'web_assets':web_assets,'web_descriptor_findings':refs,
        'abis':sorted({n.split('/')[1] for n in names if n.startswith('lib/') and n.endswith('.so')})}
(out/'installable.json').write_text(json.dumps(result,indent=2));print(json.dumps(result,indent=2))
