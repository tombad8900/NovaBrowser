"""Real UI clicks, exact time deltas, and non-overlapping controls in both directions/orientations."""
import subprocess,time,re,xml.etree.ElementTree as E,json,os
pkg='app.nova.browser.trial'
def adb(*a):return subprocess.check_output(['adb',*a],timeout=30)
def dump():
 adb('shell','uiautomator','dump','/sdcard/transport.xml')
 raw=adb('shell','cat','/sdcard/transport.xml');open('shots/transport-last.xml','wb').write(raw);return E.fromstring(raw)
def bounds(n):return list(map(int,re.findall(r'\d+',n.attrib['bounds'])))
def find(id):
 for attempt in range(2):
  root=dump()
  for n in root.iter():
   if n.attrib.get('resource-id','').endswith('/'+id) or n.attrib.get('content-desc')==id or n.attrib.get('text')==id:return n
  # PlayerView's own accessibility action is a real reveal affordance.
  reveal=next((n for n in root.iter() if n.attrib.get('content-desc')=='Show player controls'),None)
  if reveal is None:break
  b=bounds(reveal);adb('shell','input','tap',str((b[0]+b[2])//2),str(b[1]+max(80,(b[3]-b[1])//5)));time.sleep(.2)
 shot('24-failed-lookup');raise RuntimeError('Missing transport '+id)
def tap(id):
 n=find(id);b=bounds(n);adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));time.sleep(.5)
def seconds():
 t=find('exo_position').attrib.get('text','');v=[int(x) for x in t.split(':')];return sum(n*60**i for i,n in enumerate(v[::-1]))
def shot(name):open('shots/'+name+'.png','wb').write(adb('exec-out','screencap','-p'))
results=[]
for rtl in [0,1]:
 adb('shell','settings','put','global','debug.force_rtl',str(rtl))
 adb('shell','am','force-stop',pkg)
 adb('shell','am','start','-a','android.intent.action.VIEW','-d','http://10.0.2.2:8000/switch.html','--ez','testHoldControls','true',pkg);time.sleep(6)
 tap('Play in Nova');time.sleep(4)
 for orientation in ['portrait','landscape']:
  if orientation=='landscape':tap('Rotate');time.sleep(2)
  pause=find('exo_play_pause').attrib.get('content-desc','')
  if 'Pause' in pause or 'إيقاف' in pause:tap('exo_play_pause')
  initial=seconds()
  target=bounds(find('exo_play_pause'));x=str((target[0]+target[2])//2);y=str((target[1]+target[3])//2)
  adb('shell','input','tap',x,y);time.sleep(2)
  adb('shell','input','tap',x,y);time.sleep(.3)
  after=find('exo_play_pause').attrib.get('content-desc','')
  assert seconds()>initial,'Play/pause taps did not resume then pause timeline'
  before=seconds();time.sleep(1);assert abs(seconds()-before)<=1,'Pause did not stop timeline'
  tap('exo_ffwd');forward=seconds();assert abs(forward-before-10)<=1,(before,forward)
  tap('exo_rew');back=seconds();assert abs(back-before)<=1,(before,back)
  controls=[bounds(find(x)) for x in ['exo_rew','exo_play_pause','exo_ffwd']]
  for a,b in zip(controls,controls[1:]):assert a[2]<=b[0],'Transport hit targets overlap'
  for n in dump().iter():
   if n.attrib.get('content-desc') in ['Volume','الصوت']:
    v=bounds(n)
    for b in controls:assert b[3]<=v[1] or b[1]>=v[3] or b[2]<=v[0] or b[0]>=v[2],'Volume overlaps transport'
  shot('24-transport-'+str(rtl)+'-'+orientation)
  results.append({'forcedRTL':bool(rtl),'orientation':orientation,'before':before,'forward':forward,'back':back,'targets':controls,'pauseDescription':after})
 adb('shell','input','keyevent','KEYCODE_BACK')
adb('shell','settings','put','global','debug.force_rtl','0')
open('shots/transport-results.json','w').write(json.dumps(results,indent=2))
