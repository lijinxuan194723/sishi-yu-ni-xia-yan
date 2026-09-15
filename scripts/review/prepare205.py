"""Small, idempotent source corrections; never change test assertions or app data."""
from pathlib import Path

def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text()
    if new in text and old not in text:
        return
    if text.count(old) != 1:
        raise RuntimeError(f'Unexpected source drift in {path}; not overwriting')
    p.write_text(text.replace(old, new))

# The outgoing chat panel exists during its exit transition. Target the current
# top-level header, not the departing panel's identically labelled settings button.
replace_once('scripts/checks/browser205.py',
    "page.get_by_role('button',name='打开设置',exact=True).click()",
    "page.locator('main > header.page-bar').get_by_role('button',name='打开设置',exact=True).click()")
replace_once('lib/release-info.ts', '/releases/tag/v2.0.5', '/actions/workflows/review-isolated205.yml')
replace_once('lib/release-info.ts',
    '包名保持不变，支持同签名应用更新。',
    '安装前先导出完整备份；不同包名的应用不会自动共享数据。')
