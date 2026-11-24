"""Exercise the actual omnibox after persisted Google selection in preceding settings test."""
import runpy,time,re
ui=runpy.run_path('testpage/ui-check.py');adb=ui['adb'];pkg='app.nova.browser.preview'
adb('shell','am','force-stop',pkg);adb('shell','am','start','-a','android.intent.action.VIEW','-d','http://10.0.2.2:8000/plain.html',pkg);time.sleep(5)
r=ui['snapshot']();bar=next(n for n in r.iter() if n.attrib.get('class')=='android.widget.EditText');b=list(map(int,re.findall(r'\d+',bar.attrib['bounds'])))
adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));adb('shell','input','keyevent','--longpress','KEYCODE_MOVE_END');adb('shell','input','keyevent','KEYCODE_CTRL_LEFT','KEYCODE_A');adb('shell','input','text','nova%20search%20routing');adb('shell','input','keyevent','KEYCODE_ENTER');time.sleep(8)
r=ui['snapshot']();texts=[n.attrib.get('text','') for n in r.iter() if n.attrib.get('class')=='android.widget.EditText'];open('shots/search-routing.txt','w').write('\n'.join(texts));assert any('google.com/search?' in t and 'nova' in t for t in texts),texts
open('shots/33-search-routed-google.png','wb').write(adb('exec-out','screencap','-p'))
