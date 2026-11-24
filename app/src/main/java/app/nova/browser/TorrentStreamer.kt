package app.nova.browser

import org.libtorrent4j.*
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL

/**
 * Streams the largest file of a torrent over a loopback HTTP server with Range support.
 * Pieces are downloaded in order; reads block until the needed piece is on disk.
 */
class TorrentStreamer(private val dir: File) {
    interface Listener {
        fun onStatus(t: String)
        fun onReady(url: String, name: String)
        fun onError(e: String)
    }

    @Volatile var lastStatus: String = ""
    /** Test hook: connect straight to a known peer (host, port). */
    var peerHint: kotlin.Pair<String, Int>? = null
    @Volatile private var stopped = false
    private val active = java.util.concurrent.atomic.AtomicInteger(0)
    private val pollers = java.util.concurrent.atomic.AtomicInteger(0)
    private var sm: SessionManager? = null
    private var server: ServerSocket? = null
    private var handle: TorrentHandle? = null
    private lateinit var ti: TorrentInfo
    private var fileIdx = 0
    private var fileSize = 0L
    private lateinit var file: File
    private var fileName = ""
    private val token = java.util.UUID.randomUUID().toString().replace("-", "")
    val streamPath get() = "/$token"

    private var worker: Thread? = null

    fun start(src: String, l: Listener, fetchTorrent:((String)->ByteArray)?=null) {
        worker = Thread {
            try {
                dir.deleteRecursively(); dir.mkdirs()
                val s = SessionManager()
                s.applySettings(SettingsPack().uploadRateLimit(64 * 1024))
                sm = s
                s.start()
                l.onStatus("Finding peers and metadata...")
                val bytes: ByteArray = if (src.startsWith("magnet:")) {
                    var data: ByteArray? = null
                    val deadline = System.currentTimeMillis() + 90_000
                    while (data == null && !stopped && System.currentTimeMillis() < deadline) {
                        data = s.fetchMagnet(src, 20, dir)
                        if (data == null) report(l, "Finding peers and metadata... (DHT nodes: ${s.stats().dhtNodes()})")
                    }
                    data ?: throw IllegalStateException("No metadata received in 90s (no peers found)")
                } else {
                    fetchTorrent?.invoke(src) ?: URL(src).openConnection().apply{connectTimeout=15000;readTimeout=30000}.getInputStream().use { input->val bytes=readTorrentMetadata(input);require(bytes.size<=4*1024*1024){"Torrent metadata too large"};bytes }
                }
                if (stopped) return@Thread
                ti = TorrentInfo.bdecode(bytes)
                val fs = ti.files()
                val media=(0 until fs.numFiles()).filter{fs.fileName(it).substringAfterLast('.',"").lowercase() in setOf("mp4","m4v","mkv","webm","mov","avi","mp3","m4a","flac","ogg")}
                require(media.isNotEmpty()){ "No playable media in torrent" }
                val best=media.maxBy{fs.fileSize(it)}
                fileIdx = best; fileSize = fs.fileSize(best); fileName = fs.fileName(best)
                file = File(dir, fs.filePath(best))
                require(file.canonicalPath.startsWith(dir.canonicalPath+File.separator)){"Invalid torrent file path"}
                s.download(ti, dir)
                var h: TorrentHandle? = null
                while (h == null && !stopped) { h = s.find(ti.infoHash()); Thread.sleep(100) }
                if(stopped)return@Thread
                handle = h!!
                h.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
                val pr = Array(fs.numFiles()) { if (it == best) Priority.DEFAULT else Priority.IGNORE }
                h.prioritizeFiles(pr)
                prioritizeEnds()
                peerHint?.let { ph -> h.swig().connect_peer(org.libtorrent4j.swig.tcp_endpoint(org.libtorrent4j.swig.address.from_string(ph.first, org.libtorrent4j.swig.error_code()), ph.second)) }
                startServer()
                // wait for a small head start, then hand the loopback URL to the player
                val needHead = headPieces()
                val bufferDeadline=System.currentTimeMillis()+120_000
                while (!stopped) {
                    val have = needHead.count { h.havePiece(it) }
                    val st = h.status()
                    report(l, "Buffering $fileName\n${have}/${needHead.size} start pieces · peers ${st.numPeers()} · ${st.downloadRate() / 1024} KB/s")
                    if (have == needHead.size) break
                    if(System.currentTimeMillis()>bufferDeadline)throw java.io.IOException("No playable data received within two minutes")
                    Thread.sleep(800)
                }
                if (stopped) return@Thread
                l.onReady("http://127.0.0.1:${server!!.localPort}$streamPath", fileName)
                while (!stopped) {
                    val st = h.status()
                    lastStatus = "Buffering... peers ${st.numPeers()} · ${st.downloadRate() / 1024} KB/s · ${(st.progress() * 100).toInt()}% downloaded"
                    Thread.sleep(1000)
                }
            } catch (e: Throwable) {
                if (!stopped) l.onError(e.message ?: e.javaClass.simpleName)
            }
        }.also { it.start() }
    }

    private fun report(l: Listener, t: String) { lastStatus = t; l.onStatus(t) }

    private fun pieceOf(offset: Long): Int = ti.mapFile(fileIdx, offset, 1).piece()

