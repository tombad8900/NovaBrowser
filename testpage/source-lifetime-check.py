"""Only current-document captures, including refresh and same-document URL changes."""
import subprocess,time,xml.etree.ElementTree as E,re
import runpy
ui=runpy.run_path('testpage/ui-check.py')
pkg='app.nova.browser.preview'
def adb(*a):return subprocess.check_output(['adb',*a],timeout=30)
def root():
 adb('shell','uiautomator','dump','/sdcard/lifetime.xml');return E.fromstring(adb('shell','cat','/sdcard/lifetime.xml'))
def tap(label):return ui['tap'](label)
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
