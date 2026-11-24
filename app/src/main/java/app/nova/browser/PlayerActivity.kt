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
    private lateinit var topBar: LinearLayout
    private var session: PlaybackSession? = null
    private var sessionId: String? = null
    private var sourceIndex = 0
    private var streamer: TorrentStreamer? = null
    private var torrentConsent=false
    private var playGeneration=0L
    private var torrentIndex:Int?=null
    private var resolvedTorrent:PlaybackSource?=null
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
        pv=layoutInflater.inflate(R.layout.nova_player_view,root,false) as PlayerView
        pv.apply {controllerShowTimeoutMs=6000;setShowSubtitleButton(false)}
        root.addView(pv,FrameLayout.LayoutParams(-1,-1))
        val top=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(12),dp(16),dp(12),dp(8));background=android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0xE610101A.toInt(),0x0010101A)) }
        top.addView(button("‹") {finish()}.apply{text="";setCompoundDrawablesWithIntrinsicBounds(R.drawable.nova_back,0,0,0);setPadding(dp(10),0,0,0)},LinearLayout.LayoutParams(dp(48),dp(44)))
        name=TextView(this).apply {text=session!!.title;setTextColor(white);textSize=19f;typeface=android.graphics.Typeface.create("sans-serif-medium",0);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
        top.addView(name,LinearLayout.LayoutParams(0,dp(44),1f))
        top.addView(button("↻") { requestedOrientation=if(resources.configuration.orientation==2) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }.apply{text="";contentDescription="Rotate";setCompoundDrawablesWithIntrinsicBounds(R.drawable.nova_rotate,0,0,0);setPadding(dp(10),0,0,0)},LinearLayout.LayoutParams(dp(48),dp(44)))
        topBar=top
        root.addView(top,FrameLayout.LayoutParams(-1,-2,Gravity.TOP))
        actions=LinearLayout(this).apply {gravity=Gravity.CENTER;setPadding(dp(62),dp(4),dp(8),dp(8));background=android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,intArrayOf(0xD510101A.toInt(),0x0010101A))}
        val labels=listOf(tr("Volume","الصوت"),tr("Sources","المصادر"),tr("Picture size","حجم الصورة"),tr("Subtitles","الترجمات"),tr("Quality","الجودة"),tr("More","المزيد"))
        val icons=intArrayOf(R.drawable.nova_volume,R.drawable.nova_sources,R.drawable.nova_fit,R.drawable.nova_captions,R.drawable.nova_quality,R.drawable.nova_more)
        val callbacks=listOf<()->Unit>({slider(false)},{sources()},{pictureSize()},{subtitles()},{quality()},{more()})
        labels.forEachIndexed{i,label->actions.addView(ImageButton(this).apply{contentDescription=label;setImageResource(icons[i]);setColorFilter(white);background=null;setPadding(dp(10),dp(10),dp(10),dp(10));scaleType=ImageView.ScaleType.FIT_CENTER;setOnClickListener{callbacks[i]()}},LinearLayout.LayoutParams(0,dp(48),1f))}
        root.addView(actions,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
        pv.setPadding(0,0,0,0)
        status=TextView(this).apply {setTextColor(white);textSize=16f;gravity=Gravity.CENTER;setPadding(dp(24),dp(20),dp(24),dp(20));setBackgroundColor(0xCC15141D.toInt());visibility=View.GONE}
        root.addView(status,FrameLayout.LayoutParams(-1,-2,Gravity.CENTER))
        pv.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
            val show=if(isInPictureInPictureMode)View.GONE else v
            top.visibility=show;actions.visibility=show
            positionSubtitles(v==View.VISIBLE)
        })
        setContentView(root)
        positionSubtitles(true)
        sourceIndex=savedInstanceState?.getInt("source")?:0
        play(sourceIndex,savedInstanceState?.getLong("position")?:0)
    }
    private fun button(label:String,action:()->Unit)=TextView(this).apply {text=label;setTextColor(white);textSize=13f;typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL);gravity=Gravity.CENTER;contentDescription=label;setOnClickListener{action()};val a=obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground));background=a.getDrawable(0);a.recycle()}
    private fun sheet(title:String, rows:List<Pair<String,()->Unit>>) {
        val d:android.app.Dialog=if(resources.configuration.orientation==2) android.app.Dialog(this) else BottomSheetDialog(this)
        val column=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(10),dp(16),dp(10));background=android.graphics.drawable.GradientDrawable().apply{setColor(0xD9101018.toInt());cornerRadius=dp(20).toFloat();setStroke(dp(1),0xFF35313F.toInt())}}
        if(resources.configuration.orientation!=2)column.addView(TextView(this).apply{text=title;setTextColor(white);textSize=18f;setPadding(0,dp(8),0,dp(18))})
        rows.forEach{(label,action)->column.addView(TextView(this).apply{text=label;contentDescription=label.substringBefore('\n');setTextColor(white);textSize=16f;setPadding(dp(8),dp(16),dp(8),dp(16));setOnClickListener{d.dismiss();action()}},LinearLayout.LayoutParams(-1,-2))}
        val handle=View(this).apply{background=android.graphics.drawable.GradientDrawable().apply{setColor(0xFF514A66.toInt());cornerRadius=dp(3).toFloat()}}
        if(resources.configuration.orientation!=2)column.addView(handle,0,LinearLayout.LayoutParams(dp(34),dp(4)).apply{gravity=Gravity.CENTER_HORIZONTAL;bottomMargin=dp(14)})
        d.setContentView(ScrollView(this).apply{addView(column)});d.show()
        if(resources.configuration.orientation==2){d.window?.setBackgroundDrawableResource(android.R.color.transparent);d.window?.setGravity(Gravity.END or Gravity.BOTTOM);d.window?.attributes=d.window?.attributes?.apply{y=dp(108);x=dp(16)};d.window?.setLayout(minOf(dp(330),resources.displayMetrics.widthPixels-dp(80)),minOf(dp(200),resources.displayMetrics.heightPixels-dp(124)))}
        d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.background=android.graphics.drawable.GradientDrawable().apply{setColor(0xE6101018.toInt());cornerRadii=floatArrayOf(dp(24).toFloat(),dp(24).toFloat(),dp(24).toFloat(),dp(24).toFloat(),0f,0f,0f,0f)}
    }
    private fun sources() {
        sheet(tr("Playback sources","مصادر التشغيل"),session!!.sources.mapIndexed{i,s ->
            val host=runCatching{Uri.parse(s.url).host}.getOrNull().orEmpty()
            ((if(i==sourceIndex)"✓  " else "")+s.kind+" · "+host+"\n"+if(s.networkObserved)tr("Observed request","طلب مرصود") else tr("Page hint, not verified","إشارة من الصفحة، لم تُتحقق")) to {play(i,player?.currentPosition?:0)}
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
        if(entries.isEmpty()) {
            rows.add(tr("Choose subtitle file","اختيار ملف ترجمة") to {subPicker.launch(arrayOf("*/*"))})
            sheet(tr("Subtitles","الترجمات"),rows);return
        }
        val dialog=android.app.Dialog(this)
        val outer=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(12),dp(18),dp(10));background=android.graphics.drawable.GradientDrawable().apply{setColor(0xF0101018.toInt());cornerRadius=dp(22).toFloat();setStroke(dp(1),0xFF35313F.toInt())}}
        outer.addView(TextView(this).apply{text=tr("Subtitles","الترجمات");textSize=18f;setTextColor(white);setPadding(0,0,0,dp(8))})
        val body=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        val languages=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val files=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),0,0,0)}
        body.addView(ScrollView(this).apply{addView(languages)},LinearLayout.LayoutParams(dp(108),-1))
        body.addView(ScrollView(this).apply{addView(files)},LinearLayout.LayoutParams(0,-1,1f))
        outer.addView(body,LinearLayout.LayoutParams(-1,0,1f))
        fun language(e:Triple<String,Tracks.Group,Int>):String {
            val f=e.second.getTrackFormat(e.third)
            return f.language?.takeIf{it!="und"}?.substringBefore('-') ?: if(e.first.contains("عرب")||e.first.contains("arab",true))"ar" else "und"
        }
        val groups=entries.groupBy{language(it)}.toSortedMap(compareBy<String>{if(it=="ar")"" else it})
        val chips=mutableListOf<Pair<String,TextView>>()
        fun display(code:String) = when(code){"ar"->"العربية";"en"->"English";"und"->tr("Other","أخرى");else->java.util.Locale.forLanguageTag(code).getDisplayLanguage(java.util.Locale.forLanguageTag(code)).ifBlank{code}}
        fun select(code:String){
            files.removeAllViews()
            chips.forEach{(k,v)->v.setTextColor(if(k==code)accent else muted);v.background=android.graphics.drawable.GradientDrawable().apply{setColor(if(k==code)0xFF30283F.toInt() else 0xFF1C1B24.toInt());cornerRadius=dp(12).toFloat()}}
            groups[code].orEmpty().forEach{(label,g,i)->
                val active=g.isTrackSelected(i)&&!p.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
                files.addView(TextView(this).apply{text=(if(active)"✓  " else "")+label;setTextColor(if(active)accent else white);textSize=14f;maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END;setPadding(dp(10),dp(15),dp(10),dp(15));setOnClickListener{p.trackSelectionParameters=p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,false).setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup,i)).build();dialog.dismiss()}},LinearLayout.LayoutParams(-1,-2))
            }
        }
        languages.addView(button(tr("Off","إيقاف")){p.trackSelectionParameters=p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build();dialog.dismiss()},LinearLayout.LayoutParams(-1,dp(36)))
        groups.keys.forEach{code->val chip=button(display(code)){select(code)};languages.addView(chip,LinearLayout.LayoutParams(-1,dp(36)).apply{bottomMargin=dp(4)});chips.add(code to chip)}
        outer.addView(button(tr("Choose subtitle file","اختيار ملف ترجمة")){dialog.dismiss();subPicker.launch(arrayOf("*/*"))},LinearLayout.LayoutParams(-1,dp(44)))
        dialog.setContentView(outer);dialog.window?.setBackgroundDrawableResource(android.R.color.transparent);dialog.show()
        val landscape=resources.configuration.orientation==2
        dialog.window?.setGravity(if(landscape)Gravity.END or Gravity.CENTER_VERTICAL else Gravity.CENTER)
        dialog.window?.setLayout(minOf(dp(440),resources.displayMetrics.widthPixels-dp(40)),minOf(dp(280),resources.displayMetrics.heightPixels-dp(48)))
        select(if(groups.containsKey("ar"))"ar" else groups.keys.first())
    }
    private fun audio() {
        val p=player?:return;val rows=mutableListOf<Pair<String,()->Unit>>()
        p.currentTracks.groups.filter{it.type==C.TRACK_TYPE_AUDIO}.forEach{g->for(i in 0 until g.length)if(g.isTrackSupported(i)) {val f=g.getTrackFormat(i);rows.add((f.label?:f.language?:"Audio ${i+1}") to {p.trackSelectionParameters=p.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup,i)).build()})}}
        sheet(tr("Audio tracks","مسارات الصوت"),rows)
    }
    private fun pictureSize(){sheet(tr("Picture size","حجم الصورة"),listOf(tr("Fit","ملاءمة"),tr("Zoom","اقتصاص"),tr("Fill","تمديد")).mapIndexed{i,l->l to {resize=i;pv.resizeMode=intArrayOf(AspectRatioFrameLayout.RESIZE_MODE_FIT,AspectRatioFrameLayout.RESIZE_MODE_ZOOM,AspectRatioFrameLayout.RESIZE_MODE_FILL)[i]}})}
    private fun enterMiniPlayer(){
        if(player==null)return
        val ok=runCatching{enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(16,9)).build())}.getOrDefault(false)
        if(!ok)Toast.makeText(this,tr("Picture-in-picture is unavailable or disabled","العرض المصغر غير متاح أو متوقف"),Toast.LENGTH_LONG).show()
    }
    override fun onPictureInPictureModeChanged(active:Boolean,config:android.content.res.Configuration){
        super.onPictureInPictureModeChanged(active,config)
        pv.useController=!active;topBar.visibility=if(active)View.GONE else View.VISIBLE;actions.visibility=if(active)View.GONE else View.VISIBLE
        positionSubtitles(false)
        if(!active)pv.showController()
    }
    private fun more() {sheet(tr("Playback controls","أدوات التشغيل"),listOf(
        tr("Picture-in-picture","عرض مصغر") to {enterMiniPlayer()},
        tr("Speed · ${speed}x","السرعة · ${speed}x") to {sheet(tr("Speed","السرعة"),listOf(.5f,.75f,1f,1.25f,1.5f,2f).map{v->"${v}x" to {speed=v;player?.setPlaybackSpeed(v)}})},
        tr("Aspect ratio","أبعاد الصورة") to {sheet(tr("Picture size","حجم الصورة"),listOf("Fit","Zoom","Fill").mapIndexed{i,l->l to {resize=i;pv.resizeMode=intArrayOf(AspectRatioFrameLayout.RESIZE_MODE_FIT,AspectRatioFrameLayout.RESIZE_MODE_ZOOM,AspectRatioFrameLayout.RESIZE_MODE_FILL)[i]}})},
        tr("Back 10 seconds","رجوع 10 ثوانٍ") to {player?.seekBack()},tr("Forward 10 seconds","تقديم 10 ثوانٍ") to {player?.seekForward()},
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
        val ss=session?:return; val original=ss.sources.getOrNull(index)?:return
        val source=if(torrentIndex==index&&streamer!=null)resolvedTorrent?:original else original
        if(source.kind=="TORRENT") {
            if(!torrentConsent){AlertDialog.Builder(this).setTitle(tr("Stream this torrent?","تشغيل هذا التورنت؟")).setMessage(tr("Peers can see your IP address. Nova downloads and shares pieces back at up to 64 KB/s while the player is open. Cached data is deleted on normal close. Only stream content you have the right to.","يمكن للمشاركين رؤية عنوان IP الخاص بك. ينزّل نوفا أجزاء الملف ويشاركها بسرعة رفع تصل إلى 64 كيلوبايت/ثانية أثناء فتح المشغل. تُحذف البيانات المؤقتة عند الإغلاق المعتاد. شغّل فقط محتوى لديك الحق في مشاهدته.")).setPositiveButton(tr("Stream","تشغيل")){_,_->torrentConsent=true;play(index,position)}.setNegativeButton(tr("Cancel","إلغاء")){_,_->if(player==null)finish()}.show();return}
            if(streamer!=null && torrentIndex==index)return
            player?.release();player=null;pv.player=null
            streamer?.stop();streamer=null;resolvedTorrent=null;torrentIndex=index
            val generation=++playGeneration
            sourceIndex=index
            status.text=tr("Starting torrent…","جارٍ بدء التورنت…");status.visibility=View.VISIBLE
            streamer=TorrentStreamer(File(cacheDir,"torrent/"+java.util.UUID.randomUUID())).also{s->s.start(source.url,object:TorrentStreamer.Listener{
                override fun onStatus(t:String){runOnUiThread{if(generation==playGeneration&&!isFinishing&&!isDestroyed)status.text=t}}
                override fun onReady(url:String,name:String){runOnUiThread{if(generation!=playGeneration||isFinishing||isDestroyed)return@runOnUiThread;resolvedTorrent=PlaybackSource(url=url,kind="VIDEO",label=name);play(index,0)}}
                override fun onError(e:String){runOnUiThread{if(generation!=playGeneration||isFinishing||isDestroyed)return@runOnUiThread;streamer?.stop();streamer=null;resolvedTorrent=null;torrentIndex=null;status.text=tr("Torrent failed. Try another source.","تعذر تشغيل التورنت. جرّب مصدراً آخر.")}}
            },fetchTorrent={u->ScopedMediaHttp.client(ss.contexts+ss.sources.map{it.context},source.context).newCall(okhttp3.Request.Builder().url(u).build()).execute().use{r->if(!r.isSuccessful)throw java.io.IOException("Torrent request refused");val input=r.body?.byteStream()?:throw java.io.IOException("Empty torrent");val bytes=readTorrentMetadata(input);require(bytes.size<=4*1024*1024){"Torrent metadata too large"};bytes}})};return
        }
        ++playGeneration
        if(torrentIndex!=null && torrentIndex!=index){streamer?.stop();streamer=null;resolvedTorrent=null;torrentIndex=null}
        sourceIndex=index
        player?.release()
        val contexts=(ss.contexts+ss.sources.map{it.context}+ss.subtitles.map{it.context})
        val factory=ScopedMediaHttp.factory(contexts,source.context)
        val p=ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this,factory))).setSeekBackIncrementMs(10000).setSeekForwardIncrementMs(10000).build()
        val item=MediaItem.Builder().setUri(source.url)
        val mime=when(source.kind){"HLS"->MimeTypes.APPLICATION_M3U8;"DASH"->MimeTypes.APPLICATION_MPD;else->source.mime.takeIf{it.startsWith("video/")||it.startsWith("audio/")}}
        mime?.let{item.setMimeType(it)}
        item.setSubtitleConfigurations((ss.subtitles+extraSubs).distinctBy{sourceKey(it.url)}.mapIndexed{i,s ->MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.url)).setMimeType(Sniffer.mimeForSub(s.url,s.mime)).setLanguage(s.language).setLabel(s.label.ifBlank{if(s.language=="ar")"العربية" else "Subtitle ${i+1}"}).build()})
        status.setOnClickListener(null)
        status.text=tr("Opening source…","جارٍ فتح المصدر…");status.visibility=View.VISIBLE
        p.addListener(object:Player.Listener {
            override fun onIsPlayingChanged(playing:Boolean){pv.controllerShowTimeoutMs=if(playing)6000 else 0}
            override fun onPlaybackStateChanged(state:Int){if(state==Player.STATE_READY){status.visibility=View.GONE;android.util.Log.i("NovaPlayback","Ready kind=${source.kind} videoGroups=${p.currentTracks.groups.count{it.type==C.TRACK_TYPE_VIDEO}} audioGroups=${p.currentTracks.groups.count{it.type==C.TRACK_TYPE_AUDIO}}") }}
            override fun onPlayerError(e:PlaybackException){status.text=when(e.errorCode){PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS->tr("The server refused this request. Return to the page or choose another source.","رفض الخادم طلب التشغيل. ارجع للصفحة أو اختر مصدراً آخر.");PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED->tr("This response is not a valid stream. Choose another source or return to the page.","الاستجابة ليست بثاً صالحاً. اختر مصدراً آخر أو ارجع للصفحة.");else->tr("Couldn't play this source. Tap here to retry, or choose Sources.","تعذر تشغيل المصدر. اضغط هنا للمحاولة أو اختر المصادر.")};status.visibility=View.VISIBLE;status.setOnClickListener{play(sourceIndex,player?.currentPosition?:0)}}
        })
        p.trackSelectionParameters=p.trackSelectionParameters.buildUpon().setPreferredTextLanguage("ar").build()
        pv.player=p;player=p;p.setMediaItem(item.build(),position);p.setPlaybackSpeed(speed);p.prepare();p.playWhenReady=true
    }
    private fun positionSubtitles(controls:Boolean){pv.subtitleView?.apply{setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,20f);setApplyEmbeddedFontSizes(false);setStyle(androidx.media3.ui.CaptionStyleCompat(android.graphics.Color.WHITE,0x99000000.toInt(),android.graphics.Color.TRANSPARENT,androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,android.graphics.Color.BLACK,null));setPadding(dp(12),0,dp(12),if(controls)dp(108) else dp(16));setBottomPaddingFraction(.04f)}}
    override fun onConfigurationChanged(config:android.content.res.Configuration){
        super.onConfigurationChanged(config)
        positionSubtitles(pv.isControllerFullyVisible)
    }
    override fun onSaveInstanceState(out:Bundle){out.putInt("source",sourceIndex);out.putLong("position",player?.currentPosition?:0);super.onSaveInstanceState(out)}
    override fun onStop(){super.onStop();if(!isInPictureInPictureMode)player?.pause()}
    override fun onDestroy(){++playGeneration;player?.release();streamer?.stop();super.onDestroy()}
}
