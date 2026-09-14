"""Actual pointer checks on the signed APK's bundle; no model/network service used."""
import re

def verify_settings_calendar(page, check, out):
    page.emulate_media(reduced_motion='reduce')
    page.set_viewport_size({'width':390,'height':844})
    page.get_by_role('tab',name='回到身边',exact=True).click()
    header=page.locator('main > .page-bar')
    check('home has one settings action and no redundant birthday button',header.get_by_role('button').count()==1)
    def settings():
        page.get_by_role('button',name='打开设置',exact=True).first.click()
        page.get_by_label('搜索设置',exact=True).wait_for()
    def close():
        page.locator('.settings').get_by_role('button',name='关闭',exact=True).click()
        page.locator('.settings').wait_for(state='detached')
    def section(label):page.get_by_role('tab',name=label,exact=True).click()
    def back():page.get_by_role('button',name='返回设置目录',exact=True).click()
    settings()
    check('settings directory groups and nine destinations',page.locator('.settings-sections>section').count()==3 and page.locator('.settings-sections button').count()==9)
    page.screenshot(path=str(out/'settings-directory.png'))
    page.get_by_label('搜索设置',exact=True).fill('聊天模型')
    check('settings search narrows to intended destination',page.locator('.settings-sections button').count()==1)
    section('聊天模型')
    check('model fields do not contain weather controls',page.get_by_label('模型名称',exact=True).is_visible() and page.locator('.settings').get_by_text('纬度',exact=True).count()==0)
    check('model secret stays a password field',page.get_by_label('模型密钥',exact=True).get_attribute('type')=='password')
    back();section('天气与位置')
    check('weather fields do not contain model controls',page.get_by_label('纬度',exact=True).is_visible() and page.get_by_label('模型名称',exact=True).count()==0)
    page.get_by_label('纬度',exact=True).fill('91')
    check('weather rejects invalid latitude before saving',not page.get_by_label('纬度',exact=True).evaluate('(e)=>e.checkValidity()'))
    back();section('日常与数据')
    original=page.evaluate("JSON.parse(localStorage.getItem('luke-companion-v1')).since")
    page.get_by_role('button',name='相伴日期',exact=True).click()
    page.get_by_label('输入指定日期',exact=True).fill('2001-02-29')
    check('custom date refuses invalid leap day',not page.get_by_role('button',name='确定日期',exact=True).is_enabled())
    page.get_by_label('输入指定日期',exact=True).fill('2000-02-29')
    check('valid leap date can be selected',page.get_by_role('button',name='确定日期',exact=True).is_enabled())
    page.get_by_role('button',name='取消',exact=True).click();page.locator('.date-picker').wait_for(state='detached')
    check('cancelling custom date preserves source record',page.evaluate("JSON.parse(localStorage.getItem('luke-companion-v1')).since")==original)
    page.get_by_role('button',name='相伴日期',exact=True).click()
    check('back handles nested date first',page.evaluate('window.__lukeBack()') is True)
    page.locator('.date-picker').wait_for(state='detached')
    check('nested back retains settings subsection',page.get_by_role('button',name='相伴日期',exact=True).is_visible())
    page.evaluate('window.__lukeBack()');page.get_by_label('搜索设置',exact=True).wait_for()
    check('second back reaches settings directory',page.locator('.settings-directory').is_visible())
    close()
    page.get_by_role('tab',name='一起计划',exact=True).click()
    def jump(value):
        page.get_by_role('button',name='跳转到指定日期',exact=True).click()
        page.get_by_label('输入指定日期',exact=True).fill(value)
        page.get_by_role('button',name='确定日期',exact=True).click()
        page.locator('.date-picker').wait_for(state='detached')
    jump('2026-10-01')
    check('calendar jumps to selected month and date',page.locator('.luke-plan-calendar h2').inner_text()=='2026 年 10 月' and page.locator('.calendar-grid button.selected').get_attribute('aria-label').startswith('2026-10-01'))
    check('October shows seven rest dates and October 10 makeup workday',page.locator('.calendar-grid [data-kind=rest]').count()==7 and page.locator('.calendar-grid [data-kind=work]').count()==1 and '2026-10-10' in page.locator('.calendar-grid button').filter(has=page.locator('[data-kind=work]')).get_attribute('aria-label'))
    page.screenshot(path=str(out/'plan-holidays.png'))
    jump('2027-10-01')
    check('unpublished year has no invented official rest/work labels',page.locator('.holiday-mark').count()==0 and page.get_by_text('此年份未收录官方调休安排',exact=True).is_visible())
    jump('2026-09-20')
    check('September makeup Sunday is a workday', '调休上班' in page.locator('.calendar-grid button.selected').get_attribute('aria-label'))
    for w,h in [(320,568),(390,844),(844,390)]:
        page.set_viewport_size({'width':w,'height':h})
        page.get_by_role('button',name='跳转到指定日期',exact=True).click()
        d=page.locator('.date-picker');r=d.bounding_box()
        check(f'date picker inside viewport {w}x{h}',r and r['x']>=-1 and r['y']>=-1 and r['x']+r['width']<=w+1 and r['y']+r['height']<=h+1,r)
        d.get_by_role('button',name='关闭',exact=True).click();d.wait_for(state='detached')
        check(f'no modal left after real date close {w}x{h}',page.locator('[data-slot=dialog-overlay]').count()==0)
    page.set_viewport_size({'width':390,'height':844})
    colors=[]
    for season in ['春','夏','秋','冬']:
        settings();section('四季与昼夜')
        page.get_by_role('button',name=season,exact=True).click()
        page.get_by_role('button',name='正午',exact=True).click();close();page.wait_for_timeout(600)
        page.get_by_role('button',name='跳转到指定日期',exact=True).click()
        colors.append(page.locator('.date-picker').evaluate('(e)=>[getComputedStyle(e).color,getComputedStyle(e).backgroundColor]'))
        if season=='冬':page.screenshot(path=str(out/'theme-date-picker.png'))
        page.locator('.date-picker').get_by_role('button',name='关闭',exact=True).click();page.locator('.date-picker').wait_for(state='detached')
    check('four seasons change date text and surface colors',len(set(c[0] for c in colors))==4 and len(set(c[1] for c in colors))==4,colors)
    settings();section('四季与昼夜');page.get_by_role('button',name='深夜',exact=True).click();close();page.wait_for_timeout(600)
    page.get_by_role('button',name='跳转到指定日期',exact=True).click()
    night=page.locator('.date-picker').evaluate('(e)=>[getComputedStyle(e).color,getComputedStyle(e).backgroundColor]')
    check('night palette differs from day without native picker',night!=colors[-1],night)
    page.locator('.date-picker').get_by_role('button',name='取消',exact=True).click();page.locator('.date-picker').wait_for(state='detached')
    page.get_by_role('tab',name='悄悄话',exact=True).click();page.get_by_role('button',name='搜索聊天记录',exact=True).click()
    page.get_by_role('button',name='筛选消息日期',exact=True).click();page.get_by_label('输入指定日期',exact=True).fill('2026-10-01')
    page.get_by_role('button',name='确定日期',exact=True).click();page.locator('.date-picker').wait_for(state='detached')
    check('chat date filter uses themed custom control',page.get_by_role('button',name='筛选消息日期',exact=True).inner_text().startswith('2026 / 10 / 01'))
    page.get_by_role('button',name='清除筛选',exact=True).click()
    check('chat filter clear removes selected date',page.get_by_role('button',name='筛选消息日期',exact=True).inner_text().startswith('选择日期'))
    page.get_by_role('button',name='返回聊天',exact=True).click()
    page.get_by_role('tab',name='回到身边',exact=True).click()

