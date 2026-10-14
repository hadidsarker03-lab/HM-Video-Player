package com.example.videoplayer

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

// ব্যাকগ্রাউন্ড প্লেব্যাক সার্ভিস: প্লেয়ার এখানে থাকে, তাই অ্যাপ মিনিমাইজ বা স্ক্রিন বন্ধ হলেও চলতে থাকে।
// নোটিফিকেশনে Play/Pause, Previous, Next নিজে থেকে আসে।
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // HW: শুধু হার্ডওয়্যার ডিকোডার; HW+: হার্ডওয়্যার আগে, না পারলে সফটওয়্যারে ফলব্যাক
        val appCtx = applicationContext
        val selector = MediaCodecSelector { mime, secure, tunneling ->
            val all: List<MediaCodecInfo> = MediaCodecUtil.getDecoderInfos(mime, secure, tunneling)
            if (!mime.startsWith("video/")) {
                all
            } else {
                val hw = all.filter { it.hardwareAccelerated && !it.softwareOnly }
                val sw = all.filter { !(it.hardwareAccelerated && !it.softwareOnly) }
                if (AppSettings.hwMode(appCtx) == AppSettings.HW_PLUS || hw.isEmpty()) hw + sw else hw
            }
        }
        val renderers = DefaultRenderersFactory(this)
            .setMediaCodecSelector(selector)
            .setEnableDecoderFallback(true)
        val player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        current = player
        AudioFx.auxHook = { id -> player.setAuxEffectInfo(AuxEffectInfo(id, 1f)) }
        // ইকুয়ালাইজার/অডিও ইফেক্ট অডিও সেশনের সাথে যুক্ত হয়
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                AudioFx.attach(applicationContext, audioSessionId)
            }
        })
        AudioFx.attach(applicationContext, player.audioSessionId)

        // নোটিফিকেশনে চাপলে চলমান প্লেয়ার আবার খোলে
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_RESUME, true)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pending)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    // অ্যাপ রিসেন্ট থেকে সরালে, কিছু না চললে সার্ভিস বন্ধ হয়
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            player?.pause()
            stopSelf()
        }
    }

    override fun onDestroy() {
        SleepTimer.cancel()
        AudioFx.release()
        current = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        // একই প্রসেস থেকে (স্লিপ টাইমার) চলমান প্লেয়ারে পৌঁছানোর জন্য
        @Volatile
        var current: Player? = null
    }
}
