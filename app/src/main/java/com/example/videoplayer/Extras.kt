package com.example.videoplayer

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast

// ---------- রিসাইকেল বিন (Android 11+ সিস্টেম ট্র্যাশ) ----------
object RecycleBin {
    class Entry(val uri: Uri, val name: String, val video: Boolean)

    fun list(ctx: Context): List<Entry> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        val out = ArrayList<Entry>()
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?,?)"
            )
            putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                    MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO.toString()
                )
            )
        }
        try {
            ctx.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.MEDIA_TYPE
                ),
                args, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val isVideo = c.getInt(2) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    val base = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    out.add(Entry(ContentUris.withAppendedId(base, id), c.getString(1) ?: id.toString(), isVideo))
                }
            }
        } catch (e: Exception) { Dbg.w("Extras.list", e) }
        return out
    }

    // রিসাইকেল বিন স্ক্রিন (Video / Music ট্যাব, Restore ও Clean বাটন)
    fun show(act: MediaActivity, onDone: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(act, R.string.bin_need_r, Toast.LENGTH_LONG).show()
            return
        }
        act.startActivity(android.content.Intent(act, RecycleBinActivity::class.java))
    }
}
