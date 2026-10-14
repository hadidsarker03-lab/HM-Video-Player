package com.example.videoplayer

import android.content.ContentValues
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
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

    // লক ফোল্ডার: ফোনের স্টোরেজে অ্যাপের জন্য আলাদা ফোল্ডার (.nomedia সহ, গ্যালারিতে দেখা যায় না)।
    // অ্যাপ আনইনস্টল করলেও এখানকার ফাইল ও তালিকা (.index.json) থেকে যায়।
    const val FOLDER = "HM Player Lock"
    private const val INDEX = ".index.json"
    private var cache: List<Item>? = null
    private var cacheStamp = -1L

    // সব ফাইল অ্যাক্সেসের অনুমতি আছে কি না
    fun hasAccess(c: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            ContextCompat.checkSelfPermission(c, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        else true

    @Suppress("DEPRECATION")
    fun dir(c: Context): File {
        val d = File(Environment.getExternalStorageDirectory(), FOLDER)
        d.mkdirs()
        val nm = File(d, ".nomedia")
        if (!nm.exists()) try { nm.createNewFile() } catch (e: Exception) { }
        return d
    }

    private fun indexFile(c: Context) = File(dir(c), INDEX)

    private fun readIndex(c: Context): JSONObject? = try {
        val f = indexFile(c)
        if (f.exists()) JSONObject(f.readText()) else null
    } catch (e: Exception) { null }

    private fun writeIndex(c: Context, list: List<Item>, pw: String?, numeric: Boolean) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("file", it.file).put("title", it.title).put("ext", it.ext)
                .put("mime", it.mime).put("rel", it.relPath).put("audio", it.audio)
                .put("dur", it.duration).put("size", it.size).put("w", it.width).put("h", it.height))
        }
        val o = JSONObject().put("items", arr).put("numeric", numeric)
        if (pw != null) o.put("pw", pw)
        val f = indexFile(c)
        val tmp = File(f.parentFile, "$INDEX.tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(f)) { f.writeText(o.toString()); tmp.delete() }
        cache = list
        cacheStamp = f.lastModified()
    }

    fun items(c: Context): List<Item> {
        if (!hasAccess(c)) return emptyList()
        migrateOld(c)
        val f = indexFile(c)
        val stamp = if (f.exists()) f.lastModified() else 0L
        cache?.let { if (stamp == cacheStamp) return it }
        val out = mutableListOf<Item>()
        try {
            val arr = readIndex(c)?.optJSONArray("items") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val it = Item(
                    o.getString("file"), o.optString("title"), o.optString("ext"),
                    o.optString("mime"), o.optString("rel"), o.optBoolean("audio"),
                    o.optLong("dur"), o.optLong("size"), o.optInt("w"), o.optInt("h")
                )
                if (File(dir(c), it.file).exists()) out += it
            }
        } catch (e: Exception) { }
        cache = out
        cacheStamp = stamp
        return out
    }

    private fun save(c: Context, list: List<Item>) {
        val old = readIndex(c)
        val pw = AppSettings.prefs(c).getString(KEY_PW, null) ?: old?.optString("pw", "")?.ifEmpty { null }
        val num = if (AppSettings.prefs(c).getString(KEY_PW, null) != null) isNumeric(c) else (old?.optBoolean("numeric", false) ?: false)
        try { writeIndex(c, list, pw, num) } catch (e: Exception) { }
    }

    // আগের সংস্করণে অ্যাপের ভিতরের ফোল্ডারে রাখা ফাইলগুলো নতুন লক ফোল্ডারে সরানো
    private fun migrateOld(c: Context) {
        val raw = AppSettings.prefs(c).getString(KEY_ITEMS, null) ?: return
        val oldDir = File(c.getExternalFilesDir(null) ?: c.filesDir, "privacy")
        val moved = mutableListOf<Item>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val src = File(oldDir, o.getString("file"))
                if (!src.exists()) continue
                val dst = File(dir(c), src.name)
                src.inputStream().use { inp -> dst.outputStream().use { out -> inp.copyTo(out) } }
                if (dst.length() == src.length()) {
                    src.delete()
                    moved += Item(
                        o.getString("file"), o.optString("title"), o.optString("ext"),
                        o.optString("mime"), o.optString("rel"), o.optBoolean("audio"),
                        o.optLong("dur"), o.optLong("size"), o.optInt("w"), o.optInt("h")
                    )
                } else dst.delete()
            }
        } catch (e: Exception) { }
        AppSettings.prefs(c).edit().remove(KEY_ITEMS).apply()
        if (moved.isNotEmpty()) {
            cache = null
            val existing = readIndexItems(c)
            save(c, existing + moved)
        }
    }

    private fun readIndexItems(c: Context): List<Item> {
        cache = null
        cacheStamp = -1
        val arr = readIndex(c)?.optJSONArray("items") ?: return emptyList()
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Item(
                o.getString("file"), o.optString("title"), o.optString("ext"),
                o.optString("mime"), o.optString("rel"), o.optBoolean("audio"),
                o.optLong("dur"), o.optLong("size"), o.optInt("w"), o.optInt("h")
            )
        }
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

    private const val KEY_NUM = "privacy_pw_numeric"

    // নতুন পাসওয়ার্ড শুধু নম্বরের; আগের (অক্ষরের) পাসওয়ার্ড থাকলে সেটা দিতে সাধারণ কিবোর্ড
    fun isNumeric(c: Context) = AppSettings.prefs(c).getBoolean(KEY_NUM, false)

    fun hasPassword(c: Context): Boolean {
        val p = AppSettings.prefs(c)
        if (p.getString(KEY_PW, null) != null) return true
        // পুনরায় ইনস্টল করলে: লক ফোল্ডারে রাখা পাসওয়ার্ড ফেরত আনা
        if (hasAccess(c)) {
            val o = readIndex(c)
            val saved = o?.optString("pw", "")
            if (!saved.isNullOrEmpty()) {
                p.edit().putString(KEY_PW, saved).putBoolean(KEY_NUM, o.optBoolean("numeric", false)).apply()
                return true
            }
        }
        return false
    }

    fun setPassword(c: Context, pw: String) {
        val salt = UUID.randomUUID().toString()
        val v = "$salt:${hash(salt, pw)}"
        AppSettings.prefs(c).edit().putString(KEY_PW, v).putBoolean(KEY_NUM, true).apply()
        if (hasAccess(c)) try { writeIndex(c, items(c), v, true) } catch (e: Exception) { }
    }

    fun check(c: Context, pw: String): Boolean {
        val s = AppSettings.prefs(c).getString(KEY_PW, null) ?: return false
        val parts = s.split(":")
        return parts.size == 2 && hash(parts[0], pw) == parts[1]
    }
}

