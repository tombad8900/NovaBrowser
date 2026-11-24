"""Authorized public demo, distinct from generated local media. No account or spending."""
import subprocess,time,re,xml.etree.ElementTree as E

def adb(*a):return subprocess.check_output(['adb',*a],timeout=30)
def dump():
 adb('shell','uiautomator','dump','/sdcard/external.xml');return E.fromstring(adb('shell','cat','/sdcard/external.xml'))
def tap(label):
 for n in dump().iter():
  if n.attrib.get('text')==label or n.attrib.get('content-desc')==label:
   b=list(map(int,re.findall(r'\d+',n.attrib['bounds'])));adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));return
 raise RuntimeError('Missing external test control '+label)
adb('shell','am','force-stop','app.nova.browser')
adb('logcat','-c')
adb('shell','am','start','-a','android.intent.action.VIEW','-d','https://bitmovin.com/demos/multi-audio-tracks/','app.nova.browser')
time.sleep(22)
open('shots/18-external-page.png','wb').write(adb('exec-out','screencap','-p'))
xml=E.tostring(dump(),encoding='unicode');open('shots/external-page.xml','w').write(xml)
tap('Play in Nova');time.sleep(10)
log=adb('logcat','-d','-s','NovaPlayback:I','AndroidRuntime:E','ExoPlayerImplInternal:E').decode();open('shots/external-log.txt','w').write(log)
open('shots/19-external-player.png','wb').write(adb('exec-out','screencap','-p'))
if 'Ready kind=' not in log or 'Playback error' in log or 'FATAL EXCEPTION' in log:raise RuntimeError('Public demo native playback not verified')
# Reveal paused controls for inspectable panels.
adb('shell','input','tap','160','280');time.sleep(.2)
tap('Sources');open('shots/20-external-sources.png','wb').write(adb('exec-out','screencap','-p'))
