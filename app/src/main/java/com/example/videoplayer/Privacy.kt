package com.example.videoplayer

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

// লুকানো ফাইলের তালিকা + পাসওয়ার্ড। ফাইল থাকে অ্যাপের নিজস্ব ফোল্ডারে (.nomedia সহ),
// তাই গ্যালারি/অন্য কোনো অ্যাপে দেখা যায় না।
object PrivacyStore {
    private const val KEY_ITEMS = "privacy_items"
    private const val KEY_PW = "privacy_pw"

    data class Item(
        val file: String, val title: String, val ext: String, val mime: String,
        val relPath: String, val audio: Boolean, val duration: Long, val size: Long,
        val width: Int, val height: Int
    )

    fun dir(c: Context): File {
        val base = c.getExternalFilesDir(null) ?: c.filesDir
        val d = File(base, "privacy")
        d.mkdirs()
        val nm = File(d, ".nomedia")
        if (!nm.exists()) try { nm.createNewFile() } catch (e: Exception) { }
        return d
    }

    fun items(c: Context): List<Item> {
        val raw = AppSettings.prefs(c).getString(KEY_ITEMS, null) ?: return emptyList()
        val out = mutableListOf<Item>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += Item(
                    o.getString("file"), o.optString("title"), o.optString("ext"),
                    o.optString("mime"), o.optString("rel"), o.optBoolean("audio"),
                    o.optLong("dur"), o.optLong("size"), o.optInt("w"), o.optInt("h")
                )
            }
        } catch (e: Exception) { }
        return out
    }

    private fun save(c: Context, list: List<Item>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("file", it.file).put("title", it.title).put("ext", it.ext)
                .put("mime", it.mime).put("rel", it.relPath).put("audio", it.audio)
                .put("dur", it.duration).put("size", it.size).put("w", it.width).put("h", it.height))
        }
        AppSettings.prefs(c).edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    fun add(c: Context, item: Item) = save(c, items(c) + item)
    fun remove(c: Context, file: String) = save(c, items(c).filter { it.file != file })

    fun toVideo(c: Context, it: Item) = Video(
        id = ("p:" + it.file).hashCode().toLong() and 0x7fffffffL,
        uri = Uri.fromFile(File(dir(c), it.file)),
        title = it.title, duration = it.duration, size = it.size,
        width = it.width, height = it.height, folder = "", isAudio = it.audio
    )

    fun find(c: Context, v: Video): Item? =
        items(c).firstOrNull { it.audio == v.isAudio && toVideo(c, it).id == v.id }

    // ---- পাসওয়ার্ড ----
    private fun hash(salt: String, pw: String) = MessageDigest.getInstance("SHA-256")
        .digest((salt + pw).toByteArray()).joinToString("") { "%02x".format(it) }

    fun hasPassword(c: Context) = AppSettings.prefs(c).getString(KEY_PW, null) != null

    fun setPassword(c: Context, pw: String) {
        val salt = UUID.randomUUID().toString()
        AppSettings.prefs(c).edit().putString(KEY_PW, "$salt:${hash(salt, pw)}").apply()
    }

    fun check(c: Context, pw: String): Boolean {
        val s = AppSettings.prefs(c).getString(KEY_PW, null) ?: return false
        val parts = s.split(":")
        return parts.size == 2 && hash(parts[0], pw) == parts[1]
    }
}

// পাসওয়ার্ড ডায়ালগ
object PrivacyUi {
    private fun field(ctx: Context, hint: Int) = EditText(ctx).apply {
        setHint(hint)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        setSingleLine()
    }

