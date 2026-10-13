package com.example.videoplayer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// ওয়াচলিস্ট ট্র্যাকার: শুধু ফোনে সংরক্ষিত (SharedPreferences), কোনো অ্যাকাউন্ট লাগে না।
object Tracker {
    const val PLANNING = "PLANNING"
    const val WATCHING = "WATCHING"
    const val COMPLETED = "COMPLETED"
    const val DROPPED = "DROPPED"
    val STATUSES = listOf(PLANNING, WATCHING, COMPLETED, DROPPED)

    class Entry(
        val id: Int, val title: String, val cover: String?, val total: Int,
        var status: String, var progress: Int, var score: Int
    )

    private fun prefs(c: Context) = c.getSharedPreferences("tracker", Context.MODE_PRIVATE)

    fun all(c: Context): MutableList<Entry> {
        val out = ArrayList<Entry>()
        val arr = try { JSONArray(prefs(c).getString("list", "[]")) } catch (e: Exception) { JSONArray() }
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                Entry(
                    o.getInt("id"), o.getString("title"), o.optString("cover").takeIf { it.isNotEmpty() },
                    o.optInt("total"), o.optString("status", PLANNING), o.optInt("progress"), o.optInt("score")
                )
            )
        }
        return out
    }

    fun get(c: Context, id: Int): Entry? = all(c).firstOrNull { it.id == id }

    fun save(c: Context, e: Entry) {
        val list = all(c).filter { it.id != e.id }.toMutableList()
        list.add(0, e)
        write(c, list)
    }

    fun remove(c: Context, id: Int) = write(c, all(c).filter { it.id != id })

    private fun write(c: Context, list: List<Entry>) {
        val arr = JSONArray()
        for (e in list) {
            arr.put(
                JSONObject().put("id", e.id).put("title", e.title).put("cover", e.cover ?: "")
                    .put("total", e.total).put("status", e.status).put("progress", e.progress).put("score", e.score)
            )
        }
        prefs(c).edit().putString("list", arr.toString()).apply()
    }

    fun label(c: Context, s: String): String = c.getString(
        when (s) {
            PLANNING -> R.string.st_planning
            WATCHING -> R.string.st_watching
            COMPLETED -> R.string.st_completed
            else -> R.string.st_dropped
        }
    )
}
