package app.nova.browser

data class Found(val url: String, val kind: String, val source: String)

object Sniffer {
    private val video = setOf("mp4", "m4v", "webm", "mkv", "mov", "flv", "avi", "ts3", "wmv", "3gp")
    private val audio = setOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav")
    private val subs = setOf("vtt", "srt", "ass", "ssa", "ttml", "dfxp")

    /** Returns HLS, DASH, VIDEO, AUDIO, SUB, TORRENT or null when the URL is not interesting. */
    fun classify(url: String): String? {
        if (url.startsWith("magnet:?", ignoreCase = true) && url.contains("btih", true)) return "TORRENT"
        if (!url.startsWith("http", ignoreCase = true)) return null
        val path = url.substringBefore('#').substringBefore('?').lowercase()
        val ext = path.substringAfterLast('.', "")
        return when {
            ext == "m3u8" -> "HLS"
            ext == "mpd" -> "DASH"
            ext == "torrent" -> "TORRENT"
            ext in subs -> "SUB"
            ext in video -> "VIDEO"
            ext in audio -> "AUDIO"
            else -> null
        }
    }

    /** Classify by response Content-Type when the URL itself has no telling extension. */
    fun classifyMime(url: String, ct: String): String? {
        if (!url.startsWith("http", ignoreCase = true)) return null
        val c = ct.substringBefore(';').trim().lowercase()
        if (c == "text/html" || c == "application/json") return null
        return when {
            c in setOf("application/vnd.apple.mpegurl","application/x-mpegurl","audio/mpegurl","audio/x-mpegurl") -> "HLS"
            c == "application/dash+xml" -> "DASH"
            c in setOf("text/vtt","application/x-subrip","application/ttml+xml","text/x-ssa","text/x-ass","application/x-ssa","application/x-ass") -> "SUB"
            c.startsWith("video/") -> "VIDEO"
            c.startsWith("audio/") -> "AUDIO"
            else -> classify(url)
        }
    }

    fun mimeForSub(url:String,contentType:String=""):String {
        when(contentType.substringBefore(';').trim().lowercase()){
            "text/vtt"->return "text/vtt"
            "application/x-subrip"->return "application/x-subrip"
            "application/ttml+xml"->return "application/ttml+xml"
            "text/x-ssa","text/x-ass","application/x-ssa","application/x-ass"->return "text/x-ssa"
        }
        val ext = url.substringBefore('#').substringBefore('?').lowercase().substringAfterLast('.', "")
        return when (ext) {
            "srt" -> "application/x-subrip"
            "ass", "ssa" -> "text/x-ssa"
            "ttml", "dfxp" -> "application/ttml+xml"
            else -> "text/vtt"
        }
    }

    private val blockedHosts = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "google-analytics.com", "googletagmanager.com", "taboola.com", "outbrain.com", "popads.net",
        "popcash.net", "propellerads.com", "adsterra.com", "exoclick.com", "juicyads.com",
        "trafficjunky.com", "adnxs.com", "criteo.com", "scorecardresearch.com", "hotjar.com",
        "amazon-adsystem.com", "pubmatic.com", "rubiconproject.com", "onclickads.net", "clickadu.com",
        "adcash.com", "mgid.com", "ads.yahoo.com", "moatads.com", "adform.net", "yandex.ru/metrika"
    )

    fun isBlocked(host: String?): Boolean {
        if (host == null) return false
        val h = host.lowercase()
        return blockedHosts.any { h == it || h.endsWith(".$it") }
    }

    /** Pull candidate links out of free text (used for the JSON the page scan returns). */
    fun foundFromUrls(urls:List<String>,source:String):List<Found> = urls.take(150).mapNotNull{u->
        classify(u)?.let{Found(u,it,source)}?:unwrapMediaHint(u)?.let{candidate->classify(candidate)?.let{Found(candidate,it,"wrapped-hint")}}
    }.distinctBy{sourceKey(it.url)}

    /** Inspect explicit wrapper parameters only. Never treat a player/page keyword as media. */
    internal fun unwrapMediaHint(input:String):String? {
        if(input.length>16384)return null
        val keys=setOf("url","src","file","source","stream","video","media","data")
        var current=input
        val visited=mutableSetOf<String>()
        repeat(3){
            if(!visited.add(current))return null
            val uri=runCatching{java.net.URI(current)}.getOrNull()?:return null
            if(uri.scheme?.lowercase() !in setOf("http","https")||uri.host.isNullOrBlank())return null
            val options=uri.rawQuery?.split('&')?.take(40).orEmpty().mapNotNull{pair->
                val key=runCatching{java.net.URLDecoder.decode(pair.substringBefore('='),"UTF-8")}.getOrNull()?.lowercase()
                if(key !in keys)return@mapNotNull null
                val raw=pair.substringAfter('=',"")
                val once=decodeHint(raw)
                val values=listOfNotNull(once,decodeHint(once),runCatching{
                    val compact=once.replace('-','+').replace('_','/')
                    String(java.util.Base64.getDecoder().decode(compact+"=".repeat((4-compact.length%4)%4)),Charsets.UTF_8)
                }.getOrNull())
                values.firstOrNull{value->value.length<=16384&&runCatching{val u=java.net.URI(value);u.scheme?.lowercase() in setOf("http","https")&&!u.host.isNullOrBlank()&&u.rawUserInfo==null}.getOrDefault(false)}
            }
            val candidate=options.firstOrNull{classify(it)!=null}?:options.firstOrNull()?:return null
            if(classify(candidate)!=null)return candidate
            current=candidate
        }
        return null
    }
    private fun decodeHint(value:String)=runCatching{java.net.URLDecoder.decode(value.replace("+","%2B"),"UTF-8")}.getOrDefault(value)
}
