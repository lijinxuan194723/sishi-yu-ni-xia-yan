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

# Playwright's in-page string polling invokes eval and conflicts with the app's
# existing CSP. Poll the same read-only predicates via the existing CDP evaluate
# facility from the runner, without enabling unsafe-eval or bypass_csp.
p=Path('scripts/checks/browser205.py');text=p.read_text()
if 'def wait_expression(' not in text:
    anchor='def harness(page):'
    assert text.count(anchor)==1
    helper='''def wait_expression(page, expression, timeout=30000):
 until=time.monotonic()+timeout/1000
 while time.monotonic()<until:
  if page.evaluate("()=> ("+expression+")"):return
  page.wait_for_timeout(100)
 raise TimeoutError("Predicate timed out: "+expression)
'''
    assert text.count('.wait_for_function(')>=5
    text=text.replace(anchor,helper+anchor).replace('page.wait_for_function(', 'wait_expression(page,').replace('ap.wait_for_function(', 'wait_expression(ap,')
    p.write_text(text)
