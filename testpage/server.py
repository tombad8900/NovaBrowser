"""Local integration fixtures, no third-party media. Request-context requirements intentionally enforced."""
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
import json, os, sys
class Handler(SimpleHTTPRequestHandler):
    def log_message(self, fmt, *args):
        # Never log URLs, cookies or authorization values.
        pass
    def do_GET(self):
        path=self.path.split('?')[0]
        if path=='/protection.html':
            self.send_response(200);self.send_header('Content-Type','text/html');self.end_headers()
            self.wfile.write(b'<meta name=viewport content="width=device-width"><h2>Protection fixture</h2><p id=ad>Checking ad request</p><script>fetch("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",{mode:"no-cors"}).then(()=>document.getElementById("ad").textContent="Request completed").catch(()=>document.getElementById("ad").textContent="Ad request blocked or network failed");</script>');return
        if path=='/frame.html':
            self.send_response(200);self.send_header('Content-Type','text/html');self.end_headers()
            self.wfile.write(b'<title>Iframe detector test</title><h2>Iframe detector</h2><iframe src="/frame-inner.html"></iframe>');return
        if path=='/frame-inner.html':
            self.send_response(200);self.send_header('Content-Type','text/html');self.end_headers()
            self.wfile.write(b'<script>setTimeout(()=>{const a=document.createElement("a");a.href="/clip.mp4";a.textContent="Late video link";document.body.appendChild(a);},10000)</script><body>Delayed iframe source</body>');return
        if path=='/popup.html':
            self.send_response(200);self.send_header('Content-Type','text/html');self.end_headers()
            self.wfile.write(b'<meta name=viewport content="width=device-width"><h2>Popup regression</h2><a target="_blank" href="/plain.html">Open safe tab</a><script>window.open("/plain.html");</script>');return
        if path=='/switch.html':
            self.send_response(200);self.send_header('Content-Type','text/html');self.end_headers()
            self.wfile.write(b'<title>Source switch consent regression</title><h2>Source switch consent regression</h2><a href="/clip.mp4">Video</a><a href="magnet:?xt=urn:btih:0123456789012345678901234567890123456789">Torrent</a>');return
        if path=='/test.html':
            self.send_response(200);self.send_header('Content-Type','text/html');self.send_header('Set-Cookie','nova_test=ok; Path=/');self.end_headers()
            self.wfile.write(b'''<!doctype html><meta name=viewport content="width=device-width,initial-scale=1"><body style="font:18px sans-serif;background:#fafafa;color:#111;padding:24px"><title>Nova playback test</title><h2>Nova context test</h2><a href="/fake.m3u8">Not media</a><p>Protected video and Arabic subtitle.</p><video width=260 controls><source src="/protected/clip.mp4" type="video/mp4"><track src="/protected/ar.vtt" kind=subtitles srclang=ar label="Arabic"><track src="/protected/captions?id=ar" kind=subtitles srclang=ar label="Arabic extensionless"></video><script>fetch('/protected/ar.vtt');setTimeout(()=>fetch('/protected/captions?id=ar'),1500);fetch('/protected/hls/master.m3u8').then(()=>{const a=document.createElement('a');a.href='/protected/hls/fragment.m4a';document.body.appendChild(a);setTimeout(()=>fetch(a.href),1000)});fetch('/fake.m3u8');</script>''');return
        if path=='/protected/hls/fragment.m4a':
            self.send_response(200);self.send_header('Content-Type','audio/mp4');self.end_headers();self.wfile.write(b'fixture-fragment');return
        if path.startswith('/protected/'):
            cookie='nova_test=ok' in self.headers.get('Cookie','')
            referer=self.headers.get('Referer','')
            if not cookie or not referer.startswith('http://10.0.2.2:8000/'):
                self.send_response(403);self.end_headers();return
            self.path=path.removeprefix('/protected')
        if path=='/protected/captions':
            if 'nova_test=ok' not in self.headers.get('Cookie','') or not self.headers.get('Referer','').startswith('http://10.0.2.2:8000/'):
                self.send_response(403);self.end_headers();return
            self.send_response(200);self.send_header('Content-Type','text/plain');self.end_headers();self.wfile.write('WEBVTT\n\n00:00:00.000 --> 00:02:00.000\nترجمة بدون امتداد\n'.encode());return
        if path=='/fragment.m4a':
            self.send_response(200);self.send_header('Content-Type','audio/mp4');self.end_headers();self.wfile.write(b'fixture-fragment');return
        if path=='/fake.m3u8':
            self.send_response(200);self.send_header('Content-Type','text/html');self.end_headers();self.wfile.write(b'<html>Not a stream</html>');return
        return super().do_GET()
os.chdir(os.path.dirname(__file__))
ThreadingHTTPServer(('0.0.0.0',8000),Handler).serve_forever()
