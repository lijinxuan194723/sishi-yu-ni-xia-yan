"""Additional short-window navigation and scroll-independent dismissal checks."""
def verify_layout_polish(page, check):
    page.emulate_media(reduced_motion='no-preference')
    for width, height in [(844, 390), (640, 360)]:
        page.set_viewport_size({'width': width, 'height': height})
        for label in ['他的此刻', '时光手记', '计时', '回到身边']:
            tab = page.get_by_role('tab', name=label, exact=True)
            tab.click()
            rect = tab.bounding_box()
            check(f'landscape navigation reachable {label} {width}x{height}', rect is not None and
                  rect['y'] >= -1 and rect['y'] + rect['height'] <= height + 1, rect)
        page.get_by_role('button', name='打开设置', exact=True).first.click()
        page.get_by_role('tab', name='生日与纪念日', exact=True).click()
        page.get_by_role('button', name='看看生日祝福', exact=True).click()
        dialog = page.locator('.birthday-refined');dialog.wait_for()
        page.wait_for_timeout(350)
        close = dialog.get_by_role('button', name='关闭', exact=True)
        before = close.bounding_box()
        page.evaluate("""()=>{document.querySelector('.birthday-card h2').style.fontSize='44px';document.querySelector('.birthday-card [data-slot=dialog-description]').style.fontSize='26px';document.querySelector('.birthday-scroll').scrollTop=100000;}""")
        after = close.bounding_box()
        check(f'close stays pinned during birthday scroll {width}x{height}', before is not None and
              after is not None and abs(before['y'] - after['y']) < .5 and after['y'] >= 0 and
              after['y'] + after['height'] <= height, after)
        check(f'birthday frame does not scroll {width}x{height}', page.evaluate("document.querySelector('.birthday-refined').scrollTop===0"))
        handled = page.evaluate('window.__lukeBack()')
        dialog.wait_for(state='detached');page.locator('.settings').wait_for()
        check(f'back returns from birthday to settings {width}x{height}', handled and
              page.get_by_role('button', name='看看生日祝福', exact=True).is_visible())
        page.locator('.settings').get_by_role('button', name='关闭', exact=True).click()
        page.locator('.settings').wait_for(state='detached')
        check(f'no modal lock after birthday back {width}x{height}', page.locator('[role=dialog],[data-slot=dialog-overlay]').count() == 0)
    page.set_viewport_size({'width': 390, 'height': 844})
