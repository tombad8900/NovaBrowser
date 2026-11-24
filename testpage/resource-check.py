"""Same local no-media page in one versus eight tabs. Not a device benchmark."""
import subprocess,time,os,json,re
pkg='app.nova.browser.preview'
def adb(*a):return subprocess.check_output(['adb',*a],timeout=30)
adb('shell','am','force-stop',pkg)
adb('shell','am','start','-a','android.intent.action.VIEW','-d','http://10.0.2.2:8000/plain.html',pkg);time.sleep(10)
summary={}
for count in [1,8]:
 if count==8:
  for n in range(7):
   adb('shell','am','start','-a','android.intent.action.VIEW','-d','http://10.0.2.2:8000/plain.html?tab='+str(n),pkg);time.sleep(2)
  time.sleep(10)
 mem=adb('shell','dumpsys','meminfo','--package',pkg).decode()
 open('shots/resource-'+str(count)+'-tabs.txt','w').write(mem)
 values=[int(x) for x in re.findall(r'TOTAL PSS:\s+(\d+)',mem)]
 summary[str(count)]={'processPssKB':values,'totalPssKB':sum(values),'processes':len(values)}
 open('shots/resource-'+str(count)+'-tabs.png','wb').write(adb('exec-out','screencap','-p'))
open('shots/resource-summary.json','w').write(json.dumps(summary,indent=2))
