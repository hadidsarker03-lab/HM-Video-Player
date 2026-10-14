package com.example.videoplayer

import android.content.Intent
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : MediaActivity() {

    private lateinit var titleView: TextView
    private lateinit var searchBar: View
    private lateinit var searchInput: android.widget.EditText
    private lateinit var btnSearch: ImageView
    private lateinit var subRow: View
    private lateinit var tabRow: LinearLayout
    private lateinit var btnSort: ImageView
    private lateinit var btnView: ImageView
    private lateinit var content: View
    private lateinit var recycler: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var emptyBox: View
    private lateinit var emptyIcon: ImageView
    private lateinit var emptyText: TextView
    private lateinit var btnPermission: Button
    private lateinit var btnSample: Button
    private lateinit var pageMe: View
    private lateinit var nav: BottomNavigationView
    private lateinit var mediaAdapter: VideoAdapter
    private lateinit var rowAdapter: RowAdapter
    private lateinit var selection: SelectionBar
    private var librarySig = 0

    private var tab = TAB_VIDEO
    private var sub = SUB_ALL
    private var query = ""
    private var loading = false
    private var glassAtCreate = true
    private var wallAtCreate = false
    private var accentAtCreate = 0
    private var lmKey = ""
    private var shown: List<Video> = emptyList()
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

    private class MeCell(val icon: Int, val label: Int, val action: () -> Unit)

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applyTheme(this)
        super.onCreate(savedInstanceState)
        glassAtCreate = AppSettings.glass(this)
        wallAtCreate = Backgrounds.hasWallpaper(this)
        accentAtCreate = AppSettings.textIndex(this)

        // প্রথমবার চালুর সময় ডিফল্ট ভাষা (ইংলিশ) সেট করা হয়
        val p = AppSettings.prefs(this)
        if (!p.getBoolean("lang_applied", false)) {
            p.edit().putBoolean("lang_applied", true).apply()
            AppSettings.applyLocale(AppSettings.lang(this))
        }

        setContentView(R.layout.activity_main)
        UiKit.applyInsets(this, findViewById(R.id.root), findViewById(R.id.headerRow), false)

        titleView = findViewById(R.id.title)
        searchBar = findViewById(R.id.searchBar)
        searchInput = findViewById(R.id.searchInput)
        btnSearch = findViewById(R.id.btnSearch)
        subRow = findViewById(R.id.subRow)
        tabRow = findViewById(R.id.tabRow)
        btnSort = findViewById(R.id.btnSort)
        btnView = findViewById(R.id.btnView)
        content = findViewById(R.id.content)
        recycler = findViewById(R.id.recycler)
        recycler.setHasFixedSize(true)
        recycler.setItemViewCacheSize(14)
        recycler.itemAnimator = null
        progress = findViewById(R.id.progress)
        emptyBox = findViewById(R.id.emptyBox)
        emptyIcon = findViewById(R.id.emptyIcon)
        emptyText = findViewById(R.id.emptyText)
        btnPermission = findViewById(R.id.btnPermission)
        btnSample = findViewById(R.id.btnSample)
        pageMe = findViewById(R.id.pageMe)
        nav = findViewById(R.id.nav)
        nav.isItemActiveIndicatorEnabled = false

        mediaAdapter = VideoAdapter(
            this,
            onClick = { v -> MediaActions.play(this, shown, v) },
            onMore = { v, anchor ->
                MediaActions.showMenu(
                    this, anchor, v, shown, null,
                    onSelect = { mediaAdapter.startSelection(v) }
                ) { refresh() }
            }
        )
        selection = SelectionBar(this, findViewById(R.id.root), mediaAdapter) { refresh() }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (selection.active()) {
                    selection.exit()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
        rowAdapter = RowAdapter(onClick = { onRowClick(it) }, onLong = { onRowLong(it) })

        btnPermission.setOnClickListener { permissionLauncher.launch(requestPermissions) }
        btnSample.setOnClickListener {
            startActivity(
                android.content.Intent(this, PlayerActivity::class.java)
                    .putExtra(PlayerActivity.EXTRA_URI, SAMPLE_URL)
                    .putExtra(PlayerActivity.EXTRA_TITLE, getString(R.string.sample_title))
            )
        }

        btnSearch.setOnClickListener {
            if (searchBar.visibility == View.VISIBLE) closeSearch() else openSearch()
        }
        searchInput.doAfterTextChanged {
            query = it?.toString() ?: ""
            refresh()
        }
        btnSort.setOnClickListener { showSortMenu() }
        btnView.setOnClickListener {
            AppSettings.setViewMode(this, (AppSettings.viewMode(this) + 1) % 3)
            applyViewMode()
            refresh()
        }

        tab = savedInstanceState?.getInt(KEY_TAB, TAB_VIDEO) ?: TAB_VIDEO
        sub = savedInstanceState?.getInt(KEY_SUB, SUB_ALL) ?: SUB_ALL
        nav.selectedItemId = navIdFor(tab)
        nav.setOnItemSelectedListener { item ->
            val newTab = when (item.itemId) {
                R.id.nav_audio -> TAB_MUSIC
                R.id.nav_me -> TAB_ME
                else -> TAB_VIDEO
            }
            var dir = 0
            if (newTab != tab) {
                selection.exit()
                if (searchBar.visibility == View.VISIBLE) closeSearch()
                dir = if (newTab > tab) 1 else -1
                tab = newTab
                Anim.bounceNavIcon(nav, item.itemId)
            }
            buildTabs()
            refresh(true, dir)
            true
        }

        buildMe()
        applyViewMode()
        buildTabs()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (glassAtCreate != AppSettings.glass(this) ||
            wallAtCreate != Backgrounds.hasWallpaper(this) ||
            accentAtCreate != AppSettings.textIndex(this)
        ) {
            recreate()
            return
        }
        applyViewMode()
        if (!Library.loaded) refresh()
        loadMedia()
        if (tab == TAB_ME) refresh()
    }

    // ঘোরালে অ্যাক্টিভিটি নতুন করে তৈরি হয় না; শুধু কলাম সংখ্যা ঠিক করা হয়
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        lmKey = ""
        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, tab)
        outState.putInt(KEY_SUB, sub)
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    private fun navIdFor(t: Int) = when (t) {
        TAB_MUSIC -> R.id.nav_audio
        TAB_ME -> R.id.nav_me
        else -> R.id.nav_video
    }

    // ---------- মিডিয়া লোড ----------
    private fun hasPermission() = permissions.any {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun loadMedia() {
        if (!hasPermission()) {
            refresh()
            return
        }
        askNotificationOnce()
        if (loading) return
        loading = true
        progress.visibility = View.VISIBLE
        executor.execute {
            Library.reload(applicationContext)
            val sig = (Library.videos.map { it.id to it.title } + Library.audios.map { it.id to it.title })
                .hashCode()
            runOnUiThread {
                loading = false
                progress.visibility = View.GONE
                // তালিকা বদলালে তবেই আবার সাজানো হয়
                if (sig != librarySig) {
                    librarySig = sig
                    refresh()
                }
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

    // ---------- সার্চ ----------
    private fun openSearch() {
        titleView.visibility = View.GONE
        searchBar.visibility = View.VISIBLE
        btnSearch.setImageResource(R.drawable.ic_close)
        searchInput.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(searchInput, 0)
    }

    private fun closeSearch() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
        searchInput.setText("")
        query = ""
        searchBar.visibility = View.GONE
        titleView.visibility = View.VISIBLE
        btnSearch.setImageResource(R.drawable.ic_search)
        refresh()
    }

    // ---------- সাব-ট্যাব: Video / Folder / Playlist ----------
    private fun buildTabs() {
        val labels = listOf(
            if (tab == TAB_MUSIC) R.string.sub_songs else R.string.sub_video,
            R.string.sub_folder,
            R.string.sub_playlist
        )
        tabRow.removeAllViews()
        labels.forEachIndexed { i, res ->
            val selected = i == sub
            val cell = LinearLayout(this)
            cell.orientation = LinearLayout.VERTICAL
            cell.gravity = Gravity.CENTER_HORIZONTAL
            cell.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 9), UiKit.dp(this, 14), UiKit.dp(this, 9))
            cell.setBackgroundResource(if (selected) R.drawable.bg_tab_on else R.drawable.bg_tab)

            val tv = TextView(this)
            tv.setText(res)
            tv.textSize = 16f
            // সিস্টেম ফন্টের ওজন বদলালেও লেখা যেন না কাটে: ডিফল্ট ফন্ট + নিজস্ব ন্যূনতম প্রস্থ
            tv.typeface = if (selected) android.graphics.Typeface.DEFAULT_BOLD
            else android.graphics.Typeface.DEFAULT
            tv.setSingleLine(true)
            tv.ellipsize = null
            tv.includeFontPadding = true
            tv.setPadding(UiKit.dp(this, 3), 0, UiKit.dp(this, 3), 0)
            tv.minWidth = (tv.paint.measureText(getString(res)) * 1.15f).toInt() + UiKit.dp(this, 8)
            tv.setTextColor(
                UiKit.textColor(this)
            )
            cell.addView(tv, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))

            val bar = View(this)
            bar.setBackgroundResource(R.drawable.bg_indicator)
            bar.layoutParams = LinearLayout.LayoutParams(UiKit.dp(this, 20), UiKit.dp(this, 3))
                .apply { topMargin = UiKit.dp(this@MainActivity, 4) }
            // নিচের দাগ আর লাগে না: নির্বাচিত ট্যাব Neumorphism-এ ভেতরে বসা দেখায়
            bar.visibility = View.GONE
            cell.addView(bar)

            cell.setOnClickListener {
                val d = if (i > sub) 1 else if (i < sub) -1 else 0
                sub = i
                buildTabs()
                refresh(true, d)
            }
            tabRow.addView(cell, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = UiKit.dp(this@MainActivity, 8); topMargin = UiKit.dp(this@MainActivity, 6); bottomMargin = UiKit.dp(this@MainActivity, 6) })
        }
    }

    // ---------- সাজানো ও ভিউ ----------
    private fun showSortMenu() {
        val labels = intArrayOf(
            R.string.sort_date_new, R.string.sort_date_old, R.string.sort_name_az,
            R.string.sort_name_za, R.string.sort_size_big, R.string.sort_size_small,
            R.string.sort_dur_long, R.string.sort_dur_short
        )
        UiKit.borderedRadioMenu(this, btnSort, labels.toList(), AppSettings.sort(this)) { i ->
            AppSettings.setSort(this, i)
            refresh()
        }
    }

    private fun sortList(list: List<Video>): List<Video> = when (AppSettings.sort(this)) {
        0 -> list.sortedByDescending { it.dateAdded }
        1 -> list.sortedBy { it.dateAdded }
        2 -> list.sortedBy { it.title.lowercase() }
        3 -> list.sortedByDescending { it.title.lowercase() }
        4 -> list.sortedByDescending { it.size }
        5 -> list.sortedBy { it.size }
        6 -> list.sortedByDescending { it.duration }
        else -> list.sortedBy { it.duration }
    }

    private fun applyViewMode() {
        val mode = AppSettings.viewMode(this)
        mediaAdapter.setMode(mode)
        btnView.setImageResource(
            when (mode) {
                AppSettings.VIEW_GRID -> R.drawable.ic_view_grid
                AppSettings.VIEW_COMPACT -> R.drawable.ic_view_compact
                else -> R.drawable.ic_view_list
            }
        )
    }

    private fun spanCount(): Int {
        val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        return (widthDp / 170f).toInt().coerceIn(2, 5)
    }

    private fun setMediaLayout() {
        val grid = AppSettings.viewMode(this) == AppSettings.VIEW_GRID
        val span = spanCount()
        val key = if (grid) "grid$span" else "linear"
        if (key != lmKey) {
            lmKey = key
            if (grid) {
                val lm = GridLayoutManager(this, span)
                lm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int =
                        if (mediaAdapter.isHeader(position)) span else 1
                }
                recycler.layoutManager = lm
            } else {
                recycler.layoutManager = LinearLayoutManager(this)
            }
        }
        if (recycler.adapter !== mediaAdapter) recycler.adapter = mediaAdapter
    }

    private fun setRowLayout() {
        if (lmKey != "rows") {
            lmKey = "rows"
            recycler.layoutManager = LinearLayoutManager(this)
        }
        if (recycler.adapter !== rowAdapter) recycler.adapter = rowAdapter
    }

    // ---------- তালিকা দেখানো ----------
    private fun refresh(animate: Boolean = false, dir: Int = 0) {
        val isMe = tab == TAB_ME
        pageMe.visibility = if (isMe) View.VISIBLE else View.GONE
        subRow.visibility = if (isMe) View.GONE else View.VISIBLE
        btnSearch.visibility = if (isMe) View.GONE else View.VISIBLE
        if (isMe) {
            recycler.visibility = View.GONE
            emptyBox.visibility = View.GONE
        } else {
            val audio = tab == TAB_MUSIC
            val isAll = sub == SUB_ALL
            btnSort.visibility = if (isAll) View.VISIBLE else View.GONE
            btnView.visibility = if (isAll) View.VISIBLE else View.GONE
            when (sub) {
                SUB_ALL -> showMedia(audio)
                SUB_FOLDER -> showFolders(audio)
                else -> showPlaylists(audio)
            }
        }
        if (animate) Anim.slideIn(content, dir)
    }

    private fun showEmpty(audio: Boolean, message: Int, withButtons: Boolean) {
        recycler.visibility = View.GONE
        emptyBox.visibility = View.VISIBLE
        emptyIcon.setImageResource(if (audio) R.drawable.ic_audio else R.drawable.ic_video)
        emptyText.setText(message)
        val needPermission = !hasPermission()
        btnPermission.visibility = if (withButtons && needPermission) View.VISIBLE else View.GONE
        btnSample.visibility = if (withButtons && !audio) View.VISIBLE else View.GONE
    }

    private fun showMedia(audio: Boolean) {
        var list = Library.list(audio)
        if (audio && AppSettings.hideShort(this)) list = list.filter { it.duration >= 30_000 }
        val q = query.trim()
        if (q.isNotEmpty()) list = list.filter { it.title.contains(q, ignoreCase = true) }
        list = sortList(list)
        shown = list

        setMediaLayout()
        val rows: List<Row> =
            if (AppSettings.sort(this) <= 1) withMonthHeaders(list) else list.map { Row.Item(it) }
        mediaAdapter.submit(rows)

        if (list.isEmpty()) {
            if (loading) {
                recycler.visibility = View.GONE
                emptyBox.visibility = View.GONE
            } else when {
                !hasPermission() -> showEmpty(audio, R.string.perm_needed, true)
                q.isNotEmpty() -> showEmpty(audio, R.string.no_results, false)
                else -> showEmpty(audio, if (audio) R.string.empty_audio else R.string.empty_video, true)
            }
        } else {
            emptyBox.visibility = View.GONE
            recycler.visibility = View.VISIBLE
        }
    }

    private fun withMonthHeaders(list: List<Video>): List<Row> {
        val fmt = SimpleDateFormat("LLL yyyy", Locale.getDefault())
        val rows = mutableListOf<Row>()
        var last = ""
        for (v in list) {
            val label = if (v.dateAdded > 0) fmt.format(Date(v.dateAdded * 1000)) else ""
            if (label.isNotEmpty() && label != last) {
                rows.add(Row.Header(label))
                last = label
            }
            rows.add(Row.Item(v))
        }
        return rows
    }

    private fun showFolders(audio: Boolean) {
        val unknown = getString(R.string.folder_unknown)
        val groups = Library.list(audio).groupBy { it.folder.ifEmpty { unknown } }
        val rows = groups.entries.sortedBy { it.key.lowercase() }.map {
            RowData(
                "f:" + it.key, R.drawable.ic_folder, it.key,
                getString(if (audio) R.string.header_audio else R.string.header_videos, it.value.size)
            )
        }
        setRowLayout()
        rowAdapter.submit(rows)
        if (rows.isEmpty()) {
            if (loading) {
                recycler.visibility = View.GONE
                emptyBox.visibility = View.GONE
            } else {
                showEmpty(audio, if (!hasPermission()) R.string.perm_needed else
                    if (audio) R.string.empty_audio else R.string.empty_video, true)
            }
        } else {
            emptyBox.visibility = View.GONE
            recycler.visibility = View.VISIBLE
        }
    }

    private fun showPlaylists(audio: Boolean) {
        val rows = mutableListOf(
            RowData("new", R.drawable.ic_playlist_add, getString(R.string.new_playlist), ""),
            RowData(
                "fav", R.drawable.ic_favorite, getString(R.string.favorites),
                getString(R.string.count_items, Store.favoriteCount(this, audio))
            )
        )
        for ((n, ids) in Store.playlists(this, audio)) {
            rows.add(RowData("p:$n", R.drawable.ic_playlist, n, getString(R.string.count_items, ids.size)))
        }
        setRowLayout()
        rowAdapter.submit(rows)
        emptyBox.visibility = View.GONE
        recycler.visibility = View.VISIBLE
    }

    private fun onRowClick(r: RowData) {
        val audio = tab == TAB_MUSIC
        when {
            r.key == "new" -> MediaActions.promptNewPlaylist(this, audio) { refresh() }
            r.key == "fav" ->
                MediaListActivity.start(this, MediaListActivity.MODE_FAVORITES, audio)
            r.key.startsWith("p:") ->
                MediaListActivity.start(this, MediaListActivity.MODE_PLAYLIST, audio, r.key.substring(2))
            r.key.startsWith("f:") ->
                MediaListActivity.start(this, MediaListActivity.MODE_FOLDER, audio, r.key.substring(2))
        }
    }

    private fun onRowLong(r: RowData) {
        if (!r.key.startsWith("p:")) return
        val audio = tab == TAB_MUSIC
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.delete_playlist_q)
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                Store.deletePlaylist(this, audio, r.key.substring(2))
                refresh()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    // ---------- Me ট্যাব ----------
    private fun buildMe() {
        findViewById<TextView>(R.id.meVersion).text = getString(R.string.about_text, versionName())

        val grid = findViewById<LinearLayout>(R.id.meGrid)
        grid.removeAllViews()
        val cells = listOf(
            MeCell(R.drawable.ic_history, R.string.history) {
                MediaListActivity.start(this, MediaListActivity.MODE_HISTORY, false, "", true)
            },
            MeCell(R.drawable.ic_favorite, R.string.favorites) {
                MediaListActivity.start(this, MediaListActivity.MODE_FAVORITES, false, "", true)
            },
            MeCell(R.drawable.ic_playlist, R.string.sub_playlist) {
                sub = SUB_PLAYLIST
                tab = TAB_VIDEO
                nav.selectedItemId = R.id.nav_video
                buildTabs()
                refresh(true)
            },
            MeCell(R.drawable.ic_palette, R.string.settings_theme) { UiKit.chooseTheme(this) },
            MeCell(R.drawable.ic_language, R.string.settings_language) { UiKit.chooseLanguage(this) },
            MeCell(R.drawable.ic_browser, R.string.me_browser) {
                startActivity(android.content.Intent(this, BrowserActivity::class.java))
            }
        )
        cells.chunked(3).forEach { chunk ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            chunk.forEach { row.addView(meCell(it)) }
            grid.addView(row)
        }

        val list = findViewById<LinearLayout>(R.id.meList)
        list.removeAllViews()
        UiKit.row(this, list, R.drawable.ic_settings, getString(R.string.tab_settings), null) {
            SettingsActivity.open(this)
        }
        UiKit.row(this, list, R.drawable.ic_info, getString(R.string.me_about), null) {
            SettingsActivity.openAbout(this)
        }
    }

    private fun meCell(c: MeCell): View {
        val cell = LinearLayout(this)
        cell.orientation = LinearLayout.VERTICAL
        cell.gravity = Gravity.CENTER
        cell.setPadding(0, UiKit.dp(this, 16), 0, UiKit.dp(this, 14))
        cell.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        cell.setBackgroundResource(UiKit.ripple(this))

        // আইকনের চারপাশে Neumorphism বর্ডার
        val tile = FrameLayout(this)
        tile.setBackgroundResource(R.drawable.bg_tile)
        val icon = ImageView(this)
        icon.setImageResource(c.icon)
        icon.setColorFilter(UiKit.textColor(this))
        tile.addView(icon, FrameLayout.LayoutParams(UiKit.dp(this, 24), UiKit.dp(this, 24), Gravity.CENTER))
        cell.addView(tile, LinearLayout.LayoutParams(UiKit.dp(this, 48), UiKit.dp(this, 48)))

        val label = TextView(this)
        label.setText(c.label)
        label.textSize = 13f
        label.gravity = Gravity.CENTER
        label.setTextColor(UiKit.textColor(this))
        label.setPadding(0, UiKit.dp(this, 8), 0, 0)
        cell.addView(label)

        cell.setOnClickListener { c.action() }
        return cell
    }

    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
    } catch (e: Exception) {
        "1.0"
    }

    companion object {
        private const val TAB_VIDEO = 0
        private const val TAB_MUSIC = 1
        private const val TAB_ME = 2
        private const val SUB_ALL = 0
        private const val SUB_FOLDER = 1
        private const val SUB_PLAYLIST = 2
        private const val KEY_TAB = "key_tab"
        private const val KEY_SUB = "key_sub"
        const val SAMPLE_URL =
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
    }
}
