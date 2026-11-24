"""Only current-document captures, including refresh and same-document URL changes."""
import subprocess,time,xml.etree.ElementTree as E,re
pkg='app.nova.browser.trial'
def adb(*a):return subprocess.check_output(['adb',*a],timeout=30)
def root():
 adb('shell','uiautomator','dump','/sdcard/lifetime.xml');return E.fromstring(adb('shell','cat','/sdcard/lifetime.xml'))
def tap(label):
 for n in root().iter():
  if n.attrib.get('text')==label or n.attrib.get('content-desc')==label:
   b=list(map(int,re.findall(r'\d+',n.attrib['bounds'])));adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));return
 raise RuntimeError('Missing '+label)
def shot(n):open('shots/'+n+'.png','wb').write(adb('exec-out','screencap','-p'))
adb('shell','am','force-stop',pkg)
adb('shell','am','start','-a','android.intent.action.VIEW','-d','http://10.0.2.2:8000/lifetime.html',pkg);time.sleep(6)
tap('Expose source');time.sleep(2);tap('Play in Nova');time.sleep(2);shot('27-lifetime-before');adb('shell','input','keyevent','KEYCODE_BACK')
tap('Reload');time.sleep(4);tap('Play in Nova');time.sleep(.4)
assert not any(n.attrib.get('resource-id','').endswith('/exo_content_frame') for n in root().iter()),'Old source survived refresh'
shot('28-lifetime-refresh-cleared')
tap('Expose source');time.sleep(2);tap('Next document');time.sleep(2);tap('Play in Nova');time.sleep(.4)
assert not any(n.attrib.get('resource-id','').endswith('/exo_content_frame') for n in root().iter()),'Old source survived same-document navigation'
shot('29-lifetime-location-cleared')
