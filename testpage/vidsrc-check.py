"""User-named embed integration probe. Preserve results, never report access as playback."""
import subprocess,time,re,xml.etree.ElementTree as E

def adb(*a):return subprocess.check_output(['adb',*a],timeout=30)
def snap(label):
 adb('shell','uiautomator','dump','/sdcard/vidsrc.xml');raw=adb('shell','cat','/sdcard/vidsrc.xml');open('shots/'+label+'.xml','wb').write(raw);open('shots/'+label+'.png','wb').write(adb('exec-out','screencap','-p'));return E.fromstring(raw)
def click(node):
 b=list(map(int,re.findall(r'\d+',node.attrib['bounds'])));adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2))
adb('shell','am','force-stop','app.nova.browser');adb('logcat','-c')
adb('shell','am','start','-a','android.intent.action.VIEW','-d','https://vidsrc.sh/embed/movie/tt1300854','app.nova.browser');time.sleep(20)
root=snap('24-vidsrc-page')
played=False
for n in root.iter():
 label=(n.attrib.get('text','')+' '+n.attrib.get('content-desc','')).strip().lower()
 if label in ['play','play video','تشغيل']:
  click(n);played=True;time.sleep(12);break
if not played:
 # Actual inspected phone screenshot: the player's unlabeled triangle is at160,264.
 adb('shell','input','tap','160','264');time.sleep(20)
root=snap('25-vidsrc-after-play')
for n in root.iter():
 label=(n.attrib.get('text','')+' '+n.attrib.get('content-desc','')).strip().lower()
 if label in ['subtitles','captions','ترجمات','الترجمات','cc']:
  click(n);time.sleep(2);snap('26-vidsrc-site-subtitles');adb('shell','input','keyevent','KEYCODE_BACK');break
root=snap('27-vidsrc-before-native')
for n in root.iter():
 if n.attrib.get('text')=='Play in Nova':click(n);time.sleep(8);break
root=snap('28-vidsrc-native')
log=adb('logcat','-d','-s','NovaPlayback:I','NovaEngine:I','AndroidRuntime:E','ExoPlayerImplInternal:E').decode();open('shots/vidsrc-log.txt','w').write(log)
if 'Ready kind=' in log:
 adb('shell','input','tap','160','280');time.sleep(.2);adb('shell','input','tap','32','608')
 root=snap('29-vidsrc-native-controls')
 for n in root.iter():
  if n.attrib.get('content-desc')=='Subtitles':click(n);time.sleep(.3);snap('30-vidsrc-native-subtitles');break
