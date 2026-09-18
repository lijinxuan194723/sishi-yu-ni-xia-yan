import sys,json
from pathlib import Path
from playwright.sync_api import sync_playwright
import runtime210 as r
OUT=r.ROOT/'work/detail-hotfix2';OUT.mkdir(exist_ok=True,parents=True)
with sync_playwright() as pw:
 b=pw.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);ctx=b.new_context(viewport={'width':393,'height':820},is_mobile=True,has_touch=True,reduced_motion='no-preference');err=[];p=r.boot(ctx,errors=err)
 d=r.settings(p,'外观与字号');d.locator('[data-slot=dialog-close]').click();p.wait_for_timeout(220);r.nav(p,'悄悄话');p.get_by_role('button',name='对话与技能',exact=True).click();p.wait_for_timeout(200)
 p.evaluate('()=>window.__lukeBack()');p.wait_for_timeout(700);print('Back once closes current hub',p.get_by_role('dialog').count()==0,flush=True)
 p.close();p=r.boot(ctx,errors=err)
 r.nav(p,'计时');p.get_by_role('tab',name='提醒',exact=True).click();p.wait_for_timeout(200)
 slider=p.get_by_role('slider',name='拖动刻度选择倒计时');slider.scroll_into_view_if_needed();bb=slider.bounding_box();sx=bb['x']+bb['width']/2;sy=bb['y']+bb['height']/2
 # Actual pointer movement, then a simulated window blur while still down.
 p.mouse.move(sx,sy);p.mouse.down();p.mouse.move(sx-88,sy,steps=9);p.wait_for_timeout(50);p.evaluate('()=>dispatchEvent(new Event("blur"))');p.mouse.up();p.wait_for_timeout(200)
 print('Blur during ruler drag starts timer (should be false):',p.evaluate("()=>!!JSON.parse(localStorage.getItem('luke-companion-v1')).countdown?.endsAt"),flush=True)
 p.screenshot(path=str(OUT/'baseline-ruler-blur.png'));print('errors',err)
 b.close()
