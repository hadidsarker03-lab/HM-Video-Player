package com.example.videoplayer

import android.content.Context

// ফোনের ভিডিও ও অডিওর তালিকা এক জায়গায় রাখা হয়
object Library {
    @Volatile var videos: List<Video> = emptyList()
    @Volatile var audios: List<Video> = emptyList()
    @Volatile var loaded = false

    fun reload(ctx: Context) {
        videos = MediaRepo.queryVideos(ctx)
        audios = MediaRepo.queryAudio(ctx)
        loaded = true
    }

    fun list(audio: Boolean): List<Video> = if (audio) audios else videos

    fun find(audio: Boolean, id: Long): Video? = list(audio).firstOrNull { it.id == id }

    // "v:123" / "a:45" থেকে ভিডিও/অডিও খুঁজে বের করে
    fun resolve(ref: String): Video? {
        if (ref.length < 3) return null
        val audio = ref.startsWith("a:")
        val id = ref.substring(2).toLongOrNull() ?: return null
        return find(audio, id)
    }
}
