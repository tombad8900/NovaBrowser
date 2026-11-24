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
import android.webkit.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.json.JSONArray
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    class Tab(val web: WebView) {
        val found = LinkedHashMap<String, Found>()
        var title = ""
        var url = ""
        var desktop = false
    }

    private val bg = Color.parseColor("#0F1115")
    private val surface = Color.parseColor("#1A1D24")
    private val accent = Color.parseColor("#8B7BFF")
    private val textColor = Color.parseColor("#E8EAF0")
    private val dim = Color.parseColor("#8A90A0")

    private lateinit var holder: FrameLayout
    private lateinit var urlBar: EditText
    private lateinit var progress: ProgressBar
    private lateinit var snifBtn: TextView
    private lateinit var tabBtn: TextView
    private lateinit var fullscreenHost: FrameLayout
    private val tabs = mutableListOf<Tab>()
    private var cur: Tab? = null
    private lateinit var store: Store
    private val ui = Handler(Looper.getMainLooper())
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private val mobileUa: String by lazy { WebView(this).settings.userAgentString }
    private val desktopUa = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, r: Int = 14) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        buildUi()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goBack() }
        })
        val data = intent?.dataString
        if (data != null) handleExternal(data) else newTab(null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let { handleExternal(it) }
    }

    private fun handleExternal(u: String) {
        if (u.startsWith("magnet:")) { openPlayer(u, emptyList(), ""); if (tabs.isEmpty()) newTab(null) }
        else newTab(u)
    }

    private fun goBack() {
        if (customView != null) { hideCustom(); return }
        val t = cur ?: return finish()
        if (t.web.canGoBack()) t.web.goBack()
        else if (tabs.size > 1) closeTab(t)
        else finish()
    }

    private fun btn(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label; setTextColor(textColor); textSize = 17f; gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f)
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
        holder = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }

        urlBar = EditText(this).apply {
            hint = "Search or enter address"; setHintTextColor(dim); setTextColor(textColor); textSize = 15f
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
            setPadding(dp(10), dp(8), dp(10), dp(2))
        }
        barWrap.addView(urlBar)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        snifBtn = btn("◉ 0") { showSniffer() }
        tabBtn = btn("1") { showTabs() }
        row.addView(btn("‹") { cur?.web?.let { if (it.canGoBack()) it.goBack() } })
        row.addView(btn("›") { cur?.web?.let { if (it.canGoForward()) it.goForward() } })
        row.addView(btn("⟳") { cur?.web?.reload() })
        row.addView(snifBtn)
        row.addView(tabBtn)
        row.addView(btn("⋮") { showMenu() })
        barWrap.addView(row)

        col.addView(progress); col.addView(holder); col.addView(barWrap)
        root.addView(col)
        fullscreenHost = FrameLayout(this).apply { visibility = View.GONE; setBackgroundColor(Color.BLACK) }
        root.addView(fullscreenHost, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val i = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.ime())
            v.setPadding(0, i.top, 0, i.bottom); insets
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
            t.contains(" ") || !t.contains(".") -> load("https://duckduckgo.com/?q=" + URLEncoder.encode(t, "UTF-8"))
            else -> load("https://$t")
        }
    }

    private fun load(u: String) {
        val t = cur ?: return
        t.web.loadUrl(u)
    }

    // ---------- tabs ----------
    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun newTab(url: String?, switchTo: Boolean = true): Tab {
        val web = WebView(this)
        val tab = Tab(web)
        web.setBackgroundColor(bg)
        if (android.os.Build.VERSION.SDK_INT >= 29) web.isForceDarkAllowed = false
        web.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true; databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(true); javaScriptCanOpenWindowsAutomatically = false
            builtInZoomControls = true; displayZoomControls = false
            loadWithOverviewMode = true; useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            allowFileAccess = false; allowContentAccess = false
            @Suppress("DEPRECATION")
            if (android.os.Build.VERSION.SDK_INT >= 29) forceDark = WebSettings.FORCE_DARK_OFF
        }
        CookieManager.getInstance().apply { setAcceptCookie(true); setAcceptThirdPartyCookies(web, true) }
        web.setDownloadListener { u, ua, cd, mime, _ ->
            val req = DownloadManager.Request(Uri.parse(u))
            val name = URLUtil.guessFileName(u, cd, mime)
            req.setMimeType(mime)
            req.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(u) ?: "")
            req.addRequestHeader("User-Agent", ua)
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            Toast.makeText(this, "Downloading $name", Toast.LENGTH_SHORT).show()
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, r: WebResourceRequest): WebResourceResponse? {
                val u = r.url.toString()
                if (store.adblock && Sniffer.isBlocked(r.url.host))
                    return WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))
                Sniffer.classify(u)?.let { k -> ui.post { addFound(tab, Found(u, k, "network")) } }
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView, r: WebResourceRequest): Boolean {
                val u = r.url.toString()
                if (u.startsWith("magnet:")) { openPlayer(u, emptyList(), ""); return true }
                if (u.startsWith("http://") || u.startsWith("https://") || u == "about:blank") return false
                // only hand a few safe schemes to other apps, and only after a real tap
                val sch = r.url.scheme ?: ""
                if (r.hasGesture() && sch in setOf("mailto", "tel", "sms")) {
                    try { startActivity(Intent(Intent.ACTION_VIEW, r.url)) } catch (_: Exception) {}
                }
                return true
            }

            override fun onPageStarted(view: WebView, u: String, favicon: Bitmap?) {
                tab.found.clear(); tab.url = u
                if (cur === tab) { refreshCounts(); if (!urlBar.hasFocus()) urlBar.setText(shown(u)) }
            }

            override fun onPageFinished(view: WebView, u: String) {
                tab.url = u
                if (u.startsWith("http")) store.addHistory(view.title ?: "", u)
                scan(tab)
                ui.postDelayed({ scan(tab) }, 3000); ui.postDelayed({ scan(tab) }, 8000)
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, p: Int) {
                if (cur === tab) { progress.visibility = if (p in 1..99) View.VISIBLE else View.INVISIBLE; progress.progress = p }
            }
            override fun onReceivedTitle(view: WebView, t: String?) { tab.title = t ?: "" }
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, msg: android.os.Message): Boolean {
                if (!isUserGesture) return false // popup blocker
                val nt = newTab(null)
                (msg.obj as WebView.WebViewTransport).webView = nt.web
                msg.sendToTarget(); return true
            }
            override fun onShowCustomView(v: View, cb: CustomViewCallback) {
                customView = v; customCb = cb
                fullscreenHost.addView(v); fullscreenHost.visibility = View.VISIBLE
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
            override fun onHideCustomView() { hideCustom() }
        }
        tabs.add(tab)
        if (switchTo) switchTo(tab)
        if (url != null) web.loadUrl(url) else web.loadDataWithBaseURL("https://nova.home/", homeHtml(), "text/html", "utf-8", null)
        return tab
    }

    private fun hideCustom() {
        customView?.let { fullscreenHost.removeView(it) }
        fullscreenHost.visibility = View.GONE
        customCb?.onCustomViewHidden(); customView = null; customCb = null
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    private fun shown(u: String) = if (u.startsWith("data:") || u.contains("nova.home")) "" else u

    private fun switchTo(t: Tab) {
        holder.removeAllViews()
        (t.web.parent as? ViewGroup)?.removeView(t.web)
        holder.addView(t.web, FrameLayout.LayoutParams(-1, -1))
        cur = t
        urlBar.setText(shown(t.url))
        refreshCounts()
    }

    private fun closeTab(t: Tab) {
        val i = tabs.indexOf(t)
        tabs.remove(t); t.web.destroy()
        if (tabs.isEmpty()) newTab(null) else switchTo(tabs[maxOf(0, i - 1)])
    }

    private fun refreshCounts() {
        tabBtn.text = tabs.size.toString()
        val n = cur?.found?.values?.count { it.kind != "SUB" } ?: 0
        val s = cur?.found?.values?.count { it.kind == "SUB" } ?: 0
        snifBtn.text = "◉ $n" + if (s > 0) "+${s}cc" else ""
        snifBtn.setTextColor(if (n > 0) accent else textColor)
    }

    private fun addFound(t: Tab, f: Found) {
        if (t.found.containsKey(f.url)) return
        if (t.found.size > 300) return
        t.found[f.url] = f
        if (cur === t) refreshCounts()
    }

    private val scanJs = """
        (function(){try{var out=[];function add(u){if(u&&typeof u==='string')out.push(u)}
        document.querySelectorAll('video,audio,source,track,a[href],iframe,embed').forEach(function(e){add(e.currentSrc||e.src||e.href)});
        var h=document.documentElement.innerHTML.replace(/\\\//g,'/').replace(/&amp;/g,'&');
        var re=/https?:\/\/[^\s"'<>\\)]+\.(?:m3u8|mpd|mp4|webm|mkv|vtt|srt|ass|ttml)(?:\?[^\s"'<>\\)]*)?/gi;var m;var n=0;
        while((m=re.exec(h))&&n++<300){add(m[0])}
        var mg=h.match(/magnet:\?xt=urn:btih:[^\s"'<>]+/gi)||[];mg.forEach(add);
        return JSON.stringify(out.slice(0,300))}catch(e){return '[]'}})();
    """.trimIndent()

    private fun scan(t: Tab) {
        t.web.evaluateJavascript(scanJs) { res ->
            try {
                val inner = JSONArray("[" + res + "]").optString(0, "[]")
                val arr = JSONArray(inner)
                val f = Sniffer.foundFromUrls((0 until arr.length()).map { arr.getString(it) }, "page")
                f.forEach { addFound(t, it) }
            } catch (_: Exception) {}
        }
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
        scan(t)
        sheet { l, d ->
            val media = t.found.values.filter { it.kind != "SUB" }
            val subs = t.found.values.filter { it.kind == "SUB" }
            title(l, "Link detector  (${media.size} media, ${subs.size} subtitles)")
            if (media.isEmpty() && subs.isEmpty()) {
                l.addView(TextView(this).apply {
                    text = "Nothing found yet. Start the video on the page, then reopen this sheet. Links hidden behind blob: players, DRM or logins can't be captured."
                    setTextColor(dim); textSize = 13f
                })
            }
            val subUrls = subs.map { it.url }
            media.forEach { f ->
                item(l, "[${f.kind}]  ${f.url.substringBefore('?').substringAfterLast('/').ifBlank { f.url }.take(80)}", f.url,
                    { d.dismiss(); openPlayer(f.url, subUrls, t.url, t) },
                    { copy(f.url); Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show() })
            }
            if (subs.isNotEmpty()) title(l, "Subtitles (sent to the player automatically)")
            subs.forEach { f -> item(l, "[SUB]  ${f.url.substringBefore('?').substringAfterLast('/')}", f.url, { copy(f.url); Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show() }) }
            item(l, "Tap = play in Nova player. Long press = copy link.", null, {})
        }
    }

    private fun copy(s: String) {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("link", s))
    }

    private fun showTabs() {
        sheet { l, d ->
            title(l, "Tabs (${tabs.size})")
            tabs.toList().forEach { t ->
                item(l, (if (t === cur) "● " else "") + t.title.ifBlank { t.url.ifBlank { "New tab" } }, t.url,
                    { d.dismiss(); switchTo(t) }, { d.dismiss(); closeTab(t) })
            }
            item(l, "+ New tab", null, { d.dismiss(); newTab(null) })
            item(l, "Long press a tab to close it", null, {})
        }
    }

    private fun showMenu() {
        val t = cur ?: return
        sheet { l, d ->
            val bm = store.isBookmarked(t.url)
            item(l, "New tab", null, { d.dismiss(); newTab(null) })
            item(l, if (bm) "Remove bookmark" else "Bookmark this page", null, {
                d.dismiss(); if (t.url.startsWith("http")) { val on = store.toggleBookmark(t.title, t.url); Toast.makeText(this, if (on) "Bookmarked" else "Removed", Toast.LENGTH_SHORT).show() }
            })
            item(l, "Bookmarks", null, { d.dismiss(); showList("Bookmarks", store.bookmarks()) })
            item(l, "History", null, { d.dismiss(); showList("History", store.history()) })
            item(l, "Desktop site: " + if (t.desktop) "on" else "off", null, {
                d.dismiss(); t.desktop = !t.desktop
                t.web.settings.userAgentString = if (t.desktop) desktopUa else mobileUa
                t.web.reload()
            })
            item(l, "Ad & tracker blocking: " + if (store.adblock) "on" else "off", "Basic host blocklist", { d.dismiss(); store.adblock = !store.adblock; t.web.reload() })
            item(l, "Play a magnet link / torrent URL", null, { d.dismiss(); askMagnet() })
            item(l, "Share page", null, {
                d.dismiss(); startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t.url), "Share"))
            })
            item(l, "Downloads", null, { d.dismiss(); startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) })
            item(l, "Clear history & cookies", null, {
                d.dismiss(); store.clearHistory(); CookieManager.getInstance().removeAllCookies(null); WebStorage.getInstance().deleteAllData()
                Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show()
            })
        }
    }

    private fun askMagnet() {
        val e = EditText(this).apply { hint = "magnet:?xt=urn:btih:...  or  https://.../file.torrent"; setSingleLine() }
        AlertDialog.Builder(this).setTitle("Torrent / magnet").setView(e)
            .setPositiveButton("Play") { _, _ -> val s = e.text.toString().trim(); if (s.isNotEmpty()) openPlayer(s, emptyList(), "") }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showList(name: String, list: List<Store.Entry>) {
        sheet { l, d ->
            title(l, name)
            if (list.isEmpty()) l.addView(TextView(this).apply { text = "Empty"; setTextColor(dim) })
            list.take(150).forEach { e -> item(l, e.title, e.url, { d.dismiss(); load(e.url) }) }
        }
    }

    private fun homeHtml(): String {
        val bms = store.bookmarks().take(12).joinToString("") {
            "<a class='c' href='${it.url.replace("'", "%27")}'>${android.text.Html.escapeHtml(it.title.take(24))}</a>"
        }
        return """<!doctype html><html><head><meta name=viewport content='width=device-width,initial-scale=1'>
        <style>body{margin:0;background:#0F1115;color:#E8EAF0;font-family:sans-serif;text-align:center;padding:12vh 20px}
        h1{font-size:44px;letter-spacing:2px;margin:0 0 4px;color:#8B7BFF}p{color:#8A90A0;margin:0 0 28px}
        form{max-width:520px;margin:auto}input{width:100%;box-sizing:border-box;padding:14px 18px;border-radius:26px;border:0;background:#1A1D24;color:#E8EAF0;font-size:16px}
        .g{display:flex;flex-wrap:wrap;gap:10px;justify-content:center;margin-top:28px}.c{background:#1A1D24;color:#E8EAF0;text-decoration:none;padding:12px 16px;border-radius:14px;font-size:13px}</style></head>
        <body><h1>NOVA</h1><p>Browser + built-in player</p>
        <form action='https://duckduckgo.com/' method='get'><input name='q' placeholder='Search the web' autocomplete='off'></form>
        <div class='g'>$bms</div></body></html>"""
    }

    private fun openPlayer(url: String, subs: List<String>, referer: String, tab: Tab? = null) {
        if (Sniffer.classify(url) == "TORRENT") {
            AlertDialog.Builder(this).setTitle("Stream this torrent?")
                .setMessage("Nova will connect to other peers to download it. Your IP address is visible to them, and pieces you have are shared back (upload is capped at 64 KB/s) only while the player is open. Everything is deleted when you close the player. Only stream content you have the right to.")
                .setPositiveButton("Stream") { _, _ -> launchPlayer(url, subs, referer, tab) }
                .setNegativeButton("Cancel", null).show()
            return
        }
        launchPlayer(url, subs, referer, tab)
    }

    private fun launchPlayer(url: String, subs: List<String>, referer: String, tab: Tab?) {
        val i = Intent(this, PlayerActivity::class.java)
            .putExtra("url", url)
            .putStringArrayListExtra("subs", ArrayList(subs))
            .putExtra("referer", referer)
            .putExtra("ua", (tab ?: cur)?.web?.settings?.userAgentString ?: mobileUa)
        startActivity(i)
    }

    override fun onPause() { super.onPause(); CookieManager.getInstance().flush() }
    override fun onDestroy() { tabs.forEach { it.web.destroy() }; super.onDestroy() }
}