// পাসওয়ার্ড ডায়ালগ: শুধু নম্বর + ফিঙ্গারপ্রিন্ট
object PrivacyUi {
    private fun field(ctx: Context, hint: Int, numeric: Boolean = true) = EditText(ctx).apply {
        setHint(hint)
        inputType = if (numeric)
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        setSingleLine()
        textSize = 20f
        gravity = android.view.Gravity.CENTER
        filters = arrayOf(android.text.InputFilter.LengthFilter(12))
    }

    private fun box(ctx: Context, vararg f: EditText) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val m = UiKit.dp(ctx, 22)
        setPadding(m, UiKit.dp(ctx, 8), m, 0)
        f.forEach { addView(it) }
    }

    fun canFinger(ctx: Context): Boolean = try {
        BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    } catch (e: Exception) { false }

    // ফিঙ্গারপ্রিন্ট প্রম্পট
    private fun finger(act: MediaActivity, onOk: () -> Unit, onFail: () -> Unit) {
        val prompt = BiometricPrompt(
            act, ContextCompat.getMainExecutor(act),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) = onOk()
                override fun onAuthenticationError(code: Int, msg: CharSequence) = onFail()
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(act.getString(R.string.privacy))
            .setSubtitle(act.getString(R.string.use_fingerprint))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText(act.getString(R.string.use_password))
            .build()
        prompt.authenticate(info)
    }

    fun enter(act: MediaActivity, onOk: () -> Unit, onCancel: () -> Unit) {
        val pw = field(act, R.string.enter_password, PrivacyStore.isNumeric(act))
        val dlg = MaterialAlertDialogBuilder(act)
            .setTitle(R.string.privacy)
            .setView(box(act, pw))
            .setCancelable(false)
            .setPositiveButton(R.string.btn_ok, null)
            .setNegativeButton(R.string.btn_cancel) { _, _ -> onCancel() }
            .apply { if (canFinger(act)) setNeutralButton(R.string.use_fingerprint, null) }
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
            val doFinger = {
                finger(act, { dlg.dismiss(); onOk() }, { })
            }
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener { doFinger() }
            if (canFinger(act)) doFinger() // খুলতেই ফিঙ্গারপ্রিন্ট চায়
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

// লক (সরাসরি মুভ) / আনলক (আগের জায়গায় মুভ ফেরত) / স্থায়ীভাবে মোছা
object PrivacyOps {

    // সব ফাইল অ্যাক্সেসের অনুমতি না থাকলে বুঝিয়ে সেটিংসে পাঠায়
    fun ensureAccess(act: MediaActivity, onGranted: () -> Unit, onDenied: () -> Unit = {}) {
        if (PrivacyStore.hasAccess(act)) { onGranted(); return }
        MaterialAlertDialogBuilder(act)
            .setTitle(R.string.privacy)
            .setMessage(R.string.access_needed)
            .setCancelable(false)
            .setPositiveButton(R.string.btn_ok) { _, _ ->
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        act.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                .setData(Uri.parse("package:" + act.packageName))
                        )
                    } else {
                        androidx.core.app.ActivityCompat.requestPermissions(
                            act, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 77
                        )
                    }
                } catch (e: Exception) {
                    act.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
                onDenied()
            }
            .setNegativeButton(R.string.btn_cancel) { _, _ -> onDenied() }
            .show()
    }

    private fun realPath(ctx: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        @Suppress("DEPRECATION")
        try {
            ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0)
            }
        } catch (e: Exception) { }
        return null
    }

    private fun mimeOf(ctx: Context, uri: Uri, audio: Boolean): String {
        val m = try { ctx.contentResolver.getType(uri) } catch (e: Exception) { null }
        return m ?: if (audio) "audio/mpeg" else "video/mp4"
    }

    // একই ড্রাইভে হলে মুহূর্তেই মুভ (rename); না হলে কপি করে যাচাই, তারপর আসল মোছা
    private fun moveFile(src: File, dst: File): Boolean {
        dst.parentFile?.mkdirs()
        if (src.renameTo(dst)) return true
        return try {
            src.inputStream().use { i -> dst.outputStream().use { o -> i.copyTo(o) } }
            if (dst.length() == src.length() && src.delete()) true
            else { dst.delete(); false }
        } catch (e: Exception) {
            dst.delete()
            false
        }
    }

    // পুরোনো জায়গার মিডিয়া এন্ট্রি সরাতে / নতুন জায়গা যোগ করতে স্ক্যান
    private fun rescan(ctx: Context, paths: List<String>, then: () -> Unit) {
        if (paths.isEmpty()) { then(); return }
        val left = java.util.concurrent.atomic.AtomicInteger(paths.size)
        MediaScannerConnection.scanFile(ctx, paths.toTypedArray(), null) { _, _ ->
            if (left.decrementAndGet() == 0) then()
        }
    }

    private fun progress(act: MediaActivity): AlertDialog =
        MaterialAlertDialogBuilder(act)
            .setMessage(R.string.privacy_working)
            .setCancelable(false)
            .show()

    // ---------- লক (হাইড) ----------
    fun hide(act: MediaActivity, items: List<Video>, onDone: () -> Unit) {
        if (items.isEmpty()) return
        if (!PrivacyStore.hasPassword(act)) {
            PrivacyUi.setPassword(act, onSet = { hide(act, items, onDone) }, onCancel = {})
            return
        }
        ensureAccess(act, onGranted = {
            MaterialAlertDialogBuilder(act)
                .setTitle(R.string.menu_hide)
                .setMessage(R.string.hide_confirm)
                .setPositiveButton(R.string.btn_ok) { _, _ -> runHide(act, items, onDone) }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        })
    }

    private fun runHide(act: MediaActivity, items: List<Video>, onDone: () -> Unit) {
        val dlg = progress(act)
        val app = act.applicationContext
        Thread {
            val oldPaths = mutableListOf<String>()
            var ok = 0
            for (v in items) {
                try {
                    val path = realPath(app, v.uri) ?: continue
                    val src = File(path)
                    if (!src.exists()) continue
                    val name = src.name
                    val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
                    val fname = UUID.randomUUID().toString() + ext
                    val target = File(PrivacyStore.dir(app), fname)
                    val size = src.length()
                    val mime = mimeOf(app, v.uri, v.isAudio)
                    val dir = src.parent ?: ""
                    if (moveFile(src, target)) {
                        PrivacyStore.add(
                            app, PrivacyStore.Item(
                                fname, v.title, ext, mime, dir, v.isAudio,
                                v.duration, size, v.width, v.height
                            )
                        )
                        oldPaths += path
                        ok++
                    }
                } catch (e: Exception) { }
            }
            rescan(app, oldPaths) {
                Library.reload(app)
                act.runOnUiThread {
                    dlg.dismiss()
                    Toast.makeText(
                        act, if (ok > 0) R.string.hidden_done else R.string.action_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                    onDone()
                }
            }
        }.start()
    }

    // ---------- আনলক (আনহাইড): আগের ফোল্ডারে ফেরত মুভ ----------
    fun unhide(act: MediaActivity, videos: List<Video>, onDone: () -> Unit) {
        val list = videos.mapNotNull { PrivacyStore.find(act, it) }
        if (list.isEmpty()) return
        val dlg = progress(act)
        val app = act.applicationContext
        Thread {
            val newPaths = mutableListOf<String>()
            list.forEach { restore(app, it)?.let { p -> newPaths += p } }
            rescan(app, newPaths) {
                Library.reload(app)
                act.runOnUiThread {
                    dlg.dismiss()
                    Toast.makeText(
                        act, if (newPaths.isNotEmpty()) R.string.unhidden_done else R.string.action_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                    onDone()
                }
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun restore(ctx: Context, it: PrivacyStore.Item): String? {
        val src = File(PrivacyStore.dir(ctx), it.file)
        if (!src.exists()) {
            PrivacyStore.remove(ctx, it.file)
            return null
        }
        val root = Environment.getExternalStorageDirectory()
        // মূল ফোল্ডার: সংরক্ষিত পথ; না থাকলে Movies / Music
        var base = when {
            it.relPath.startsWith("/") -> File(it.relPath)
            it.relPath.isNotEmpty() -> File(root, it.relPath)
            else -> Environment.getExternalStoragePublicDirectory(
                if (it.audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
            )
        }
        if (!base.exists() && !base.mkdirs()) {
            base = Environment.getExternalStoragePublicDirectory(
                if (it.audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
            )
            base.mkdirs()
        }
        var target = File(base, it.title + it.ext)
        var n = 1
        while (target.exists()) target = File(base, it.title + " (" + n++ + ")" + it.ext)
        if (!moveFile(src, target)) return null
        PrivacyStore.remove(ctx, it.file)
        return target.absolutePath
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
        AppSettings.applyTheme(this)
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
                UiKit.borderedMenu(this@PrivacyActivity, v, listOf(1 to R.string.change_password)) {
                    PrivacyUi.change(this@PrivacyActivity)
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

        PrivacyOps.ensureAccess(this, onGranted = { askPassword() }, onDenied = { finish() })
    }

    private fun askPassword() {
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
        UiKit.borderedMenu(
            this, anchor,
            listOf(1 to R.string.menu_play, 2 to R.string.menu_unhide, 3 to R.string.menu_select, 4 to R.string.btn_delete)
        ) { id ->
            when (id) {
                1 -> play(v)
                2 -> PrivacyOps.unhide(this, listOf(v)) { render() }
                3 -> adapter.startSelection(v)
                4 -> PrivacyOps.deleteForever(this, listOf(v)) { render() }
            }
        }
    }
}
