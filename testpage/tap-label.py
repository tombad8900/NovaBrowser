import sys,re,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
for n in root.iter():
 if sys.argv[2].lower() in n.attrib.get('text','').lower():
  a=list(map(int,re.findall(r'\d+',n.attrib['bounds'])));print((a[0]+a[2])//2,(a[1]+a[3])//2);break
else:raise SystemExit('label not found')
