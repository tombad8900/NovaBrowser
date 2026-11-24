"""Fresh bounded accessibility reads. Failed dumps never reuse old XML."""
import subprocess,time,xml.etree.ElementTree as E,re,os

def adb(*a):return subprocess.check_output(['adb',*a],timeout=30)
def snapshot():
 adb('shell','rm','-f','/sdcard/nova-check.xml')
 out=adb('shell','uiautomator','dump','/sdcard/nova-check.xml')
 if b'dumped to:' not in out:return None
 raw=adb('shell','cat','/sdcard/nova-check.xml');os.makedirs('shots',exist_ok=True);open('shots/latest-ui.xml','wb').write(raw)
 return E.fromstring(raw)
def locate(label,timeout=25):
 end=time.monotonic()+timeout
 while time.monotonic()<end:
  root=snapshot()
  if root is not None:
   for n in root.iter():
    if n.attrib.get('text')==label or n.attrib.get('content-desc')==label:return n
  time.sleep(.5)
 open('shots/failed-wait.png','wb').write(adb('exec-out','screencap','-p'))
 open('shots/failed-wait-activity.txt','wb').write(adb('shell','dumpsys','activity','activities'))
 raise RuntimeError('Timed out waiting for visible '+label)
def tap(label):
 n=locate(label);b=list(map(int,re.findall(r'\d+',n.attrib['bounds'])));adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));time.sleep(.3)
