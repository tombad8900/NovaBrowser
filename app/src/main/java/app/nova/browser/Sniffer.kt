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
            c == "text/vtt" || c == "application/x-subrip" -> "SUB"
            c.startsWith("video/") -> "VIDEO"
            c.startsWith("audio/") -> "AUDIO"
            else -> classify(url)
        }
    }

    fun mimeForSub(url: String): String {
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
    fun foundFromUrls(urls: List<String>, source: String): List<Found> =
        urls.mapNotNull { u -> classify(u)?.let { Found(u, it, source) } }
}
