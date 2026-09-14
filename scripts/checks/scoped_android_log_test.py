import unittest
from scoped_android_log import after_marker
class ScopedLogTests(unittest.TestCase):
 def test_old_completion_does_not_impersonate_new_transition(self):
  self.assertEqual(after_marker('I LukeReturn: finished\nI LukeCase: marker-1\nI LukeReturn: show 3\n','marker-1'),'I LukeReturn: show 3\n')
 def test_new_crash_is_preserved(self):
  self.assertIn('FATAL EXCEPTION',after_marker('I LukeCase: marker-1\nE AndroidRuntime: FATAL EXCEPTION\n','marker-1'))
 def test_missing_boundary_fails(self):
  with self.assertRaises(AssertionError):after_marker('I LukeReturn: finished','marker-1')
 def test_duplicate_boundary_fails(self):
  with self.assertRaises(AssertionError):after_marker('I LukeCase: marker-1\nI LukeCase: marker-1\n','marker-1')
 def test_empty_observed_interval_is_empty(self):
  self.assertEqual(after_marker('I LukeCase: marker-1\n','marker-1'),'')
if __name__=='__main__':unittest.main()
