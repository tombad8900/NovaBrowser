package app.nova.browser

import org.junit.Assert.*
import org.junit.Test

class SnifferTest {
    @Test fun classifies() {
        assertEquals("HLS", Sniffer.classify("https://cdn.x.com/a/master.m3u8?token=1"))
        assertEquals("DASH", Sniffer.classify("https://x.com/v.mpd"))
        assertEquals("VIDEO", Sniffer.classify("https://x.com/movie.MP4#t=3"))
        assertEquals("SUB", Sniffer.classify("https://x.com/en.vtt"))
        assertEquals("SUB", Sniffer.classify("https://x.com/en.srt?x=1"))
        assertEquals("TORRENT", Sniffer.classify("magnet:?xt=urn:btih:abc&dn=x"))
        assertEquals("TORRENT", Sniffer.classify("https://x.com/a.torrent"))
        assertNull(Sniffer.classify("https://x.com/index.html"))
        assertNull(Sniffer.classify("blob:https://x.com/123"))
    }
    @Test fun blocks() {
        assertTrue(Sniffer.isBlocked("ads.doubleclick.net"))
        assertFalse(Sniffer.isBlocked("example.com"))
    }
    @Test fun subMime() {
        assertEquals("application/x-subrip", Sniffer.mimeForSub("http://a/b.srt"))
        assertEquals("text/vtt", Sniffer.mimeForSub("http://a/b.vtt"))
    }
}
