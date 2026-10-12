package com.example.videoplayer

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Player

// প্লেয়ার স্ক্রিনের শর্টকাট তালিকা (ক্রম স্ক্রিনশটের মতো)
enum class Shortcut(val key: String, val icon: Int, val label: Int) {
    NIGHT("night", R.drawable.ic_night, R.string.sc_night),
    CUSTOMISE("customise", R.drawable.ic_edit, R.string.sc_customise),
    SHUFFLE("shuffle", R.drawable.ic_shuffle, R.string.sc_shuffle),
    LOOP("loop", R.drawable.ic_repeat, R.string.sc_loop),
    MUTE("mute", R.drawable.ic_volume_off, R.string.sc_mute),
    SLEEP("sleep", R.drawable.ic_timer, R.string.sc_sleep),
    AB("ab", R.drawable.ic_ab_repeat, R.string.sc_ab),
    AUDIO_FX("audio_fx", R.drawable.ic_audio_fx, R.string.sc_audio_fx),
    EQ("eq", R.drawable.ic_equalizer, R.string.sc_eq),
    SPEED("speed", R.drawable.ic_speed_dummy, R.string.sc_speed),
    SCREENSHOT("screenshot", R.drawable.ic_screenshot, R.string.sc_screenshot),
    BGPLAY("bgplay", R.drawable.ic_headset, R.string.sc_bgplay),
    ROTATION("rotation", R.drawable.ic_rotate, R.string.sc_rotation),
    MIRROR("mirror", R.drawable.ic_flip, R.string.sc_mirror),
    VFLIP("vflip", R.drawable.ic_flip_v, R.string.sc_vflip);
}

// ইকুয়ালাইজার, বাস বুস্ট, ভার্চুয়ালাইজার, লাউডনেস।
// প্লেয়ার চলে PlaybackService-এ (একই প্রসেস), তাই এখান থেকেই অডিও সেশনে যুক্ত হয়।
object AudioFx {
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null
    private var loud: LoudnessEnhancer? = null
    private var session = C.AUDIO_SESSION_ID_UNSET

    fun attach(ctx: Context, sessionId: Int) {
        if (sessionId == session && eq != null) return
        release()
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
        session = sessionId
        eq = try { Equalizer(0, sessionId) } catch (e: Exception) { null }
        bass = try { BassBoost(0, sessionId) } catch (e: Exception) { null }
        virt = try { Virtualizer(0, sessionId) } catch (e: Exception) { null }
        loud = try { LoudnessEnhancer(sessionId) } catch (e: Exception) { null }
        applyAll(ctx)
    }

    fun release() {
        try { eq?.release() } catch (e: Exception) { }
        try { bass?.release() } catch (e: Exception) { }
        try { virt?.release() } catch (e: Exception) { }
        try { loud?.release() } catch (e: Exception) { }
        eq = null
        bass = null
        virt = null
        loud = null
        session = C.AUDIO_SESSION_ID_UNSET
    }

    fun eqSupported() = eq != null
    fun bassSupported() = bass?.strengthSupported == true
    fun virtSupported() = virt?.strengthSupported == true
    fun loudSupported() = loud != null

    fun applyAll(ctx: Context) {
        val e = eq
        if (e != null) {
            try {
                val saved = AppSettings.eqLevels(ctx)
                for (i in 0 until e.numberOfBands.toInt()) {
                    val v = saved.getOrNull(i) ?: continue
                    e.setBandLevel(i.toShort(), v.toShort())
                }
                e.enabled = AppSettings.eqOn(ctx)
            } catch (ex: Exception) { }
        }
        val b = bass
        if (b != null) {
            try {
                val v = AppSettings.fx(ctx, "bass")
                if (b.strengthSupported) b.setStrength(v.toShort())
                b.enabled = v > 0
            } catch (ex: Exception) { }
        }
        val vr = virt
        if (vr != null) {
            try {
                val v = AppSettings.fx(ctx, "virt")
                if (vr.strengthSupported) vr.setStrength(v.toShort())
                vr.enabled = v > 0
            } catch (ex: Exception) { }
        }
        val l = loud
        if (l != null) {
            try {
                val v = AppSettings.fx(ctx, "loud")
                l.setTargetGain(v)
                l.enabled = v > 0
            } catch (ex: Exception) { }
        }
    }

    // ---- ইকুয়ালাইজার ব্যান্ড ----
    class Band(val index: Int, val freqHz: Int, val level: Int)

    fun bandRange(): IntArray {
        val r = eq?.bandLevelRange ?: return intArrayOf(-1500, 1500)
        return intArrayOf(r[0].toInt(), r[1].toInt())
    }

    fun bands(): List<Band> {
        val e = eq ?: return emptyList()
        return try {
            (0 until e.numberOfBands.toInt()).map {
                Band(it, e.getCenterFreq(it.toShort()) / 1000, e.getBandLevel(it.toShort()).toInt())
            }
        } catch (ex: Exception) {
            emptyList()
        }
    }

    fun setBand(ctx: Context, index: Int, level: Int) {
        val e = eq ?: return
        try {
            e.setBandLevel(index.toShort(), level.toShort())
            saveLevels(ctx)
        } catch (ex: Exception) { }
    }

    fun presets(): List<String> {
        val e = eq ?: return emptyList()
        return try {
            (0 until e.numberOfPresets.toInt()).map { e.getPresetName(it.toShort()) }
        } catch (ex: Exception) {
            emptyList()
        }
    }

    fun usePreset(ctx: Context, index: Int) {
        val e = eq ?: return
        try {
            e.usePreset(index.toShort())
            saveLevels(ctx)
        } catch (ex: Exception) { }
    }

    private fun saveLevels(ctx: Context) {
        AppSettings.setEqLevels(ctx, bands().map { it.level })
    }
}

// স্লিপ টাইমার: অ্যাক্টিভিটি বন্ধ হলেও চলে (প্লেয়ার সার্ভিসে থাকে)
object SleepTimer {
    private val handler = Handler(Looper.getMainLooper())
    private var endAt = 0L
    private var onEnd: (() -> Unit)? = null

    private val fire = Runnable {
        endAt = 0L
        PlaybackService.current?.pause()
        onEnd?.invoke()
    }

    fun start(minutes: Int, ended: () -> Unit) {
        cancel()
        onEnd = ended
        endAt = SystemClock.elapsedRealtime() + minutes * 60_000L
        handler.postDelayed(fire, minutes * 60_000L)
    }

    fun cancel() {
        handler.removeCallbacks(fire)
        endAt = 0L
    }

    fun active() = endAt > SystemClock.elapsedRealtime()

    fun remainingMinutes(): Int =
        if (active()) ((endAt - SystemClock.elapsedRealtime() + 59_999L) / 60_000L).toInt() else 0
}

fun Player.safeSpeedLabel(): String {
    val s = playbackParameters.speed
    val txt = if (s == s.toInt().toFloat()) s.toInt().toString() else s.toString().trimEnd('0')
    return txt + "X"
}
