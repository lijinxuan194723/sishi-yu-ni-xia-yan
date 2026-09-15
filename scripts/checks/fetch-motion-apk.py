"""Fetch only the successful Preview APK produced from this exact commit and branch."""
import json,os,pathlib,subprocess,time,zipfile
repo=os.environ['GITHUB_REPOSITORY'];sha=os.environ['GITHUB_SHA'];branch=os.environ['GITHUB_REF_NAME']
def api(path):return json.loads(subprocess.check_output(['gh','api',path],text=True))
for attempt in range(90):
 runs=api(f'repos/{repo}/actions/workflows/preview-apk.yml/runs?per_page=30')['workflow_runs']
 run=next((r for r in runs if r['head_sha']==sha and r['head_branch']==branch),None)
 if run and run['status']=='completed':
  if run['conclusion']!='success':raise SystemExit('Matching APK build did not succeed')
  break
 time.sleep(5)
else:raise SystemExit('Timed out waiting for the exact-commit APK')
path=pathlib.Path('work/motion-candidate');path.mkdir(parents=True,exist_ok=True)
subprocess.run(['gh','run','download',str(run['id']),'--repo',repo,'--name','Four-Seasons-Luke-Memo-Preview','--dir',str(path)],check=True)
apk=next(path.glob('*.apk'))
with zipfile.ZipFile(apk) as z:
 for name in z.namelist():
  if name.startswith('assets/web/') and not name.endswith('/'):
   rel=pathlib.PurePosixPath(name.removeprefix('assets/web/'))
   if '..' in rel.parts:raise SystemExit('Unsafe archive path')
   dest=pathlib.Path('work/mobile-web')/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(z.read(name))
(path/'build.json').write_text(json.dumps({'commit':sha,'branch':branch,'runId':run['id'],'runUrl':run['html_url']},indent=2))
# Compare the last delivered preview. This is a disposable emulator fixture.
if branch=='fix/rounded-native-startup':
 previous=pathlib.Path('work/motion-previous');previous.mkdir(parents=True,exist_ok=True)
 subprocess.run(['gh','run','download','34876394478','--repo',repo,'--name','Four-Seasons-Luke-Memo-Preview','--dir',str(previous)],check=True)
