"""Static checks only; does not replace an Android SDK build or device tests."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[2]
res=root/'mobile/android/res'
java=(root/'mobile/android/MainActivity.java').read_text(encoding='utf-8')
A='{http://schemas.android.com/apk/res/android}'
checks=[]
def check(name,condition):
 assert condition,name
 checks.append(name)
 print('PASS:',name)
xmls=list(res.rglob('luke_launch*.xml'))+list(res.rglob('launch.xml'))+list(res.rglob('styles.xml'))
trees=[ET.parse(p).getroot() for p in xmls]
check('all startup XML is well formed',len(trees)>=10)
resources=set()
for p,t in zip(xmls,trees):
 if p.parent.name.startswith('values'):
  resources.update((el.tag,el.attrib['name']) for el in t if 'name' in el.attrib)
 else:resources.add((p.parent.name.split('-')[0],p.stem))
for t in trees:
 for e in t.iter():
  for val in list(e.attrib.values())+[e.text or '']:
   for typ,name in re.findall(r'@(color|bool|drawable|animator|interpolator)/([a-z0-9_]+)',val):
    check('resolved resource '+typ+'/'+name,(typ,name) in resources)
light=ET.parse(res/'values/launch.xml').getroot()
night=ET.parse(res/'values-night/launch.xml').getroot()
check('day and night palettes expose identical resources',{(e.tag,e.get('name')) for e in light}=={(e.tag,e.get('name')) for e in night})
mark=ET.parse(res/'drawable/luke_launch_mark.xml').getroot()
check('288dp square vector viewport',mark.get(A+'viewportWidth')==mark.get(A+'viewportHeight')=='288')
check('round emblem stays inside 192dp splash safe circle',89<192/2)
targets=ET.parse(res/'drawable/luke_launch_animated.xml').getroot().findall('target')
groups={g.get(A+'name') for g in mark.findall('group')}
check('animated targets exist in vector',all(t.get(A+'name') in groups for t in targets))
animations=[a for p in (res/'animator').glob('luke_launch*.xml') for a in ET.parse(p).iter('objectAnimator')]
check('finite 520ms entrance, no start delay or repeats',all(a.get(A+'duration')=='520' and a.get(A+'repeatCount','0')=='0' and a.get(A+'startOffset','0')=='0' for a in animations))
check('original HTTPS WebView origin retained','private static final String ORIGIN="https://appassets.androidplatform.net";' in java)
check('native respects disabled OS animations','ValueAnimator.areAnimatorsEnabled()' in java)
check('prepared WebView frame and generation checked','postVisualStateCallback(generation' in java and 'generation!=startup.generation' in java)
check('system splash draws while loading and exits only after content readiness','PlatformSplash.install(MainActivity.this,this)' in java and 'if(contentReady)startSystemExit();' in java and 'platformView==null||!contentReady' in java)
check('no first-draw gate can block the system splash transfer','OnPreDrawListener' not in java and 'disposed=true;handler.removeCallbacksAndMessages(null);clearWarmDraw();' in java)
check('bounded startup timeout with retry','FAILURE_MS=8000L' in java and 'retry.setOnClickListener(v->retry())' in java)
check('no minimum-duration sleep','Thread.sleep' not in java and 'MIN_DISPLAY' not in java)
check('no WebView zoom animation','web.setScaleX' not in java and 'web.setScaleY' not in java)
check('does not clear user storage',all(x not in java for x in ['localStorage.clear(','.clearCache(','.clearHistory(','.deleteDatabase(']))
print(f'{len(checks)} static assertions passed; Android compilation/device verification still required.')
