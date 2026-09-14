"""Geometry checks for reduced motion: centering is layout, not an animation."""
def verify_reduced_dialogs(page, check):
    # The preceding test ends on Notes, which deliberately has no settings entry.
    # Use the real navigation and pointer path, not a hidden button or forced click.
    page.get_by_role('tab', name='回到身边', exact=True).click()
    for width, height in [(320, 568), (390, 844), (844, 390)]:
        page.set_viewport_size({'width': width, 'height': height})
        page.get_by_role('button', name='打开设置', exact=True).first.click()
        dialog = page.locator('.settings')
        dialog.wait_for()
        rect = dialog.bounding_box()
        check(f'reduced motion preserves dialog placement {width}x{height}',
              rect is not None and rect['x'] >= -1 and rect['y'] >= -1 and
              rect['x'] + rect['width'] <= width + 1 and rect['y'] + rect['height'] <= height + 1, rect)
        close = dialog.get_by_role('button', name='关闭', exact=True)
        box = close.bounding_box()
        check(f'reduced motion close remains reachable {width}x{height}',
              box is not None and box['width'] >= 44 and box['height'] >= 44 and
              box['x'] >= 0 and box['y'] >= 0 and box['x'] + box['width'] <= width + 1 and
              box['y'] + box['height'] <= height + 1, box)
        # Real pointer click, not DOM click(): clipping/overlays must fail this test.
        close.click()
        dialog.wait_for(state='detached')
        check(f'no stranded modal after reduced close {width}x{height}',
              page.locator('[data-slot="dialog-overlay"]').count() == 0)
    page.set_viewport_size({'width': 390, 'height': 844})
    page.get_by_role('tab', name='他的此刻', exact=True).click()
    page.get_by_role('button', name='开始情景对话', exact=True).click()
    topic = page.locator('.topic-chat')
    topic.wait_for()
    rect = topic.bounding_box()
    check('reduced topic dialog preserves centering', rect is not None and rect['x'] >= 0 and
          rect['y'] >= 0 and rect['x'] + rect['width'] <= 391 and rect['y'] + rect['height'] <= 845, rect)
    topic.get_by_role('button', name='关闭', exact=True).click()
    topic.wait_for(state='detached')
