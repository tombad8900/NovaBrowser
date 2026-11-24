"""Manual PiP on an actual local video, leave app stopped afterward."""
import subprocess,time,re,xml.etree.ElementTree as E
def adb(*a):return subprocess.check_output(['adb',*a],timeout=30)
def dump():
 adb('shell','uiautomator','dump','/sdcard/pip.xml');raw=adb('shell','cat','/sdcard/pip.xml');open('shots/pip-ui.xml','wb').write(raw);open('shots/pip-ui.png','wb').write(adb('exec-out','screencap','-p'));return E.fromstring(raw)
def tap(label):
 for n in dump().iter():
  if n.attrib.get('text')==label or n.attrib.get('content-desc')==label:
   b=list(map(int,re.findall(r'\d+',n.attrib['bounds'])));adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));return
 raise RuntimeError('Missing PiP control '+label)
pkg='app.nova.browser.trial'
adb('shell','am','force-stop',pkg)
adb('shell','am','start','-a','android.intent.action.VIEW','-d','http://10.0.2.2:8000/switch.html',pkg);time.sleep(8)
tap('Play in Nova');time.sleep(3)
if not any(n.attrib.get('content-desc')=='More' for n in dump().iter()):adb('shell','input','tap','160','280')
tap('More');tap('Picture-in-picture');time.sleep(3)
activity=adb('shell','dumpsys','activity','activities').decode();open('shots/pip-state.txt','w').write(activity)
open('shots/31-picture-in-picture.png','wb').write(adb('exec-out','screencap','-p'))
if not ('mode=pinned' in activity or 'windowingMode=2' in activity):raise RuntimeError('No pinned PiP activity')
adb('shell','am','force-stop',pkg)
