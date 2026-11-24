package app.nova.browser

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.File

class PlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private lateinit var pv: PlayerView
    private lateinit var status: TextView
    private var streamer: TorrentStreamer? = null
    private var playUrl = ""
    private var title = ""
    private var isTorrent = false
    private val subs = mutableListOf<Pair<String, String>>() // uri to mime
    private val speeds = floatArrayOf(1f, 1.25f, 1.5f, 2f, 0.75f)
    private var speedIdx = 0
    private var resizeIdx = 0
    private val resizes = intArrayOf(AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_ZOOM, AspectRatioFrameLayout.RESIZE_MODE_FILL)

    private val pickSub = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
            } ?: uri.toString()
            subs.add(uri.toString() to Sniffer.mimeForSub(name))
            reload()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }
        pv = PlayerView(this).apply { setShowSubtitleButton(true); controllerShowTimeoutMs = 3500 }
        root.addView(pv, FrameLayout.LayoutParams(-1, -1))
        status = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt()); textSize = 14f; gravity = Gravity.CENTER
            setBackgroundColor(0x99000000.toInt()); setPadding(32, 24, 32, 24)
        }
        root.addView(status, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(16, 16, 16, 16) }
        fun chip(t: String, f: (TextView) -> Unit) = TextView(this).apply {
            text = t; setTextColor(0xFFFFFFFF.toInt()); textSize = 13f; setPadding(24, 12, 24, 12)
            setBackgroundColor(0x88000000.toInt()); setOnClickListener { f(this) }
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { rightMargin = 12 }
        }
        bar.addView(chip("‹ Back") { finish() })
        bar.addView(chip("Subtitle file") { pickSub.launch(arrayOf("*/*")) })
        bar.addView(chip("1x") { v -> speedIdx = (speedIdx + 1) % speeds.size; player?.setPlaybackSpeed(speeds[speedIdx]); v.text = "${speeds[speedIdx]}x" })
        bar.addView(chip("Fit") { v -> resizeIdx = (resizeIdx + 1) % resizes.size; pv.resizeMode = resizes[resizeIdx]; v.text = listOf("Fit", "Zoom", "Fill")[resizeIdx] })
        root.addView(bar, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START))
        setContentView(root)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        val url = intent.getStringExtra("url") ?: return finish()
        intent.getStringArrayListExtra("subs")?.forEach { subs.add(it to Sniffer.mimeForSub(it)) }
        isTorrent = url.startsWith("magnet:") || Sniffer.classify(url) == "TORRENT"
        if (isTorrent) startTorrent(url) else { playUrl = url; status.visibility = View.GONE; reload() }
    }

    private fun startTorrent(src: String) {
        status.text = "Starting torrent engine..."
        val s = TorrentStreamer(File(cacheDir, "torrent"))
        streamer = s
        s.start(src, object : TorrentStreamer.Listener {
            override fun onStatus(t: String) { runOnUiThread { if (status.visibility == View.VISIBLE) status.text = t } }
            override fun onReady(url: String, name: String) = runOnUiThread { playUrl = url; title = name; status.visibility = View.GONE; reload() }
            override fun onError(e: String) { runOnUiThread { status.visibility = View.VISIBLE; status.text = "Torrent error: $e" } }
        })
    }

    private fun reload() {
        val pos = player?.currentPosition ?: 0L
        val wasTorrentStats = isTorrent
        val ua = intent.getStringExtra("ua") ?: "Nova"
        val headers = HashMap<String, String>()
        intent.getStringExtra("referer")?.takeIf { it.startsWith("http") }?.let { headers["Referer"] = it; headers["Origin"] = Uri.parse(it).let { u -> "${u.scheme}://${u.host}" } }
        val http = DefaultHttpDataSource.Factory().setUserAgent(ua).setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(if (isTorrent) emptyMap() else headers)
            .setConnectTimeoutMs(15000).setReadTimeoutMs(if (isTorrent) 180000 else 20000)
        val dsf = androidx.media3.datasource.DefaultDataSource.Factory(this, http)
        val lc = DefaultLoadControl.Builder().setBufferDurationsMs(30000, 120000, 2000, 4000).build()
        player?.release()
        val p = ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(dsf)).setLoadControl(lc).build()
        val b = MediaItem.Builder().setUri(playUrl)
        when (Sniffer.classify(playUrl)) {
            "HLS" -> b.setMimeType(MimeTypes.APPLICATION_M3U8)
            "DASH" -> b.setMimeType(MimeTypes.APPLICATION_MPD)
        }
        b.setSubtitleConfigurations(subs.mapIndexed { i, (u, m) ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(u)).setMimeType(m).setLanguage("und")
                .setLabel("Subtitle ${i + 1}").setSelectionFlags(if (i == 0) C.SELECTION_FLAG_DEFAULT else 0).build()
        })
        p.setMediaItem(b.build(), pos)
        p.setPlaybackSpeed(speeds[speedIdx])
        p.addListener(object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                status.visibility = View.VISIBLE
                status.text = "Can't play this: ${e.errorCodeName}\n${e.cause?.message ?: e.message ?: ""}".take(300)
            }
            override fun onPlaybackStateChanged(s: Int) {
                if (s == Player.STATE_READY) status.visibility = View.GONE
                if (s == Player.STATE_BUFFERING && wasTorrentStats) { status.visibility = View.VISIBLE; status.text = streamer?.lastStatus ?: "Buffering..." }
            }
        })
        p.prepare(); p.playWhenReady = true
        pv.player = p; player = p
    }

    override fun onStop() { super.onStop(); player?.pause() }
    override fun onDestroy() { player?.release(); streamer?.stop(); super.onDestroy() }
}
