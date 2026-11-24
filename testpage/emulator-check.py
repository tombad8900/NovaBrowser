import subprocess,xml.etree.ElementTree as ET,re,time,os
os.makedirs('shots',exist_ok=True)
def adb(*a):return subprocess.check_output(['adb',*a],timeout=20)
def shot(name):open('shots/'+name+'.png','wb').write(adb('exec-out','screencap','-p'))
def dump():
 adb('shell','uiautomator','dump','/sdcard/current.xml');return ET.fromstring(adb('shell','cat','/sdcard/current.xml'))
def tap(label):
 for n in dump().iter():
  if n.attrib.get('text','').removeprefix('✓  ')==label or n.attrib.get('content-desc','')==label:
   b=list(map(int,re.findall(r'\d+',n.attrib['bounds'])));adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));return
 raise RuntimeError('Missing control '+label)
def controls():
 adb('shell','input','tap','160','280');time.sleep(.2)
def expect(text):
 if not any(text in n.attrib.get('text','') for n in dump().iter()):raise RuntimeError('Missing expected text '+text)
tap('Play in Nova');time.sleep(3);adb('shell','input','tap','36','608');time.sleep(.5);shot('4-player')
tap('Sources');expect('Playback sources');
if any('fake' in n.attrib.get('text','').lower() for n in dump().iter()):raise RuntimeError('Rejected HTML shown as source')
if any('AUDIO' in n.attrib.get('text','') for n in dump().iter()):raise RuntimeError('Known playlist fragment exposed as playable source')
shot('5-sources')
tap('VIDEO · 10.0.2.2');time.sleep(3);adb('shell','input','tap','36','608');shot('5a-mp4')
tap('Sources');tap('HLS · 10.0.2.2');time.sleep(3);adb('shell','input','tap','36','608');shot('5b-hls')
tap('Subtitles');expect('العربية');expect('Arabic extensionless');shot('6-subtitles');tap('Arabic extensionless');time.sleep(2);shot('6b-extensionless-selected');tap('Subtitles');adb('shell','input','keyevent','KEYCODE_BACK')
tap('Quality');expect('Available quality');expect('144p');expect('240p');shot('7-quality');tap('144p');time.sleep(2);shot('7b-quality-selected')
tap('Rotate');time.sleep(2);shot('8-landscape')
tap('Subtitles');expect('العربية');shot('8b-landscape-subs');adb('shell','input','keyevent','KEYCODE_BACK')
tap('Sources');expect('HLS · 10.0.2.2');shot('8c-landscape-sources');adb('shell','input','keyevent','KEYCODE_BACK')
adb('shell','input','keyevent','KEYCODE_BACK')
# Back to portrait browser before any additional website checks.
adb('shell','settings','put','system','accelerometer_rotation','0')
adb('shell','settings','put','system','user_rotation','0')

log=adb('logcat','-d','-s','AndroidRuntime:E','ExoPlayerImplInternal:E').decode(errors='replace')
open('shots/player-errors.txt','w').write(log)
if 'FATAL EXCEPTION' in log or 'Playback error' in log:raise RuntimeError('Runtime or playback error in captured log')
