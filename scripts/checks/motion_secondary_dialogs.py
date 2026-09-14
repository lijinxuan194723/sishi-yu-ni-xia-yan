"""Exercise secondary modal lifecycles with actual compiled app components."""
import json

TRACE = '''({selector,action})=>new Promise(resolve=>{
 const rows=[],start=performance.now();(new Function(action))();
 function sample(){const n=document.querySelector(selector),s=n&&getComputedStyle(n);
 rows.push({t:performance.now()-start,exists:!!n,opacity:s?Number(s.opacity):null});
 if(performance.now()-start<850)requestAnimationFrame(sample);else resolve(rows);}
 requestAnimationFrame(sample);
})'''


def verify_secondary_dialogs(page, check):
    page.emulate_media(reduced_motion='no-preference')
    page.set_viewport_size({'width': 390, 'height': 844})

    def sample(name, selector, action, exiting=False):
        rows = page.evaluate(TRACE, {'selector': selector, 'action': action})
        mid = {round(r['opacity'], 3) for r in rows if r['exists'] and .01 < r['opacity'] < .99}
        alive = [r for r in rows if r['exists']]
        check(name + ' includes intermediate visual states', len(mid) >= 5 and len(alive) >= 8,
              {'intermediate': len(mid), 'frames': rows})
        check(name + ' finishes without a stranded modal',
              (not rows[-1]['exists'] and alive[-1]['opacity'] < .12) if exiting else
              (rows[-1]['exists'] and rows[-1]['opacity'] >= .99))

    def click_text(text, scope='document'):
        return f"[...{scope}.querySelectorAll('button')].find(b=>b.textContent.trim()==={json.dumps(text)}).click()"

    page.get_by_role('tab', name='他的此刻', exact=True).click()
    page.get_by_role('button', name='添加书籍', exact=True).scroll_into_view_if_needed()
    sample('book editor entry', '.book-dialog', click_text('添加书籍'))
    page.locator('.book-dialog').get_by_label('书名', exact=True).fill('Motion book')
    sample('book editor cancel', '.book-dialog', click_text('取消', "document.querySelector('.book-dialog')"), True)
    check('cancelled book is not persisted', page.evaluate("!JSON.parse(localStorage.getItem('luke-companion-v1')||'{}').books?.some(b=>b.title==='Motion book')"))
    for name, index in [('favorites', 0), ('recommendation history', 1)]:
        page.locator('.music-moment .recommendation-actions').scroll_into_view_if_needed()
        # An unconfigured offline test has only the folder and history buttons.
        action = f"document.querySelector('.music-moment .recommendation-actions').querySelectorAll('button')[{index}].click()"
        sample(name + ' entry', '.recommendation-folder', action)
        close = page.locator('.recommendation-folder').get_by_role('button', name='关闭', exact=True)
        box = close.bounding_box()
        check(name + ' close is visible', box is not None and box['x'] >= 0 and box['y'] >= 0 and
              box['x'] + box['width'] <= 391 and box['y'] + box['height'] <= 845, box)
        sample(name + ' exit', '.recommendation-folder', "document.querySelector('.recommendation-folder [data-slot=dialog-close]').click()", True)
    page.get_by_role('tab', name='回到身边', exact=True).click()
    page.get_by_role('button', name='打开设置', exact=True).first.click()
    page.locator('.settings').wait_for()
    page.get_by_role('tab', name='生日与纪念日', exact=True).click()
    page.get_by_role('button', name='看看生日祝福', exact=True).scroll_into_view_if_needed()
    sample('birthday entry', '.birthday-screen', click_text('看看生日祝福'))
    sample('birthday exit', '.birthday-screen', click_text('收下这份祝福'), True)
    page.locator('.settings').wait_for()
    page.locator('.settings').get_by_role('button', name='关闭', exact=True).click()
    page.locator('.settings').wait_for(state='detached')
    check('birthday returns cleanly to settings then home', page.locator('[data-slot="dialog-overlay"]').count() == 0)
    page.emulate_media(reduced_motion='reduce')