    private fun box(ctx: Context, vararg f: EditText) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val m = UiKit.dp(ctx, 22)
        setPadding(m, UiKit.dp(ctx, 8), m, 0)
        f.forEach { addView(it) }
    }

    fun enter(act: MediaActivity, onOk: () -> Unit, onCancel: () -> Unit) {
        val pw = field(act, R.string.enter_password)
        val dlg = MaterialAlertDialogBuilder(act)
            .setTitle(R.string.privacy)
            .setView(box(act, pw))
            .setCancelable(false)
            .setPositiveButton(R.string.btn_ok, null)
            .setNegativeButton(R.string.btn_cancel) { _, _ -> onCancel() }
            .create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (PrivacyStore.check(act, pw.text.toString())) {
                    dlg.dismiss()
                    onOk()
                } else {
                    pw.error = act.getString(R.string.password_wrong)
                }
            }
        }
        dlg.show()
    }

    fun setPassword(act: MediaActivity, onSet: () -> Unit, onCancel: () -> Unit) {
        val a = field(act, R.string.set_password)
        val b = field(act, R.string.confirm_password)
        val dlg = MaterialAlertDialogBuilder(act)
            .setTitle(R.string.set_password)
            .setMessage(R.string.password_warn)
            .setView(box(act, a, b))
            .setCancelable(false)
            .setPositiveButton(R.string.btn_ok, null)
            .setNegativeButton(R.string.btn_cancel) { _, _ -> onCancel() }
            .create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val p = a.text.toString()
                when {
                    p.length < 4 -> a.error = act.getString(R.string.password_short)
                    p != b.text.toString() -> b.error = act.getString(R.string.password_mismatch)
                    else -> {
                        PrivacyStore.setPassword(act, p)
                        dlg.dismiss()
                        onSet()
                    }
                }
            }
        }
        dlg.show()
    }

    fun change(act: MediaActivity) {
        enter(act, onOk = {
            setPassword(act, onSet = {
                Toast.makeText(act, R.string.password_changed, Toast.LENGTH_SHORT).show()
            }, onCancel = {})
        }, onCancel = {})
    }
}

// হাইড / আনহাইড / স্থায়ীভাবে মোছা
object PrivacyOps {

    private class Meta(val name: String, val mime: String, val rel: String)

