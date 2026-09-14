"""Pure regression fixtures for Android test observation; no device access."""
import unittest
import xml.etree.ElementTree as ET
from motion_observation import choose_control, bound_renderer

PKG = 'com.luke.summer.preview'
NAME = 'com.google.android.webview:sandboxed_process0:org.chromium.content.app.SandboxedProcessService0:0'
PS = f'PID NAME\n100 {PKG}\n200 {NAME}\n300 other:sandboxed_process0\n400 other'

def service(pid=200, name=NAME, host=100, package=PKG):
    return f'  * ServiceRecord{{abc u0 {name}}}\n    app=ProcessRecord{{xyz {pid}:{name}/u0i12}}\n    Bindings:\n      * Client AppBindRecord{{def ProcessRecord{{xyz {host}:{package}/u0a123}}}}\n'

class ObservationTests(unittest.TestCase):
    def test_other_app_renderer_is_not_selected(self):
        self.assertEqual(bound_renderer(service()+service(300,'other:sandboxed_process0',400,'other'),PS,'100',PKG),'200')
    def test_missing_binding_fails_closed(self):
        self.assertIsNone(bound_renderer(service().replace('Client AppBindRecord','Unknown'),PS,'100',PKG))
    def test_stale_pid_is_refused(self):
        self.assertIsNone(bound_renderer(service(),PS.replace('200 ', '201 '),'100',PKG))
    def test_host_is_not_guessed_by_prefix(self):
        self.assertIsNone(bound_renderer(service(host=1000),PS,'100',PKG))
    def test_shared_renderer_is_refused(self):
        text=service()+'      * Client AppBindRecord{abc ProcessRecord{xyz 400:other/u0a144}}\n'
        self.assertIsNone(bound_renderer(text,PS,'100',PKG))
    def test_ambiguous_renderers_are_refused(self):
        ps=PS+f'\n500 {NAME}'
        self.assertIsNone(bound_renderer(service()+service(pid=500),ps,'100',PKG))
    def test_same_bound_process_repeated_is_unambiguous(self):
        self.assertEqual(bound_renderer(service()+service(),PS,'100',PKG),'200')
    def test_host_name_must_match(self):
        self.assertIsNone(bound_renderer(service(),PS.replace(PKG,'different'),'100',PKG))
    def test_selection_ignores_noninteractive_same_label(self):
        root=ET.fromstring('<hierarchy width="720" height="1280"><node text="计时" clickable="true" enabled="true" bounds="[592,1062][708,1170]"/><node text="计时" clickable="false" enabled="true" bounds="[32,208][688,1184]"/></hierarchy>')
        self.assertEqual(choose_control(root,'计时').get('bounds'),'[592,1062][708,1170]')
    def test_offscreen_disabled_and_empty_nodes_are_refused(self):
        for box,enabled in [('[0,0][0,0]','true'),('[750,1][900,90]','true'),('[0,0][90,90]','false'),('[-100,0][-30,70]','true')]:
            root=ET.fromstring(f'<hierarchy width="720" height="1280"><node text="关闭" clickable="true" enabled="{enabled}" bounds="{box}"/></hierarchy>')
            self.assertIsNone(choose_control(root,'关闭'))
    def test_duplicate_interactive_labels_fail(self):
        root=ET.fromstring('<hierarchy width="720" height="1280"><node text="关闭" clickable="true" enabled="true" bounds="[0,0][90,90]"/><node text="关闭" clickable="true" enabled="true" bounds="[100,0][190,90]"/></hierarchy>')
        with self.assertRaises(AssertionError):choose_control(root,'关闭')

if __name__ == '__main__':unittest.main()
