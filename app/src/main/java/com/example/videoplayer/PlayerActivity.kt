package com.example.videoplayer

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import kotlin.math.abs

class PlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var feedback: TextView
    private lateinit var feedbackBox: View
    private lateinit var feedbackIcon: ImageView
    private lateinit var audioArt: View
    private lateinit var topBar: View
    private lateinit var bottomPanel: View
    private lateinit var titleView: TextView
    private lateinit var aspectLabel: TextView
    private lateinit var rotateLabel: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var posText: TextView
    private lateinit var durText: TextView
    private lateinit var btnPlay: ImageView
    private lateinit var btnPrev: ImageView
    private lateinit var btnNext: ImageView
    private lateinit var audio: AudioManager

    // প্লেয়ার চলে PlaybackService-এ; এখানে MediaController দিয়ে সংযোগ করা হয়
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var player: Player? = null
    private var isAudio = false
    private var needLoad = true
    private var audioFlags = BooleanArray(0)
    private var tickCount = 0

    // প্লেলিস্ট
    private var uris: List<String> = emptyList()
    private var titles: List<String> = emptyList()

    private var savedIndex = 0
    private var savedPosition = 0L
    private var savedPlayWhenReady = true

    private val handler = Handler(Looper.getMainLooper())
    private val hideFeedback = Runnable { feedbackBox.visibility = View.GONE }

    // কন্ট্রোল প্যানেল
    private var controlsVisible = true
    private var dragging = false
    private val autoHide = Runnable {
        if (!isAudio && player?.isPlaying == true && !dragging) hideControls() else scheduleAutoHide()
    }
    private val ticker = object : Runnable {
        override fun run() {
            updateProgress()
            tickCount++
            if (tickCount % 10 == 0) savePosition()
            handler.postDelayed(this, 500)
        }
    }

    // ফ্রেম মোড: Full / Stretch / Crop
    private val aspectModes = intArrayOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    )
    private val aspectNames by lazy {
        arrayOf(
            getString(R.string.aspect_full),
            getString(R.string.aspect_stretch),
            getString(R.string.aspect_crop)
        )
    }
    private var aspectIndex = 0

    // রোটেশন মোড: Auto / Landscape / Portrait
    private val rotationModes = intArrayOf(
        ActivityInfo.SCREEN_ORIENTATION_SENSOR,
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    )
    private val rotationNames by lazy {
        arrayOf(
            getString(R.string.rot_auto),
            getString(R.string.rot_landscape),
            getString(R.string.rot_portrait)
        )
    }
    private var rotationIndex = 0

    // জেসচার স্টেট
    private enum class Mode { NONE, SEEK, BRIGHTNESS, VOLUME }
    private var mode = Mode.NONE
    private var startPos = 0L
    private var pendingSeek = -1L
    private var volAccum = 0f

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Toast.makeText(
                this@PlayerActivity,
                getString(R.string.err_play, error.errorCodeName),
                Toast.LENGTH_LONG
            ).show()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            btnPlay.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            onItemChanged(mediaItem)
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                !AppSettings.continuous(this@PlayerActivity)
            ) {
                player?.pause()
            }
            updateProgress()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val resume = intent.getBooleanExtra(EXTRA_RESUME, false)
        isAudio = intent.getBooleanExtra(EXTRA_AUDIO, false)

        val list = intent.getStringArrayListExtra(EXTRA_URIS)
        if (list != null && list.isNotEmpty()) {
            uris = list
            titles = intent.getStringArrayListExtra(EXTRA_TITLES) ?: list
            savedIndex = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, list.size - 1)
        } else {
            val single = intent.getStringExtra(EXTRA_URI)
            if (single != null) {
                uris = listOf(single)
                titles = listOf(intent.getStringExtra(EXTRA_TITLE) ?: "")
                savedIndex = 0
            } else if (!resume) {
                Toast.makeText(this, getString(R.string.err_not_found), Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        }

        audioFlags = intent.getBooleanArrayExtra(EXTRA_FLAGS) ?: BooleanArray(0)
        if (audioFlags.isNotEmpty()) isAudio = audioFlags.getOrElse(savedIndex) { isAudio }

        // নতুন করে খুললে প্লেলিস্ট লোড হয়; নোটিফিকেশন বা রিস্টোর থেকে এলে চলমান প্লেয়ারেই যুক্ত হয়
        needLoad = savedInstanceState == null && !resume
        if (savedInstanceState != null) {
            savedIndex = savedInstanceState.getInt(KEY_INDEX, savedIndex).coerceAtLeast(0)
            savedPosition = savedInstanceState.getLong(KEY_POS, 0L)
            savedPlayWhenReady = savedInstanceState.getBoolean(KEY_PLAY, true)
        }

        audio = getSystemService(AUDIO_SERVICE) as AudioManager
        playerView = findViewById(R.id.playerView)
        feedback = findViewById(R.id.feedback)
        feedbackBox = findViewById(R.id.feedbackBox)
        feedbackIcon = findViewById(R.id.feedbackIcon)
        audioArt = findViewById(R.id.audioArt)
        topBar = findViewById(R.id.topBar)
        bottomPanel = findViewById(R.id.bottomPanel)
        titleView = findViewById(R.id.videoTitle)
        seekBar = findViewById(R.id.seekBar)
        posText = findViewById(R.id.posText)
        durText = findViewById(R.id.durText)
        btnPlay = findViewById(R.id.btnPlay)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        aspectLabel = findViewById(R.id.aspectLabel)
        rotateLabel = findViewById(R.id.rotateLabel)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        playerView.keepScreenOn = true

        applyAudioMode()
        setupControls()
        setupGestures()
        enterImmersive()
        applyCutoutInsets()
    }

    override fun onStart() {
        super.onStart()
        if (!::playerView.isInitialized) return
        connectController()
        handler.post(ticker)
        scheduleAutoHide()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(ticker)
        releaseController()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        releaseController()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_INDEX, player?.currentMediaItemIndex ?: savedIndex)
        outState.putLong(KEY_POS, player?.currentPosition ?: savedPosition)
        outState.putBoolean(KEY_PLAY, player?.playWhenReady ?: savedPlayWhenReady)
    }

    // ---------- প্লেয়ার সংযোগ (ব্যাকগ্রাউন্ড সার্ভিসের সাথে) ----------
    private fun connectController() {
        if (controllerFuture != null) return
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({ onControllerReady(future) }, ContextCompat.getMainExecutor(this))
    }

    private fun onControllerReady(future: ListenableFuture<MediaController>) {
        if (controllerFuture !== future) return // এর মধ্যে অ্যাক্টিভিটি থেমে গেছে
        val c = try {
            future.get()
        } catch (e: Exception) {
            Toast.makeText(
                this, getString(R.string.err_play, e.javaClass.simpleName), Toast.LENGTH_LONG
            ).show()
            return
        }
        player = c
        playerView.player = c
        c.addListener(playerListener)

        if (needLoad || c.mediaItemCount == 0) {
            if (uris.isEmpty()) {
                finish()
                return
            }
            loadItems(c)
        }
        onItemChanged(c.currentMediaItem)
        btnPlay.setImageResource(
            if (c.isPlaying || c.playWhenReady) R.drawable.ic_pause else R.drawable.ic_play
        )
        updateProgress()
        scheduleAutoHide()
    }

    private fun loadItems(c: MediaController) {
        val items = uris.mapIndexed { i, u ->
            val extras = Bundle().apply { putBoolean(META_AUDIO, audioFlags.getOrElse(i) { isAudio }) }
            MediaItem.Builder()
                .setMediaId(u)
                .setUri(Uri.parse(u))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(titles.getOrElse(i) { "" })
                        .setExtras(extras)
                        .build()
                )
                .build()
        }
        val start = savedIndex.coerceIn(0, items.size - 1)
        var pos = savedPosition
        // আগের জায়গা থেকে চালানো (শুধু ভিডিও)
        if (pos == 0L && AppSettings.resume(this) && !audioFlags.getOrElse(start) { isAudio }) {
            val saved = Store.getPos(this, uris[start])
            if (saved > 5_000) pos = saved
        }
        c.setMediaItems(items, start, pos)
        c.playWhenReady = savedPlayWhenReady
        c.prepare()
        needLoad = false
    }

    private fun onItemChanged(item: MediaItem?) {
        titleView.text = item?.mediaMetadata?.title ?: ""
        val audioFlag = item?.mediaMetadata?.extras?.getBoolean(META_AUDIO, false)
        if (audioFlag != null && audioFlag != isAudio) {
            isAudio = audioFlag
            applyAudioMode()
        }
    }

    private fun applyAudioMode() {
        audioArt.visibility = if (isAudio) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnAspect).visibility = if (isAudio) View.GONE else View.VISIBLE
    }

    // স্ক্রিন ছাড়লে: ব্যাকগ্রাউন্ড প্লে চালু থাকলে গান/ভিডিওর শব্দ চলতে থাকে
    private fun releaseController() {
        val c = player
        if (c != null) {
            savePosition()
            savedIndex = c.currentMediaItemIndex
            savedPosition = c.currentPosition
            savedPlayWhenReady = c.playWhenReady
            c.removeListener(playerListener)
            // ব্যাক চাপলে ভিডিও থামে; গান ব্যাকগ্রাউন্ড চালু থাকলে চলতে থাকে
            val keepPlaying = AppSettings.backgroundPlay(this) && (!isFinishing || isAudio)
            if (!keepPlaying) {
                c.pause()
                if (isFinishing) c.clearMediaItems()
            }
        }
        if (::playerView.isInitialized) playerView.player = null
        player = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    // ভিডিওর বর্তমান অবস্থান সংরক্ষণ (শেষের কাছাকাছি হলে শুরু থেকে)
    private fun savePosition() {
        val p = player ?: return
        val item = p.currentMediaItem ?: return
        if (item.mediaMetadata.extras?.getBoolean(META_AUDIO, false) == true) return
        val dur = p.duration
        val pos = p.currentPosition
        val value = if (dur != C.TIME_UNSET && dur > 0 && pos > dur - 5_000) 0L else pos
        Store.savePos(this, item.mediaId, value)
    }

    private fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
        } else {
            if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
            p.play()
        }
    }

    // ---------- কন্ট্রোল বাটন ও সিকবার ----------
    private fun setupControls() {
        btnPlay.setOnClickListener { togglePlay(); scheduleAutoHide() }

        btnPrev.setOnClickListener {
            player?.let { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() else it.seekTo(0) }
            scheduleAutoHide()
        }
        btnNext.setOnClickListener {
            player?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
            scheduleAutoHide()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) posText.text = progress.toLong().toTimeString()
            }

            override fun onStartTrackingTouch(sb: SeekBar) {
                dragging = true
                handler.removeCallbacks(autoHide)
            }

            override fun onStopTrackingTouch(sb: SeekBar) {
                player?.seekTo(sb.progress.toLong())
                dragging = false
                scheduleAutoHide()
            }
        })

        aspectIndex = getSharedPreferences("settings", MODE_PRIVATE)
            .getInt("aspect", 0).coerceIn(0, aspectModes.size - 1)
        applyAspect(false)
        findViewById<View>(R.id.btnAspect).setOnClickListener {
            aspectIndex = (aspectIndex + 1) % aspectModes.size
            getSharedPreferences("settings", MODE_PRIVATE).edit()
                .putInt("aspect", aspectIndex).apply()
            applyAspect(true)
            scheduleAutoHide()
        }

        rotationIndex = getSharedPreferences("settings", MODE_PRIVATE)
            .getInt("rotation", 0).coerceIn(0, rotationModes.size - 1)
        applyRotation(false)
        findViewById<View>(R.id.btnRotate).setOnClickListener {
            rotationIndex = (rotationIndex + 1) % rotationModes.size
            getSharedPreferences("settings", MODE_PRIVATE).edit()
                .putInt("rotation", rotationIndex).apply()
            applyRotation(true)
            scheduleAutoHide()
        }
    }

    private fun updateProgress() {
        val p = player ?: return
        btnPrev.alpha = if (p.hasPreviousMediaItem()) 1f else 0.4f
        btnNext.alpha = if (p.hasNextMediaItem()) 1f else 0.4f
        if (!controlsVisible || dragging) return
        val dur = if (p.duration == C.TIME_UNSET) 0L else p.duration.coerceAtLeast(0L)
        seekBar.max = dur.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        seekBar.progress = p.currentPosition.coerceIn(0L, dur).toInt()
        seekBar.secondaryProgress = p.bufferedPosition.coerceIn(0L, dur).toInt()
        posText.text = p.currentPosition.coerceAtLeast(0L).toTimeString()
        durText.text = dur.toTimeString()
    }

    // ---------- অ্যানিমেটেড কন্ট্রোল প্যানেল (ভিডিও ফ্রেম অপরিবর্তিত থাকে) ----------
    private fun scheduleAutoHide() {
        handler.removeCallbacks(autoHide)
        handler.postDelayed(autoHide, 3500)
    }

    private fun showControls() {
        controlsVisible = true
        updateProgress()
        topBar.animate().cancel()
        bottomPanel.animate().cancel()
        topBar.visibility = View.VISIBLE
        bottomPanel.visibility = View.VISIBLE
        topBar.animate().alpha(1f).translationY(0f)
            .setDuration(240).setInterpolator(DecelerateInterpolator()).start()
        bottomPanel.animate().alpha(1f).translationY(0f)
            .setDuration(240).setInterpolator(DecelerateInterpolator()).start()
        scheduleAutoHide()
    }

    private fun hideControls() {
        controlsVisible = false
        handler.removeCallbacks(autoHide)
        topBar.animate().cancel()
        bottomPanel.animate().cancel()
        topBar.animate().alpha(0f).translationY(-topBar.height * 0.5f)
            .setDuration(220).withEndAction {
                if (!controlsVisible) topBar.visibility = View.GONE
            }.start()
        bottomPanel.animate().alpha(0f).translationY(bottomPanel.height * 0.5f)
            .setDuration(220).withEndAction {
                if (!controlsVisible) bottomPanel.visibility = View.GONE
            }.start()
    }

    private fun applyAspect(announce: Boolean) {
        playerView.resizeMode = aspectModes[aspectIndex]
        aspectLabel.text = aspectNames[aspectIndex]
        if (announce) {
            show(aspectNames[aspectIndex], R.drawable.ic_aspect)
            handler.removeCallbacks(hideFeedback)
            handler.postDelayed(hideFeedback, 700)
        }
    }

    private fun applyRotation(announce: Boolean) {
        requestedOrientation = rotationModes[rotationIndex]
        rotateLabel.text = rotationNames[rotationIndex]
        if (announce) {
            show(rotationNames[rotationIndex], R.drawable.ic_rotate)
            handler.removeCallbacks(hideFeedback)
            handler.postDelayed(hideFeedback, 700)
        }
    }

    // ক্যামেরা/নচের অংশেও ভিডিও দেখায়; কন্ট্রোল থাকে নিরাপদ অংশের ভেতরে
    private fun applyCutoutInsets() {
        val root = findViewById<View>(R.id.playerRoot)
        val topBase = intArrayOf(topBar.paddingLeft, topBar.paddingRight)
        val panelBase = intArrayOf(bottomPanel.paddingLeft, bottomPanel.paddingRight)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val i = insets.getInsets(
                WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.systemBars()
            )
            topBar.setPadding(
                topBase[0] + i.left, topBar.paddingTop, topBase[1] + i.right, topBar.paddingBottom
            )
            bottomPanel.setPadding(
                panelBase[0] + i.left, bottomPanel.paddingTop,
                panelBase[1] + i.right, bottomPanel.paddingBottom
            )
            insets
        }
    }

    private fun enterImmersive() {
        // নচ/ক্যামেরার জায়গাতেও কনটেন্ট আঁকা হবে (Android 9+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
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
                if (isAudio) return true
                if (controlsVisible) hideControls() else showControls()
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
                        show("${(lp.screenBrightness * 100).toInt()}%", R.drawable.ic_brightness)
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
                        show("${now * 100 / max}%", R.drawable.ic_volume)
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
        show(
            if (ms > 0) "+${ms / 1000}s" else "−${abs(ms) / 1000}s",
            if (ms > 0) R.drawable.ic_forward else R.drawable.ic_rewind
        )
        handler.removeCallbacks(hideFeedback)
        handler.postDelayed(hideFeedback, 600)
    }

    private fun show(text: String, icon: Int? = null) {
        handler.removeCallbacks(hideFeedback)
        feedback.text = text
        if (icon != null) {
            feedbackIcon.setImageResource(icon)
            feedbackIcon.visibility = View.VISIBLE
        } else {
            feedbackIcon.visibility = View.GONE
        }
        feedbackBox.visibility = View.VISIBLE
    }

    companion object {
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_URIS = "extra_uris"
        const val EXTRA_TITLES = "extra_titles"
        const val EXTRA_INDEX = "extra_index"
        const val EXTRA_AUDIO = "extra_audio"
        const val EXTRA_FLAGS = "extra_flags"
        const val EXTRA_RESUME = "extra_resume"
        private const val META_AUDIO = "meta_audio"
        private const val KEY_INDEX = "key_index"
        private const val KEY_POS = "key_pos"
        private const val KEY_PLAY = "key_play"
    }
}
