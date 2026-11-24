package app.nova.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.URLUtil
import org.json.JSONObject
import org.mozilla.geckoview.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.json.JSONArray
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    class Tab(val session: GeckoSession) {
        val found = LinkedHashMap<String, Found>()
        val rejected=linkedSetOf<String>()
        val fragments=linkedSetOf<String>()
        val subtitleHints=LinkedHashMap<String,Pair<String,String>>()
        val resources = LinkedHashMap<String, PlaybackSource>()
        val contexts = LinkedHashMap<String, RequestContext>()
        var title = ""
        var url = ""
        var desktop = false
        var generation = 0L
        var loadStarted = 0L
        var playbackSessionId:String?=null
        var media:MediaSession?=null
        var savedState:GeckoSession.SessionState?=null
        var recovering=false
        var canBack = false
        var canFwd = false
    }

    private val bg = Color.parseColor("#0F1115")
    private val surface = Color.parseColor("#1A1D24")
    private val accent = Color.parseColor("#8B7BFF")
    private val textColor = Color.parseColor("#E8EAF0")
    private val dim = Color.parseColor("#8A90A0")

    private lateinit var holder: FrameLayout
    private lateinit var urlBar: EditText
    private lateinit var progress: ProgressBar
    private lateinit var playAction: TextView
    private lateinit var tabBtn: TextView
    private lateinit var fullscreenHost: FrameLayout
    private val tabs = mutableListOf<Tab>()
    private val detectorAttachments=SessionAttachments<Tab>()
    private var cur: Tab? = null
    private lateinit var store: Store
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var geckoView: GeckoView
    private var inFullscreen = false
    private var lastHeartbeat=0L
    private var maxUiDelay=0L
    private var heartbeats=0
    private val heartbeat=object:Runnable {
        override fun run(){
            val now=android.os.SystemClock.elapsedRealtime()
            if(lastHeartbeat>0)maxUiDelay=maxOf(maxUiDelay,(now-lastHeartbeat-500).coerceAtLeast(0))
            lastHeartbeat=now
            if(++heartbeats%20==0){android.util.Log.i("NovaPerf","UiHeartbeat maxDelayMs=$maxUiDelay tabs=${tabs.size}");maxUiDelay=0}
            ui.postDelayed(this,500)
        }
    }
    private fun tr(en: String, ar: String) = if (resources.configuration.locales[0].language == "ar") ar else en
    private fun protection(t: Tab) {
        val host = Uri.parse(t.url).host ?: return
        sheet { l, d ->
            title(l, tr("Site protection", "حماية الموقع"))
            item(l, "uBlock Origin · "+adBlockState(), tr("Open ad filtering controls for this page", "فتح أدوات حجب الإعلانات لهذه الصفحة"), {
                d.dismiss(); if(!AdBlock.show(t.session)) Toast.makeText(this,tr("Ad filter is starting. Try again shortly.","جارٍ بدء حجب الإعلانات. حاول بعد قليل."),Toast.LENGTH_LONG).show()
            })
            item(l, host, null, {})
            item(l, if(store.protection(host)) tr("Protection is on", "الحماية مفعلة") else tr("Protection is off for this site", "الحماية متوقفة لهذا الموقع"),
                tr("Tap to change and reload. Some sites need exceptions.", "اضغط للتغيير وإعادة التحميل. بعض المواقع تحتاج استثناءً."), {
                    d.dismiss(); t.session.settings.useTrackingProtection=store.toggleProtection(host);t.session.reload()
                })
            item(l, tr("Tracking protection for this site", "حماية التتبع لهذا الموقع"),
                tr("Cookie isolation and GPC remain on globally. Use uBlock controls above for ad-filter exceptions.", "عزل ملفات الارتباط وإشارة الخصوصية يظلان مفعلين. استخدم أدوات uBlock أعلاه لاستثناءات حجب الإعلانات."), {})
        }
    }

    private fun adBlockState() = when(AdBlock.state.lowercase()) {
        "on" -> tr("on","مفعّل")
        "off" -> tr("off","متوقف")
        "starting", "installing" -> tr("starting","جارٍ البدء")
        "unavailable" -> tr("unavailable","غير متاح")
        else -> tr("loading","جارٍ التحميل")
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, r: Int = 14) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        Engine.get(this)
        Engine.whenReady { ext -> if(!isDestroyed&&!isFinishing)detectorAttachments.ready{attachExt(it,ext)} }
        buildUi()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goBack() }
        })
        val data = intent?.dataString
        if (data != null) handleExternal(data) else newTab(null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let { handleExternal(it) }
    }

    private fun handleExternal(u: String) {
        if (u.startsWith("magnet:")) { openPlayer(u, emptyList(), ""); if (tabs.isEmpty()) newTab(null) }
        else newTab(u)
    }

    private fun goBack() {
        if (inFullscreen) { cur?.session?.exitFullScreen(); return }
        val t = cur ?: return finish()
        if (t.canBack) t.session.goBack()
        else if (tabs.size > 1) closeTab(t)
        else finish()
    }

    private fun btn(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label; setTextColor(textColor); textSize = 12f; gravity = Gravity.CENTER
        setLineSpacing(dp(3).toFloat(),1f);background=rounded(surface,12)
        contentDescription=label.substringAfter('\n',label)
        minHeight=dp(60);setPadding(dp(2),dp(6),dp(2),dp(6))
        layoutParams = LinearLayout.LayoutParams(0, dp(60), 1f).apply{setMargins(dp(2),0,dp(2),0)}
        setOnClickListener { onClick() }
    }

    @SuppressLint("SetTextI18n")
    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(bg) }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progressTintList = android.content.res.ColorStateList.valueOf(accent)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)); visibility = View.INVISIBLE
        }
        holder = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f); setBackgroundColor(Color.WHITE) }
        geckoView = GeckoView(this)
        holder.addView(geckoView, FrameLayout.LayoutParams(-1, -1))

        urlBar = EditText(this).apply {
            hint = tr("Search or enter address", "ابحث أو أدخل عنواناً"); setHintTextColor(dim); setTextColor(textColor); textSize = 15f
            setSingleLine(); background = rounded(surface, 22)
            setPadding(dp(16), 0, dp(16), 0)
            imeOptions = EditorInfo.IME_ACTION_GO
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            layoutParams = LinearLayout.LayoutParams(-1, dp(44))
            setOnEditorActionListener { _, a, e ->
                if (a == EditorInfo.IME_ACTION_GO || e?.keyCode == KeyEvent.KEYCODE_ENTER) { go(text.toString()); true } else false
            }
            setOnFocusChangeListener { _, f -> if (f) post { selectAll() } }
        }
        val barWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#14171D"))
            setPadding(dp(8), dp(10), dp(8), dp(14))
        }
        barWrap.addView(urlBar)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tabBtn = btn("1\n"+tr("Tabs","التبويبات")) { showTabs() }
        row.addView(btn("‹\n"+tr("Back","رجوع")) { cur?.let { if (it.canBack) it.session.goBack() } })
        row.addView(btn("›\n"+tr("Next","التالي")) { cur?.let { if (it.canFwd) it.session.goForward() } })
        row.addView(btn("⟳\n"+tr("Reload","تحديث")) { cur?.session?.reload() })
        row.addView(btn("◇\n"+tr("Protect","الحماية")) { cur?.let{protection(it)} })
        row.addView(tabBtn)
        row.addView(btn("⋮\n"+tr("Menu","القائمة")) { showMenu() })
        playAction=TextView(this).apply {
            text=tr("Play in Nova", "تشغيل في نوفا");setTextColor(accent);textSize=14f;gravity=Gravity.CENTER
            setPadding(0,dp(10),0,dp(10));setOnClickListener{showSniffer()}
        }
        barWrap.addView(playAction)
        barWrap.addView(row)

        col.addView(progress); col.addView(holder); col.addView(barWrap)
        root.addView(col)
        fullscreenHost = FrameLayout(this).apply { visibility = View.GONE; setBackgroundColor(Color.BLACK) }
        root.addView(fullscreenHost, FrameLayout.LayoutParams(-1, -1))
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window,false)
        window.navigationBarColor=Color.parseColor("#14171D")
        setContentView(root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val i = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.ime())
            v.setPadding(i.left, i.top, i.right, i.bottom); insets
        }
    }

    private fun go(input: String) {
        val t = input.trim()
        if (t.isEmpty()) return
        urlBar.clearFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(urlBar.windowToken, 0)
        when {
            t.startsWith("magnet:") -> openPlayer(t, emptyList(), "")
            t.startsWith("http://") || t.startsWith("https://") -> load(t)
            t.contains(" ") || !t.contains(".") -> load(searchUrl(t))
            else -> load("https://$t")
        }
    }

    private fun load(u: String) {
        val t = cur ?: return
        t.session.load(GeckoSession.Loader().uri(u))
    }

    // ---------- tabs ----------
    private fun attachExt(tab: Tab, ext: WebExtension) {
        tab.session.webExtensionController.setMessageDelegate(ext, object : WebExtension.MessageDelegate {
            override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
                if (nativeApp != "nova" || sender.webExtension.id != Engine.EXT_ID || message !is JSONObject) return null
                when (message.optString("t")) {
                    "resourceRef" -> {
                        if (message.optString("page").substringBefore('#') != tab.url.substringBefore('#')) return null
                        val resource = Engine.observations.remove(message.optString("token")) ?: return null
                        val generation = tab.generation
                        val u = resource.optString("url")
                        val ct = resource.optString("ct")
                        val h = resource.optJSONObject("headers") ?: JSONObject()
                        val headers = h.keys().asSequence().associateWith { h.optString(it) }
                        val context = RequestContext(u, headers, resource.optLong("time", System.currentTimeMillis()))
                        val kind = if(resource.optBoolean("rejected")) "REJECTED" else if (resource.optBoolean("segment")) "CONTEXT" else if(resource.optBoolean("track")) "SUB" else Sniffer.classifyMime(u, ct) ?: "CONTEXT"
                        ui.post {
                            if (generation != tab.generation) return@post
                            if(kind=="REJECTED") {tab.rejected.add(sourceKey(u));trimPageState(tab);tab.found.remove(sourceKey(u));tab.resources.remove(sourceKey(u));if(cur===tab)refreshCounts();return@post}
                            if (tab.contexts.size >= 300) tab.contexts.remove(tab.contexts.keys.first())
                            tab.contexts[sourceKey(u)] = context
                            if (resource.optBoolean("segment")) {val key=sourceKey(u);tab.fragments.add(key);trimPageState(tab);tab.found.remove(key);tab.resources.remove(key);if(cur===tab)refreshCounts();return@post}
                            if (kind == "CONTEXT") {
                                tab.subtitleHints[sourceKey(u)]?.let{(lang,label)->if(sourceKey(u) !in tab.rejected){tab.resources[sourceKey(u)]=subtitleFromTrack(u,lang,label,null,context);addFound(tab,Found(u,"SUB","track+network"));trimPageState(tab)}}
                                return@post
                            }
                            val arabic = Regex("(?i)([/_.?&=-])(ar|ara|arabic)([/_.?&=-]|$)").containsMatchIn(u)
                            val hint=tab.subtitleHints[sourceKey(u)]
                            val source = PlaybackSource(url=u,kind=kind,mime=ct,context=context,evidence="network",language=hint?.first?:if(arabic)"ar" else "und",label=hint?.second?:if(arabic)"العربية" else "")
                            tab.resources[sourceKey(u)] = source
                            trimPageState(tab)
                            addFound(tab, Found(u, kind, "network"))
                        }
                    }
                    "net" -> {
                        val u = message.optString("url")
                        Sniffer.classifyMime(u, message.optString("ct"))?.let { k -> ui.post { addFound(tab, Found(u, k, "network")) } }
                    }
                    "scan" -> {
                        if(message.optString("page").substringBefore('#') != tab.url.substringBefore('#')) return null
                        val generation=tab.generation
                        val arr = message.optJSONArray("urls")
                        if (arr != null) {
                            val f = Sniffer.foundFromUrls((0 until arr.length()).map { arr.getString(it) }, "page")
                            val tracks=message.optJSONArray("tracks")
                            val hints=(0 until (tracks?.length()?:0)).mapNotNull{i->tracks?.optJSONObject(i)?.let{o->Triple(o.optString("url"),o.optString("language","und"),o.optString("label"))}}
                            ui.post { if(generation==tab.generation) {
                                hints.forEach{(u,lang,label)->if(u.startsWith("http://")||u.startsWith("https://")){
                                    val key=sourceKey(u);tab.subtitleHints[key]=lang to label
                                    if(key !in tab.rejected && key !in tab.fragments){
                                        val previous=tab.resources[key]
                                        tab.resources[key]=subtitleFromTrack(u,lang,label,previous,tab.contexts[key])
                                        addFound(tab,Found(u,"SUB","track"))
                                    }
                                }}
                                trimPageState(tab)
                                f.forEach { addFound(tab, it) }
                            }}
                        }
                    }
                }
                return null
            }
        }, "nova")
    }

    private fun newTab(url: String?, switchTo: Boolean = true, session: GeckoSession? = null): Tab {
        if (tabs.size >= 8) {
            Toast.makeText(this,tr("Close a tab before opening another (8 tab limit)","أغلق علامة تبويب قبل فتح أخرى (الحد ٨)"),Toast.LENGTH_LONG).show()
            session?.close()
            return cur ?: tabs.first()
        }
        val s = session ?: GeckoSession(GeckoSessionSettings.Builder().usePrivateMode(false).useTrackingProtection(true).suspendMediaWhenInactive(true).build())
        val tab = Tab(s)
        s.mediaSessionDelegate=object:MediaSession.Delegate {
            override fun onActivated(session:GeckoSession,mediaSession:MediaSession){tab.media=mediaSession}
            override fun onDeactivated(session:GeckoSession,mediaSession:MediaSession){tab.media=null}
        }
        s.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onSessionStateChange(session:GeckoSession,state:GeckoSession.SessionState){tab.savedState=state}

            override fun onPageStart(session: GeckoSession, u: String) {
                tab.loadStarted = android.os.SystemClock.elapsedRealtime()
                clearPageCaptures(tab)
                tab.url = u
                if (cur === tab) { refreshCounts(); if (!urlBar.hasFocus()) urlBar.setText(shown(u)) }
            }
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                if(success)tab.recovering=false
                android.util.Log.i("NovaPerf", "Page stop success=$success elapsedMs=${android.os.SystemClock.elapsedRealtime()-tab.loadStarted} tabs=${tabs.size} captures=${tab.found.size}")
                if (success && tab.url.startsWith("http")) store.addHistory(tab.title, tab.url)
                if (!success && cur === tab) Toast.makeText(this@MainActivity,tr("Page couldn't finish loading. Reload or check site protection.","تعذر إكمال تحميل الصفحة. أعد التحميل أو راجع حماية الموقع."),Toast.LENGTH_LONG).show()
            }
            override fun onProgressChange(session: GeckoSession, p: Int) {
                if (cur === tab) { progress.visibility = if (p in 1..99) View.VISIBLE else View.INVISIBLE; progress.progress = p }
            }
        }
        s.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(session: GeckoSession, u: String?, perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
                val next=u?:""
                if(next!=tab.url)clearPageCaptures(tab)
                tab.url = next
                Uri.parse(tab.url).host?.let { host -> session.settings.useTrackingProtection=store.protection(host) }
                if (cur === tab && !urlBar.hasFocus()) urlBar.setText(shown(tab.url))
            }
            override fun onCanGoBack(session: GeckoSession, v: Boolean) { tab.canBack = v }
            override fun onCanGoForward(session: GeckoSession, v: Boolean) { tab.canFwd = v }
            override fun onLoadRequest(session: GeckoSession, r: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
                val u = r.uri
                if (u.startsWith("magnet:")) { ui.post { openPlayer(u, emptyList(), "") }; return GeckoResult.deny() }
                if (u.startsWith("http://") || u.startsWith("https://")) {
                    Uri.parse(u).host?.let { session.settings.useTrackingProtection=store.protection(it) }
                    if(r.target==GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW && !r.hasUserGesture) return GeckoResult.deny()
                    return GeckoResult.allow()
                }
                if (u.startsWith("about:") || u.startsWith("data:") || u.startsWith("blob:")) return GeckoResult.allow()
                // only hand a few safe schemes to other apps, and only after a real tap
                val sch = Uri.parse(u).scheme ?: ""
                if (r.hasUserGesture && sch in setOf("mailto", "tel", "sms")) {
                    ui.post { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) } catch (_: Exception) {} }
                }
                return GeckoResult.deny()
            }
            override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? {
                val html = "<html><meta name='viewport' content='width=device-width,initial-scale=1'><body style='font:18px sans-serif;background:#15141d;color:#eee;padding:36px'><h2>"+tr("Page unavailable","تعذر فتح الصفحة")+"</h2><p>"+tr("Check your connection or reload. Site protection can be adjusted from the shield button.","راجع الاتصال أو أعد التحميل. يمكنك تغيير حماية الموقع من زر الدرع.")+"</p></body></html>"
                return GeckoResult.fromValue("data:text/html;charset=utf-8;base64,"+android.util.Base64.encodeToString(html.toByteArray(Charsets.UTF_8),android.util.Base64.NO_WRAP))
            }
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                if(tabs.size >= 8) return GeckoResult.fromValue(null)
                val ns = GeckoSession(GeckoSessionSettings.Builder().usePrivateMode(false).useTrackingProtection(true).suspendMediaWhenInactive(true).build())
                ui.post { newTab(null, true, ns) }
                return GeckoResult.fromValue(ns)
            }
        }
        s.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onCrash(session:GeckoSession){recoverTab(tab)}
            override fun onKill(session:GeckoSession){recoverTab(tab)}
            override fun onTitleChange(session: GeckoSession, t: String?) { tab.title = t ?: "" }
            override fun onFullScreen(session: GeckoSession, full: Boolean) {
                inFullscreen = full
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (full) View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY else View.SYSTEM_UI_FLAG_VISIBLE
            }
            override fun onCloseRequest(session: GeckoSession) { ui.post { closeTab(tab) } }
            override fun onExternalResponse(session: GeckoSession, resp: WebResponse) {
                val name = URLUtil.guessFileName(resp.uri, null, resp.headers["content-type"])
                try {
                    val req = DownloadManager.Request(Uri.parse(resp.uri))
                    req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                    (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                    ui.post { Toast.makeText(this@MainActivity, tr("Downloading $name","جارٍ تنزيل $name"), Toast.LENGTH_SHORT).show() }
                } catch (_: Exception) {}
            }
        }
        s.promptDelegate = Prompts(this)
        // Gecko owns opening sessions returned by onNewSession. Never race it by opening here.
        if (session == null && !s.isOpen) s.open(Engine.get(this))
        AdBlock.attach(s) { popup ->
            val dialog=android.app.Dialog(this)
            val view=GeckoView(this)
            popup.open(Engine.get(this));view.setSession(popup)
            dialog.setContentView(view);dialog.window?.setLayout(-1,dp(520))
            dialog.setOnDismissListener{view.releaseSession();popup.close()};dialog.show()
            dialog.window?.setLayout(-1,dp(520))
        }
        tabs.add(tab)
        detectorAttachments.register(tab)
        if (switchTo) switchTo(tab)
        if (session == null) {
            if (url != null) s.load(GeckoSession.Loader().uri(url))
            else s.load(GeckoSession.Loader().uri("data:text/html;charset=utf-8;base64," + android.util.Base64.encodeToString(homeHtml().toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)).flags(GeckoSession.LOAD_FLAGS_FORCE_ALLOW_DATA_URI))
        }
        return tab
    }

    private fun shown(u: String) = if (u.startsWith("data:") || u.startsWith("about:") || u.contains("nova.home")) "" else u

    private fun switchTo(t: Tab) {
        cur?.session?.let{it.setPriorityHint(GeckoSession.PRIORITY_DEFAULT);it.setActive(false);Engine.get(this).webExtensionController.setTabActive(it,false)}
        geckoView.releaseSession()
        geckoView.setSession(t.session)
        t.session.setPriorityHint(GeckoSession.PRIORITY_HIGH)
        t.session.setActive(true)
        Engine.get(this).webExtensionController.setTabActive(t.session,true)
        cur = t
        urlBar.setText(shown(t.url))
        refreshCounts()
    }

    private fun recoverTab(tab:Tab){
        if(isFinishing||isDestroyed||tab !in tabs||tab.recovering)return
        tab.recovering=true
        tab.found.clear();tab.resources.clear();tab.contexts.clear();tab.rejected.clear();tab.fragments.clear();tab.generation++
        try{
            if(!tab.session.isOpen)tab.session.open(Engine.get(this))
            tab.savedState?.let{tab.session.restoreState(it)}?:tab.url.takeIf{it.startsWith("http")}?.let{tab.session.loadUri(it)}
            if(cur===tab){geckoView.releaseSession();geckoView.setSession(tab.session);tab.session.setActive(true);refreshCounts();Toast.makeText(this,tr("This tab was reloaded after its process stopped.","أُعيد تحميل علامة التبويب بعد توقف عمليتها."),Toast.LENGTH_LONG).show()}
            else tab.session.setActive(false)
        }catch(_:Exception){tab.recovering=false;android.util.Log.w("NovaEngine","Tab recovery could not finish")}
    }

    private fun clearPageCaptures(t:Tab){
        t.generation++
        t.found.clear();t.resources.clear();t.contexts.clear();t.rejected.clear();t.fragments.clear();t.subtitleHints.clear()
        PlaybackSessions.remove(t.playbackSessionId);t.playbackSessionId=null
        if(cur===t)refreshCounts()
    }

    private fun closeTab(t: Tab) {
        clearPageCaptures(t)
        val i = tabs.indexOf(t)
        detectorAttachments.unregister(t)
        tabs.remove(t)
        if (cur === t) geckoView.releaseSession()
        t.session.close()
        if (tabs.isEmpty()) newTab(null) else if (cur === t) switchTo(tabs[maxOf(0, i - 1)])
    }

    private fun refreshCounts() {
        tabBtn.text = tabs.size.toString()+"\n"+tr("Tabs","التبويبات")
        val ready=cur?.found?.values?.any{it.kind!="SUB"}==true
        playAction.alpha=if(ready)1f else .5f
        playAction.background=if(ready)rounded(0xFF252035.toInt(),14) else null
    }

    private fun trimPageState(t:Tab){
        while(t.rejected.size>600)t.rejected.remove(t.rejected.first())
        while(t.fragments.size>600)t.fragments.remove(t.fragments.first())
        while(t.subtitleHints.size>300)t.subtitleHints.remove(t.subtitleHints.keys.first())
        while(t.resources.size>300)t.resources.remove(t.resources.keys.first())
    }
    private fun addFound(t: Tab, f: Found) {
        val key=sourceKey(f.url)
        if(key in t.rejected || key in t.fragments || t.found.containsKey(key)) return
        if (t.found.size > 300) return
        t.found[key] = f
        if (cur === t) refreshCounts()
    }

    // ---------- sheets ----------
    private fun sheet(build: (LinearLayout, BottomSheetDialog) -> Unit) {
        val d = BottomSheetDialog(this)
        val sv = ScrollView(this)
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(24)); setBackgroundColor(Color.parseColor("#14171D")) }
        sv.addView(l); build(l, d); d.setContentView(sv); d.show()
    }

    private fun title(l: LinearLayout, s: String) = l.addView(TextView(this).apply {
        text = s; setTextColor(textColor); textSize = 18f; setPadding(0, 0, 0, dp(10))
    })

    private fun item(l: LinearLayout, main: String, sub: String?, onClick: () -> Unit, onLong: (() -> Unit)? = null) {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = rounded(surface); setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
            setOnClickListener { onClick() }
            if (onLong != null) setOnLongClickListener { onLong(); true }
        }
        c.addView(TextView(this).apply { text = main; setTextColor(textColor); textSize = 15f; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
        if (sub != null) c.addView(TextView(this).apply { text = sub; setTextColor(dim); textSize = 12f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE })
        l.addView(c)
    }

    private fun showSniffer() {
        val t = cur ?: return
        val candidates = t.found.values.filter { it.kind != "SUB" }
        if (candidates.isEmpty()) {
            Toast.makeText(this, tr("Start the video on the page, then tap Play in Nova","ابدأ الفيديو في الصفحة ثم اختر تشغيل في نوفا"), Toast.LENGTH_LONG).show()
            return
        }
        val all = t.found.values.map { f -> t.resources[sourceKey(f.url)] ?: PlaybackSource(url=f.url,kind=f.kind,language=t.subtitleHints[sourceKey(f.url)]?.first?:"und",label=t.subtitleHints[sourceKey(f.url)]?.second?:"") }
        val ps = PlaybackSession(title=t.title.ifBlank { "Nova" }, pageUrl=t.url,
            sources=all.filter { it.kind != "SUB" },
            subtitles=all.filter { it.kind == "SUB" }, contexts=t.contexts.values.toList())
        t.media?.pause()
        PlaybackSessions.remove(t.playbackSessionId)
        val id=PlaybackSessions.put(ps);t.playbackSessionId=id
        startActivity(Intent(this, PlayerActivity::class.java).putExtra("playbackSession",id).putExtra("testHoldControls",intent.getBooleanExtra("testHoldControls",false)))
    }

    private fun copy(s: String) {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("link", s))
    }

    private fun showTabs() {
        sheet { l, d ->
            title(l, tr("Tabs (${tabs.size})","علامات التبويب (${tabs.size})"))
            tabs.toList().forEach { t ->
                item(l, (if (t === cur) "● " else "") + t.title.ifBlank { t.url.ifBlank { tr("New tab","علامة تبويب جديدة") } }, t.url,
                    { d.dismiss(); switchTo(t) }, { d.dismiss(); closeTab(t) })
            }
            item(l, tr("+ New tab","+ علامة تبويب جديدة"), null, { d.dismiss(); newTab(null) })
            item(l, tr("Long press a tab to close it","اضغط مطولاً على علامة تبويب لإغلاقها"), null, {})
        }
    }

    private fun showMenu() {
        val t = cur ?: return
        sheet { l, d ->
            val bm = store.isBookmarked(t.url)
            item(l, tr("New tab","علامة تبويب جديدة"), null, { d.dismiss(); newTab(null) })
            item(l, if (bm) tr("Remove bookmark","إزالة من المحفوظات") else tr("Bookmark this page","حفظ الصفحة"), null, {
                d.dismiss(); if (t.url.startsWith("http")) { val on = store.toggleBookmark(t.title, t.url); Toast.makeText(this, if (on) tr("Bookmarked","تم الحفظ") else tr("Removed","تمت الإزالة"), Toast.LENGTH_SHORT).show() }
            })
            item(l, tr("Bookmarks","المحفوظات"), null, { d.dismiss(); showList(tr("Bookmarks","المحفوظات"), store.bookmarks()) })
            item(l, tr("History","السجل"), null, { d.dismiss(); showList(tr("History","السجل"), store.history()) })
            item(l, tr("Desktop site: ","نسخة سطح المكتب: ") + if (t.desktop) tr("on","مفعلة") else tr("off","متوقفة"), null, {
                d.dismiss(); t.desktop = !t.desktop
                t.session.settings.userAgentMode = if (t.desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
                t.session.settings.viewportMode = if (t.desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
                t.session.reload()
            })
            item(l, tr("Site protection", "حماية الموقع"), tr("Tracking, permissions and compatibility", "التتبع والأذونات والتوافق"), { d.dismiss(); protection(t) })
            item(l, tr("Settings", "الإعدادات"), null, { d.dismiss(); showSettings() })
            item(l, tr("Play a magnet link / torrent URL","تشغيل رابط مغناطيسي أو تورنت"), null, { d.dismiss(); askMagnet() })
            item(l, tr("Share page","مشاركة الصفحة"), null, {
                d.dismiss(); startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t.url), tr("Share","مشاركة")))
            })
            item(l, tr("Downloads","التنزيلات"), null, { d.dismiss(); startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) })
            item(l, tr("Clear history & cookies","مسح السجل وملفات الارتباط"), null, {
                d.dismiss();showSettings()
            })
        }
    }

    private fun askMagnet() {
        val e = EditText(this).apply { hint = tr("magnet:?xt=urn:btih:...  or  https://.../file.torrent","رابط مغناطيسي أو رابط ملف تورنت"); setSingleLine() }
        AlertDialog.Builder(this).setTitle(tr("Torrent / magnet","رابط تورنت أو مغناطيسي")).setView(e)
            .setPositiveButton(tr("Play","تشغيل")) { _, _ -> val s = e.text.toString().trim(); if (s.isNotEmpty()) openPlayer(s, emptyList(), "") }
            .setNegativeButton(tr("Cancel","إلغاء"), null).show()
    }

    private fun showList(name: String, list: List<Store.Entry>) {
        sheet { l, d ->
            title(l, name)
            if (list.isEmpty()) l.addView(TextView(this).apply { text = tr("Empty","لا يوجد محتوى"); setTextColor(dim) })
            list.take(150).forEach { e -> item(l, e.title, e.url, { d.dismiss(); load(e.url) }) }
        }
    }

    private fun showSettings() {
        sheet { l,d ->
            title(l,tr("Settings", "الإعدادات"))
            title(l,tr("Privacy & protection", "الخصوصية والحماية"))
            item(l,tr("Site protection", "حماية الموقع"),tr("Change protection for the current site", "تغيير حماية الموقع الحالي"),{d.dismiss();cur?.let{protection(it)}})
            item(l,tr("Clear browsing data", "مسح بيانات التصفح"),tr("History, cookies and site storage", "السجل وملفات الارتباط وتخزين المواقع"),{
                AlertDialog.Builder(this).setTitle(tr("Clear browsing data?", "مسح بيانات التصفح؟")).setNegativeButton(tr("Cancel", "إلغاء"),null).setPositiveButton(tr("Clear", "مسح")){_,_->store.clearHistory();Engine.get(this).storageController.clearData(StorageController.ClearFlags.ALL);d.dismiss()}.show()
            })
            title(l,tr("Browser", "المتصفح"))
            item(l,tr("Search", "البحث"),store.searchEngine,{d.dismiss();chooseSearchEngine()})
            item(l,tr("Language", "اللغة"),tr("Choose app language", "اختيار لغة التطبيق"),{d.dismiss();chooseLanguage()})
            item(l,tr("Playback", "التشغيل"),tr("Subtitle appearance", "مظهر الترجمة"),{d.dismiss();playbackSettings()})
            title(l,tr("About Nova", "عن نوفا"))
            item(l,tr("Nova · 0.3 development","نوفا · 0.3 قيد التطوير"),tr("In development. Not a finished browser.", "قيد التطوير. ليس متصفحاً مكتملاً بعد."),{})
        }
    }

    private fun searchUrl(query:String):String {
        val encoded=URLEncoder.encode(query,"UTF-8")
        return when(store.searchEngine){"Google"->"https://www.google.com/search?q=$encoded";"Bing"->"https://www.bing.com/search?q=$encoded";else->"https://duckduckgo.com/?q=$encoded"}
    }
    private fun chooseSearchEngine(){
        val names=arrayOf("DuckDuckGo","Google","Bing")
        AlertDialog.Builder(this).setTitle(tr("Search engine","محرك البحث")).setSingleChoiceItems(names,names.indexOf(store.searchEngine)){d,i->store.searchEngine=names[i];d.dismiss();showSettings()}.setNegativeButton(tr("Cancel","إلغاء"),null).show()
    }
    private fun chooseLanguage(){
        val names=arrayOf(tr("Device language","لغة الجهاز"),"العربية","English")
        AlertDialog.Builder(this).setTitle(tr("App language","لغة التطبيق")).setItems(names){_,i->androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags(arrayOf("","ar","en")[i]))}.setNegativeButton(tr("Cancel","إلغاء"),null).show()
    }
    private fun playbackSettings(){sheet{l,d->
        title(l,tr("Subtitle appearance","مظهر الترجمة"))
        item(l,tr("Font size","حجم الخط"),store.subtitleSize.toString(),{d.dismiss();subtitleNumber(true)})
        item(l,tr("Height above bottom","الارتفاع عن أسفل الفيديو"),store.subtitleHeight.toString(),{d.dismiss();subtitleNumber(false)})
        item(l,tr("Font","نوع الخط"),when(store.subtitleFont){"serif"->tr("Serif","ذو أطراف");"monospace"->tr("Monospace","متساوي العرض");else->tr("Default","افتراضي")},{d.dismiss();AlertDialog.Builder(this).setTitle(tr("Font","نوع الخط")).setItems(arrayOf(tr("Default","افتراضي"),tr("Serif","ذو أطراف"),tr("Monospace","متساوي العرض"))){_,i->store.subtitleFont=arrayOf("sans-serif","serif","monospace")[i];playbackSettings()}.show()})
        item(l,tr("Dark background","خلفية داكنة"),if(store.subtitleBackground)tr("On","مفعلة") else tr("Off","متوقفة"),{store.subtitleBackground=!store.subtitleBackground;d.dismiss();playbackSettings()})
        item(l,tr("Reset subtitle style","استعادة مظهر الترجمة الافتراضي"),null,{store.subtitleSize=20;store.subtitleHeight=16;store.subtitleFont="sans-serif";store.subtitleBackground=true;d.dismiss();playbackSettings()})
    }}
    private fun subtitleNumber(size:Boolean){
        val bar=SeekBar(this).apply{max=if(size)28 else 160;progress=if(size)store.subtitleSize-12 else store.subtitleHeight}
        AlertDialog.Builder(this).setTitle(if(size)tr("Font size","حجم الخط") else tr("Height above bottom","الارتفاع عن أسفل الفيديو")).setView(bar).setPositiveButton(tr("Save","حفظ")){_,_->if(size)store.subtitleSize=bar.progress+12 else store.subtitleHeight=bar.progress;playbackSettings()}.setNegativeButton(tr("Cancel","إلغاء"),null).show()
    }

    private fun homeHtml(): String {
        val title=tr("A quieter way to watch", "تصفّح بهدوء. شاهد بطريقتك")
        val hint=tr("Search the web or enter a site", "ابحث أو أدخل عنوان موقع")
        return """<!doctype html><html lang='${if(resources.configuration.locales[0].language=="ar")"ar" else "en"}' dir='auto'><meta name='viewport' content='width=device-width,initial-scale=1'><style>
        *{box-sizing:border-box}body{margin:0;background:#111018;color:#f3f1fa;font-family:sans-serif;padding:64px 24px}small{color:#b5a7ff;letter-spacing:5px;font-size:15px}h1{font-size:32px;line-height:1.3;margin:20px 0 32px;font-weight:600}input{width:100%;border:1px solid #37333f;border-radius:18px;background:#201e29;color:#f3f1fa;font-size:15px;padding:18px}p{color:#9b97aa;line-height:1.7;font-size:13px;margin-top:28px}.orb{width:44px;height:44px;border:2px solid #b5a7ff;border-radius:15px;transform:rotate(-12deg);margin-bottom:28px}</style>
        <div class='orb'></div><small>NOVA</small><h1>$title</h1><form action='${when(store.searchEngine){"Google"->"https://www.google.com/search";"Bing"->"https://www.bing.com/search";else->"https://duckduckgo.com/"}}'><input name='q' placeholder='$hint'></form><p>${tr("Your browser and player, connected. Open a video, then use Play in Nova.", "المتصفح والمشغل في تجربة واحدة. افتح فيديو ثم اختر التشغيل في نوفا.")}</p></html>"""
    }

    private fun openPlayer(url: String, subs: List<String>, referer: String, tab: Tab? = null) {
        launchPlayer(url, subs, referer, tab)
    }

    private fun launchPlayer(url: String, subs: List<String>, referer: String, tab: Tab?) {
        val i = Intent(this, PlayerActivity::class.java)
            .putExtra("url", url)
            .putStringArrayListExtra("subs", ArrayList(subs))
            .putExtra("referer", referer)
            .putExtra("ua", GeckoSession.getDefaultUserAgent())
        startActivity(i)
    }

    override fun onPause(){super.onPause();cur?.session?.setActive(false);ui.removeCallbacks(heartbeat);lastHeartbeat=0}
    override fun onResume(){super.onResume();cur?.session?.setActive(true);ui.removeCallbacks(heartbeat);ui.post(heartbeat)}
    override fun onDestroy() { detectorAttachments.clear();tabs.forEach { it.session.close() };store.close(); super.onDestroy() }
}
