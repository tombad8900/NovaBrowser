import subprocess,time,xml.etree.ElementTree as E
subprocess.check_call(['adb','shell','am','start','-a','android.intent.action.VIEW','-d','magnet:?xt=urn:btih:0123456789012345678901234567890123456789','app.nova.browser'])
time.sleep(2)
subprocess.check_call(['adb','shell','uiautomator','dump','/sdcard/consent.xml'])
xml=subprocess.check_output(['adb','shell','cat','/sdcard/consent.xml']).decode()
open('shots/consent.xml','w').write(xml)
if 'Peers can see your IP address' not in xml:raise RuntimeError('Torrent disclosure missing')
open('shots/16-torrent-consent.png','wb').write(subprocess.check_output(['adb','exec-out','screencap','-p']))
subprocess.check_call(['adb','shell','input','keyevent','KEYCODE_BACK'])
subprocess.check_call(['adb','shell','input','keyevent','KEYCODE_BACK'])

# Cancel torrent selection while a real local video is already playing.
import re
def adb(*a):return subprocess.check_output(['adb',*a],timeout=25)
def root():
 adb('shell','uiautomator','dump','/sdcard/switch.xml');raw=adb('shell','cat','/sdcard/switch.xml');open('shots/switch.xml','wb').write(raw);open('shots/switch-state.png','wb').write(adb('exec-out','screencap','-p'));return E.fromstring(raw)
def tap(label):
 for n in root().iter():
  if n.attrib.get('text','').removeprefix('✓  ').strip()==label.strip() or n.attrib.get('content-desc')==label:
   b=list(map(int,re.findall(r'\d+',n.attrib['bounds'])));adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));return
 raise RuntimeError('Missing '+label)
adb('shell','am','start','-a','android.intent.action.VIEW','-d','http://10.0.2.2:8000/switch.html','app.nova.browser');time.sleep(5)
tap('Play in Nova');time.sleep(3)
adb('shell','input','tap','36','608');tap('Sources');tap('TORRENT · ')

if not any('Peers can see your IP address' in n.attrib.get('text','') for n in root().iter()):raise RuntimeError('Switch disclosure missing')
tap('Cancel');tap('Sources')
if not any(n.attrib.get('text','')=='✓  VIDEO · 10.0.2.2' for n in root().iter()):raise RuntimeError('Cancel changed current source')
open('shots/16b-canceled-source-preserved.png','wb').write(adb('exec-out','screencap','-p'))
adb('shell','input','keyevent','KEYCODE_BACK');adb('shell','input','keyevent','KEYCODE_BACK')
