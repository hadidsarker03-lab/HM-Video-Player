package com.example.videoplayer

import android.annotation.SuppressLint
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlin.math.abs

class PlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var feedback: TextView
    private lateinit var topBar: View
    private lateinit var audio: AudioManager

    private var player: ExoPlayer? = null
    private lateinit var videoUri: Uri

    // onStop-এ সেভ হয়, onStart-এ ফিরিয়ে আনা হয়
    private var savedPosition = 0L
    private var savedPlayWhenReady = true

    private val handler = Handler(Looper.getMainLooper())
    private val hideFeedback = Runnable { feedback.visibility = View.GONE }

    // জেসচার স্টেট
    private enum class Mode { NONE, SEEK, BRIGHTNESS, VOLUME }
    private var mode = Mode.NONE
    private var startPos = 0L
    private var pendingSeek = -1L
    private var volAccum = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val uriStr = intent.getStringExtra(EXTRA_URI)
        if (uriStr == null) {
            Toast.makeText(this, "ভিডিও পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        videoUri = Uri.parse(uriStr)

        savedPosition = savedInstanceState?.getLong(KEY_POS) ?: 0L
        savedPlayWhenReady = savedInstanceState?.getBoolean(KEY_PLAY) ?: true

        audio = getSystemService(AUDIO_SERVICE) as AudioManager
        playerView = findViewById(R.id.playerView)
        feedback = findViewById(R.id.feedback)
        topBar = findViewById(R.id.topBar)

        findViewById<TextView>(R.id.videoTitle).text = intent.getStringExtra(EXTRA_TITLE) ?: ""
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        playerView.keepScreenOn = true
        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { vis ->
            topBar.visibility = if (vis == View.VISIBLE) View.VISIBLE else View.GONE
        })

        setupGestures()
        enterImmersive()
    }

    override fun onStart() {
        super.onStart()
        initPlayer()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_POS, player?.currentPosition ?: savedPosition)
        outState.putBoolean(KEY_PLAY, player?.playWhenReady ?: savedPlayWhenReady)
    }

    private fun initPlayer() {
        if (player != null) return
        player = ExoPlayer.Builder(this).build().also { p ->
            playerView.player = p
            p.setMediaItem(MediaItem.fromUri(videoUri))
            p.seekTo(savedPosition)
            p.playWhenReady = savedPlayWhenReady
            p.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Toast.makeText(
                        this@PlayerActivity,
                        "ভিডিও চালানো যাচ্ছে না: ${error.errorCodeName}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
            p.prepare()
        }
    }

    private fun releasePlayer() {
        player?.let {
            savedPosition = it.currentPosition
            savedPlayWhenReady = it.playWhenReady
            it.release()
        }
        player = null
        playerView.player = null
    }

    private fun enterImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    // ---------- জেসচার কন্ট্রোল ----------
    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (playerView.isControllerFullyVisible) playerView.hideController()
                else playerView.showController()
                return true
            }

            // ডাবল ট্যাপ: বামে ১০ সেকেন্ড পিছনে, ডানে ১০ সেকেন্ড সামনে
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (e.x < playerView.width / 2f) seekBy(-10_000) else seekBy(10_000)
                return true
            }

            override fun onScroll(
                e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float
            ): Boolean {
                val start = e1 ?: return false
                val p = player ?: return false
                if (mode == Mode.NONE) {
                    mode = when {
                        abs(e2.x - start.x) > abs(e2.y - start.y) -> Mode.SEEK
                        start.x < playerView.width / 2f -> Mode.BRIGHTNESS
                        else -> Mode.VOLUME
                    }
                    startPos = p.currentPosition
                    volAccum = 0f
                }
                when (mode) {
                    Mode.SEEK -> {
                        val dur = if (p.duration == C.TIME_UNSET) Long.MAX_VALUE else p.duration
                        val delta = ((e2.x - start.x) / playerView.width * 120_000).toLong()
                        pendingSeek = (startPos + delta).coerceIn(0, dur)
                        val sign = if (delta >= 0) "+" else "−"
                        show("${pendingSeek.toTimeString()}\n($sign${abs(delta) / 1000}s)")
                    }
                    Mode.BRIGHTNESS -> {
                        val lp = window.attributes
                        val cur = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                        lp.screenBrightness = (cur + dy / playerView.height).coerceIn(0.02f, 1f)
                        window.attributes = lp
                        show("🔆 ${(lp.screenBrightness * 100).toInt()}%")
                    }
                    Mode.VOLUME -> {
                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        volAccum += dy / playerView.height * max * 1.5f
                        val steps = volAccum.toInt()
                        if (steps != 0) {
                            val v = (audio.getStreamVolume(AudioManager.STREAM_MUSIC) + steps)
                                .coerceIn(0, max)
                            audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                            volAccum -= steps
                        }
                        val now = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                        show("🔊 ${now * 100 / max}%")
                    }
                    else -> {}
                }
                return true
            }
        })

        playerView.setOnTouchListener { _, ev ->
            detector.onTouchEvent(ev)
            if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) {
                endScroll()
            }
            true
        }
    }

    private fun endScroll() {
        if (mode == Mode.SEEK && pendingSeek >= 0) player?.seekTo(pendingSeek)
        mode = Mode.NONE
        pendingSeek = -1L
        handler.removeCallbacks(hideFeedback)
        handler.postDelayed(hideFeedback, 600)
    }

    private fun seekBy(ms: Long) {
        val p = player ?: return
        val dur = if (p.duration == C.TIME_UNSET) Long.MAX_VALUE else p.duration
        p.seekTo((p.currentPosition + ms).coerceIn(0, dur))
        show(if (ms > 0) "⏩ +${ms / 1000}s" else "⏪ −${abs(ms) / 1000}s")
        handler.removeCallbacks(hideFeedback)
        handler.postDelayed(hideFeedback, 600)
    }

    private fun show(text: String) {
        handler.removeCallbacks(hideFeedback)
        feedback.text = text
        feedback.visibility = View.VISIBLE
    }

    companion object {
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_TITLE = "extra_title"
        private const val KEY_POS = "key_pos"
        private const val KEY_PLAY = "key_play"
    }
}
