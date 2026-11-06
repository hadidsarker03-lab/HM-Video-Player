package com.example.videoplayer

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.ContentValues
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.provider.MediaStore
import android.util.Rational
import android.view.TextureView
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.OnBackPressedCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
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
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.VideoSize
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
    private lateinit var audioCover: ImageView
    private lateinit var audioTitle: TextView
    private lateinit var audioArtist: TextView
    private var coverJob = 0
    private lateinit var topBar: View
    private lateinit var bottomPanel: View
    private lateinit var titleView: TextView
    private lateinit var shortcutRow: LinearLayout
    private lateinit var shortcutScroll: View
    private lateinit var nightOverlay: View
    private lateinit var morePanel: View
    private lateinit var morePanelContent: LinearLayout
    private lateinit var btnLock: ImageView
    private lateinit var btnUnlock: ImageView
    private lateinit var hwLabel: TextView
    private lateinit var volumePill: LevelPill
    private lateinit var brightPill: LevelPill
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

    // বাইরের সাবটাইটেল ফাইল (.srt/.vtt/.ass) বাছাই
    private val subtitlePicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) addExternalSubtitle(uri) }

    private fun addExternalSubtitle(uri: Uri) {
        val c = player ?: return
        val cur = c.currentMediaItem ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) { Dbg.w("PlayerActivity.addExternalSubtitle", e) }
        var name = uri.lastPathSegment ?: ""
        try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) name = it.getString(0) ?: name }
        } catch (e: Exception) { Dbg.w("PlayerActivity.addExternalSubtitle", e) }
        val lower = name.lowercase()
        val mime = when {
            lower.endsWith(".vtt") -> androidx.media3.common.MimeTypes.TEXT_VTT
            lower.endsWith(".ass") || lower.endsWith(".ssa") -> androidx.media3.common.MimeTypes.TEXT_SSA
            lower.endsWith(".ttml") || lower.endsWith(".xml") -> androidx.media3.common.MimeTypes.APPLICATION_TTML
            else -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
        }
        try {
            val sub = MediaItem.SubtitleConfiguration.Builder(uri)
                .setMimeType(mime)
                .setLanguage("und")
                .setLabel(name)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            val item = MediaItem.Builder()
                .setMediaId(cur.mediaId)
                .setUri(Uri.parse(cur.mediaId))
                .setMediaMetadata(cur.mediaMetadata)
                .setSubtitleConfigurations(listOf(sub))
                .build()
            val idx = c.currentMediaItemIndex
            val pos = c.currentPosition
            val play = c.playWhenReady
            c.replaceMediaItem(idx, item)
            c.seekTo(idx, pos)
            c.playWhenReady = play
            Toast.makeText(this, R.string.sub_added, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.sub_failed, Toast.LENGTH_SHORT).show()
        }
    }
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
    private val hideLevels = Runnable {
        if (::volumePill.isInitialized && volumePill.visibility == View.VISIBLE) volumePill.slideOut()
        if (::brightPill.isInitialized && brightPill.visibility == View.VISIBLE) brightPill.slideOut()
    }

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

    // ফ্রেম মোড: Fill screen (ক্যামেরা/নচসহ পুরো ডিসপ্লে ভরে) / Fit / Stretch
    private val aspectNames by lazy {
        arrayOf(
            getString(R.string.aspect_fill),
            getString(R.string.aspect_fit),
            getString(R.string.aspect_stretch)
        )
    }
    private var aspectIndex = 0

    // নতুন ফিচারের অবস্থা
    private var locked = false
    private var inPip = false
    private var nightOn = false
    private var mirrorOn = false
    private var flipOn = false
    private var abA = -1L
    private var abB = -1L
    private var cutting = false
    private val shortcutViews = HashMap<Shortcut, View>()
    private var favCell: View? = null
    private val hideUnlock = Runnable { if (locked) btnUnlock.visibility = View.GONE }
    private val abRun = object : Runnable {
        override fun run() {
            val p = player
            if (p != null && abA >= 0 && abB > abA) {
                if (p.currentPosition >= abB || p.currentPosition < abA - 300) p.seekTo(abA)
                handler.postDelayed(this, 120)
            }
        }
    }

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
    private var volLevel = 0f

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Toast.makeText(
                this@PlayerActivity,
                getString(R.string.err_play, error.errorCodeName),
                Toast.LENGTH_LONG
            ).show()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Anim.swapIcon(btnPlay, if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        }

        override fun onRepeatModeChanged(repeatMode: Int) = updateShortcutStates()
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = updateShortcutStates()
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) =
            updateShortcutStates()
        override fun onVolumeChanged(volume: Float) = updateShortcutStates()
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (aspectIndex == 0) applyAspect(false)
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
        // কনটেন্ট দেখানোর আগেই নচ/ক্যামেরা এলাকা ও ফুলস্ক্রিন সেট (নইলে কালো পাশ থেকে যায়)
        enterImmersive()
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
        if (AppSettings.subtitleCustom(this)) {
            playerView.subtitleView?.apply {
                setApplyEmbeddedStyles(true)
                setUserDefaultStyle()
                setUserDefaultTextSize()
            }
        }
        setupClock()
        feedback = findViewById(R.id.feedback)
        feedbackBox = findViewById(R.id.feedbackBox)
        feedbackIcon = findViewById(R.id.feedbackIcon)
        audioArt = findViewById(R.id.audioArt)
        audioCover = findViewById(R.id.audioCover)
        audioTitle = findViewById(R.id.audioTitle)
        audioArtist = findViewById(R.id.audioArtist)
        findViewById<View>(R.id.audioBtnTimer).setOnClickListener {
            PlayerDialogs.sleep(this) { updateShortcutStates() }
        }
        findViewById<View>(R.id.audioBtnEq).setOnClickListener {
            PlayerDialogs.equalizer(this) { updateShortcutStates() }
        }
        findViewById<View>(R.id.audioBtnFav).setOnClickListener { toggleFavourite() }
        findViewById<View>(R.id.audioBtnQueue).setOnClickListener {
            player?.let { PlayerDialogs.queue(this, it) }
        }
        topBar = findViewById(R.id.topBar)
        bottomPanel = findViewById(R.id.bottomPanel)
        titleView = findViewById(R.id.videoTitle)
        seekBar = findViewById(R.id.seekBar)
        posText = findViewById(R.id.posText)
        durText = findViewById(R.id.durText)
        btnPlay = findViewById(R.id.btnPlay)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        shortcutRow = findViewById(R.id.shortcutRow)
        shortcutScroll = findViewById(R.id.shortcutScroll)
        nightOverlay = findViewById(R.id.nightOverlay)
        morePanel = findViewById(R.id.morePanel)
        morePanelContent = findViewById(R.id.morePanelContent)
        btnLock = findViewById(R.id.btnLock)
        btnUnlock = findViewById(R.id.btnUnlock)
        hwLabel = findViewById(R.id.hwLabel)
        volumePill = findViewById(R.id.volumePill)
        brightPill = findViewById(R.id.brightPill)
        volumePill.setSide(true)
        brightPill.setSide(false)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        playerView.keepScreenOn = true

        applyAudioMode()
        setupControls()
        setupTopBar()
        buildShortcuts()
        buildMorePanel()
        setupGestures()
        enterImmersive()
        applyCutoutInsets()

        nightOn = AppSettings.nightMode(this)
        nightOverlay.visibility = if (nightOn) View.VISIBLE else View.GONE
        updateShortcutStates()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    locked -> revealUnlock()
                    morePanel.visibility == View.VISIBLE -> hidePanel()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
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
        handler.removeCallbacks(abRun)
        releaseController()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        vinylAnim?.cancel()
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
        // ফিরে এলে ভিডিও ট্র্যাক আবার চালু
        c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).build()
        // সার্ভিসটা স্টার্টেড রাখি, যাতে স্ক্রিন বন্ধ/অ্যাপ মিনিমাইজেও প্লেব্যাক না থামে
        try { startService(Intent(this, PlaybackService::class.java)) } catch (e: Exception) { Dbg.w("PlayerActivity.onControllerReady", e) }
        askBatteryOnce()
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
        applyVideoDisplay()
        applyFlip()
        updateShortcutStates()
        if (abA >= 0 && abB > abA) handler.post(abRun)
        Anim.swapIcon(btnPlay, 
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
        if (zoom != 1f) {
            zoom = 1f
            applyFlip()
        }
        titleView.text = item?.mediaMetadata?.title ?: ""
        audioTitle.text = item?.mediaMetadata?.title ?: ""
        val artist = item?.mediaMetadata?.artist?.toString() ?: ""
        audioArtist.text = artist
        audioArtist.visibility = if (artist.isEmpty()) View.GONE else View.VISIBLE
        loadCover(item?.localConfiguration?.uri)
        refreshPanel()
        // নতুন ভিডিওতে গেলে A - B রিপিট বন্ধ হয়
        if (abA >= 0) {
            abA = -1L
            abB = -1L
            handler.removeCallbacks(abRun)
            updateShortcutStates()
        }
        val audioFlag = item?.mediaMetadata?.extras?.getBoolean(META_AUDIO, false)
        if (audioFlag != null && audioFlag != isAudio) {
            isAudio = audioFlag
            applyAudioMode()
        }
    }

    private fun applyAudioMode() {
        val audioOnlyVideo = !isAudio && !AppSettings.videoDisplay(this)
        audioArt.visibility = if (isAudio) View.VISIBLE else View.GONE
        updateVinyl(audioOnlyVideo)
        findViewById<View>(R.id.btnAspect).visibility = if (isAudio) View.GONE else View.VISIBLE
        if (::sideButtons.isInitialized) {
            sideButtons.visibility = if (isAudio || !controlsVisible) View.GONE else View.VISIBLE
        }
    }

    // "Video Display" বন্ধ থাকলে ভিডিও ট্র্যাক বন্ধ হয়, শুধু শব্দ চলে
    private fun applyVideoDisplay() {
        val p = player ?: return
        val on = AppSettings.videoDisplay(this) || isAudio
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !on).build()
        applyAudioMode()
    }

    // স্ক্রিন ছাড়লে: ব্যাকগ্রাউন্ড প্লে চালু থাকলে গান/ভিডিওর শব্দ চলতে থাকে
    // ব্যাকগ্রাউন্ড প্লে ধরে রাখতে (বিশেষ করে Xiaomi/MIUI) ব্যাটারি অপ্টিমাইজেশন ছাড় চাওয়া — একবারই
    @android.annotation.SuppressLint("BatteryLife")
    private fun askBatteryOnce() {
        try {
            val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
            val prefs = AppSettings.prefs(this)
            if (pm.isIgnoringBatteryOptimizations(packageName) || prefs.getBoolean("battery_asked", false)) return
            prefs.edit().putBoolean("battery_asked", true).apply()
            startActivity(
                Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(android.net.Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) { Dbg.w("PlayerActivity.askBatteryOnce", e) }
    }

    private fun releaseController() {
        val c = player
        if (c != null) {
            savePosition()
            savedIndex = c.currentMediaItemIndex
            savedPosition = c.currentPosition
            savedPlayWhenReady = c.playWhenReady
            c.removeListener(playerListener)
            // ব্যাক চাপলে ভিডিও থামে; গান ব্যাকগ্রাউন্ড চালু থাকলে চলতে থাকে
            val keepPlaying = isAudio || FloatingPlayer.active ||
                (AppSettings.backgroundPlay(this) && !isFinishing)
            if (!keepPlaying) {
                c.pause()
                if (isFinishing) c.clearMediaItems()
            } else {
                // ব্যাকগ্রাউন্ডে ছবির দরকার নেই: ভিডিও ট্র্যাক বন্ধ করলে শুধু শব্দ নিরবচ্ছিন্ন চলে
                if (!FloatingPlayer.active) {
                    c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
                }
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

        aspectIndex = if (AppSettings.fixedRatio(this))
            AppSettings.prefs(this).getInt("aspect2", 0).coerceIn(0, 2) else 0
        applyAspect(false)
        findViewById<View>(R.id.btnAspect).setOnClickListener {
            cycleAspect()
            scheduleAutoHide()
        }
        playerView.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (aspectIndex == 0 &&
                (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop)
            ) applyAspect(false)
        }

        rotationIndex = AppSettings.prefs(this).getInt("rotation", 0).coerceIn(0, rotationModes.size - 1)
        applyRotation(false)

        btnLock.setOnClickListener { setLocked(true) }
        btnUnlock.setOnClickListener { setLocked(false) }
        findViewById<View>(R.id.btnPip).setOnClickListener { enterPip() }
    }

    // ---------- HDR ----------
    private var hdrOn = false

    private fun applyHdr(announce: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.colorMode =
                if (hdrOn) ActivityInfo.COLOR_MODE_HDR else ActivityInfo.COLOR_MODE_DEFAULT
        }
        findViewById<TextView>(R.id.hdrText).setTextColor(if (hdrOn) panelAccent else 0xFFFFFFFF.toInt())
        if (announce) flash(getString(if (hdrOn) R.string.hdr_on else R.string.hdr_off))
    }

    // ---------- ডান পাশের বাটন (স্ক্রিনশট, রোটেট, ম্যাজিক কাট) ----------
    private lateinit var sideButtons: View

    private fun setupSideButtons() {
        sideButtons = findViewById(R.id.sideButtons)
        sideButtons.visibility = if (isAudio) View.GONE else View.VISIBLE
        // স্ক্রিনশট বাটন (আগে ভুল করে কাঁচি/কাট ছিল; কাট এখন "আরও" প্যানেলে আছে)
        findViewById<View>(R.id.btnSideCut).setOnClickListener {
            takeScreenshot()
            scheduleAutoHide()
        }
        findViewById<View>(R.id.btnSideRotate).setOnClickListener {
            onShortcut(Shortcut.ROTATION)
            scheduleAutoHide()
        }
        findViewById<View>(R.id.btnSideMagic).setOnClickListener {
            magicCut()
            scheduleAutoHide()
        }
    }

    // ম্যাজিক কাট: ১ম ট্যাপে শুরু (A), ২য় ট্যাপে শেষ (B) ও কাট ডায়ালগ
    private fun magicCut() {
        val p = player ?: return
        when {
            abA < 0 -> toggleAB()
            abB < 0 -> {
                if (p.currentPosition <= abA + 500) {
                    Toast.makeText(this, R.string.magic_set_b, Toast.LENGTH_SHORT).show()
                    return
                }
                toggleAB()
                showCut()
            }
            else -> showCut()
        }
    }

    // ---------- শুধু অডিও মোডের স্ক্রিন (ভিনাইল ডিস্ক) ----------
    private var vinylAnim: android.animation.ObjectAnimator? = null

    private fun setupAudioOnlyBox() {
        findViewById<View>(R.id.btnBackToVideo).setOnClickListener {
            AppSettings.setVideoDisplay(this, true)
            applyVideoDisplay()
        }
        findViewById<View>(R.id.btnSaveAudio).visibility = View.GONE
    }

    private fun updateVinyl(show: Boolean) {
        val box = findViewById<View>(R.id.audioOnlyBox)
        box.visibility = if (show) View.VISIBLE else View.GONE
        val disc = findViewById<View>(R.id.vinylDisc)
        if (show) {
            if (vinylAnim == null) {
                vinylAnim = android.animation.ObjectAnimator.ofFloat(disc, "rotation", 0f, 360f).apply {
                    duration = 6000
                    repeatCount = android.animation.ValueAnimator.INFINITE
                    interpolator = android.view.animation.LinearInterpolator()
                }
            }
            if (vinylAnim?.isStarted != true) vinylAnim?.start()
        } else {
            vinylAnim?.cancel()
            vinylAnim = null
        }
    }

    // ---------- উপরের বাটন ----------
    private fun setupTopBar() {
        // হেডফোন: শুধু অডিও মোড (ব্যাকগ্রাউন্ডেও চলবে)
        findViewById<View>(R.id.btnAudioTrack).setOnClickListener {
            if (isAudio) {
                player?.let { PlayerDialogs.tracks(this, it, C.TRACK_TYPE_AUDIO) }
            } else {
                AppSettings.setVideoDisplay(this, false)
                AppSettings.setBackgroundPlay(this, true)
                applyVideoDisplay()
                flash(getString(R.string.bgplay_on), R.drawable.ic_headset)
            }
            scheduleAutoHide()
        }
        // লিস্ট: প্লেয়িং কিউ
        findViewById<View>(R.id.btnQueueTop).setOnClickListener {
            player?.let { PlayerDialogs.queue(this, it) }
            scheduleAutoHide()
        }
        // HDR: HDR-সাপোর্ট করা ডিসপ্লেতে HDR কালার মোড
        hdrOn = AppSettings.prefs(this).getBoolean("hdr_on", false)
        applyHdr(false)
        findViewById<View>(R.id.btnHdr).setOnClickListener {
            hdrOn = !hdrOn
            AppSettings.prefs(this).edit().putBoolean("hdr_on", hdrOn).apply()
            applyHdr(true)
            scheduleAutoHide()
        }
        setupSideButtons()
        setupAudioOnlyBox()
        // অ্যাপ ইনস্টলের পর প্রথমবার ভিডিও প্লেয়ারে ঢুকলে টিউটোরিয়াল (একবারই)
        if (!isAudio && !AppSettings.tutorialSeen(this)) {
            AppSettings.setTutorialSeen(this)
            window.decorView.post { if (!isFinishing && !isDestroyed) PlayerDialogs.tutorial(this) }
        }
        findViewById<View>(R.id.btnSubtitle).setOnClickListener {
            val labels = arrayOf(getString(R.string.sub_choose_track), getString(R.string.sub_add_file))
            MaterialAlertDialogBuilder(this)
                .setItems(labels) { _, which ->
                    if (which == 0) {
                        player?.let { PlayerDialogs.tracks(this, it, C.TRACK_TYPE_TEXT) }
                    } else {
                        subtitlePicker.launch(arrayOf("*/*"))
                    }
                }
                .show()
            scheduleAutoHide()
        }
        updateHwLabel()
        hwLabel.setOnClickListener {
            // HW <-> HW+ বদল; নতুন ডিকোডার নিতে বর্তমান জায়গা থেকে আবার প্রস্তুত করা হয়
            val next = if (AppSettings.hwMode(this) == AppSettings.HW) AppSettings.HW_PLUS else AppSettings.HW
            AppSettings.setHwMode(this, next)
            updateHwLabel()
            Toast.makeText(
                this, if (next == AppSettings.HW) R.string.hw_toast else R.string.hw_plus_toast,
                Toast.LENGTH_SHORT
            ).show()
            player?.let { p ->
                val pos = p.currentPosition
                p.stop()
                p.prepare()
                p.seekTo(pos)
            }
            scheduleAutoHide()
        }
        findViewById<View>(R.id.btnMore).setOnClickListener { showPanel() }
    }

    private fun cycleAspect() {
        aspectIndex = (aspectIndex + 1) % 3
        AppSettings.prefs(this).edit().putInt("aspect2", aspectIndex).apply()
        applyAspect(true)
    }

    // ---------- শর্টকাট সারি ----------
    // সবসময় দেখানো শর্টকাট; বাকিগুলো " > " তীরে চাপলে অ্যানিমেশনসহ বের হয়
    private val alwaysVisible = setOf(Shortcut.SPEED, Shortcut.LOOP, Shortcut.MUTE)
    private var shortcutsExpanded = false
    private var extraRow: LinearLayout? = null
    private var arrowView: View? = null

    private fun buildShortcuts() {
        shortcutRow.removeAllViews()
        shortcutViews.clear()
        // উপরের শর্টকাট সারি স্থায়ীভাবে বন্ধ
        shortcutScroll.visibility = View.GONE
        arrowView = null
        extraRow = null
        if (true) return
        val hidden = AppSettings.hiddenShortcuts(this)
        val extra = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        var extraCount = 0
        // প্রথমে স্থির তিনটি (Shortcut তালিকার ক্রমে), তারপর তীর, তারপর বাকিগুলো
        fun make(s: Shortcut): View {
            val v = layoutInflater.inflate(R.layout.item_shortcut, shortcutRow, false)
            v.findViewById<ImageView>(R.id.scIcon).setImageResource(s.icon)
            v.findViewById<TextView>(R.id.scLabel).setText(s.label)
            v.setOnClickListener {
                onShortcut(s)
                scheduleAutoHide()
            }
            shortcutViews[s] = v
            return v
        }
        for (s in Shortcut.values()) {
            if (s.key in hidden || s !in alwaysVisible) continue
            shortcutRow.addView(make(s))
        }
        for (s in Shortcut.values()) {
            if (s.key in hidden || s in alwaysVisible) continue
            extra.addView(make(s))
            extraCount++
        }
        if (extraCount > 0) {
            val arrow = layoutInflater.inflate(R.layout.item_shortcut, shortcutRow, false)
            arrow.findViewById<ImageView>(R.id.scIcon).setImageResource(R.drawable.ic_chevron)
            arrow.findViewById<TextView>(R.id.scLabel).apply { text = ""; background = null }
            arrow.rotation = if (shortcutsExpanded) 180f else 0f
            arrow.setOnClickListener {
                toggleShortcutsExpanded()
                scheduleAutoHide()
            }
            arrow.translationZ = UiKit.dp(this, 4).toFloat()
            if (shortcutsExpanded) shortcutRow.addView(arrow, 0) else shortcutRow.addView(arrow)
            shortcutRow.addView(extra)
            Anim.attachTree(shortcutRow)
            arrowView = arrow
            extraRow = extra
            extra.visibility = if (shortcutsExpanded) View.VISIBLE else View.GONE
        } else {
            arrowView = null
            extraRow = null
        }
        updateShortcutStates()
    }

    private fun toggleShortcutsExpanded(ms: Long = 320L) {
        val extra = extraRow ?: return
        val arrow = arrowView ?: return
        shortcutsExpanded = !shortcutsExpanded
        // খুললে তীর সবার সামনে (বামে), বন্ধ করলে আগের জায়গায়
        shortcutRow.removeView(arrow)
        if (shortcutsExpanded) {
            shortcutRow.addView(arrow, 0)
            shortcutScroll.scrollTo(0, 0)
        } else {
            shortcutRow.addView(arrow, shortcutRow.indexOfChild(extra))
        }
        arrow.animate().cancel()
        arrow.animate().rotation(if (shortcutsExpanded) 180f else 0f).setDuration(250).start()
        extra.animate().cancel()
        val lp = extra.layoutParams
        val wSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        extra.measure(wSpec, wSpec)
        val full = extra.measuredWidth
        val from = if (shortcutsExpanded) 0 else full
        val to = if (shortcutsExpanded) full else 0
        extra.visibility = View.VISIBLE
        extra.alpha = if (shortcutsExpanded) 0f else 1f
        android.animation.ValueAnimator.ofInt(from, to).apply {
            duration = ms
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                lp.width = it.animatedValue as Int
                extra.layoutParams = lp
                val f = if (full == 0) 1f else (lp.width.toFloat() / full)
                extra.alpha = f
                // তীরের পাশ থেকে স্লাইড করে বেরোনোর অনুভূতি
                extra.translationX = -(1f - f) * UiKit.dp(this@PlayerActivity, 40)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) {
                    extra.translationX = 0f
                    if (shortcutsExpanded) {
                        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                        extra.alpha = 1f
                    } else {
                        extra.visibility = View.GONE
                        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                    extra.layoutParams = lp
                }
            })
            start()
        }
    }

    private fun mark(s: Shortcut, on: Boolean) {
        shortcutViews[s]?.findViewById<View>(R.id.scCircle)?.isSelected = on
    }

    private fun updateShortcutStates() {
        if (!::shortcutRow.isInitialized) return
        val p = player
        mark(Shortcut.NIGHT, nightOn)
        mark(Shortcut.SHUFFLE, p?.shuffleModeEnabled == true)
        val rep = p?.repeatMode ?: Player.REPEAT_MODE_OFF
        mark(Shortcut.LOOP, rep != Player.REPEAT_MODE_OFF)
        shortcutViews[Shortcut.LOOP]?.findViewById<ImageView>(R.id.scIcon)?.setImageResource(
            if (rep == Player.REPEAT_MODE_ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat
        )
        mark(Shortcut.MUTE, p != null && p.volume == 0f)
        mark(Shortcut.SLEEP, SleepTimer.active())
        mark(Shortcut.AB, abA >= 0)
        mark(Shortcut.EQ, AppSettings.eqOn(this))
        mark(Shortcut.BOOST, AppSettings.fx(this, "boost") > 0)
        mark(Shortcut.BGPLAY, AppSettings.backgroundPlay(this))
        mark(Shortcut.MIRROR, mirrorOn)
        mark(Shortcut.VFLIP, flipOn)
        // স্পিড: আইকনের জায়গায় "1X" লেখা
        shortcutViews[Shortcut.SPEED]?.let { v ->
            v.findViewById<View>(R.id.scIcon).visibility = View.GONE
            val tx = v.findViewById<TextView>(R.id.scText)
            tx.visibility = View.VISIBLE
            tx.text = p?.safeSpeedLabel() ?: "1X"
            mark(Shortcut.SPEED, p != null && p.playbackParameters.speed != 1f)
        }
    }

    private fun onShortcut(s: Shortcut) {
        val p = player
        when (s) {
            Shortcut.NIGHT -> toggleNight()
            Shortcut.CUSTOMISE -> showCustomise()
            Shortcut.SHUFFLE -> p?.let {
                it.shuffleModeEnabled = !it.shuffleModeEnabled
                flash(getString(if (it.shuffleModeEnabled) R.string.shuffle_on else R.string.shuffle_off),
                    R.drawable.ic_shuffle)
            }
            Shortcut.LOOP -> p?.let {
                val next = when (it.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                    Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                    else -> Player.REPEAT_MODE_OFF
                }
                it.repeatMode = next
                flash(
                    getString(
                        when (next) {
                            Player.REPEAT_MODE_ONE -> R.string.loop_one
                            Player.REPEAT_MODE_ALL -> R.string.loop_all
                            else -> R.string.loop_off
                        }
                    ),
                    if (next == Player.REPEAT_MODE_ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat
                )
            }
            Shortcut.MUTE -> p?.let {
                val mute = it.volume != 0f
                it.volume = if (mute) 0f else 1f
                flash(getString(if (mute) R.string.muted else R.string.unmuted), R.drawable.ic_volume_off)
            }
            Shortcut.SLEEP -> PlayerDialogs.sleep(this) { updateShortcutStates() }
            Shortcut.AB -> toggleAB()
            Shortcut.AUDIO_FX -> PlayerDialogs.audioEffect(this)
            Shortcut.BOOST -> PlayerDialogs.booster(this) { updateShortcutStates() }
            Shortcut.EQ -> PlayerDialogs.equalizer(this) { updateShortcutStates() }
            Shortcut.SPEED -> p?.let {
                PlayerDialogs.speed(this, it) {
                    updateShortcutStates()
                    flash(
                        getString(R.string.speed_plays, (it.playbackParameters.speed * 100).toInt()),
                        R.drawable.ic_speed_dummy
                    )
                }
            }
            Shortcut.FLOAT -> startFloating()
            Shortcut.SCREENSHOT -> takeScreenshot()
            Shortcut.BGPLAY -> {
                val on = !AppSettings.backgroundPlay(this)
                AppSettings.setBackgroundPlay(this, on)
                flash(getString(if (on) R.string.bgplay_on else R.string.bgplay_off), R.drawable.ic_headset)
                updateShortcutStates()
            }
            Shortcut.ROTATION -> if (AppSettings.lockOrientation(this)) {
                Toast.makeText(this, R.string.row_lock_orient, Toast.LENGTH_SHORT).show()
            } else {
                rotationIndex = (rotationIndex + 1) % rotationModes.size
                AppSettings.prefs(this).edit().putInt("rotation", rotationIndex).apply()
                applyRotation(true)
            }
            Shortcut.MIRROR -> {
                mirrorOn = !mirrorOn
                applyFlip()
                flash(getString(if (mirrorOn) R.string.mirror_on else R.string.mirror_off), R.drawable.ic_flip)
                updateShortcutStates()
            }
            Shortcut.VFLIP -> {
                flipOn = !flipOn
                applyFlip()
                flash(getString(if (flipOn) R.string.flip_on else R.string.flip_off), R.drawable.ic_flip_v)
                updateShortcutStates()
            }
        }
    }

    private fun toggleNight() {
        nightOn = !nightOn
        AppSettings.setNightMode(this, nightOn)
        nightOverlay.visibility = if (nightOn) View.VISIBLE else View.GONE
        flash(getString(if (nightOn) R.string.night_on else R.string.night_off), R.drawable.ic_night)
        updateShortcutStates()
    }

    private var zoom = 1f

    private var panX = 0f
    private var panY = 0f

    private fun applyFlip() {
        val v = playerView.videoSurfaceView ?: return
        v.scaleX = (if (mirrorOn) -1f else 1f) * zoom
        v.scaleY = (if (flipOn) -1f else 1f) * zoom
        if (zoom <= 1f) { panX = 0f; panY = 0f }
        v.translationX = panX
        v.translationY = panY
    }

    // জুম অবস্থায় ডানে-বামে/ওপর-নিচে সরানো (জুমের সীমার ভেতরে)
    private fun panBy(dx: Float, dy: Float) {
        val maxX = playerView.width * (zoom - 1f) / 2f
        val maxY = playerView.height * (zoom - 1f) / 2f
        panX = (panX - dx).coerceIn(-maxX, maxX)
        panY = (panY - dy).coerceIn(-maxY, maxY)
        applyFlip()
    }

    // ---------- ব্যাটারি ও সময় (কন্ট্রোল লুকানো থাকলে) ----------
    private var clockView: TextView? = null
    private val clockTick = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 15_000)
        }
    }

    private fun setupClock() {
        val tv = TextView(this)
        tv.setTextColor(0xFFFFFFFF.toInt())
        tv.textSize = 13f
        tv.setShadowLayer(4f, 0f, 0f, 0xFF000000.toInt())
        val lp = android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.TOP or android.view.Gravity.END
        )
        lp.topMargin = UiKit.dp(this, 14)
        lp.marginEnd = UiKit.dp(this, 18)
        tv.visibility = View.GONE
        findViewById<ViewGroup>(R.id.playerRoot).addView(tv, lp)
        clockView = tv
        handler.post(clockTick)
    }

    private fun updateClock() {
        val tv = clockView ?: return
        if (!AppSettings.showBatteryTime(this) || controlsVisible || inPip || locked) {
            tv.visibility = View.GONE
            return
        }
        val bi = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bi?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bi?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val time = android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date())
        tv.text = if (pct >= 0) "$time  •  $pct%" else time
        tv.visibility = View.VISIBLE
    }

    // অন্য অ্যাপে গেলে অটো পপ-আপ (পিকচার-ইন-পিকচার উইন্ডো)
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (AppSettings.autoPopup(this) && !isAudio && !inPip && player?.isPlaying == true) enterPip()
    }

    // ---------- লং প্রেসে স্পিড ----------
    private var lpActive = false
    private var lpPrevSpeed = 1f

    private fun startLongPress() {
        val p = player ?: return
        if (lpActive || !AppSettings.longPressBoost(this) || !p.isPlaying) return
        lpActive = true
        lpPrevSpeed = p.playbackParameters.speed
        p.setPlaybackSpeed(AppSettings.longPressSpeed(this))
        show("%.1fx".format(AppSettings.longPressSpeed(this)), R.drawable.ic_forward)
        if (AppSettings.longPressVibrate(this)) vibrateShort()
    }

    private fun endLongPress() {
        if (!lpActive) return
        lpActive = false
        player?.setPlaybackSpeed(lpPrevSpeed)
        handler.removeCallbacks(hideFeedback)
        handler.postDelayed(hideFeedback, 300)
    }

    @Suppress("DEPRECATION")
    private fun vibrateShort() {
        try {
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
            else getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vib.vibrate(android.os.VibrationEffect.createOneShot(30, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            else vib.vibrate(30)
        } catch (e: Exception) { Dbg.w("PlayerActivity.vibrateShort", e) }
    }

    // ---------- ভাসমান (Floating) উইন্ডো ----------
    private fun startFloating() {
        if (!FloatingPlayer.canDraw(this)) {
            Toast.makeText(this, R.string.float_need_perm, Toast.LENGTH_LONG).show()
            FloatingPlayer.requestPermission(this)
            return
        }
        val c = player
        // সার্ভিসের প্লেয়ারে ছবি ভাসমান উইন্ডোতে যাবে; তাই আগে এখানকার সারফেস ছেড়ে দিই
        playerView.player = null
        if (!FloatingPlayer.show(applicationContext)) {
            playerView.player = c
            Toast.makeText(this, R.string.float_failed, Toast.LENGTH_SHORT).show()
            return
        }
        moveTaskToBack(true)
    }

    // A - B রিপিট: ১ম ট্যাপ A, ২য় ট্যাপ B (লুপ শুরু), ৩য় ট্যাপ বন্ধ
    private fun toggleAB() {
        val p = player ?: return
        when {
            abA < 0 -> {
                abA = p.currentPosition
                flash(getString(R.string.ab_a, abA.toTimeString()), R.drawable.ic_ab_repeat)
            }
            abB < 0 -> {
                if (p.currentPosition <= abA + 500) return
                abB = p.currentPosition
                p.seekTo(abA)
                handler.removeCallbacks(abRun)
                handler.post(abRun)
                flash(getString(R.string.ab_on), R.drawable.ic_ab_repeat)
            }
            else -> {
                abA = -1L
                abB = -1L
                handler.removeCallbacks(abRun)
                flash(getString(R.string.ab_off), R.drawable.ic_ab_repeat)
            }
        }
        updateShortcutStates()
    }

    // "Customise Items" ও প্যানেলের চেকবক্স: কোন শর্টকাট দেখাবে
    private fun showCustomise() {
        val all = Shortcut.values()
        val hidden = AppSettings.hiddenShortcuts(this)
        val labels = all.map { getString(it.label) }.toTypedArray()
        val checked = BooleanArray(all.size) { all[it].key !in hidden }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.customise_title)
            .setMultiChoiceItems(labels, checked) { _, which, on -> checked[which] = on }
            .setPositiveButton(R.string.btn_apply) { _, _ ->
                val newHidden = all.filterIndexed { i, _ -> !checked[i] }.map { it.key }.toSet()
                AppSettings.setHiddenShortcuts(this, newHidden)
                buildShortcuts()
                buildMorePanel()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    // ---------- স্ক্রিনশট ----------
    private fun takeScreenshot() {
        val bmp = (playerView.videoSurfaceView as? TextureView)?.bitmap
        if (bmp == null || !saveImage(bmp)) {
            Toast.makeText(this, R.string.screenshot_fail, Toast.LENGTH_SHORT).show()
        } else {
            flash(getString(R.string.screenshot_saved), R.drawable.ic_screenshot)
        }
    }

    private fun saveImage(bmp: Bitmap): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "HM_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/HM Video Player")
            }
        }
        return try {
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                ?: false
        } catch (e: Exception) {
            Dbg.w("PlayerActivity.saveImage", e)
            false
        }
    }

    // ---------- লক ও পিকচার-ইন-পিকচার ----------
    private fun setLocked(on: Boolean) {
        locked = on
        handler.removeCallbacks(hideUnlock)
        if (on) {
            hidePanel()
            hideControls()
            btnUnlock.visibility = View.VISIBLE
            handler.postDelayed(hideUnlock, 2500)
            flash(getString(R.string.locked), R.drawable.ic_lock)
            handler.removeCallbacks(hideFeedback)
            handler.postDelayed(hideFeedback, 900)
        } else {
            btnUnlock.visibility = View.GONE
            showControls()
        }
    }

    private fun revealUnlock() {
        btnUnlock.visibility = View.VISIBLE
        handler.removeCallbacks(hideUnlock)
        handler.postDelayed(hideUnlock, 2500)
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            !packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            Toast.makeText(this, R.string.pip_unsupported, Toast.LENGTH_SHORT).show()
            return
        }
        hidePanel()
        hideControls()
        enterPictureInPictureMode(
            PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
        )
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean, newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        if (inPip) {
            hidePanel()
            controlsVisible = false
            topBar.visibility = View.GONE
            bottomPanel.visibility = View.GONE
            if (::sideButtons.isInitialized) sideButtons.visibility = View.GONE
        } else {
            showControls()
        }
    }

    // ---------- ডান পাশের "আরও" প্যানেল ----------
    private class Cell(val icon: Int, val label: Int, val key: Shortcut? = null, val action: () -> Unit)

    // প্যানেলের সিয়ান অ্যাকসেন্ট (রেফারেন্স ভিডিওর মতো)
    private val panelAccent = 0xFF00B8FF.toInt()

    private fun panelTitle(res: Int): View {
        val t = TextView(this)
        t.setText(res)
        t.setTextColor(0xFFFFFFFF.toInt())
        t.textSize = 12f
        t.setPadding(UiKit.dp(this, 4), UiKit.dp(this, 14), 0, UiKit.dp(this, 6))
        return t
    }

    private fun panelDivider(): View {
        val v = View(this)
        v.setBackgroundColor(0x22FFFFFF)
        v.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
            .apply { topMargin = UiKit.dp(this@PlayerActivity, 10) }
        return v
    }

    private fun addCells(c: LinearLayout, cells: List<Cell>) {
        for (rowCells in cells.chunked(4)) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (cell in rowCells) {
                val v = layoutInflater.inflate(R.layout.item_shortcut, row, false)
                v.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                v.findViewById<ImageView>(R.id.scIcon).setImageResource(cell.icon)
                v.findViewById<TextView>(R.id.scLabel).setText(cell.label)
                v.setOnClickListener { cell.action() }
                if (cell.label == R.string.p_fav) favCell = v
                cell.key?.let { shortcutViews[it] = v }
                row.addView(v)
            }
            // শেষ সারিতে ফাঁকা ঘর, যাতে ঘরগুলো সমান চওড়া থাকে
            repeat(4 - rowCells.size) {
                row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            }
            c.addView(row)
        }
    }

    // লুপ: শাফল / এক ভিডিও / সব / বন্ধ
    private fun loopSegmented(): View {
        val r = UiKit.dp(this, 24).toFloat()
        val box = LinearLayout(this)
        box.orientation = LinearLayout.HORIZONTAL
        box.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = r
            setColor(0x66000000)
        }
        box.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 48)
        ).apply { topMargin = UiKit.dp(this@PlayerActivity, 4) }
        val icons = listOf(R.drawable.ic_shuffle, R.drawable.ic_repeat_one, R.drawable.ic_repeat, R.drawable.ic_loop_off)
        val items = ArrayList<ImageView>()
        fun selected(): Int {
            val p = player ?: return 3
            return when {
                p.shuffleModeEnabled -> 0
                p.repeatMode == Player.REPEAT_MODE_ONE -> 1
                p.repeatMode == Player.REPEAT_MODE_ALL -> 2
                else -> 3
            }
        }
        fun paint() {
            val sel = selected()
            items.forEachIndexed { i, iv ->
                iv.background = if (i == sel) android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = r
                    setColor(panelAccent)
                } else null
            }
        }
        icons.forEachIndexed { i, ic ->
            val iv = ImageView(this)
            iv.setImageResource(ic)
            iv.setPadding(UiKit.dp(this, 12), UiKit.dp(this, 12), UiKit.dp(this, 12), UiKit.dp(this, 12))
            iv.setOnClickListener {
                val p = player ?: return@setOnClickListener
                when (i) {
                    0 -> p.shuffleModeEnabled = true
                    1 -> { p.shuffleModeEnabled = false; p.repeatMode = Player.REPEAT_MODE_ONE }
                    2 -> { p.shuffleModeEnabled = false; p.repeatMode = Player.REPEAT_MODE_ALL }
                    else -> { p.shuffleModeEnabled = false; p.repeatMode = Player.REPEAT_MODE_OFF }
                }
                paint()
                scheduleAutoHide()
            }
            box.addView(iv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            items.add(iv)
        }
        paint()
        return box
    }

    private fun brightnessRow(): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = android.view.Gravity.CENTER_VERTICAL
        val small = ImageView(this)
        small.setImageResource(R.drawable.ic_brightness)
        small.alpha = 0.6f
        row.addView(small, LinearLayout.LayoutParams(UiKit.dp(this, 20), UiKit.dp(this, 20)))
        val bar = SeekBar(this)
        bar.max = 100
        bar.progressTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt())
        bar.thumbTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt())
        val lp = window.attributes
        bar.progress = ((if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness) * 100).toInt()
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val a = window.attributes
                a.screenBrightness = (progress / 100f).coerceIn(0.02f, 1f)
                window.attributes = a
            }

            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        row.addView(bar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val big = ImageView(this)
        big.setImageResource(R.drawable.ic_brightness)
        row.addView(big, LinearLayout.LayoutParams(UiKit.dp(this, 26), UiKit.dp(this, 26)))
        return row
    }

    private fun decoderRow(): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val hw = TextView(this)
        val hwp = TextView(this)
        hw.setText(R.string.decoder_hw)
        hwp.setText(R.string.decoder_hw_plus)
        for (t in listOf(hw, hwp)) {
            t.textSize = 12f
            t.setPadding(UiKit.dp(this, 4), UiKit.dp(this, 8), UiKit.dp(this, 20), UiKit.dp(this, 8))
            row.addView(t)
        }
        fun paint() {
            val plus = AppSettings.hwMode(this) == AppSettings.HW_PLUS
            hw.setTextColor(if (!plus) panelAccent else 0xFFFFFFFF.toInt())
            hwp.setTextColor(if (plus) panelAccent else 0xFFFFFFFF.toInt())
        }
        hw.setOnClickListener {
            if (AppSettings.hwMode(this) != AppSettings.HW) hwLabel.performClick()
            paint()
        }
        hwp.setOnClickListener {
            if (AppSettings.hwMode(this) != AppSettings.HW_PLUS) hwLabel.performClick()
            paint()
        }
        paint()
        return row
    }

    private fun openCast() {
        try {
            startActivity(Intent(android.provider.Settings.ACTION_CAST_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.cast_unsupported, Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendFeedback() {
        val i = Intent(Intent.ACTION_SEND)
        i.type = "text/plain"
        i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_subject))
        try {
            startActivity(Intent.createChooser(i, getString(R.string.p_feedback)))
        } catch (e: Exception) {
            notAvailable()
        }
    }

    private fun buildMorePanel() {
        val c = morePanelContent
        c.removeAllViews()
        favCell = null
        // প্যানেলের ঘরগুলোও অবস্থা-চিহ্ন (night, A-B, mirror…) পায়
        for (s in Shortcut.values()) shortcutViews.remove(s)

        // ১. মূল ফিচার (রেফারেন্সের ক্রম: অডিও ট্র্যাক, ইকুয়ালাইজার, কাস্ট, শেয়ার / কাট, ফেভারিট, বুকমার্ক, কিউ)
        addCells(c, listOf(
            Cell(R.drawable.ic_audiotrack, R.string.p_audio_track) {
                hidePanel(); player?.let { PlayerDialogs.tracks(this, it, C.TRACK_TYPE_AUDIO) }
            },
            Cell(R.drawable.ic_equalizer, R.string.sc_eq, Shortcut.EQ) {
                hidePanel(); onShortcut(Shortcut.EQ)
            },
            Cell(R.drawable.ic_cast, R.string.p_cast) { hidePanel(); openCast() },
            Cell(R.drawable.ic_share, R.string.p_share) {
                hidePanel()
                currentVideo()?.let { MediaActions.share(this, it) } ?: notAvailable()
            },
            Cell(R.drawable.ic_cut, R.string.p_cut) { hidePanel(); showCut() },
            Cell(R.drawable.ic_favorite, R.string.p_fav) { toggleFavourite() },
            Cell(R.drawable.ic_bookmark, R.string.p_bookmark) {
                hidePanel(); player?.let { PlayerDialogs.bookmarks(this, it) }
            },
            Cell(R.drawable.ic_queue, R.string.p_queue) {
                hidePanel(); player?.let { PlayerDialogs.queue(this, it) }
            }
        ))

        // ২. প্লে সেটিংস: প্রথম চারটি রেফারেন্সের মতো (A-B, নাইট, মিরর, টাইমার), বাকিগুলো তার নিচে
        c.addView(panelTitle(R.string.sec_play_settings))
        val closing = setOf(
            Shortcut.SPEED, Shortcut.SLEEP, Shortcut.AUDIO_FX, Shortcut.BOOST,
            Shortcut.SCREENSHOT, Shortcut.FLOAT
        )
        addCells(c, (listOf(
            Shortcut.AB, Shortcut.NIGHT, Shortcut.MIRROR, Shortcut.SLEEP,
            Shortcut.SPEED, Shortcut.MUTE, Shortcut.VFLIP, Shortcut.ROTATION,
            Shortcut.BGPLAY, Shortcut.AUDIO_FX, Shortcut.BOOST, Shortcut.SCREENSHOT,
            Shortcut.FLOAT
        ).map { s ->
            Cell(s.icon, s.label, s) {
                if (s in closing) hidePanel()
                onShortcut(s)
            }
        }) + listOf(
            Cell(R.drawable.ic_aspect, R.string.p_aspect) { cycleAspect() },
            Cell(R.drawable.ic_playlist_add, R.string.p_playlist) {
                hidePanel()
                currentVideo()?.let { MediaActions.addToPlaylist(this, it) } ?: notAvailable()
            }
        ))

        // ৩. লুপ
        c.addView(panelTitle(R.string.sec_loop))
        c.addView(loopSegmented())
        c.addView(panelDivider())

        // ৪. ব্রাইটনেস
        c.addView(panelTitle(R.string.display_brightness))
        c.addView(brightnessRow())
        c.addView(panelDivider())

        // ৫. ডিকোডার
        c.addView(panelTitle(R.string.sec_decoder))
        c.addView(decoderRow())
        c.addView(panelDivider())

        // ৬. আরও
        c.addView(panelTitle(R.string.sec_more))
        addCells(c, listOf(
            Cell(R.drawable.ic_edit, R.string.p_edit_player) { hidePanel(); showMore() },
            Cell(R.drawable.ic_bulb, R.string.p_tutorial) {
                hidePanel(); PlayerDialogs.tutorial(this)
            },
            Cell(R.drawable.ic_info, R.string.p_info) {
                hidePanel()
                currentVideo()?.let { MediaActions.details(this, it) } ?: notAvailable()
            },
            Cell(R.drawable.ic_language, R.string.p_feedback) { hidePanel(); sendFeedback() }
        ))

        c.addView(switchRow(R.string.p_video_display, AppSettings.videoDisplay(this)) { on ->
            AppSettings.setVideoDisplay(this, on)
            applyVideoDisplay()
        })
        updateShortcutStates()
    }

    private fun switchRow(label: Int, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = android.view.Gravity.CENTER_VERTICAL
        row.setPadding(UiKit.dp(this, 4), UiKit.dp(this, 10), UiKit.dp(this, 4), UiKit.dp(this, 10))
        row.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = UiKit.dp(this@PlayerActivity, 10) }
        val tx = TextView(this)
        tx.setText(label)
        tx.setTextColor(0xFFFFFFFF.toInt())
        tx.textSize = 13f
        tx.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val sw = MaterialSwitch(this)
        sw.isChecked = checked
        sw.setOnCheckedChangeListener { _, on -> onChange(on) }
        row.addView(tx)
        row.addView(sw)
        return row
    }

    private fun panelIsPortrait() =
        resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    // পোর্ট্রেটে নিচ থেকে উঠে আসা স্বচ্ছ প্যানেল, ল্যান্ডস্কেপে ডানের সাইড প্যানেল
    private fun layoutPanel() {
        val lp = morePanel.layoutParams as android.widget.FrameLayout.LayoutParams
        if (panelIsPortrait()) {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = (resources.displayMetrics.heightPixels * 0.58f).toInt()
            lp.gravity = android.view.Gravity.BOTTOM
        } else {
            lp.width = UiKit.dp(this, 360)
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT
            lp.gravity = android.view.Gravity.END
        }
        morePanel.layoutParams = lp
        morePanel.setBackgroundColor(0xB3101418.toInt())
    }

    private fun panelHiddenOffset(): Float =
        if (panelIsPortrait()) resources.displayMetrics.heightPixels.toFloat()
        else UiKit.dp(this, 360).toFloat()

    private fun showPanel() {
        refreshPanel()
        hideControls()
        layoutPanel()
        morePanel.animate().cancel()
        morePanel.visibility = View.VISIBLE
        if (panelIsPortrait()) {
            morePanel.translationX = 0f
            morePanel.translationY = panelHiddenOffset()
            morePanel.animate().translationY(0f).setDuration(220)
                .setInterpolator(DecelerateInterpolator()).start()
        } else {
            morePanel.translationY = 0f
            morePanel.translationX = panelHiddenOffset()
            morePanel.animate().translationX(0f).setDuration(220)
                .setInterpolator(DecelerateInterpolator()).start()
        }
    }

    private fun hidePanel() {
        if (morePanel.visibility != View.VISIBLE) return
        morePanel.animate().cancel()
        val a = morePanel.animate().setDuration(180)
        if (panelIsPortrait()) a.translationY(panelHiddenOffset()) else a.translationX(panelHiddenOffset())
        a.withEndAction { morePanel.visibility = View.GONE }.start()
    }

    // অডিওর এমবেডেড কভার আর্ট থাকলে গোল ছবিতে দেখায়
    private fun loadCover(uri: Uri?) {
        val my = ++coverJob
        audioCover.setImageResource(R.drawable.ic_audio)
        audioCover.scaleType = ImageView.ScaleType.CENTER_INSIDE
        audioCover.setPadding(UiKit.dp(this, 56), UiKit.dp(this, 56), UiKit.dp(this, 56), UiKit.dp(this, 56))
        if (uri == null) return
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
            var bmp: Bitmap? = null
            try {
                val r = android.media.MediaMetadataRetriever()
                r.setDataSource(this@PlayerActivity, uri)
                val bytes = r.embeddedPicture
                r.release()
                if (bytes != null) {
                    val o = android.graphics.BitmapFactory.Options()
                    o.inJustDecodeBounds = true
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                    var sample = 1
                    while (o.outWidth / sample > 800) sample *= 2
                    val o2 = android.graphics.BitmapFactory.Options()
                    o2.inSampleSize = sample
                    bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o2)
                }
            } catch (e: Exception) { Dbg.w("audio cover", e) }
            bmp
            }
            run {
                if (my == coverJob && result != null && !isDestroyed) {
                    audioCover.setPadding(0, 0, 0, 0)
                    audioCover.scaleType = ImageView.ScaleType.CENTER_CROP
                    audioCover.setImageBitmap(result)
                }
            }
        }
    }

    private fun refreshPanel() {
        val v = currentVideo()
        val isFav = v != null && Store.isFav(this, v.isAudio, v.id)
        findViewById<ImageView>(R.id.audioBtnFav)
            .setColorFilter(if (isFav) UiKit.accent(this) else android.graphics.Color.WHITE)
        favCell?.findViewById<View>(R.id.scCircle)?.isSelected =
            v != null && Store.isFav(this, v.isAudio, v.id)
    }

    private fun currentVideo(): Video? {
        val id = player?.currentMediaItem?.mediaId ?: return null
        return (Library.videos + Library.audios).firstOrNull { it.uri.toString() == id }
    }

    private fun notAvailable() {
        Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show()
    }

    private fun toggleFavourite() {
        val v = currentVideo()
        if (v == null) {
            notAvailable()
            return
        }
        Store.toggleFav(this, v.isAudio, v.id)
        refreshPanel()
    }

    // ডিসপ্লে সেটিংস: ব্রাইটনেস, নাইট মোড, মিরর, ভার্টিক্যাল ফ্লিপ
    private fun showDisplaySettings() {
        val pad = UiKit.dp(this, 22)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(pad, UiKit.dp(this, 8), pad, 0)
        val lbl = TextView(this)
        lbl.setText(R.string.display_brightness)
        box.addView(lbl)
        val bar = SeekBar(this)
        bar.max = 100
        val lp = window.attributes
        bar.progress = ((if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness) * 100).toInt()
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val a = window.attributes
                a.screenBrightness = (progress / 100f).coerceIn(0.02f, 1f)
                window.attributes = a
            }

            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        box.addView(bar)
        fun sw(label: Int, on: Boolean, change: (Boolean) -> Unit) {
            val s = MaterialSwitch(this)
            s.setText(label)
            s.isChecked = on
            s.setOnCheckedChangeListener { _, b -> change(b) }
            box.addView(s)
        }
        sw(R.string.sc_night, nightOn) { if (it != nightOn) toggleNight() }
        sw(R.string.sc_mirror, mirrorOn) {
            mirrorOn = it; applyFlip(); updateShortcutStates()
        }
        sw(R.string.sc_vflip, flipOn) {
            flipOn = it; applyFlip(); updateShortcutStates()
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.display_title)
            .setView(box)
            .setPositiveButton(R.string.btn_ok, null)
            .show()
    }

    private fun showMore() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.more_title)
            .setItems(arrayOf(getString(R.string.more_reset))) { _, _ ->
                AppSettings.resetPlayer(this)
                nightOn = false
                nightOverlay.visibility = View.GONE
                aspectIndex = 0
                applyAspect(false)
                applyVideoDisplay()
                buildShortcuts()
                buildMorePanel()
                Toast.makeText(this, R.string.more_reset_done, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    // ---------- কাট (A - B অংশ নতুন ভিডিও হিসেবে সেভ) ----------
    private fun showCut() {
        val p = player ?: return
        if (abA < 0 || abB <= abA) {
            Toast.makeText(this, R.string.cut_need_ab, Toast.LENGTH_LONG).show()
            return
        }
        val src = p.currentMediaItem?.localConfiguration?.uri
        if (src == null || cutting || PrivacyStore.isVaultUri(src)) {
            notAvailable()
            return
        }
        val from = abA
        val to = abB
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.cut_title)
            .setMessage(getString(R.string.cut_msg, from.toTimeString(), to.toTimeString()))
            .setPositiveButton(R.string.cut_start) { _, _ -> startCut(src, from, to) }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun startCut(src: Uri, from: Long, to: Long) {
        val clip = MediaItem.Builder()
            .setUri(src)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(from)
                    .setEndPositionMs(to)
                    .build()
            )
            .build()
        val out = File(cacheDir, "cut_${System.currentTimeMillis()}.mp4")
        val app = applicationContext
        cutting = true
        Toast.makeText(this, R.string.cut_progress, Toast.LENGTH_SHORT).show()
        try {
            Transformer.Builder(this)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        cutting = false
                        AppScope.launch(Dispatchers.IO) {
                            val ok = saveVideoFile(app, out)
                            out.delete()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    app, if (ok) R.string.cut_done else R.string.cut_fail,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }

                    override fun onError(
                        composition: Composition, exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        cutting = false
                        out.delete()
                        Toast.makeText(app, R.string.cut_fail, Toast.LENGTH_LONG).show()
                    }
                })
                .build()
                .start(EditedMediaItem.Builder(clip).build(), out.absolutePath)
        } catch (e: Exception) {
            cutting = false
            Toast.makeText(this, R.string.cut_fail, Toast.LENGTH_LONG).show()
        }
    }

    private fun saveVideoFile(ctx: Context, f: File): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "HM_cut_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/HM Video Player")
            }
        }
        return try {
            val uri = ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            ctx.contentResolver.openOutputStream(uri)?.use { o -> f.inputStream().use { it.copyTo(o) } }
            true
        } catch (e: Exception) {
            Dbg.w("PlayerActivity.saveVideoFile", e)
            false
        }
    }

    // ছোট বার্তা (ভিডিওর মাঝখানে)
    private fun flash(text: String, icon: Int? = null) {
        show(text, icon)
        handler.removeCallbacks(hideFeedback)
        handler.postDelayed(hideFeedback, 800)
    }

    private fun updateHwLabel() {
        hwLabel.text = if (AppSettings.hwMode(this) == AppSettings.HW_PLUS) "HW+" else "HW"
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
        if (locked || inPip) return
        controlsVisible = true
        updateClock()
        if (::sideButtons.isInitialized && !isAudio) {
            sideButtons.animate().cancel()
            sideButtons.visibility = View.VISIBLE
            sideButtons.animate().alpha(1f).setDuration(240).start()
        }
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
        updateClock()
        if (::sideButtons.isInitialized) {
            sideButtons.animate().cancel()
            sideButtons.animate().alpha(0f).setDuration(220).withEndAction {
                if (!controlsVisible) sideButtons.visibility = View.GONE
            }.start()
        }
        handler.removeCallbacks(autoHide)
        // স্ক্রিনে কোনো অপশন না থাকলে শর্টকাট সারি স্লাইড করে তীরে ঢুকে যায়
        if (shortcutsExpanded) toggleShortcutsExpanded(220L)
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

    // Fill screen: ভিডিওর অনুপাত ডিসপ্লের কাছাকাছি হলে জুম করে ক্যামেরা/নচসহ পুরো ডিসপ্লে ভরে দেয়;
    // অনুপাত অনেক আলাদা (যেমন পোর্ট্রেট ভিডিও) হলে ছেঁটে যাওয়া এড়াতে Fit হয়
    private fun smartFillMode(): Int {
        val vs = player?.videoSize
        val dw = playerView.width
        val dh = playerView.height
        if (vs == null || vs.width <= 0 || vs.height <= 0 || dw <= 0 || dh <= 0) {
            return AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
        val videoRatio = vs.width * vs.pixelWidthHeightRatio / vs.height
        val ratio = videoRatio / (dw.toFloat() / dh)
        return if (ratio in 0.6f..1.6f) AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    private fun applyAspect(announce: Boolean) {
        playerView.resizeMode = when (aspectIndex) {
            0 -> smartFillMode()
            1 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            else -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        }
        if (announce) flash(aspectNames[aspectIndex], R.drawable.ic_aspect)
    }

    private fun applyRotation(announce: Boolean) {
        requestedOrientation = rotationModes[rotationIndex]
        if (announce) flash(rotationNames[rotationIndex], R.drawable.ic_rotate)
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
        window.addFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // ঘোরালে নচ/ক্যামেরার পাশেও যেন ভিডিও ভরে যায় এবং ফ্রেম নতুন মাপে বসে
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        enterImmersive()
        window.decorView.requestApplyInsets()
        playerView.post { applyAspect(false) }
    }

    // বের হওয়ার সময় কালো ঝলক এড়াতে সিস্টেম অ্যানিমেশন বন্ধ
    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
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

            override fun onLongPress(e: MotionEvent) {
                if (mode == Mode.NONE) startLongPress()
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (morePanel.visibility == View.VISIBLE) {
                    hidePanel()
                    return true
                }
                if (isAudio || !AppSettings.videoDisplay(this@PlayerActivity)) return true
                if (controlsVisible) hideControls() else showControls()
                return true
            }

            // ডাবল ট্যাপ: বামে ১০ সেকেন্ড পিছনে, ডানে ১০ সেকেন্ড সামনে
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val third = playerView.width / 3f
                val step = AppSettings.seekSeconds(this@PlayerActivity) * 1000L
                val seekOn = AppSettings.doubleTapSeek(this@PlayerActivity)
                when {
                    e.x < third -> if (seekOn) seekBy(-step)
                    e.x > third * 2 -> if (seekOn) seekBy(step)
                    else -> player?.let { if (it.isPlaying) it.pause() else it.play() }
                }
                return true
            }

            override fun onScroll(
                e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float
            ): Boolean {
                val start = e1 ?: return false
                val p = player ?: return false
                if (lpActive) return true
                // জুম করা অবস্থায় এক আঙুলে টানলে ছবি সরবে
                if (zoom > 1f && AppSettings.zoomPan(this@PlayerActivity)) {
                    panBy(dx, dy)
                    return true
                }
                if (!AppSettings.gestures(this@PlayerActivity)) return false
                if (mode == Mode.NONE) {
                    mode = when {
                        abs(e2.x - start.x) > abs(e2.y - start.y) -> Mode.SEEK
                        start.x < playerView.width / 2f -> Mode.BRIGHTNESS
                        else -> Mode.VOLUME
                    }
                    startPos = p.currentPosition
                    volAccum = -1f
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
                        // পর্দার উচ্চতার ~৮০% টানলে পুরো রেঞ্জ; ছোট টানে ১% করে বদলায়
                        lp.screenBrightness = (cur + dy / (playerView.height * 0.8f)).coerceIn(0.01f, 1f)
                        window.attributes = lp
                        showLevel(brightPill, lp.screenBrightness, R.drawable.ic_brightness)
                    }
                    Mode.VOLUME -> {
                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        if (volAccum < 0f) {
                            // টানের শুরুতে বর্তমান লেভেল (সিস্টেম ধাপ × প্লেয়ার গেইন)
                            volLevel = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max * p.volume
                            volAccum = 0f
                        }
                        volLevel = (volLevel + dy / (playerView.height * 0.8f)).coerceIn(0f, 1f)
                        applyVolume(volLevel, max)
                        showLevel(volumePill, volLevel, if (volLevel <= 0f) R.drawable.ic_volume_off else R.drawable.ic_volume)
                    }
                    else -> {}
                }
                return true
            }
        })

        // দুই আঙুলে চিমটি কেটে জুম (১x – ৪x)
        val scaler = android.view.ScaleGestureDetector(this,
            object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(d: android.view.ScaleGestureDetector): Boolean {
                    if (!AppSettings.zoomPan(this@PlayerActivity)) return true
                    zoom = (zoom * d.scaleFactor).coerceIn(1f, 4f)
                    applyFlip()
                    show("${(zoom * 100).toInt()}%")
                    return true
                }
            })
        var ignoreTouch = false

        playerView.setOnTouchListener { _, ev ->
            if (locked) {
                if (ev.action == MotionEvent.ACTION_UP) revealUnlock()
                return@setOnTouchListener true
            }
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) ignoreTouch = false
            if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) endLongPress()
            scaler.onTouchEvent(ev)
            if (scaler.isInProgress || ev.pointerCount > 1) ignoreTouch = true
            if (ignoreTouch) {
                mode = Mode.NONE
                pendingSeek = -1L
                if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) {
                    endScroll()
                }
                return@setOnTouchListener true
            }
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
        handler.removeCallbacks(hideLevels)
        handler.postDelayed(hideLevels, 700)
    }

    // ১% ধাপে ভলিউম: সিস্টেম ভলিউম ধাপ (উপরের দিকে গোল) × প্লেয়ার গেইন দিয়ে মাঝের মানও পাওয়া যায়
    private fun applyVolume(level: Float, max: Int) {
        val exact = level * max
        val stream = kotlin.math.ceil(exact).toInt().coerceIn(0, max)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, stream, 0)
        player?.volume = if (stream == 0) 1f else (exact / stream).coerceIn(0f, 1f)
    }

    private fun showLevel(pill: LevelPill, level: Float, icon: Int) {
        handler.removeCallbacks(hideLevels)
        pill.setIcon(icon)
        pill.setLevel(level)
        if (pill.visibility != View.VISIBLE || pill.alpha < 1f) pill.slideIn()
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
