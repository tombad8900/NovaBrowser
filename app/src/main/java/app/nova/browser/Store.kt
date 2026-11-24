package app.nova.browser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Store(ctx: Context) {
    private val historyWorker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val p = ctx.getSharedPreferences("nova", Context.MODE_PRIVATE)

    data class Entry(val title: String, val url: String)

    private fun read(key: String): MutableList<Entry> {
        val out = mutableListOf<Entry>()
        val arr = try { JSONArray(p.getString(key, "[]")) } catch (e: Exception) { JSONArray() }
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(Entry(o.optString("t"), o.optString("u")))
        }
        return out
    }

    private fun write(key: String, list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("t", it.title).put("u", it.url)) }
        p.edit().putString(key, arr.toString()).apply()
    }

    fun bookmarks() = read("bm")
    fun history() = read("hist")

    fun toggleBookmark(title: String, url: String): Boolean {
        val l = bookmarks()
        val i = l.indexOfFirst { it.url == url }
        return if (i >= 0) { l.removeAt(i); write("bm", l); false } else { l.add(0, Entry(title.ifBlank { url }, url)); write("bm", l); true }
    }

    fun isBookmarked(url: String) = bookmarks().any { it.url == url }

    fun addHistory(title: String, url: String) {
        historyWorker.execute {
        val l = history()
        l.removeAll { it.url == url }
        l.add(0, Entry(title.ifBlank { url }, url))
        write("hist", l.take(500))
        }
    }

    fun clearHistory() { historyWorker.execute { write("hist", emptyList()) } }
    fun protection(host: String) = !p.getStringSet("protectionOff", emptySet())!!.contains(host.lowercase())
    fun toggleProtection(host: String): Boolean {
        val set = p.getStringSet("protectionOff", emptySet())!!.toMutableSet()
        val on = set.remove(host.lowercase()); if (!on) set.add(host.lowercase())
        p.edit().putStringSet("protectionOff", set).apply(); return on
    }

    fun close(){historyWorker.shutdown()}

    var adblock: Boolean
        get() = p.getBoolean("adblock", true)
        set(v) { p.edit().putBoolean("adblock", v).apply() }
}
