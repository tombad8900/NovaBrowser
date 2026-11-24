// Runs in every page and frame. Only the top frame forwards network hits; every frame scans its DOM.
function send(m) { try { browser.runtime.sendNativeMessage("nova", m); } catch (e) {} }
var isTop = (window === window.top);
function fwd(item) { if (isTop && item.token) send({ t: "resourceRef", token: item.token, page: location.href }); }
browser.runtime.onMessage.addListener(function (msg) {
  if (msg && msg.nova === "resource") fwd(msg);
});
if (isTop) browser.runtime.sendMessage({ nova: "hello" }).then(function(list){(list||[]).forEach(fwd);}).catch(function(){});
var re = /https?:\/\/[^\s"'<>\\)]+\.(?:m3u8|mpd|mp4|webm|mkv|vtt|srt|ass|ttml)(?:\?[^\s"'<>\\)]*)?/gi;
function scan() {
  try {
    var out = [];
    function add(u) { if (u && typeof u === "string" && out.length < 300) out.push(u); }
    document.querySelectorAll("video,audio,source,track,a[href],iframe,embed").forEach(function (e) {
      add(e.currentSrc || e.src || e.href);
    });
    var h = (document.documentElement ? document.documentElement.innerHTML.slice(0, 350000) : "").replace(/\\\//g, "/").replace(/&amp;/g, "&");
    var m, n = 0;
    re.lastIndex = 0;
    while ((m = re.exec(h)) && n++ < 300) add(m[0]);
    (h.match(/magnet:\?xt=urn:btih:[^\s"'<>]+/gi) || []).forEach(add);
    if (out.length) send({ t: "scan", urls: out });
  } catch (e) {}
}
document.addEventListener("play", function () { setTimeout(scan, 300); }, true);
document.addEventListener("loadedmetadata", function () { setTimeout(scan, 300); }, true);
window.addEventListener("load", function () { setTimeout(scan, 500); setTimeout(scan, 3000); setTimeout(scan, 8000); });
