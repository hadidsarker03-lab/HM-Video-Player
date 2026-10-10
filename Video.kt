package com.example.videoplayer

import android.net.Uri
import java.util.Locale

data class Video(
    val id: Long,
    val uri: Uri,
    val title: String,
    val duration: Long,
    val size: Long
)

fun Long.toTimeString(): String {
    val total = this / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

fun Long.toSizeString(): String {
    val mb = this / (1024.0 * 1024.0)
    return if (mb >= 1024) String.format(Locale.US, "%.2f GB", mb / 1024)
    else String.format(Locale.US, "%.1f MB", mb)
}
