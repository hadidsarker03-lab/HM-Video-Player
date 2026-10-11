package com.example.videoplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
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
    private lateinit var btnView: ImageView
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

    // মিডিয়া + (Android 13+) নোটিফিকেশন অনুমতি একসাথে চাওয়া হয়
    private val requestPermissions: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            permissions + Manifest.permission.POST_NOTIFICATIONS
        else permissions

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            AppSettings.prefs(this).edit().putBoolean("notif_asked", true).apply()
            loadMedia()
        }

    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(AppSettings.themeRes(this))
        super.onCreate(savedInstanceState)

        // প্রথমবার চালুর সময় ডিফল্ট ভাষা (ইংলিশ) সেট করা হয়
        val p = AppSettings.prefs(this)
        if (!p.getBoolean("lang_applied", false)) {
            p.edit().putBoolean("lang_applied", true).apply()
            AppSettings.applyLocale(AppSettings.lang(this))
        }

        setContentView(R.layout.activity_main)

        // সিস্টেম বার স্বচ্ছ; কনটেন্ট বারের নিচে পর্যন্ত যায়, হেডার স্ট্যাটাস বারের নিচে নামে
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val root = findViewById<View>(R.id.root)
        val headerRow = findViewById<View>(R.id.headerRow)
        val baseTop = headerRow.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, 0, bars.right, 0)
            headerRow.setPadding(
                headerRow.paddingLeft, baseTop + bars.top,
                headerRow.paddingRight, headerRow.paddingBottom
            )
            insets
        }

        content = findViewById(R.id.content)
        recycler = findViewById(R.id.recycler)
        progress = findViewById(R.id.progress)
        emptyBox = findViewById(R.id.emptyBox)
        emptyIcon = findViewById(R.id.emptyIcon)
        emptyText = findViewById(R.id.emptyText)
        btnPermission = findViewById(R.id.btnPermission)
        btnSample = findViewById(R.id.btnSample)
        headerSub = findViewById(R.id.headerSub)
        btnView = findViewById(R.id.btnView)
        settingsPage = findViewById(R.id.settingsPage)
        nav = findViewById(R.id.nav)

        adapter = VideoAdapter { video -> openPlaylist(video) }
        recycler.adapter = adapter
        applyViewMode()

        // হেডারের বাটনে চাপলে ভিউ বদলায়: লিস্ট → গ্রিড → কমপ্যাক্ট
        btnView.setOnClickListener {
            setViewMode((AppSettings.viewMode(this) + 1) % 3)
        }

        btnPermission.setOnClickListener { permissionLauncher.launch(requestPermissions) }
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
        askNotificationOnce()
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

    private fun askNotificationOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val p = AppSettings.prefs(this)
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted && !p.getBoolean("notif_asked", false)) {
            p.edit().putBoolean("notif_asked", true).apply()
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ---------- ট্যাব ----------
    private fun refreshTab(animate: Boolean) {
        if (currentTab == TAB_SETTINGS) {
            recycler.visibility = View.GONE
            emptyBox.visibility = View.GONE
            settingsPage.visibility = View.VISIBLE
            headerSub.text = getString(R.string.header_settings)
            btnView.visibility = View.GONE
        } else {
            settingsPage.visibility = View.GONE
            btnView.visibility = View.VISIBLE
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

    // ---------- ভিউ মোড ----------
    private fun spanCount(): Int {
        val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        return (widthDp / 170f).toInt().coerceIn(2, 5)
    }

    private fun applyViewMode() {
        val mode = AppSettings.viewMode(this)
        adapter.setMode(mode)
        recycler.layoutManager =
            if (mode == AppSettings.VIEW_GRID) GridLayoutManager(this, spanCount())
            else LinearLayoutManager(this)
        btnView.setImageResource(
            when (mode) {
                AppSettings.VIEW_GRID -> R.drawable.ic_view_grid
                AppSettings.VIEW_COMPACT -> R.drawable.ic_view_compact
                else -> R.drawable.ic_view_list
            }
        )
    }

    private fun setViewMode(mode: Int) {
        AppSettings.setViewMode(this, mode)
        applyViewMode()
        buildSettings()
    }

    // ---------- সেটিংস: ভাষা, থিম, ভিউ ----------
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

        buildOptions(
            findViewById(R.id.viewOptions),
            listOf(
                getString(R.string.layout_list),
                getString(R.string.layout_grid),
                getString(R.string.layout_compact)
            ),
            AppSettings.viewMode(this)
        ) { i -> setViewMode(i) }

        val onOff = listOf(getString(R.string.opt_on), getString(R.string.opt_off))
        buildOptions(
            findViewById(R.id.glassOptions),
            onOff,
            if (AppSettings.glass(this)) 0 else 1
        ) { i ->
            AppSettings.setGlass(this, i == 0)
            recreate()
        }
        buildOptions(
            findViewById(R.id.bgOptions),
            onOff,
            if (AppSettings.backgroundPlay(this)) 0 else 1
        ) { i ->
            AppSettings.setBackgroundPlay(this, i == 0)
            buildSettings()
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
            tv.textSize = 13f
            tv.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            tv.setPadding(dp(6), dp(11), dp(6), dp(11))
            if (sel) tv.setBackgroundResource(R.drawable.bg_option_selected) else tv.background = null
            tv.setTextColor(
                if (sel) ContextCompat.getColor(this, R.color.on_accent)
                else ContextCompat.getColor(this, R.color.text_secondary)
            )
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (i > 0) lp.marginStart = dp(4)
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
