package com.example.videoplayer

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// AniList GraphQL (শুধু মেটাডেটা: নাম, কভার, রেটিং, ট্রেইলার, বৈধ স্ট্রিমিং লিংক)। API key লাগে না।
object AniApi {
    private const val ENDPOINT = "https://graphql.anilist.co"

    class Item(val id: Int, val title: String, val cover: String?, val score: Int, val episodes: Int, val genres: String)
    class Link(val site: String, val url: String)
    class Detail(
        val item: Item, val banner: String?, val description: String, val status: String,
        val year: Int, val trailerId: String?, val trailerSite: String?, val links: List<Link>
    )

    private fun post(query: String, vars: JSONObject): JSONObject {
        val body = JSONObject().put("query", query).put("variables", vars).toString()
        val c = URL(ENDPOINT).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15000
        c.readTimeout = 20000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("Accept", "application/json")
        c.outputStream.use { it.write(body.toByteArray()) }
        if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
        val text = c.inputStream.bufferedReader().use { it.readText() }
        c.disconnect()
        return JSONObject(text)
    }

    private fun item(m: JSONObject): Item {
        val t = m.getJSONObject("title")
        val name = t.optString("english").takeIf { it.isNotEmpty() && it != "null" }
            ?: t.optString("romaji", "?")
        val g = m.optJSONArray("genres")
        val genres = (0 until (g?.length() ?: 0)).joinToString(" · ") { g!!.getString(it) }
        val cover = m.optJSONObject("coverImage")?.optString("large")?.takeIf { it.isNotEmpty() && it != "null" }
        return Item(m.getInt("id"), name, cover, m.optInt("averageScore", 0), m.optInt("episodes", 0), genres)
    }

    private const val FIELDS = "id title{romaji english} coverImage{large} averageScore episodes genres"

    // query খালি হলে ট্রেন্ডিং; নাহলে সার্চ
    fun list(query: String, page: Int): List<Item> {
        val q = "query(\$s:String,\$p:Int){Page(page:\$p,perPage:30){media(type:ANIME,search:\$s," +
            "sort:[${if (query.isEmpty()) "TRENDING_DESC" else "SEARCH_MATCH"}]){$FIELDS}}}"
        val vars = JSONObject().put("p", page)
        if (query.isNotEmpty()) vars.put("s", query)
        val arr = post(q, vars).getJSONObject("data").getJSONObject("Page").getJSONArray("media")
        return (0 until arr.length()).map { item(arr.getJSONObject(it)) }
    }

    fun detail(id: Int): Detail {
        val q = "query(\$id:Int){Media(id:\$id,type:ANIME){$FIELDS bannerImage description(asHtml:false) status " +
            "startDate{year} trailer{id site} externalLinks{site url type}}}"
        val m = post(q, JSONObject().put("id", id)).getJSONObject("data").getJSONObject("Media")
        val ls = m.optJSONArray("externalLinks")
        val links = ArrayList<Link>()
        for (i in 0 until (ls?.length() ?: 0)) {
            val o = ls!!.getJSONObject(i)
            if (o.optString("type") == "STREAMING" && o.optString("url").startsWith("http")) {
                links.add(Link(o.optString("site"), o.getString("url")))
            }
        }
        val tr = m.optJSONObject("trailer")
        val desc = m.optString("description", "").replace(Regex("<[^>]*>"), "").trim()
        return Detail(
            item(m), m.optString("bannerImage").takeIf { it.isNotEmpty() && it != "null" }, desc,
            m.optString("status", ""), m.optJSONObject("startDate")?.optInt("year", 0) ?: 0,
            tr?.optString("id")?.takeIf { it.isNotEmpty() && it != "null" }, tr?.optString("site"), links
        )
    }
}
