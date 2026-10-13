package com.example.videoplayer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.Executors

// ফোল্ডার, প্লেলিস্ট, প্রিয় ও ইতিহাসের তালিকা দেখানোর স্ক্রিন
class MediaListActivity : MediaActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var adapter: VideoAdapter
    private lateinit var selection: SelectionBar

    private var mode = MODE_FOLDER
    private var audio = false
    private var all = false
    private var name = ""
    private var items: List<Video> = emptyList()
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_list)
        UiKit.applyInsets(this, findViewById(R.id.root), findViewById(R.id.headerRow), true)

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_FOLDER
        audio = intent.getBooleanExtra(EXTRA_AUDIO, false)
        all = intent.getBooleanExtra(EXTRA_ALL, false)
        name = intent.getStringExtra(EXTRA_NAME) ?: ""

        recycler = findViewById(R.id.recycler)
        recycler.setHasFixedSize(true)
        recycler.setItemViewCacheSize(14)
        recycler.itemAnimator = null
        emptyText = findViewById(R.id.emptyText)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.title).text = when (mode) {
            MODE_FAVORITES -> getString(R.string.favorites)
            MODE_HISTORY -> getString(R.string.history)
            else -> name
        }

        val action = findViewById<ImageView>(R.id.btnAction)
        if (mode == MODE_HISTORY || mode == MODE_PLAYLIST) {
            action.visibility = View.VISIBLE
            action.setOnClickListener { confirmAction() }
        }

        adapter = VideoAdapter(
            this,
            onClick = { v -> MediaActions.play(this, items, v) },
            onMore = { v, anchor ->
                MediaActions.showMenu(
                    this, anchor, v, items,
                    if (mode == MODE_PLAYLIST) name else null,
                    onSelect = { adapter.startSelection(v) }
                ) { render() }
            }
        )
        recycler.adapter = adapter
        selection = SelectionBar(this, findViewById(R.id.root), adapter) { render() }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (selection.active()) {
                    selection.exit()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (Library.loaded) {
            render()
        } else {
            executor.execute {
                Library.reload(applicationContext)
                runOnUiThread { render() }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    private fun confirmAction() {
        val msg = if (mode == MODE_HISTORY) R.string.clear_history_q else R.string.delete_playlist_q
        MaterialAlertDialogBuilder(this)
            .setMessage(msg)
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                if (mode == MODE_HISTORY) Store.clearHistory(this)
                else Store.deletePlaylist(this, audio, name)
                if (mode == MODE_PLAYLIST) finish() else render()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun render() {
        val unknown = getString(R.string.folder_unknown)
        items = when (mode) {
            MODE_FOLDER -> Library.list(audio).filter { it.folder.ifEmpty { unknown } == name }
            MODE_PLAYLIST -> (Store.playlists(this, audio)[name] ?: mutableListOf<Long>())
                .mapNotNull { Library.find(audio, it) }
            MODE_FAVORITES -> Store.favorites(this).mapNotNull { Library.resolve(it) }
                .filter { all || it.isAudio == audio }
            else -> Store.history(this).mapNotNull { Library.resolve(it) }
        }

        val grid = AppSettings.viewMode(this) == AppSettings.VIEW_GRID
        if (grid) {
            val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
            val span = (widthDp / 170f).toInt().coerceIn(2, 5)
            recycler.layoutManager = GridLayoutManager(this, span)
        } else if (recycler.layoutManager !is LinearLayoutManager ||
            recycler.layoutManager is GridLayoutManager
        ) {
            recycler.layoutManager = LinearLayoutManager(this)
        }
        adapter.setMode(AppSettings.viewMode(this))
        adapter.submit(items.map { Row.Item(it) })

        emptyText.setText(R.string.no_results)
        emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    companion object {
        const val MODE_FOLDER = "folder"
        const val MODE_PLAYLIST = "playlist"
        const val MODE_FAVORITES = "favorites"
        const val MODE_HISTORY = "history"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_AUDIO = "audio"
        private const val EXTRA_ALL = "all"
        private const val EXTRA_NAME = "name"

        fun start(ctx: Context, mode: String, audio: Boolean, name: String = "", all: Boolean = false) {
            ctx.startActivity(Intent(ctx, MediaListActivity::class.java).apply {
                putExtra(EXTRA_MODE, mode)
                putExtra(EXTRA_AUDIO, audio)
                putExtra(EXTRA_NAME, name)
                putExtra(EXTRA_ALL, all)
            })
        }
    }
}
