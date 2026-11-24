import subprocess,time,re

def adb(*args):return subprocess.check_output(['adb',*args],timeout=30)
# Existing frame tab is current; don't exceed the explicit eight-tab limit.
adb('shell','uiautomator','dump','/sdcard/before-recovery.xml')
before=adb('shell','cat','/sdcard/before-recovery.xml').decode()
if 'Iframe detector' not in before:raise RuntimeError('Recovery precondition: frame tab is not active')
adb('root');adb('wait-for-device')
processes=adb('shell','ps','-A','-o','PID,NAME').decode()
pids=[line.split()[0] for line in processes.splitlines() if 'app.nova.browser:sandboxed_tab' in line]
if not pids:raise RuntimeError('No content process available for recovery test')
for pid in pids:adb('shell','kill','-9',pid)
time.sleep(7)
adb('shell','uiautomator','dump','/sdcard/recovered.xml')
xml=adb('shell','cat','/sdcard/recovered.xml').decode();open('shots/recovered.xml','w').write(xml)
open('shots/17-recovered-tab.png','wb').write(adb('exec-out','screencap','-p'))
if 'Iframe detector' not in xml or 'http://10.0.2.2:8000/frame.html' not in xml:raise RuntimeError('Content-process recovery failed to restore frame page')
open('shots/recovery-log.txt','w').write(adb('logcat','-d','-s','NovaEngine:I','AndroidRuntime:E').decode())
adb('shell','pidof','app.nova.browser')
