package app.nova.browser

import org.junit.Assert.*
import org.junit.Test
import org.libtorrent4j.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TorrentStreamTest {
    @Test fun streamsFromLocalSeeder() {
        org.junit.Assume.assumeTrue(System.getenv("NOVA_NATIVE_DIR") != null)
        val work = File.createTempFile("ttest", "").also { it.delete(); it.mkdirs() }
        val seedDir = File(work, "seed").also { it.mkdirs() }
        val data = ByteArray(6 * 1024 * 1024).also { java.util.Random(7).nextBytes(it) }
        val f = File(seedDir, "clip.mp4"); f.writeBytes(data)
        val sha = MessageDigest.getInstance("SHA-1").digest(data).joinToString("") { "%02x".format(it) }
        val tb = TorrentBuilder().path(f).pieceSize(256 * 1024)
        val tiBytes = tb.generate().entry().bencode()
        val torrentFile = File(work, "clip.torrent"); torrentFile.writeBytes(tiBytes)
        val seeder = SessionManager()
        val sp = SettingsPack().listenInterfaces("127.0.0.1:46123")
        seeder.applySettings(sp)
        seeder.start()
        try {
            seeder.download(TorrentInfo(torrentFile), seedDir)
            Thread.sleep(2500)
            val streamer = TorrentStreamer(File(work, "dl"))
            streamer.peerHint = "127.0.0.1" to 46123
            val ready = CountDownLatch(1); var url = ""; var err = ""
            streamer.start(torrentFile.toURI().toString(), object : TorrentStreamer.Listener {
                override fun onStatus(t: String) { println("status: " + t.replace('\n', ' ')) }
                override fun onReady(u: String, name: String) { url = u; ready.countDown() }
                override fun onError(e: String) { err = e; ready.countDown() }
            })
            assertTrue("not ready: $err", ready.await(90, TimeUnit.SECONDS))
            assertEquals("", err)
            // range read in the middle (seek) then the whole file
            val c = URL(url).openConnection() as HttpURLConnection
            c.setRequestProperty("Range", "bytes=3000000-3000999"); c.readTimeout = 60000
            assertEquals(206, c.responseCode)
            assertArrayEquals(data.copyOfRange(3000000, 3001000), c.inputStream.readBytes())
            val c2 = URL(url).openConnection() as HttpURLConnection; c2.readTimeout = 60000
            val all = c2.inputStream.readBytes()
            assertEquals(data.size, all.size)
            assertEquals(sha, MessageDigest.getInstance("SHA-1").digest(all).joinToString("") { "%02x".format(it) })
            streamer.stop()
        } finally { seeder.stop() }
    }
}
