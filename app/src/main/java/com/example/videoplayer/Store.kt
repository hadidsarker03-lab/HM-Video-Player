package com.example.videoplayer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// প্রিয়, ইতিহাস, প্লেলিস্ট ও চালানোর অবস্থান সংরক্ষণ (SharedPreferences)
object Store {

    private fun sp(c: Context) = AppSettings.prefs(c)

    fun ref(audio: Boolean, id: Long) = (if (audio) "a:" else "v:") + id

    private fun readList(c: Context, key: String): MutableList<String> {
        val s = sp(c).getString(key, null) ?: return mutableListOf()
        return try {
            val a = JSONArray(s)
            val out = mutableListOf<String>()
            for (i in 0 until a.length()) out.add(a.getString(i))
            out
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun writeList(c: Context, key: String, list: List<String>) {
        sp(c).edit().putString(key, JSONArray(list).toString()).apply()
    }

    // ---------- প্রিয় ----------
    fun favorites(c: Context): List<String> = readList(c, "fav")

    fun favoriteCount(c: Context, audio: Boolean): Int {
        val prefix = if (audio) "a:" else "v:"
        return favorites(c).count { it.startsWith(prefix) }
    }

    fun isFav(c: Context, audio: Boolean, id: Long) = favorites(c).contains(ref(audio, id))

    fun toggleFav(c: Context, audio: Boolean, id: Long): Boolean {
        val list = readList(c, "fav")
        val r = ref(audio, id)
        val nowFav = if (list.contains(r)) {
            list.remove(r)
            false
        } else {
            list.add(0, r)
            true
        }
        writeList(c, "fav", list)
        return nowFav
    }

    // ---------- ইতিহাস ----------
    fun history(c: Context): List<String> = readList(c, "hist")

    fun addHistory(c: Context, audio: Boolean, id: Long) {
        val list = readList(c, "hist")
        val r = ref(audio, id)
        list.remove(r)
        list.add(0, r)
        while (list.size > 100) list.removeAt(list.size - 1)
        writeList(c, "hist", list)
    }

    fun clearHistory(c: Context) {
        sp(c).edit().remove("hist").apply()
    }

    // ---------- প্লেলিস্ট ----------
    private fun plKey(audio: Boolean) = if (audio) "pl_a" else "pl_v"

    fun playlists(c: Context, audio: Boolean): LinkedHashMap<String, MutableList<Long>> {
        val out = LinkedHashMap<String, MutableList<Long>>()
        val s = sp(c).getString(plKey(audio), null) ?: return out
        try {
            val o = JSONObject(s)
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val a = o.getJSONArray(k)
                val ids = mutableListOf<Long>()
                for (i in 0 until a.length()) ids.add(a.getLong(i))
                out[k] = ids
            }
        } catch (e: Exception) {
            // নষ্ট ডাটা উপেক্ষা
        }
        return out
    }

    private fun savePlaylists(c: Context, audio: Boolean, map: Map<String, List<Long>>) {
        val o = JSONObject()
        for ((k, v) in map) {
            val a = JSONArray()
            for (id in v) a.put(id)
            o.put(k, a)
        }
        sp(c).edit().putString(plKey(audio), o.toString()).apply()
    }

    fun createPlaylist(c: Context, audio: Boolean, name: String): Boolean {
        if (name.isBlank()) return false
        val map = playlists(c, audio)
        if (map.containsKey(name)) return false
        map[name] = mutableListOf()
        savePlaylists(c, audio, map)
        return true
    }

    fun addToPlaylist(c: Context, audio: Boolean, name: String, id: Long) {
        val map = playlists(c, audio)
        val list = map[name] ?: return
        if (!list.contains(id)) list.add(id)
        savePlaylists(c, audio, map)
    }

    fun removeFromPlaylist(c: Context, audio: Boolean, name: String, id: Long) {
        val map = playlists(c, audio)
        map[name]?.remove(id)
        savePlaylists(c, audio, map)
    }

    fun deletePlaylist(c: Context, audio: Boolean, name: String) {
        val map = playlists(c, audio)
        map.remove(name)
        savePlaylists(c, audio, map)
    }

    // ---------- চালানোর অবস্থান (Resume) ----------
    fun savePos(c: Context, key: String, pos: Long) {
        sp(c).edit().putLong("pos_$key", pos).apply()
    }

    fun getPos(c: Context, key: String): Long = sp(c).getLong("pos_$key", 0L)
}
