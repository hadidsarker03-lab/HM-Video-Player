package com.example.videoplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var content: View
    private lateinit var recycler: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var emptyBox: View
    private lateinit var emptyIcon: ImageView
    private lateinit var emptyText: TextView
    private lateinit var btnPermission: Button
    private lateinit var btnSample: Button
    private lateinit var headerSub: TextView
    private lateinit var settingsPage: View
    private lateinit var nav: BottomNavigationView
    private lateinit var adapter: VideoAdapter

    private var videos: List<Video> = emptyList()
    private var audios: List<Video> = emptyList()
    private var currentTab = TAB_VIDEO
    private var loading = false
    private val executor = Executors.newSingleThreadExecutor()

    private val permissions: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            loadMedia()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(AppSettings.accentStyle(this))
        super.onCreate(savedInstanceState)

        // প্রথমবার চালুর সময় ডিফল্ট ভাষা (ইংলিশ) সেট করা হয়
        val p = AppSettings.prefs(this)
        if (!p.getBoolean("lang_applied", false)) {
            p.edit().putBoolean("lang_applied", true).apply()
            AppSettings.applyLocale(AppSettings.lang(this))
        }

        setContentView(R.layout.activity_main)

        content = findViewById(R.id.content)
        recycler = findViewById(R.id.recycler)
        progress = findViewById(R.id.progress)
        emptyBox = findViewById(R.id.emptyBox)
        emptyIcon = findViewById(R.id.emptyIcon)
        emptyText = findViewById(R.id.emptyText)
        btnPermission = findViewById(R.id.btnPermission)
        btnSample = findViewById(R.id.btnSample)
        headerSub = findViewById(R.id.headerSub)
        settingsPage = findViewById(R.id.settingsPage)
        nav = findViewById(R.id.nav)

        adapter = VideoAdapter { video -> openPlaylist(video) }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        btnPermission.setOnClickListener { permissionLauncher.launch(permissions) }
        btnSample.setOnClickListener { openSingle(SAMPLE_URL, getString(R.string.sample_title)) }

        buildSettings()

        currentTab = savedInstanceState?.getInt(KEY_TAB, TAB_VIDEO) ?: TAB_VIDEO
        nav.setOnItemSelectedListener { item ->
            currentTab = when (item.itemId) {
                R.id.nav_audio -> TAB_AUDIO
                R.id.nav_settings -> TAB_SETTINGS
                else -> TAB_VIDEO
            }
            refreshTab(true)
            true
        }
        nav.selectedItemId = when (currentTab) {
            TAB_AUDIO -> R.id.nav_audio
            TAB_SETTINGS -> R.id.nav_settings
            else -> R.id.nav_video
        }
        refreshTab(false)
    }

    override fun onResume() {
        super.onResume()
        loadMedia()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, currentTab)
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    // ---------- মিডিয়া লোড ----------
    private fun hasPermission() = permissions.any {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun loadMedia() {
        if (!hasPermission()) {
            refreshTab(false)
            return
        }
        if (loading) return
        loading = true
        progress.visibility = View.VISIBLE
        executor.execute {
            val v = MediaRepo.queryVideos(this)
            val a = MediaRepo.queryAudio(this)
            runOnUiThread {
                loading = false
                videos = v
                audios = a
                progress.visibility = View.GONE
                refreshTab(false)
            }
        }
    }

    // ---------- ট্যাব ----------
    private fun refreshTab(animate: Boolean) {
        if (currentTab == TAB_SETTINGS) {
            recycler.visibility = View.GONE
            emptyBox.visibility = View.GONE
            settingsPage.visibility = View.VISIBLE
            headerSub.text = getString(R.string.header_settings)
        } else {
            settingsPage.visibility = View.GONE
            val audio = currentTab == TAB_AUDIO
            val list = if (audio) audios else videos
            adapter.submit(list, audio)
            headerSub.text = when {
                list.isEmpty() -> ""
                audio -> getString(R.string.header_audio, list.size)
                else -> getString(R.string.header_videos, list.size)
            }
            recycler.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
            val showEmpty = list.isEmpty() && !loading
            emptyBox.visibility = if (showEmpty) View.VISIBLE else View.GONE
            if (showEmpty) {
                val perm = hasPermission()
                emptyIcon.setImageResource(if (audio) R.drawable.ic_audio else R.drawable.ic_video)
                emptyText.setText(
                    when {
                        !perm -> R.string.perm_needed
                        audio -> R.string.empty_audio
                        else -> R.string.empty_video
                    }
                )
                btnPermission.visibility = if (perm) View.GONE else View.VISIBLE
                btnSample.visibility = if (audio) View.GONE else View.VISIBLE
            }
        }
        if (animate) {
            content.alpha = 0f
            content.animate().alpha(1f).setDuration(200).start()
        }
    }

    // ---------- প্লেয়ার চালু ----------
    private fun openPlaylist(video: Video) {
        val audio = currentTab == TAB_AUDIO
        val full = if (audio) audios else videos
        val index = full.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
        // বড় লিস্ট হলে Intent-এ শুধু আশেপাশের অংশ পাঠানো হয়
        val from = (index - 500).coerceAtLeast(0)
        val to = (index + 500).coerceAtMost(full.size)
        val list = full.subList(from, to)
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putStringArrayListExtra(
                PlayerActivity.EXTRA_URIS, ArrayList(list.map { it.uri.toString() })
            )
            putStringArrayListExtra(
                PlayerActivity.EXTRA_TITLES, ArrayList(list.map { it.title })
            )
            putExtra(PlayerActivity.EXTRA_INDEX, index - from)
            putExtra(PlayerActivity.EXTRA_AUDIO, audio)
        })
    }

    private fun openSingle(uri: String, title: String) {
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_URI, uri)
            putExtra(PlayerActivity.EXTRA_TITLE, title)
        })
    }

    // ---------- সেটিংস: ভাষা, থিম, অ্যাকসেন্ট ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildSettings() {
        val langCodes = listOf("en", "bn")
        buildOptions(
            findViewById(R.id.langOptions),
            listOf("English", "বাংলা"),
            langCodes.indexOf(AppSettings.lang(this)).coerceAtLeast(0)
        ) { i -> AppSettings.setLang(this, langCodes[i]) }

        val modes = listOf("system", "light", "dark")
        buildOptions(
            findViewById(R.id.themeOptions),
            listOf(
                getString(R.string.mode_system),
                getString(R.string.mode_light),
                getString(R.string.mode_dark)
            ),
            modes.indexOf(AppSettings.themeMode(this)).coerceAtLeast(0)
        ) { i -> AppSettings.setThemeMode(this, modes[i]) }

        val row = findViewById<LinearLayout>(R.id.accentRow)
        row.removeAllViews()
        val selected = AppSettings.accent(this)
        AppSettings.ACCENTS.forEachIndexed { i, color ->
            val dot = FrameLayout(this)
            dot.layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                marginEnd = dp(12)
            }
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            if (i == selected) {
                val check = ImageView(this)
                check.setImageResource(R.drawable.ic_check)
                check.layoutParams = FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER)
                dot.addView(check)
            }
            dot.setOnClickListener {
                if (i != selected) {
                    AppSettings.setAccent(this, i)
                    recreate()
                }
            }
            row.addView(dot)
        }

        findViewById<TextView>(R.id.versionText).text =
            getString(R.string.about_text, versionName())
    }

    private fun buildOptions(
        container: LinearLayout,
        labels: List<String>,
        selected: Int,
        onSelect: (Int) -> Unit
    ) {
        container.removeAllViews()
        labels.forEachIndexed { i, label ->
            val sel = i == selected
            val tv = TextView(this)
            tv.text = label
            tv.gravity = Gravity.CENTER
            tv.textSize = 14f
            tv.setTypeface(tv.typeface, Typeface.BOLD)
            tv.setPadding(dp(12), dp(12), dp(12), dp(12))
            tv.setBackgroundResource(if (sel) R.drawable.bg_option_selected else R.drawable.bg_option)
            tv.setTextColor(if (sel) Color.WHITE else ContextCompat.getColor(this, R.color.text_primary))
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (i > 0) lp.marginStart = dp(8)
            tv.layoutParams = lp
            tv.setOnClickListener { if (!sel) onSelect(i) }
            container.addView(tv)
        }
    }

    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
    } catch (e: Exception) {
        "1.0"
    }

    companion object {
        private const val TAB_VIDEO = 0
        private const val TAB_AUDIO = 1
        private const val TAB_SETTINGS = 2
        private const val KEY_TAB = "key_tab"
        const val SAMPLE_URL =
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
    }
}
