package app.nova.browser

import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.*
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File

/** Native playback consumes the complete capture session, not a naked URL intent. */
class PlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private lateinit var pv: PlayerView
    private lateinit var status: TextView
    private lateinit var name: TextView
    private lateinit var actions: LinearLayout
    private var session: PlaybackSession? = null
    private var sessionId: String? = null
    private var sourceIndex = 0
    private var streamer: TorrentStreamer? = null
    private var extraSubs = mutableListOf<PlaybackSource>()
    private var resize = 0
    private var speed = 1f
    private val white = 0xFFF2F2F7.toInt()
    private val muted = 0xFFABAAB8.toInt()
    private val accent = 0xFFAB9FFF.toInt()
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun ar()=resources.configuration.locales[0].language=="ar"
    private fun tr(en:String, arabic:String)=if(ar()) arabic else en
    private val subPicker=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val displayName = contentResolver.query(it, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if(c.moveToFirst()) c.getString(0) else "" }.orEmpty()
            val mime = Sniffer.mimeForSub(displayName)
            extraSubs.add(PlaybackSource(url=it.toString(),kind="SUB",mime=mime,label=displayName.ifBlank{tr("Local subtitle","ترجمة محلية")}))
            play(sourceIndex,player?.currentPosition?:0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window,false)
        androidx.core.view.WindowInsetsControllerCompat(window,window.decorView).hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        sessionId=intent.getStringExtra("playbackSession")
        session=PlaybackSessions.get(sessionId)
        if(session==null) {
            val u=intent.getStringExtra("url")
            if(u.isNullOrBlank()) { finish(); return }
            session=PlaybackSession(title="Nova",pageUrl="",sources=listOf(PlaybackSource(url=u,kind=Sniffer.classify(u)?:"VIDEO")),
                subtitles=intent.getStringArrayListExtra("subs").orEmpty().map{PlaybackSource(url=it,kind="SUB",mime=Sniffer.mimeForSub(it))},contexts=emptyList())
        }
        val root=FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }
        pv=PlayerView(this).apply { controllerShowTimeoutMs=3500; setShowSubtitleButton(false) }
        root.addView(pv,FrameLayout.LayoutParams(-1,-1))
        val top=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(12),dp(16),dp(12),dp(8));setBackgroundColor(0x99000000.toInt()) }
        top.addView(button("‹") {finish()},LinearLayout.LayoutParams(dp(48),dp(44)))
        name=TextView(this).apply {text=session!!.title;setTextColor(white);textSize=15f;maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
        top.addView(name,LinearLayout.LayoutParams(0,dp(44),1f))
        top.addView(button("↻") { requestedOrientation=if(resources.configuration.orientation==2) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE },LinearLayout.LayoutParams(dp(48),dp(44)))
        root.addView(top,FrameLayout.LayoutParams(-1,-2,Gravity.TOP))
        actions=LinearLayout(this).apply {gravity=Gravity.CENTER;setPadding(dp(6),dp(4),dp(6),dp(8));setBackgroundColor(0xBB101018.toInt())}
        val labels=listOf(tr("Sources","المصادر"),tr("Subtitles","الترجمات"),tr("Quality","الجودة"),tr("More","المزيد"))
        listOf<()->Unit>({sources()},{subtitles()},{quality()},{more()}).forEachIndexed{i,f ->actions.addView(button(labels[i],f),LinearLayout.LayoutParams(0,dp(44),1f))}
        root.addView(actions,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
        pv.setPadding(0,dp(52),0,dp(60))
        status=TextView(this).apply {setTextColor(white);textSize=16f;gravity=Gravity.CENTER;setPadding(dp(24),dp(20),dp(24),dp(20));setBackgroundColor(0xCC15141D.toInt());visibility=View.GONE}
        root.addView(status,FrameLayout.LayoutParams(-1,-2,Gravity.CENTER))
        pv.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->top.visibility=v;actions.visibility=v })
        setContentView(root)
        sourceIndex=savedInstanceState?.getInt("source")?:0
        play(sourceIndex,savedInstanceState?.getLong("position")?:0)
    }
    private fun button(label:String,action:()->Unit)=TextView(this).apply {text=label;setTextColor(white);textSize=13f;gravity=Gravity.CENTER;setOnClickListener{action()}}
    private fun sheet(title:String, rows:List<Pair<String,()->Unit>>) {
        val d=BottomSheetDialog(this); val column=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(14),dp(20),dp(24));setBackgroundColor(0xFF17161F.toInt())}
        column.addView(TextView(this).apply{text=title;setTextColor(accent);textSize=21f;setPadding(0,dp(8),0,dp(18))})
        rows.forEach{(label,action)->column.addView(TextView(this).apply{text=label;setTextColor(white);textSize=16f;setPadding(dp(8),dp(16),dp(8),dp(16));setOnClickListener{d.dismiss();action()}},LinearLayout.LayoutParams(-1,-2))}
        d.setContentView(ScrollView(this).apply{addView(column)});d.show()
    }
    private fun sources() {
        sheet(tr("Playback sources","مصادر التشغيل"),session!!.sources.mapIndexed{i,s ->
            val host=runCatching{Uri.parse(s.url).host}.getOrNull().orEmpty()
            ((if(i==sourceIndex)"✓  " else "")+s.kind+" · "+host) to {play(i,player?.currentPosition?:0)}
        })
    }
    private fun quality() {
        val p=player?:return
        val rows=mutableListOf<Pair<String,()->Unit>>()
        rows.add(tr("Auto","تلقائي") to {p.trackSelectionParameters=p.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()})
        p.currentTracks.groups.filter{it.type==C.TRACK_TYPE_VIDEO}.forEach{g ->
            for(i in 0 until g.length) if(g.isTrackSupported(i)) {
                val f=g.getTrackFormat(i);val label=if(f.height>0)"${f.height}p" else tr("Video track ${i+1}","مسار فيديو ${i+1}")
                rows.add(label to {p.trackSelectionParameters=p.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup,i)).build()})
            }
        }
        sheet(tr("Available quality","الجودات المتاحة"),rows)
    }
    private fun subtitles() {
        val p=player?:return; val rows=mutableListOf<Pair<String,()->Unit>>()
        rows.add(tr("Off","إيقاف") to {p.trackSelectionParameters=p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build()})
        val entries=mutableListOf<Triple<String,androidx.media3.common.Tracks.Group,Int>>()
        p.currentTracks.groups.filter{it.type==C.TRACK_TYPE_TEXT}.forEach{g ->for(i in 0 until g.length) if(g.isTrackSupported(i)) {
            val f=g.getTrackFormat(i); entries.add(Triple(f.label?:f.language?:tr("Subtitle ${entries.size+1}","ترجمة ${entries.size+1}"),g,i))
        }}
        entries.sortedBy{if(it.first.contains("ar",true)||it.first.contains("عرب"))0 else 1}.forEach{(label,g,i)->rows.add(label to {p.trackSelectionParameters=p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,false).setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup,i)).build()})}
        rows.add(tr("Choose subtitle file","اختيار ملف ترجمة") to {subPicker.launch(arrayOf("*/*"))})
        sheet(tr("Subtitles · Arabic first","الترجمات · العربية أولاً"),rows)
    }
    private fun audio() {
        val p=player?:return;val rows=mutableListOf<Pair<String,()->Unit>>()
        p.currentTracks.groups.filter{it.type==C.TRACK_TYPE_AUDIO}.forEach{g->for(i in 0 until g.length)if(g.isTrackSupported(i)) {val f=g.getTrackFormat(i);rows.add((f.label?:f.language?:"Audio ${i+1}") to {p.trackSelectionParameters=p.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup,i)).build()})}}
        sheet(tr("Audio tracks","مسارات الصوت"),rows)
    }
    private fun more() {sheet(tr("Playback controls","أدوات التشغيل"),listOf(
        tr("Speed · ${speed}x","السرعة · ${speed}x") to {sheet(tr("Speed","السرعة"),listOf(.5f,.75f,1f,1.25f,1.5f,2f).map{v->"${v}x" to {speed=v;player?.setPlaybackSpeed(v)}})},
        tr("Aspect ratio","أبعاد الصورة") to {sheet(tr("Picture size","حجم الصورة"),listOf("Fit","Zoom","Fill").mapIndexed{i,l->l to {resize=i;pv.resizeMode=intArrayOf(AspectRatioFrameLayout.RESIZE_MODE_FIT,AspectRatioFrameLayout.RESIZE_MODE_ZOOM,AspectRatioFrameLayout.RESIZE_MODE_FILL)[i]}})},
        tr("Audio","الصوت") to {audio()},tr("Volume","مستوى الصوت") to {slider(false)},tr("Brightness","السطوع") to {slider(true)}))}
    private fun slider(bright:Boolean) {
        val audio=getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max=if(bright)100 else audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val slider=SeekBar(this).apply{this.max=max;progress=if(bright)50 else audio.getStreamVolume(AudioManager.STREAM_MUSIC);setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(s:SeekBar?,v:Int,user:Boolean){if(!user)return;if(bright){window.attributes=window.attributes.apply{screenBrightness=(v/100f).coerceAtLeast(.03f)}}else audio.setStreamVolume(AudioManager.STREAM_MUSIC,v,0)}
            override fun onStartTrackingTouch(s:SeekBar?){};override fun onStopTrackingTouch(s:SeekBar?){}
        })}
        AlertDialog.Builder(this).setTitle(if(bright)tr("Brightness","السطوع")else tr("Volume","مستوى الصوت")).setView(slider).setPositiveButton("OK",null).show()
    }
    private fun play(index:Int,position:Long) {
        val ss=session?:return; val source=ss.sources.getOrNull(index)?:return;sourceIndex=index
        if(source.kind=="TORRENT") {
            if(streamer!=null)return
            status.text=tr("Starting torrent…","جارٍ بدء التورنت…");status.visibility=View.VISIBLE
            streamer=TorrentStreamer(File(cacheDir,"torrent")).also{s->s.start(source.url,object:TorrentStreamer.Listener{
                override fun onStatus(t:String){runOnUiThread{status.text=t}}
                override fun onReady(url:String,name:String){runOnUiThread{session=ss.copy(sources=listOf(PlaybackSource(url=url,kind="VIDEO")));play(0,0)}}
                override fun onError(e:String){runOnUiThread{status.text=tr("Torrent failed. Try another source.","تعذر تشغيل التورنت. جرّب مصدراً آخر.")}}
            })};return
        }
        player?.release()
        val contexts=(ss.contexts+ss.sources.map{it.context}+ss.subtitles.map{it.context})
        val factory=ScopedMediaHttp.factory(contexts,source.context)
        val p=ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this,factory))).build()
        val item=MediaItem.Builder().setUri(source.url)
        val mime=source.mime.takeIf{it.isNotBlank()}?:when(source.kind){"HLS"->MimeTypes.APPLICATION_M3U8;"DASH"->MimeTypes.APPLICATION_MPD;else->null}
        mime?.let{item.setMimeType(it)}
        item.setSubtitleConfigurations((ss.subtitles+extraSubs).mapIndexed{i,s ->MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.url)).setMimeType(s.mime.ifBlank{Sniffer.mimeForSub(s.url)}).setLanguage(s.language).setLabel(s.label.ifBlank{if(s.language=="ar")"العربية" else "Subtitle ${i+1}"}).build()})
        status.text=tr("Opening source…","جارٍ فتح المصدر…");status.visibility=View.VISIBLE
        p.addListener(object:Player.Listener {
            override fun onPlaybackStateChanged(state:Int){if(state==Player.STATE_READY)status.visibility=View.GONE}
            override fun onPlayerError(e:PlaybackException){status.text=when(e.errorCode){PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS->tr("The server refused this request. Return to the page or choose another source.","رفض الخادم طلب التشغيل. ارجع للصفحة أو اختر مصدراً آخر.");PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED->tr("This response is not a valid stream. Choose another source or return to the page.","الاستجابة ليست بثاً صالحاً. اختر مصدراً آخر أو ارجع للصفحة.");else->tr("Couldn't play this source. Tap here to retry, or choose Sources.","تعذر تشغيل المصدر. اضغط هنا للمحاولة أو اختر المصادر.")};status.visibility=View.VISIBLE;status.setOnClickListener{play(sourceIndex,player?.currentPosition?:0)}}
        })
        p.trackSelectionParameters=p.trackSelectionParameters.buildUpon().setPreferredTextLanguage("ar").build()
        pv.player=p;player=p;p.setMediaItem(item.build(),position);p.setPlaybackSpeed(speed);p.prepare();p.playWhenReady=true
    }
    override fun onSaveInstanceState(out:Bundle){out.putInt("source",sourceIndex);out.putLong("position",player?.currentPosition?:0);super.onSaveInstanceState(out)}
    override fun onStop(){super.onStop();player?.pause()}
    override fun onDestroy(){player?.release();streamer?.stop();super.onDestroy()}
}