if __name__=='__main__':
    from pathlib import Path
    import argparse,json,functools,http.server,threading,os,traceback
    from playwright.sync_api import sync_playwright
    parser=argparse.ArgumentParser();parser.add_argument('--root',required=True);parser.add_argument('--out',required=True);args=parser.parse_args()
    root=Path(args.root).resolve();out=Path(args.out).resolve();out.mkdir(parents=True,exist_ok=True)
    class Quiet(http.server.SimpleHTTPRequestHandler):
        def log_message(self,*a):pass
    server=http.server.ThreadingHTTPServer(('127.0.0.1',0),functools.partial(Quiet,directory=str(root)))
    threading.Thread(target=server.serve_forever,daemon=True).start();results=[];errors=[]
    def check(name,condition,detail=None):
        results.append({'name':name,'passed':bool(condition),'detail':detail})
        print(('PASS ' if condition else 'FAIL ')+name,flush=True)
        if not condition:raise AssertionError(name)
    with sync_playwright() as p:
        browser=p.chromium.launch(executable_path=os.getenv('CHROMIUM_PATH','/usr/bin/chromium'),args=['--no-sandbox'])
        page=browser.new_page(viewport={'width':390,'height':844},reduced_motion='reduce');page.set_default_timeout(10000)
        page.on('pageerror',lambda e:errors.append(str(e)));page.route('https://**/*',lambda r:r.abort());page.route('**/api/**',lambda r:r.abort())
        try:
            page.goto(f'http://127.0.0.1:{server.server_port}/');page.get_by_role('button',name='打开设置',exact=True).first.wait_for();page.wait_for_timeout(500)
            verify_settings_calendar(page,check,out);check('no unhandled exceptions during new UI checks',not errors,errors)
        except Exception:
            page.screenshot(path=str(out/'failure.png'));traceback.print_exc();raise
        finally:
            (out/'results.json').write_text(json.dumps({'tests':results,'pageErrors':errors,'network':'HTTPS and API requests blocked; no model requests'},ensure_ascii=False,indent=2))
            browser.close();server.shutdown()
