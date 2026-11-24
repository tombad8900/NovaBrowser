// Watches network requests and response types, then hands media/subtitle candidates to the content
// script of the same tab, which forwards them to the app through that tab's own session.
var EXT = /\.(m3u8|mpd|mp4|m4v|webm|mkv|mov|flv|mp3|m4a|aac|ogg|opus|vtt|srt|ass|ssa|ttml|dfxp|torrent)(\?|#|$)/i;
var MIME = /^(video\/|audio\/|application\/(vnd\.apple\.mpegurl|x-mpegurl|dash\+xml|x-subrip)|text\/vtt)/i;
var buf = {};
function push(tabId, item) {
  if (tabId < 0) return;
  var b = buf[tabId] || (buf[tabId] = []);
  if (b.length > 300) return;
  for (var i = 0; i < b.length; i++) if (b[i].url === item.url) return;
  b.push(item);
  browser.tabs.sendMessage(tabId, { nova: "net", item: item }).catch(function () {});
}
browser.webRequest.onBeforeRequest.addListener(function (d) {
  if (d.type === "main_frame") { buf[d.tabId] = []; return; }
  if (EXT.test(d.url)) push(d.tabId, { url: d.url, ct: "" });
}, { urls: ["<all_urls>"] });
browser.webRequest.onHeadersReceived.addListener(function (d) {
  if (d.type === "main_frame") return;
  var ct = "";
  (d.responseHeaders || []).forEach(function (h) { if (h.name.toLowerCase() === "content-type") ct = h.value || ""; });
  if (MIME.test(ct)) push(d.tabId, { url: d.url, ct: ct });
}, { urls: ["<all_urls>"] }, ["responseHeaders"]);
browser.tabs.onRemoved.addListener(function (id) { delete buf[id]; });
browser.runtime.onMessage.addListener(function (msg, sender) {
  if (msg && msg.nova === "hello" && sender.tab) return Promise.resolve(buf[sender.tab.id] || []);
});
