"""Actual caption pixels at two persisted Nova styles, not source-page style inheritance."""
import runpy,subprocess,time,xml.etree.ElementTree as E,re
ui=runpy.run_path('testpage/ui-check.py');adb=ui['adb'];tap=ui['tap'];pkg='app.nova.browser.preview'
def shot(n):open('shots/'+n+'.png','wb').write(adb('exec-out','screencap','-p'))
def launch():
 adb('shell','am','force-stop',pkg);adb('shell','am','start','-a','android.intent.action.VIEW','-d','http://10.0.2.2:8000/caption.html','--ez','testHoldControls','true',pkg)
 for attempt in range(3):
  tap('تشغيل في نوفا');time.sleep(1)
  activities=adb('shell','dumpsys','activity','activities').decode()
  if re.search(r'(?:mResumedActivity:|topResumedActivity=).*PlayerActivity',activities):break
  time.sleep(1)
 else:raise RuntimeError('Caption source did not become ready')
 time.sleep(4)
launch();shot('30-caption-default')
adb('shell','input','tap','160','110');time.sleep(.5);shot('30b-caption-portrait-hidden')
adb('shell','input','tap','160','110');adb('shell','settings','put','system','accelerometer_rotation','0');adb('shell','settings','put','system','user_rotation','1');time.sleep(2);shot('30c-caption-landscape-shown')
adb('shell','input','tap','320','90');time.sleep(.5);shot('30d-caption-landscape-hidden')
adb('shell','settings','put','system','user_rotation','0');adb('shell','input','keyevent','KEYCODE_BACK')
tap('القائمة');tap('الإعدادات');tap('التشغيل');tap('حجم الخط')
# Native SeekBar receives keyboard input, not a hidden preference mutation.
r=ui['snapshot']();bar=next(n for n in r.iter() if n.attrib.get('class')=='android.widget.SeekBar');b=list(map(int,re.findall(r'\d+',bar.attrib['bounds'])))
adb('shell','input','tap',str(b[0]+(b[2]-b[0])//2),str((b[1]+b[3])//2));tap('حفظ');shot('31-caption-size-setting')
adb('shell','input','keyevent','KEYCODE_BACK');launch();shot('32-caption-custom')
open('shots/caption-preferences.xml','wb').write(adb('shell','run-as',pkg,'cat','shared_prefs/nova.xml'))
