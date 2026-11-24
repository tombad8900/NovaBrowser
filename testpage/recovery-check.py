import subprocess,time,re

def adb(*args):return subprocess.check_output(['adb',*args],timeout=30)
adb('shell','am','start','-a','android.intent.action.VIEW','-d','http://10.0.2.2:8000/plain.html','app.nova.browser')
time.sleep(4)
adb('root');adb('wait-for-device')
processes=adb('shell','ps','-A','-o','PID,NAME').decode()
pids=[line.split()[0] for line in processes.splitlines() if 'app.nova.browser:sandboxed_tab' in line]
if not pids:raise RuntimeError('No content process available for recovery test')
for pid in pids:adb('shell','kill','-9',pid)
time.sleep(7)
adb('shell','uiautomator','dump','/sdcard/recovered.xml')
xml=adb('shell','cat','/sdcard/recovered.xml').decode();open('shots/recovered.xml','w').write(xml)
open('shots/17-recovered-tab.png','wb').write(adb('exec-out','screencap','-p'))
if 'Plain unstyled page' not in xml:raise RuntimeError('Content-process recovery failed to restore plain page')
adb('shell','pidof','app.nova.browser')
