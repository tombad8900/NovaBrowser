// Content scripts never receive request headers. Network references are opaque.
function send(m) { try { browser.runtime.sendNativeMessage('nova', m).catch(()=>{}); } catch (_) {} }
const isTop = window === window.top;
let epoch=null;
function fwd(item) { if (isTop && item.token && item.epoch===epoch) send({t:'resourceRef',token:item.token,page:location.href}); }
browser.runtime.onMessage.addListener(m => { if(m && m.nova==='resource') fwd(m); });
if(isTop) browser.runtime.sendMessage({nova:'hello'}).then(result=>{epoch=result.epoch;(result.items||[]).forEach(fwd);}).catch(()=>{});
const mediaPattern=/https?:\/\/[^\s"'<>\\)]+\.(?:m3u8|mpd|mp4|webm|mkv|vtt|srt|ass|ttml)(?:\?[^\s"'<>\\)]*)?/gi;
let timer=0;
const sent=new Set();
function scan() {
  timer=0;
  try {
    const out=[];
    function add(u) {if(typeof u==='string' && /^https?:|^magnet:/i.test(u) && !sent.has(u) && out.length<150){out.push(u);sent.add(u);}}
    const nodes=document.querySelectorAll('video,audio,source,track,a[href],iframe,embed');
    for(let i=0;i<Math.min(nodes.length,500);i++) add(nodes[i].currentSrc||nodes[i].src||nodes[i].href);
    // Walk a bounded set of script text rather than serialize the entire DOM.
    let budget=200000;
    const scripts=document.scripts;
    for(let i=0;i<Math.min(scripts.length,50)&&budget>0;i++) {
      let h=(scripts[i].textContent||'').slice(0,budget).replace(/\\\//g,'/').replace(/&amp;/g,'&');budget-=h.length;
      mediaPattern.lastIndex=0;let m,n=0;
      while((m=mediaPattern.exec(h))&&n++<150)add(m[0]);
    }
    const tracks=[];
    document.querySelectorAll('track[src]').forEach(e=>{if(tracks.length<60)tracks.push({url:e.src,language:e.srclang||'und',label:e.label||''});});
    if(out.length||tracks.length)send({t:'scan',urls:out,tracks,page:location.href});
    if(sent.size>600)sent.clear();
  }catch(_){}
}
function queue(){if(!timer)timer=setTimeout(scan,350);}
document.addEventListener('play',queue,true);
document.addEventListener('loadedmetadata',queue,true);
window.addEventListener('load',()=>{queue();setTimeout(queue,3000);setTimeout(queue,8000);});