    private fun queryMeta(ctx: Context, uri: Uri): Meta {
        val proj = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.RELATIVE_PATH)
        else arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE)
        try {
            ctx.contentResolver.query(uri, proj, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    return Meta(
                        c.getString(0) ?: "", c.getString(1) ?: "",
                        if (proj.size > 2) (c.getString(2) ?: "") else ""
                    )
                }
            }
        } catch (e: Exception) { }
        return Meta("", "", "")
    }

    private fun progress(act: MediaActivity): AlertDialog =
        MaterialAlertDialogBuilder(act)
            .setMessage(R.string.privacy_working)
            .setCancelable(false)
            .show()

    // ---------- হাইড ----------
    fun hide(act: MediaActivity, items: List<Video>, onDone: () -> Unit) {
        if (items.isEmpty()) return
        if (!PrivacyStore.hasPassword(act)) {
            PrivacyUi.setPassword(act, onSet = { hide(act, items, onDone) }, onCancel = {})
            return
        }
        MaterialAlertDialogBuilder(act)
            .setTitle(R.string.menu_hide)
            .setMessage(R.string.hide_confirm)
            .setPositiveButton(R.string.btn_ok) { _, _ -> runHide(act, items, onDone) }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun runHide(act: MediaActivity, items: List<Video>, onDone: () -> Unit) {
        val dlg = progress(act)
        val app = act.applicationContext
        Thread {
            val done = mutableListOf<Pair<Video, PrivacyStore.Item>>()
            for (v in items) {
                var out: File? = null
                try {
                    val meta = queryMeta(app, v.uri)
                    val ext = if (meta.name.contains('.')) "." + meta.name.substringAfterLast('.') else ""
                    val fname = UUID.randomUUID().toString() + ext
                    val target = File(PrivacyStore.dir(app), fname)
                    out = target
                    app.contentResolver.openInputStream(v.uri)!!.use { i ->
                        target.outputStream().use { o -> i.copyTo(o) }
                    }
                    if (target.length() <= 0L || (v.size > 0 && target.length() != v.size)) {
                        target.delete()
                        continue
                    }
                    val mime = meta.mime.ifEmpty { if (v.isAudio) "audio/mpeg" else "video/mp4" }
                    done += v to PrivacyStore.Item(
                        fname, v.title, ext, mime, meta.rel, v.isAudio,
                        v.duration, v.size, v.width, v.height
                    )
                } catch (e: Exception) {
                    out?.delete()
                }
            }
            act.runOnUiThread {
                dlg.dismiss()
                removeOriginals(act, done, onDone)
            }
        }.start()
    }

    // কপি সফল হলে তবেই আসল ফাইল মোছা হয়; মোছা না গেলে কপি বাতিল
    private fun removeOriginals(
        act: MediaActivity, done: List<Pair<Video, PrivacyStore.Item>>, onDone: () -> Unit
    ) {
        if (done.isEmpty()) {
            Toast.makeText(act, R.string.action_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val dir = PrivacyStore.dir(act)
        val check = {
            val removed = done.filter { Library.find(it.first.isAudio, it.first.id) == null }
            val kept = done - removed.toSet()
            removed.forEach { PrivacyStore.add(act, it.second) }
            kept.forEach { File(dir, it.second.file).delete() }
            Toast.makeText(
                act, if (removed.isNotEmpty()) R.string.hidden_done else R.string.action_failed,
                Toast.LENGTH_SHORT
            ).show()
            onDone()
        }
        val uris = done.map { it.first.uri }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pi = MediaStore.createDeleteRequest(act.contentResolver, uris)
                act.requestGrant(pi.intentSender, onCancel = {
                    done.forEach { File(dir, it.second.file).delete() }
                }) { MediaActions.finishChange(act, 0, 0, check) }
            } catch (e: Exception) {
                done.forEach { File(dir, it.second.file).delete() }
                Toast.makeText(act, R.string.action_failed, Toast.LENGTH_SHORT).show()
            }
        } else {
            MediaActions.deleteLegacy(act, done.map { it.first }, 0, 0, check, 0)
        }
    }

    // ---------- আনহাইড ----------
    fun unhide(act: MediaActivity, videos: List<Video>, onDone: () -> Unit) {
        val list = videos.mapNotNull { PrivacyStore.find(act, it) }
        if (list.isEmpty()) return
        val dlg = progress(act)
        val app = act.applicationContext
        Thread {
            var ok = 0
            list.forEach { if (restore(app, it)) ok++ }
            Library.reload(app)
            act.runOnUiThread {
                dlg.dismiss()
                Toast.makeText(
                    act, if (ok > 0) R.string.unhidden_done else R.string.action_failed,
                    Toast.LENGTH_SHORT
                ).show()
                onDone()
            }
        }.start()
    }

    private fun restore(ctx: Context, it: PrivacyStore.Item): Boolean {
        val src = File(PrivacyStore.dir(ctx), it.file)
        if (!src.exists()) {
            PrivacyStore.remove(ctx, it.file)
            return false
        }
        val name = it.title + it.ext
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val coll = if (it.audio)
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, it.mime)
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        it.relPath.ifEmpty { if (it.audio) "Music/" else "Movies/" }
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = ctx.contentResolver.insert(coll, cv) ?: return false
                try {
                    ctx.contentResolver.openOutputStream(uri)!!.use { o ->
                        src.inputStream().use { i -> i.copyTo(o) }
                    }
                    val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    ctx.contentResolver.update(uri, done, null, null)
                } catch (e: Exception) {
                    ctx.contentResolver.delete(uri, null, null)
                    return false
                }
            } else {
                @Suppress("DEPRECATION")
                val base = Environment.getExternalStoragePublicDirectory(
                    if (it.audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
                )
                base.mkdirs()
                var target = File(base, name)
                var n = 1
                while (target.exists()) target = File(base, it.title + " (" + n++ + ")" + it.ext)
                src.inputStream().use { i -> target.outputStream().use { o -> i.copyTo(o) } }
                MediaScannerConnection.scanFile(ctx, arrayOf(target.absolutePath), null, null)
            }
        } catch (e: Exception) {
            return false
        }
        src.delete()
        PrivacyStore.remove(ctx, it.file)
        return true
    }

    // ---------- স্থায়ীভাবে মোছা ----------
    fun deleteForever(act: MediaActivity, videos: List<Video>, onDone: () -> Unit) {
        val list = videos.mapNotNull { PrivacyStore.find(act, it) }
        if (list.isEmpty()) return
        MaterialAlertDialogBuilder(act)
            .setMessage(act.getString(R.string.delete_files_q, list.size))
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                list.forEach {
                    File(PrivacyStore.dir(act), it.file).delete()
                    PrivacyStore.remove(act, it.file)
                }
                Toast.makeText(act, R.string.deleted, Toast.LENGTH_SHORT).show()
                onDone()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }
}

