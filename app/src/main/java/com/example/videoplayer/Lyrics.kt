package com.example.videoplayer

import android.content.Context
import android.net.Uri
import java.io.File

// গানের পাশে একই নামের .lrc ফাইল থাকলে সময় মিলিয়ে লিরিক্স দেখায়
object Lyrics {
    class Line(val timeMs: Long, val text: String)

    private val tag = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")

    fun load(ctx: Context, audioUri: Uri): List<Line> {
        return try {
            val path = ctx.contentResolver.query(audioUri, arrayOf("_data"), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: return emptyList()
            val base = path.substringBeforeLast('.')
            val f = listOf("$base.lrc", "$base.LRC").map { File(it) }.firstOrNull { it.isFile && it.length() < 512_000 }
                ?: return emptyList()
            parse(f.readText())
        } catch (e: Exception) {
            Dbg.w("Lyrics.load", e)
            emptyList()
        }
    }

    fun parse(src: String): List<Line> {
        val out = ArrayList<Line>()
        for (raw in src.lineSequence()) {
            val ms = tag.findAll(raw).toList()
            if (ms.isEmpty()) continue
            val text = raw.substring(ms.last().range.last + 1).trim()
            for (m in ms) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val fr = m.groupValues[3]
                val frMs = when (fr.length) { 0 -> 0L; 1 -> fr.toLong() * 100; 2 -> fr.toLong() * 10; else -> fr.take(3).toLong() }
                out += Line(min * 60_000 + sec * 1000 + frMs, text)
            }
        }
        return out.sortedBy { it.timeMs }
    }

    // বর্তমান সময়ে যে লাইনটি চলছে তার ইনডেক্স (-1 = এখনও শুরু হয়নি)
    fun indexAt(lines: List<Line>, posMs: Long): Int {
        var lo = 0
        var hi = lines.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= posMs) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }
}
