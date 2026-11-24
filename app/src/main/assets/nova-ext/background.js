// Request metadata is delivered directly to native code, never to page content scripts.
const requests = new Map(), pending = new Map();
const ext = /\.(m3u8|mpd|mp4|m4v|webm|mkv|mov|mp3|m4a|aac|ogg|opus|vtt|srt|ass|ssa|ttml|dfxp|torrent)(\?|#|$)/i;
const mime = /^(video\/|audio\/|application\/(vnd\.apple\.mpegurl|x-mpegurl|dash\+xml|x-subrip)|text\/vtt)/i;
const allowed = new Set(['user-agent','referer','origin','cookie','authorization','accept','accept-language','x-requested-with']);
let serial = 0;
function headersOf(list) {
  const out = {}; for (const h of list || []) if (allowed.has(h.name.toLowerCase()) && h.value) out[h.name] = h.value;
  return out;
}
function record(d) {
  if (d.tabId < 0 || d.method !== 'GET' || !/^https?:/.test(d.url)) return;
  requests.set(d.requestId, {url:d.url, headers:headersOf(d.requestHeaders), time:Date.now()});
  if (requests.size > 600) requests.delete(requests.keys().next().value);
}
browser.webRequest.onSendHeaders.addListener(record, {urls:['<all_urls>']}, ['requestHeaders']);
browser.webRequest.onHeadersReceived.addListener(async d => {
  if (d.tabId < 0 || d.type === 'main_frame' || d.statusCode < 200 || d.statusCode >= 400) return;
  const r = requests.get(d.requestId) || {url:d.url,headers:{},time:Date.now()};
  const ct = (d.responseHeaders || []).find(h => h.name.toLowerCase() === 'content-type');
  const type = ct ? ct.value.split(';')[0].trim() : '';
  // A misleading extension must not turn an HTML/error response into a video.
  if (/text\/html|application\/json/i.test(type)) return;
  const isCandidate = mime.test(type) || ext.test(d.url);
  const segment = /\.(ts|m4s|m4a|aac)(\?|#|$)/i.test(d.url);
  if (!isCandidate && d.type !== 'media' && !segment) return;
  const token = Date.now().toString(36) + '-' + (++serial);
  const observation = {t:'resource',token,url:d.url,ct:type,segment,headers:r.headers,time:r.time};
  try { await browser.runtime.sendNativeMessage('nova', observation); } catch (_) { return; }
  let b = pending.get(d.tabId) || []; b.push({token}); if (b.length>200) b.shift(); pending.set(d.tabId,b);
  browser.tabs.sendMessage(d.tabId,{nova:'resource',token},{frameId:0}).catch(()=>{});
}, {urls:['<all_urls>']}, ['responseHeaders']);
browser.webRequest.onCompleted.addListener(d=>requests.delete(d.requestId),{urls:['<all_urls>']});
browser.webRequest.onErrorOccurred.addListener(d=>requests.delete(d.requestId),{urls:['<all_urls>']});
browser.webRequest.onBeforeRequest.addListener(d=>{if(d.type==='main_frame') pending.delete(d.tabId);},{urls:['<all_urls>']});
browser.tabs.onRemoved.addListener(id=>pending.delete(id));
browser.runtime.onMessage.addListener((m,s)=>{if(m && m.nova==='hello' && s.tab) return Promise.resolve(pending.get(s.tab.id)||[]);});
