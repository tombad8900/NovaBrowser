import subprocess,xml.etree.ElementTree as E,re,time
root=E.parse('shots/frame.xml').getroot()
for n in root.iter():
 if n.attrib.get('text')=='Play in Nova':
  b=list(map(int,re.findall(r'\d+',n.attrib['bounds'])));subprocess.check_call(['adb','shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2)]);break
else:raise RuntimeError('Player launch control missing')
time.sleep(3)
subprocess.check_call(['adb','shell','uiautomator','dump','/sdcard/frameplayer.xml'])
xml=subprocess.check_output(['adb','shell','cat','/sdcard/frameplayer.xml']).decode()
open('shots/frameplayer.xml','w').write(xml)
if 'Iframe detector test' not in xml:raise RuntimeError('Delayed iframe source was not handed to player')
open('shots/15-frame-player.png','wb').write(subprocess.check_output(['adb','exec-out','screencap','-p']))
