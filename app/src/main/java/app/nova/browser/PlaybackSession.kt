package app.nova.browser

import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** A request observation stays attached to its own resource. Never persist credentials. */
data class RequestContext(val url: String, val headers: Map<String, String>, val observedAt: Long = System.currentTimeMillis()) {
    fun matches(target: String): Boolean = try {
        val a = URI(url).normalize(); val b = URI(target).normalize()
        if(a.rawPath.contains("%2f",true)||b.rawPath.contains("%2f",true)||a.rawPath.contains("%2e",true)||b.rawPath.contains("%2e",true)) throw IllegalArgumentException("Encoded path boundary")
        a.scheme.equals(b.scheme, true) && a.host.equals(b.host, true) && a.port == b.port &&
            (a.path == b.path || b.path.startsWith(a.path.substringBeforeLast('/', "") + "/"))
    } catch (_: Exception) { false }
    fun safeHeaders(target: String, now: Long = System.currentTimeMillis()): Map<String, String> {
        if (!matches(target) || now - observedAt > 30 * 60_000L) return emptyMap()
        return headers.filter { (k, v) -> k.lowercase() in ALLOWED && !v.contains('\r') && !v.contains('\n') }
    }
    companion object { val ALLOWED = setOf("user-agent", "referer", "origin", "cookie", "authorization", "accept", "accept-language", "x-requested-with") }
}

data class PlaybackSource(
    val id: String = UUID.randomUUID().toString(), val url: String, val kind: String,
    val mime: String = "", val language: String = "und", val label: String = "",
    val context: RequestContext = RequestContext(url, emptyMap()), val evidence: String = "hint"
) {
    val networkObserved get() = evidence=="network" || evidence=="track+network"
    val hasRequestHeaders get() = context.headers.isNotEmpty()
    // Observation is not player readiness; only Media3 can confirm loading/decoding.
}

data class PlaybackSession(val id: String = UUID.randomUUID().toString(), val title: String,
    val pageUrl: String, val sources: List<PlaybackSource>, val subtitles: List<PlaybackSource>,
    val contexts: List<RequestContext>, val createdAt: Long = System.currentTimeMillis())

object PlaybackSessions {
    private val sessions = ConcurrentHashMap<String, PlaybackSession>()
    fun put(session: PlaybackSession): String {
        sessions.entries.removeIf { System.currentTimeMillis() - it.value.createdAt > 30 * 60_000L }
        if (sessions.size >= 8) sessions.keys.firstOrNull()?.let { sessions.remove(it) }
        sessions[session.id] = session; return session.id
    }
    fun get(id: String?): PlaybackSession? {
        val s=id?.let{sessions[it]}?:return null
        if(System.currentTimeMillis()-s.createdAt>30*60_000L){sessions.remove(id);return null}
        return s
    }
    fun remove(id: String?) { if (id != null) sessions.remove(id) }
}

/** Preserve signed URLs verbatim. Only fragment differences are known to be request-irrelevant. */
fun sourceKey(url: String) = url.substringBefore('#')

/** A track is a candidate, not proof of load/format success. Keep network context if observed. */
internal fun subtitleFromTrack(url:String,language:String,label:String,previous:PlaybackSource?,context:RequestContext?):PlaybackSource {
    val old=previous?.takeIf{it.kind=="SUB"}
    return (old?:PlaybackSource(url=url,kind="SUB",mime=Sniffer.mimeForSub(url),context=context?:RequestContext(url,emptyMap()),evidence=if(context==null)"track-hint" else "track+network")).copy(language=language,label=label,context=context?:old?.context?:RequestContext(url,emptyMap()))
}
