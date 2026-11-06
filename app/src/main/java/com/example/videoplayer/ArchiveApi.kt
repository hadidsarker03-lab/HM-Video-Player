package com.example.videoplayer

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Internet Archive: পাবলিক ডোমেইন / ফ্রি-টু-ওয়াচ সিনেমা, কার্টুন ও পুরনো টিভি। API key লাগে না; ভিডিও অ্যাপের প্লেয়ারেই চলে।
object ArchiveApi {
    class Item(val id: String, val title: String, val year: String, val thumb: String)
    class Video(val name: String, val url: String)

    val CATEGORIES = listOf(
        "moviesandfilms" to R.string.arc_movies,
        "animationandcartoons" to R.string.arc_cartoons,
        "classic_tv" to R.string.arc_tv
    )

    private fun get(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 25000
        c.setRequestProperty("User-Agent", "HMVideoPlayer/1.0")
        if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
        val t = c.inputStream.bufferedReader().use { it.readText() }
        c.disconnect()
        return t
    }

    fun list(collection: String, query: String, page: Int): List<Item> {
        var q = "collection:($collection) AND mediatype:(movies)"
        if (query.isNotEmpty()) q += " AND title:(${query.replace(Regex("[^\\p{L}\\p{N} ]"), " ")})"
        val url = "https://archive.org/advancedsearch.php?q=" + URLEncoder.encode(q, "UTF-8") +
            "&fl[]=identifier&fl[]=title&fl[]=year&sort[]=downloads+desc&rows=30&page=$page&output=json"
        val docs = JSONObject(get(url)).getJSONObject("response").getJSONArray("docs")
        return (0 until docs.length()).map {
            val o = docs.getJSONObject(it)
            val id = o.getString("identifier")
            Item(id, o.optString("title", id), o.optString("year", ""), "https://archive.org/services/img/$id")
        }
    }

    // আইটেমের প্লে-যোগ্য ফাইল (mp4/ogv/webm/mkv); ছোট/স্যাম্পল ফাইল বাদ
    fun videos(id: String): List<Video> {
        val files = JSONObject(get("https://archive.org/metadata/$id")).optJSONArray("files") ?: return emptyList()
        val out = ArrayList<Pair<Long, Video>>()
        for (i in 0 until files.length()) {
            val f = files.getJSONObject(i)
            val name = f.optString("name")
            val lower = name.lowercase()
            val ok = lower.endsWith(".mp4") || lower.endsWith(".m4v") || lower.endsWith(".ogv") ||
                lower.endsWith(".webm") || lower.endsWith(".mkv")
            if (!ok || f.optString("source") == "derivative" && lower.contains("thumb")) continue
            val size = f.optString("size", "0").toLongOrNull() ?: 0L
            if (size in 1..2_000_000) continue
            val enc = name.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
            out.add(size to Video(name.substringAfterLast('/'), "https://archive.org/download/$id/$enc"))
        }
        // একই নামের একাধিক ফরম্যাট থাকলে mp4 অগ্রাধিকার
        val byBase = LinkedHashMap<String, Video>()
        for ((_, v) in out.sortedBy { if (it.second.name.lowercase().endsWith(".mp4")) 0 else 1 }) {
            byBase.putIfAbsent(v.name.substringBeforeLast('.'), v)
        }
        return byBase.values.sortedBy { it.name.lowercase() }
    }
}
