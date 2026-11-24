import subprocess,xml.etree.ElementTree as ET,re,time,os
os.makedirs('shots',exist_ok=True)
def adb(*a):return subprocess.check_output(['adb',*a])
def shot(name):open('shots/'+name+'.png','wb').write(adb('exec-out','screencap','-p'))
def dump():
 adb('shell','uiautomator','dump','/sdcard/current.xml');return ET.fromstring(adb('shell','cat','/sdcard/current.xml'))
def tap(label):
 for n in dump().iter():
  if n.attrib.get('text','')==label:
   b=list(map(int,re.findall(r'\d+',n.attrib['bounds'])));adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));return
 raise RuntimeError('Missing control '+label)
def controls():
 adb('shell','input','tap','160','280');time.sleep(.5)
 root=dump()
 if not any(n.attrib.get('text')=='Sources' for n in root.iter()):
  adb('shell','input','tap','160','280');time.sleep(.5)
def expect(text):
 if not any(text in n.attrib.get('text','') for n in dump().iter()):raise RuntimeError('Missing expected text '+text)
tap('Play in Nova');time.sleep(4);shot('4-player');controls();tap('Sources');time.sleep(.5);expect('Playback sources');shot('5-sources');adb('shell','input','keyevent','KEYCODE_BACK');controls();tap('Subtitles');time.sleep(.5);expect('Arabic first');shot('6-subtitles');adb('shell','input','keyevent','KEYCODE_BACK');controls();tap('Quality');time.sleep(.5);expect('Available quality');shot('7-quality');adb('shell','input','keyevent','KEYCODE_BACK');controls();tap('↻');time.sleep(2);shot('8-landscape');adb('shell','input','keyevent','KEYCODE_BACK')
