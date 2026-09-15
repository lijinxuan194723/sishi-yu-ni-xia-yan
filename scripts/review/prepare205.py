"""Idempotent harness corrections. Do not alter app CSP, test predicates or data."""
from pathlib import Path

def replace_once(path, old, new):
    p=Path(path);text=p.read_text()
    if new in text and old not in text:return
    if text.count(old)!=1:raise RuntimeError(f'Unexpected source drift in {path}; not overwriting')
    p.write_text(text.replace(old,new))

replace_once('scripts/checks/browser205.py',
    "page.get_by_role('button',name='打开设置',exact=True).click()",
    "page.locator('main > header.page-bar').get_by_role('button',name='打开设置',exact=True).click()")
replace_once('lib/release-info.ts','/releases/tag/v2.0.5','/actions/workflows/review-isolated205.yml')
replace_once('lib/release-info.ts','包名保持不变，支持同签名应用更新。','安装前先导出完整备份；不同包名的应用不会自动共享数据。')

# Poll unchanged predicates from the test runner. The APK keeps its original
# restrictive CSP; neither unsafe-eval nor bypass_csp is enabled.
p=Path('scripts/checks/browser205.py');text=p.read_text()
helper='''def wait_expression(page, expression, timeout=30000, arg=None):
 until=time.monotonic()+timeout/1000
 predicate=expression if arg is not None else "()=> ("+expression+")"
 while time.monotonic()<until:
  if page.evaluate(predicate,arg):return
  page.wait_for_timeout(100)
 raise TimeoutError("Predicate timed out: "+expression)
'''
if 'def wait_expression(' not in text:
    anchor='def harness(page):';assert text.count(anchor)==1
    assert text.count('.wait_for_function(')>=5
    text=text.replace(anchor,helper+anchor).replace('page.wait_for_function(', 'wait_expression(page,').replace('ap.wait_for_function(', 'wait_expression(ap,')
elif helper not in text:
    start=text.index('def wait_expression(');end=text.index('def harness(page):',start)
    old=text[start:end]
    assert 'def wait_expression(page, expression, timeout=30000):' in old and len(old)<500
    text=text[:start]+helper+text[end:]
compile(text,str(p),'exec');p.write_text(text)
# Validate both call forms before starting the browser job.
import time
namespace={'time':time};exec(helper,namespace)
class Probe:
    def __init__(self):self.calls=[]
    def evaluate(self,predicate,arg):self.calls.append((predicate,arg));return True
probe=Probe();namespace['wait_expression'](probe,'true')
namespace['wait_expression'](probe,'([s])=>s===1',arg=[1])
assert probe.calls==[('()=> (true)',None),('([s])=>s===1',[1])]