// Privacy স্ক্রিন: পাসওয়ার্ড দিয়ে ঢুকতে হয়
class PrivacyActivity : MediaActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var adapter: VideoAdapter
    private lateinit var selection: SelectionBar
    private var videos: List<Video> = emptyList()
    private var unlocked = false

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        setTheme(AppSettings.themeRes(this))
        super.onCreate(savedInstanceState)
        // রিসেন্ট অ্যাপ/স্ক্রিনশটে লুকানো ফাইল দেখা যাবে না
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_media_list)
        UiKit.applyInsets(this, findViewById(R.id.root), findViewById(R.id.headerRow), true)

        recycler = findViewById(R.id.recycler)
        recycler.setHasFixedSize(true)
        recycler.setItemViewCacheSize(14)
        recycler.itemAnimator = null
        emptyText = findViewById(R.id.emptyText)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.title).setText(R.string.privacy)
        findViewById<ImageView>(R.id.btnAction).apply {
            visibility = View.VISIBLE
            setImageResource(R.drawable.ic_more)
            contentDescription = getString(R.string.change_password)
            setOnClickListener { v ->
                PopupMenu(this@PrivacyActivity, v).apply {
                    menu.add(0, 1, 0, R.string.change_password)
                    setOnMenuItemClickListener { PrivacyUi.change(this@PrivacyActivity); true }
                    show()
                }
            }
        }

        adapter = VideoAdapter(
            this,
            onClick = { v -> play(v) },
            onMore = { v, anchor -> menu(v, anchor) }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        selection = SelectionBar(this, findViewById(R.id.root), adapter, privateMode = true) { render() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (selection.active()) selection.exit() else finish()
            }
        })

        if (PrivacyStore.hasPassword(this)) {
            PrivacyUi.enter(this, { unlock() }, { finish() })
        } else {
            PrivacyUi.setPassword(this, { unlock() }, { finish() })
        }
    }

    private fun unlock() {
        unlocked = true
        render()
    }

    override fun onResume() {
        super.onResume()
        if (unlocked) render()
    }

    private fun render() {
        if (!unlocked) return
        videos = PrivacyStore.items(this).map { PrivacyStore.toVideo(this, it) }
        adapter.submit(videos.map { Row.Item(it) })
        emptyText.setText(R.string.privacy_empty)
        emptyText.visibility = if (videos.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun play(v: Video) {
        val index = videos.indexOfFirst { it.id == v.id && it.isAudio == v.isAudio }.coerceAtLeast(0)
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putStringArrayListExtra(PlayerActivity.EXTRA_URIS, ArrayList(videos.map { it.uri.toString() }))
            putStringArrayListExtra(PlayerActivity.EXTRA_TITLES, ArrayList(videos.map { it.title }))
            putExtra(PlayerActivity.EXTRA_INDEX, index)
            putExtra(PlayerActivity.EXTRA_FLAGS, BooleanArray(videos.size) { videos[it].isAudio })
            putExtra(PlayerActivity.EXTRA_AUDIO, v.isAudio)
        })
    }

    private fun menu(v: Video, anchor: View) {
        val pm = PopupMenu(this, anchor)
        pm.menu.add(0, 1, 0, R.string.menu_play)
        pm.menu.add(0, 2, 1, R.string.menu_unhide)
        pm.menu.add(0, 3, 2, R.string.menu_select)
        pm.menu.add(0, 4, 3, R.string.btn_delete)
        pm.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> play(v)
                2 -> PrivacyOps.unhide(this, listOf(v)) { render() }
                3 -> adapter.startSelection(v)
                4 -> PrivacyOps.deleteForever(this, listOf(v)) { render() }
            }
            true
        }
        pm.show()
    }
}
