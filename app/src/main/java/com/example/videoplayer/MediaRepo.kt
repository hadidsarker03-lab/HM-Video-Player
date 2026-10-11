package com.example.videoplayer

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

object MediaRepo {

    fun queryVideos(ctx: Context): List<Video> =
        query(ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)

    fun queryAudio(ctx: Context): List<Video> =
        query(ctx, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)

    private fun query(ctx: Context, collection: Uri): List<Video> {
        val list = mutableListOf<Video>()
        val projection = arrayOf("_id", "_display_name", "duration", "_size")
        try {
            ctx.contentResolver.query(collection, projection, null, null, "date_added DESC")
                ?.use { c ->
                    val idCol = c.getColumnIndexOrThrow("_id")
                    val nameCol = c.getColumnIndexOrThrow("_display_name")
                    val durCol = c.getColumnIndexOrThrow("duration")
                    val sizeCol = c.getColumnIndexOrThrow("_size")
                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        list += Video(
                            id = id,
                            uri = ContentUris.withAppendedId(collection, id),
                            title = (c.getString(nameCol) ?: "Media").substringBeforeLast('.'),
                            duration = c.getLong(durCol),
                            size = c.getLong(sizeCol)
                        )
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
