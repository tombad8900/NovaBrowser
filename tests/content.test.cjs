const fs=require('fs'),vm=require('vm'),assert=require('assert');
const sent=[],timers=[];let observer;
const script={textContent:'const u="https://cdn.test/first.mp4";'};
const doc={scripts:[script],querySelectorAll:()=>[],addEventListener:()=>{}};
const win={addEventListener:()=>{}};win.top=win;
const browser={runtime:{sendNativeMessage:async(a,m)=>sent.push(m),sendMessage:async()=>({epoch:1,items:[]}),onMessage:{addListener:()=>{}}}};
const ctx={browser,window:win,document:doc,location:{href:'https://page.test/'},MutationObserver:class{constructor(f){observer=f}observe(){}},setTimeout:f=>{timers.push(f);return timers.length},Map,Set,WeakMap,JSON};
vm.runInNewContext(fs.readFileSync('app/src/main/assets/nova-ext/content.js','utf8'),ctx);
function flush(){while(timers.length)timers.shift()();}
(async()=>{
 await new Promise(r=>setImmediate(r));flush();assert.equal(sent.length,1);assert(sent[0].urls.includes('https://cdn.test/first.mp4'));
 observer([{type:'childList'}]);flush();assert.equal(sent.length,1,'unchanged document should not resend tracks');
 script.textContent='const u="https://cdn.test/other.mp4";';observer([{type:'childList'}]);flush();assert.equal(sent.length,2);assert(sent[1].urls.includes('https://cdn.test/other.mp4'),'same-length script replacement must be scanned');
 console.log('PASS bounded content scan, unchanged metadata dedupe, same-length script updates');
})().catch(e=>{console.error(e);process.exit(1)});
