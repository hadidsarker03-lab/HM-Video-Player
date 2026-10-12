package com.example.videoplayer

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

object MediaRepo {

    fun queryVideos(ctx: Context): List<Video> =
        query(ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, false)

    fun queryAudio(ctx: Context): List<Video> =
        query(ctx, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true)

    private fun query(ctx: Context, collection: Uri, audio: Boolean): List<Video> {
        val base = arrayOf("_id", "_display_name", "duration", "_size", "date_added")
        val extra =
            if (audio) arrayOf("bucket_display_name")
            else arrayOf("width", "height", "bucket_display_name")
        // কিছু কলাম পুরনো ফোনে না থাকতে পারে, তাই ব্যর্থ হলে কম কলাম দিয়ে আবার চেষ্টা
        return run(ctx, collection, audio, base + extra)
            ?: run(ctx, collection, audio, base)
            ?: emptyList()
    }

    private fun run(ctx: Context, collection: Uri, audio: Boolean, projection: Array<String>): List<Video>? {
        val list = mutableListOf<Video>()
        return try {
            ctx.contentResolver.query(collection, projection, null, null, "date_added DESC")
                ?.use { c ->
                    val idCol = c.getColumnIndexOrThrow("_id")
                    val nameCol = c.getColumnIndex("_display_name")
                    val durCol = c.getColumnIndex("duration")
                    val sizeCol = c.getColumnIndex("_size")
                    val dateCol = c.getColumnIndex("date_added")
                    val wCol = c.getColumnIndex("width")
                    val hCol = c.getColumnIndex("height")
                    val folderCol = c.getColumnIndex("bucket_display_name")
                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        list += Video(
                            id = id,
                            uri = ContentUris.withAppendedId(collection, id),
                            title = (if (nameCol >= 0) c.getString(nameCol) else null)
                                ?.substringBeforeLast('.') ?: "Media",
                            duration = if (durCol >= 0) c.getLong(durCol) else 0L,
                            size = if (sizeCol >= 0) c.getLong(sizeCol) else 0L,
                            dateAdded = if (dateCol >= 0) c.getLong(dateCol) else 0L,
                            width = if (wCol >= 0) c.getInt(wCol) else 0,
                            height = if (hCol >= 0) c.getInt(hCol) else 0,
                            folder = (if (folderCol >= 0) c.getString(folderCol) else null) ?: "",
                            isAudio = audio
                        )
                    }
                    list
                }
        } catch (e: Exception) {
            null
        }
    }
}
