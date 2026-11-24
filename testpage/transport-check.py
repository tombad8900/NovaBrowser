"""Tap actual control bounds; verify independent player-state telemetry, plus pixels."""
import subprocess,time,re,xml.etree.ElementTree as E,json
pkg='app.nova.browser.trial'
def adb(*a):return subprocess.check_output(['adb',*a],timeout=30)
def dump():
 adb('shell','rm','-f','/sdcard/transport.xml');adb('shell','uiautomator','dump','/sdcard/transport.xml')
 return E.fromstring(adb('shell','cat','/sdcard/transport.xml'))
def tap(label):
 for n in dump().iter():
  if n.attrib.get('text')==label or n.attrib.get('content-desc')==label:
   b=list(map(int,re.findall(r'\d+',n.attrib['bounds'])));adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));time.sleep(.4);return
 raise RuntimeError('Missing '+label)
def shot(n):open('shots/'+n+'.png','wb').write(adb('exec-out','screencap','-p'))
def state():
 lines=adb('logcat','-d','-s','NovaTouchTest:I','-v','raw').decode().splitlines()
 for line in reversed(lines):
  if line.startswith('{'):return json.loads(line)
 raise RuntimeError('No debug touch state')
def control(key):
 b=state()[key];assert b[2]>b[0] and b[3]>b[1],b
 adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));time.sleep(.7)
results=[]
for locale in ['en','ar']:
 if locale=='ar':
  adb('shell','am','start','-a','android.intent.action.VIEW','-d','http://10.0.2.2:8000/plain.html',pkg);time.sleep(4)
  tap('Menu');tap('Settings');tap('Language');tap('العربية');time.sleep(3)
  tap('القائمة');tap('الإعدادات');tap('البحث');tap('Google');shot('25-arabic-settings');tap('التشغيل');tap('خلفية داكنة');shot('26-arabic-subtitle-settings');adb('shell','input','keyevent','KEYCODE_BACK')
  adb('shell','am','force-stop',pkg);adb('shell','am','start','-a','android.intent.action.VIEW','-d','http://10.0.2.2:8000/plain.html',pkg);time.sleep(4)
  tap('القائمة');tap('الإعدادات');assert any(n.attrib.get('text')=='Google' for n in dump().iter()),'Search choice did not persist'
  tap('التشغيل');assert any(n.attrib.get('text')=='متوقفة' for n in dump().iter()),'Subtitle background did not persist'
  shot('26b-persisted-arabic-subtitle-settings');tap('استعادة مظهر الترجمة الافتراضي');adb('shell','input','keyevent','KEYCODE_BACK')
 adb('shell','am','force-stop',pkg);adb('logcat','-c')
 adb('shell','am','start','-a','android.intent.action.VIEW','-d','http://10.0.2.2:8000/switch.html','--ez','testHoldControls','true',pkg);time.sleep(6)
 tap('تشغيل في نوفا' if locale=='ar' else 'Play in Nova');time.sleep(4)
 for orientation in ['portrait','landscape']:
  if orientation=='landscape':
   # Android input rotation, no player view lookup or stale UIAutomator file.
   adb('shell','settings','put','system','accelerometer_rotation','0');adb('shell','settings','put','system','user_rotation','1');time.sleep(2)
  if state()['playing']:control('play')
  initial=state()['position'];control('play');time.sleep(2);control('play');assert state()['position']>initial+1000
  before=state()['position'];time.sleep(1);assert abs(state()['position']-before)<300
  control('forward');forward=state()['position'];assert abs(forward-before-10000)<500,(before,forward)
  control('back');back=state()['position'];assert abs(back-before)<500,(before,back)
  s=state();assert s['locale']==locale,s
  assert s['back'][2]<=s['play'][0] and s['play'][2]<=s['forward'][0]
  shot('24-transport-'+locale+'-'+orientation);results.append(s);open('shots/transport-results.json','w').write(json.dumps(results,indent=2))
 adb('shell','input','keyevent','KEYCODE_BACK');adb('shell','settings','put','system','user_rotation','0')