    private fun headPieces(): List<Int> {
        val first = pieceOf(0)
        val last = pieceOf(maxOf(0L, minOf(fileSize - 1, 3L * 1024 * 1024)))
        return (first..last).toList()
    }

    private fun prioritizeEnds() {
        val h = handle ?: return
        val first = pieceOf(0); val lastHead = pieceOf(minOf(fileSize - 1, 3L * 1024 * 1024))
        for (p in first..lastHead) h.piecePriority(p, Priority.TOP_PRIORITY)
        val tailStart = pieceOf(maxOf(0L, fileSize - 2L * 1024 * 1024)); val tailEnd = pieceOf(fileSize - 1)
        for (p in tailStart..tailEnd) h.piecePriority(p, Priority.TOP_PRIORITY)
    }

    private fun startServer() {
        val ss = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        server = ss
        Thread { while (!stopped) { try { val c = ss.accept(); Thread { active.incrementAndGet(); try { serve(c) } finally { active.decrementAndGet() } }.start() } catch (_: Exception) { break } } }.start()
    }

    private fun mime(): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "mp4", "m4v" -> "video/mp4"; "mkv" -> "video/x-matroska"; "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"; "mp3" -> "audio/mpeg"; "flac" -> "audio/flac"; "m4a" -> "audio/mp4"
        else -> "application/octet-stream"
    }

    private fun serve(c: Socket) {
        try {
            c.use { sock ->
                val inp = sock.getInputStream().bufferedReader()
                val reqLine = inp.readLine() ?: return
                var range: String? = null
                var host: String? = null
                while (true) { val line = inp.readLine() ?: break; if (line.isEmpty()) break
                    if (line.startsWith("Host:", true)) host = line.substringAfter(':').trim()
                    if (line.startsWith("Range:", true)) range = line.substringAfter(':').trim() }
                val path = reqLine.split(" ").getOrNull(1) ?: ""
                if (path != streamPath) { sock.getOutputStream().write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".toByteArray()); return }
                if (host != null && host != "127.0.0.1:${server?.localPort}") { sock.getOutputStream().write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\n\r\n".toByteArray()); return }
                var start = 0L; var end = fileSize - 1
                if (range != null && range.startsWith("bytes=")) {
                    val r = range.removePrefix("bytes=").split("-")
                    r[0].toLongOrNull()?.let { start = it }
                    r.getOrNull(1)?.toLongOrNull()?.let { end = minOf(it, fileSize - 1) }
                }
                if (start >= fileSize) { sock.getOutputStream().write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$fileSize\r\n\r\n".toByteArray()); return }
                val out = sock.getOutputStream().buffered(65536)
                val partial = range != null
                val hdr = StringBuilder(if (partial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
                hdr.append("Content-Type: ${mime()}\r\nAccept-Ranges: bytes\r\nContent-Length: ${end - start + 1}\r\n")
                if (partial) hdr.append("Content-Range: bytes $start-$end/$fileSize\r\n")
                hdr.append("Connection: close\r\n\r\n")
                if (reqLine.startsWith("HEAD")) { out.write(hdr.toString().toByteArray()); out.flush(); return }
                out.write(hdr.toString().toByteArray())
                var pos = start
                val buf = ByteArray(128 * 1024)
                var raf: RandomAccessFile? = null
                try {
                    while (pos <= end && !stopped) {
                        val len = minOf(buf.size.toLong(), end - pos + 1).toInt()
                        val firstP = pieceOf(pos); val lastP = pieceOf(pos + len - 1)
                        awaitPieces(firstP, lastP)
                        if (stopped) break
                        if (raf == null) raf = RandomAccessFile(file, "r")
                        raf.seek(pos)
                        val n = raf.read(buf, 0, len)
                        if (n <= 0) { Thread.sleep(200); continue }
                        out.write(buf, 0, n); pos += n
                    }
                    out.flush()
                } finally { raf?.close() }
            }
        } catch (_: Exception) { }
    }

    private fun awaitPieces(first: Int, last: Int) {
        val h = handle ?: return
        for (p in first..last) {
            if (h.havePiece(p)) continue
            // ask libtorrent to fetch the pieces right after the read position first
            for (q in p..minOf(p + 12, ti.numPieces() - 1)) if (!h.havePiece(q)) h.setPieceDeadline(q, (q - p) * 400 + 500)
            while (!h.havePiece(p) && !stopped) Thread.sleep(150)
        }
    }

    fun stop() {
        stopped = true
        try { server?.close() } catch (_: Exception) {}
        Thread {
            // let in-flight readers and the status loop leave native code before the session is torn down
            val end = System.currentTimeMillis() + 4000
            while ((active.get() > 0 || worker?.isAlive == true) && System.currentTimeMillis() < end) { try { Thread.sleep(50) } catch (_: Exception) {} }
            try { sm?.stop() } catch (_: Exception) {}
            try { Thread.sleep(500); dir.deleteRecursively() } catch (_: Exception) {}
        }.start()
    }
}

/** Bounded read compatible with Android26. */
internal fun readTorrentMetadata(input:java.io.InputStream):ByteArray {
 val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(16384)
 while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=4*1024*1024){"Torrent metadata too large"};out.write(buffer,0,n)}
 return out.toByteArray()
}
