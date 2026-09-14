"""Build/install a separate read-only test observer, never included in the app APK.
Reuses one UiAutomation instead of repeatedly starting/destroying `uiautomator dump`.
Uses only the SDK already provisioned in the disposable CI emulator job.
"""
import json,os,pathlib,socket,struct,subprocess,time

class AndroidObserver:
    def __init__(self, adb, output):
        self.adb=adb;self.output=output;self.port=37621;self.process=None;self.log=None
        self.calls=0;self.root_waits=0
    def start(self):
        # Establish adbd's test-only UID before opening the long-lived instrumentation
        # stream. Restarting adbd during renderer injection can disconnect that stream
        # and invalidates UiAutomation's active-window connection.
        if self.adb('shell','getprop','ro.kernel.qemu').strip()!='1':
            raise RuntimeError('Observer requires a disposable emulator')
        elevation=self.adb('root');self.adb('wait-for-device')
        uid=self.adb('shell','id','-u').strip()
        (self.output/'observer-preflight.txt').write_text(elevation+'\nuid='+uid+'\nRoot configured before opening UiAutomation; no physical device access.\n')
        if uid!='0':raise RuntimeError('Recovery validation requires a rootable emulator image')
        home=pathlib.Path(os.environ['ANDROID_HOME'])
        tools=home/'build-tools'/'35.0.0'
        if not tools.exists():tools=sorted((home/'build-tools').iterdir())[-1]
        android=home/'platforms'/'android-35'/'android.jar'
        source=pathlib.Path(__file__).parent/'android-observer'
        stage=pathlib.Path('work/android-observer-build');stage.mkdir(parents=True,exist_ok=True)
        classes=stage/'classes';classes.mkdir(exist_ok=True)
        dex=stage/'dex';dex.mkdir(exist_ok=True)
        def run(*args):subprocess.run([str(a) for a in args],check=True,timeout=90)
        run('javac','-encoding','UTF-8','--release','8','-classpath',android,'-d',classes,source/'Observer.java')
        run(tools/'d8','--lib',android,'--min-api','26','--output',dex,*classes.rglob('*.class'))
        raw=stage/'unsigned.apk';aligned=stage/'aligned.apk';signed=stage/'observer.apk'
        run(tools/'aapt','package','-f','-M',source/'AndroidManifest.xml','-I',android,'-F',raw)
        subprocess.run([str(tools/'aapt'),'add',str(raw.resolve()),'classes.dex'],cwd=dex,check=True,timeout=30)
        run(tools/'zipalign','-f','4',raw,aligned)
        key=stage/'observer.p12'
        if not key.exists():run('keytool','-genkeypair','-noprompt','-alias','observer','-keystore',key,'-storetype','PKCS12','-storepass','test-only','-keypass','test-only','-keyalg','RSA','-keysize','2048','-validity','1','-dname','CN=Disposable Motion Observer')
        run(tools/'apksigner','sign','--ks',key,'--ks-pass','pass:test-only','--out',signed,aligned)
        self.adb('install','-r',str(signed),timeout=60)
        self.adb('forward','tcp:'+str(self.port),'tcp:'+str(self.port))
        self.log=(self.output/'observer.log').open('w')
        self.process=subprocess.Popen(['adb','shell','am','instrument','-w','-r','-e','port',str(self.port),'com.luke.motion.observer/.Observer'],stdout=self.log,stderr=subprocess.STDOUT)
        end=time.monotonic()+20
        while time.monotonic()<end:
            try:
                if self.request('ping').get('ready'):return
            except (OSError,ConnectionError):pass
            if self.process.poll() is not None:raise RuntimeError('UI observer terminated; see observer.log')
            time.sleep(.2)
        raise RuntimeError('UI observer startup timed out')
    def request(self,command):
        with socket.create_connection(('127.0.0.1',self.port),timeout=15) as client:
            client.sendall(command.encode()+b'\n')
            stream=client.makefile('rb')
            header=stream.read(4)
            if len(header)!=4:raise ConnectionError('Truncated observer header')
            size=struct.unpack('>I',header)[0]
            if not 0<size<6000000:raise ValueError('Invalid observer response size')
            payload=stream.read(size)
            if len(payload)!=size:raise ConnectionError('Truncated observer body')
            return json.loads(payload)
    def dump(self):
        self.calls+=1
        for attempt in range(10):
            value=self.request('dump')
            if 'xml' in value:return value['xml']
            # Empty root is transient while a window changes. Never catch crashes,
            # dead connections, tree errors, or silently restart the observer/app.
            if value.get('error')!='java.io.IOException: No active accessibility root':raise RuntimeError(value)
            self.root_waits+=1;time.sleep(.2)
        raise RuntimeError('No active accessibility root after bounded wait')
    def close(self):
        (self.output/'observer.json').write_text(json.dumps({'type':'persistent-read-only-UiAutomation','dumpCalls':self.calls,'emptyRootWaits':self.root_waits,'appModifiedForTesting':False},indent=2))
        if self.process is not None:
            try:self.request('stop');self.process.wait(timeout=5)
            except Exception:self.process.terminate()
        if self.log is not None:self.log.close()
        subprocess.run(['adb','forward','--remove','tcp:'+str(self.port)],check=False,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
