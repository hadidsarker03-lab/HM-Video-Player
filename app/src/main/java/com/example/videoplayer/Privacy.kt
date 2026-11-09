package com.example.videoplayer

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import android.content.ContentValues
import android.animation.ObjectAnimator
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils
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
    private const val KEY_PW = "privacy_pw" // পুরনো সংস্করণের হ্যাশ (শুধু মাইগ্রেশনের জন্য)
    private const val KEY_SET = "vault_pw_set"
    private const val KEY_LEN = "vault_pin_len"
    private const val KEY_PENDING = "vault_enc_pending"
    private const val KEY_FAILS = "vault_fails"
    private const val KEY_UNTIL = "vault_lock_until"

    data class Item(
        val file: String, val title: String, val ext: String, val mime: String,
        val relPath: String, val audio: Boolean, val duration: Long, val size: Long,
        val width: Int, val height: Int
    )

    // লক ফোল্ডার: ফোনের স্টোরেজে অ্যাপের জন্য আলাদা ফোল্ডার (.nomedia সহ, গ্যালারিতে দেখা যায় না)।
    // অ্যাপ আনইনস্টল করলেও এখানকার ফাইল ও তালিকা (.index.json) থেকে যায়।
    const val FOLDER = ".HM Player Lock"
    private const val OLD_FOLDER = "HM Player Lock"
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

    // ফোল্ডার তৈরি না করে শুধু পথ
    @Suppress("DEPRECATION")
    fun rootPath(): String = File(Environment.getExternalStorageDirectory(), FOLDER).absolutePath

    fun isVaultPath(p: String) = p.startsWith(rootPath() + "/")
    fun isVaultUri(u: Uri) = u.scheme == "file" && u.path?.let { isVaultPath(it) } == true
    fun thumbFile(c: Context, file: String) = File(dir(c), file + "t")

    @Suppress("DEPRECATION")
    fun dir(c: Context): File {
        val root = Environment.getExternalStorageDirectory()
        val d = File(root, FOLDER)
        val old = File(root, OLD_FOLDER)
        // আগের দৃশ্যমান ফোল্ডার থাকলে লুকানো (ডট) ফোল্ডারে সরানো
        if (!d.exists() && old.exists()) try { old.renameTo(d) } catch (e: Exception) { Dbg.w("Privacy.dir", e) }
        d.mkdirs()
        val nm = File(d, ".nomedia")
        if (!nm.exists()) try { nm.createNewFile() } catch (e: Exception) { Dbg.w("Privacy.dir", e) }
        return d
    }

    private fun indexFile(c: Context) = File(dir(c), INDEX)

    private fun readIndex(c: Context): JSONObject? = try {
        val f = indexFile(c)
        if (f.exists()) JSONObject(f.readText()) else null
    } catch (e: Exception) { Dbg.w("Privacy.readIndex", e); null }

    private fun writeIndex(c: Context, list: List<Item>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("file", it.file).put("title", it.title).put("ext", it.ext)
                .put("mime", it.mime).put("rel", it.relPath).put("audio", it.audio)
                .put("dur", it.duration).put("size", it.size).put("w", it.width).put("h", it.height))
        }
        val o = JSONObject().put("items", arr)
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
        migrateLayout(c)
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
        } catch (e: Exception) { Dbg.w("Privacy.items", e) }
        cache = out
        cacheStamp = stamp
        return out
    }

    private fun save(c: Context, list: List<Item>) {
        try { writeIndex(c, list) } catch (e: Exception) { Dbg.w("Privacy.save", e) }
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
        } catch (e: Exception) { Dbg.w("Privacy.migrateOld", e) }
        AppSettings.prefs(c).edit().remove(KEY_ITEMS).apply()
        if (moved.isNotEmpty()) {
            cache = null
            val existing = readIndexItems(c)
            save(c, existing + moved)
        }
    }

    // পুরোনো ফাইলের নাম বদলে ডট-নাম (এক্সটেনশন ছাড়া) করা, যাতে কোনো ফাইল ম্যানেজারে ভিডিও/অডিও চেনা না যায়
    private fun migrateLayout(c: Context) {
        val p = AppSettings.prefs(c)
        if (p.getBoolean("lock_v2", false)) return
        val list = readIndexItems(c)
        if (list.isNotEmpty()) {
            val out = list.map { i ->
                if (i.file.startsWith(".")) i else {
                    val nf = "." + UUID.randomUUID().toString().replace("-", "")
                    if (File(dir(c), i.file).renameTo(File(dir(c), nf))) i.copy(file = nf) else i
                }
            }
            save(c, out)
        }
        p.edit().putBoolean("lock_v2", true).apply()
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
    // পুরনো সংস্করণের হ্যাশ (শুধু আগের ব্যবহারকারীর মাইগ্রেশনের জন্য)
    private fun legacyHash(salt: String, pw: String) = MessageDigest.getInstance("SHA-256")
        .digest((salt + pw).toByteArray()).joinToString("") { "%02x".format(it) }

    private const val KEY_NUM = "privacy_pw_numeric"
    private const val MAX_FAILS_FREE = 5
    private const val MAX_LOCK_MS = 15 * 60 * 1000L

    const val R_OK = 0
    const val R_WRONG = 1
    const val R_LOCKED = 2

    // নতুন পাসওয়ার্ড শুধু নম্বরের; আগের (অক্ষরের) পাসওয়ার্ড থাকলে সেটা দিতে সাধারণ কিবোর্ড
    fun isNumeric(c: Context) = AppSettings.prefs(c).getBoolean(KEY_NUM, false)

    // সংরক্ষিত PIN-এর দৈর্ঘ্য (০ = অজানা); জানা থাকলে শেষ সংখ্যা দিতেই নিজে থেকে যাচাই হয়
    fun pinLength(c: Context): Int = AppSettings.prefs(c).getInt(KEY_LEN, 0)

    // ফিঙ্গারপ্রিন্টে খুলতে হলে মাস্টার কী ফোনেই থাকতে হবে (নতুন ইনস্টলের পর একবার PIN লাগে)
    fun fingerReady(c: Context): Boolean = VaultKeys.master(c) != null

    fun hasPassword(c: Context): Boolean {
        val p = AppSettings.prefs(c)
        if (hasAccess(c)) {
            if (VaultKeys.hasKeyFile(c)) {
                p.edit().putBoolean(KEY_SET, true).putBoolean(KEY_NUM, VaultKeys.keyFileNumeric()).apply()
                return true
            }
            if (p.getString(KEY_PW, null) != null) return true
            // পুনরায় ইনস্টলে পুরনো সংস্করণের লক ফোল্ডারে রাখা হ্যাশ ফেরত আনা (এরপর PIN দিলে নতুন ব্যবস্থায় চলে যাবে)
            val o = readIndex(c)
            val saved = o?.optString("pw", "")
            if (!saved.isNullOrEmpty()) {
                p.edit().putString(KEY_PW, saved).putBoolean(KEY_NUM, o.optBoolean("numeric", false)).apply()
                return true
            }
            return false
        }
        return p.getBoolean(KEY_SET, false) || p.getString(KEY_PW, null) != null
    }

    // ---- ভুল চেষ্টায় অপেক্ষা (brute-force ঠেকাতে) ----
    fun lockRemainingMs(c: Context): Long {
        val until = AppSettings.prefs(c).getLong(KEY_UNTIL, 0L)
        val left = until - System.currentTimeMillis()
        return if (left <= 0) 0L else minOf(left, MAX_LOCK_MS)
    }

    private fun recordFail(c: Context): Long {
        val p = AppSettings.prefs(c)
        val n = p.getInt(KEY_FAILS, 0) + 1
        var delay = 0L
        if (n >= MAX_FAILS_FREE) {
            delay = minOf(30_000L shl minOf(n - MAX_FAILS_FREE, 5), MAX_LOCK_MS)
            p.edit().putInt(KEY_FAILS, n).putLong(KEY_UNTIL, System.currentTimeMillis() + delay).apply()
        } else p.edit().putInt(KEY_FAILS, n).apply()
        return delay
    }

    private fun clearFails(c: Context) {
        AppSettings.prefs(c).edit().remove(KEY_FAILS).remove(KEY_UNTIL).apply()
    }

    // PIN যাচাই ব্যাকগ্রাউন্ডে (ধীর KDF), ফলাফল মেইন থ্রেডে: (R_OK | R_WRONG | R_LOCKED, অপেক্ষার মিলিসেকেন্ড)
    // স্ক্রিন বন্ধ হয়ে গেলে ফলাফল আর পৌঁছায় না, কিন্তু ভুল-চেষ্টার হিসাব ঠিকই রেকর্ড হয়।
    fun verify(act: MediaActivity, pw: String, cb: (Int, Long) -> Unit) {
        val app = act.applicationContext
        val rem = lockRemainingMs(app)
        if (rem > 0) { cb(R_LOCKED, rem); return }
        act.lifecycleScope.launch {
            val r = withContext(Dispatchers.Default) {
                val ok = try { check(app, pw) } catch (e: Exception) { Dbg.w("vault check", e); false }
                var wait = 0L
                if (ok) clearFails(app) else wait = recordFail(app)
                Pair(ok, wait)
            }
            when {
                r.first -> cb(R_OK, 0L)
                r.second > 0 -> cb(R_LOCKED, r.second)
                else -> cb(R_WRONG, 0L)
            }
        }
    }

    // ধীর (ব্যাকগ্রাউন্ড থ্রেড থেকে ডাকুন)। সঠিক হলে মাস্টার কী মেমোরি ও Keystore-এ বসে।
    fun check(c: Context, pw: String): Boolean {
        val p = AppSettings.prefs(c)
        if (hasAccess(c) && VaultKeys.hasKeyFile(c)) {
            val m = VaultKeys.unwrap(pw) ?: return false
            VaultKeys.setSession(m)
            VaultKeys.storeKeystore(c, m)
            if (isNumeric(c)) p.edit().putInt(KEY_LEN, pw.length).apply()
            return true
        }
        return migrateLegacy(c, pw)
    }

    // পুরনো (SHA-256 হ্যাশ) ব্যবস্থা থেকে নতুনে: পাসওয়ার্ড ঠিক হলে মাস্টার কী তৈরি; ফাইল এনক্রিপশন পরে (encryptPending)
    private fun migrateLegacy(c: Context, pw: String): Boolean {
        val p = AppSettings.prefs(c)
        val s = p.getString(KEY_PW, null) ?: return false
        val parts = s.split(":")
        if (parts.size != 2) return false
        val calc = legacyHash(parts[0], pw)
        if (!MessageDigest.isEqual(calc.toByteArray(), parts[1].toByteArray())) return false
        val master = VaultCrypto.randomBytes(32)
        if (!VaultKeys.writeKeyFile(c, master, pw, isNumeric(c))) return true // লিখতে না পারলে আগের মতোই চলে
        VaultKeys.setSession(master)
        VaultKeys.storeKeystore(c, master)
        p.edit().remove(KEY_PW).putBoolean(KEY_SET, true).putBoolean(KEY_PENDING, true)
            .putInt(KEY_LEN, if (isNumeric(c)) pw.length else 0).apply()
        // লক ফোল্ডারের তালিকা থেকে পুরনো হ্যাশ মুছে ফেলা
        try { writeIndex(c, readIndexItems(c)) } catch (e: Exception) { Dbg.w("Privacy.migrateLegacy", e) }
        return true
    }

    // নতুন PIN সেট/বদল। বদলের সময় শুধু মাস্টার কী নতুন PIN-এ মোড়া হয়, ফাইল আবার এনক্রিপ্ট করতে হয় না।
    fun setPassword(c: Context, pw: String): Boolean {
        val existing = VaultKeys.master(c)
        if (existing == null && hasPassword(c)) return false // তালা বন্ধ: আগে খুলতে হবে
        val master = existing ?: VaultCrypto.randomBytes(32)
        if (!VaultKeys.writeKeyFile(c, master, pw, true)) return false
        VaultKeys.setSession(master)
        VaultKeys.storeKeystore(c, master)
        AppSettings.prefs(c).edit().remove(KEY_PW).putBoolean(KEY_NUM, true).putBoolean(KEY_SET, true)
            .putInt(KEY_LEN, pw.length).apply()
        clearFails(c)
        return true
    }

    fun setPasswordAsync(act: MediaActivity, pw: String, cb: (Boolean) -> Unit) {
        val app = act.applicationContext
        act.lifecycleScope.launch {
            val ok = withContext(Dispatchers.Default) {
                try { setPassword(app, pw) } catch (e: Exception) { Dbg.w("vault setPassword", e); false }
            }
            cb(ok)
        }
    }

    // পুরনো ফাইল এখনও এনক্রিপ্ট হয়নি?
    fun pendingEncrypt(c: Context) = AppSettings.prefs(c).getBoolean(KEY_PENDING, false)
    fun setPendingEncrypt(c: Context, v: Boolean) {
        AppSettings.prefs(c).edit().putBoolean(KEY_PENDING, v).apply()
    }
}

// পাসওয়ার্ড ডায়ালগ: শুধু নম্বর + ফিঙ্গারপ্রিন্ট
object PrivacyUi {
    // নতুন PIN কমপক্ষে এত সংখ্যার (পুরনো ৪ সংখ্যার PIN দিয়ে খোলা যায়, কিন্তু নতুন করে ৬+ লাগবে)
    const val NEW_PIN_MIN = 6

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
    } catch (e: Exception) { Dbg.w("Privacy.canFinger", e); false }

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

    fun lockedText(act: MediaActivity, ms: Long): String =
        act.getString(R.string.password_locked, (ms / 1000 + 1).toInt())

    fun enter(act: MediaActivity, onOk: () -> Unit, onCancel: () -> Unit) {
        // আগের (অক্ষরের) পাসওয়ার্ড থাকলে সাধারণ কিবোর্ডের ডায়ালগ
        if (!PrivacyStore.isNumeric(act)) { enterLegacy(act, onOk, onCancel); return }
        val can = canFinger(act) && PrivacyStore.fingerReady(act)
        lateinit var sheet: PinSheet
        sheet = PinSheet(
            act, R.string.privacy, R.string.enter_password, can,
            { finger(act, { sheet.dismiss(); onOk() }, { }) }, onCancel,
            onSubmit = { pw, sh ->
                sh.setBusy(true)
                PrivacyStore.verify(act, pw) { r, ms ->
                    sh.setBusy(false)
                    when (r) {
                        PrivacyStore.R_OK -> { sh.dismiss(); onOk() }
                        PrivacyStore.R_LOCKED -> sh.error(lockedText(act, ms))
                        else -> sh.error(R.string.password_wrong)
                    }
                }
            },
            autoLen = PrivacyStore.pinLength(act)
        )
        sheet.show()
        if (can) finger(act, { sheet.dismiss(); onOk() }, { })
    }

    private fun enterLegacy(act: MediaActivity, onOk: () -> Unit, onCancel: () -> Unit) {
        val pw = field(act, R.string.enter_password, PrivacyStore.isNumeric(act))
        val dlg = MaterialAlertDialogBuilder(act)
            .setTitle(R.string.privacy)
            .setView(box(act, pw))
            .setCancelable(false)
            .setPositiveButton(R.string.btn_ok, null)
            .setNegativeButton(R.string.btn_cancel) { _, _ -> onCancel() }
            .apply { if (canFinger(act) && PrivacyStore.fingerReady(act)) setNeutralButton(R.string.use_fingerprint, null) }
            .create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                PrivacyStore.verify(act, pw.text.toString()) { r, ms ->
                    when (r) {
                        PrivacyStore.R_OK -> { dlg.dismiss(); onOk() }
                        PrivacyStore.R_LOCKED -> pw.error = lockedText(act, ms)
                        else -> pw.error = act.getString(R.string.password_wrong)
                    }
                }
            }
            val fingerOk = canFinger(act) && PrivacyStore.fingerReady(act)
            val doFinger = {
                finger(act, { dlg.dismiss(); onOk() }, { })
            }
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener { if (fingerOk) doFinger() }
            if (fingerOk) doFinger() // খুলতেই ফিঙ্গারপ্রিন্ট চায়
        }
        dlg.show()
    }

    // নতুন PIN তৈরি: দু'ধাপে (তৈরি, তারপর নিশ্চিত)
    fun setPassword(act: MediaActivity, onSet: () -> Unit, onCancel: () -> Unit) {
        var first: String? = null
        val sheet = PinSheet(
            act, R.string.set_password, R.string.password_warn, false, null, onCancel,
            onSubmit = { pw, sh ->
                val f = first
                when {
                    f == null -> {
                        first = pw
                        sh.clear()
                        sh.setTitles(R.string.confirm_password, 0)
                    }
                    f != pw -> {
                        first = null
                        sh.setTitles(R.string.set_password, R.string.password_warn)
                        sh.error(R.string.password_mismatch)
                    }
                    else -> {
                        sh.setBusy(true)
                        PrivacyStore.setPasswordAsync(act, pw) { ok ->
                            sh.setBusy(false)
                            sh.dismiss()
                            if (ok) onSet() else {
                                Toast.makeText(act, R.string.action_failed, Toast.LENGTH_SHORT).show()
                                onCancel()
                            }
                        }
                    }
                }
            },
            minLen = NEW_PIN_MIN
        )
        sheet.show()
    }

    fun change(act: MediaActivity) {
        enter(act, onOk = {
            setPassword(act, onSet = {
                Toast.makeText(act, R.string.password_changed, Toast.LENGTH_SHORT).show()
            }, onCancel = {})
        }, onCancel = {})
    }
}

// প্রফেশনাল PIN শিট: লক আইকন, ডট ইন্ডিকেটর, নিজস্ব নম্বর কীপ্যাড, ফিঙ্গারপ্রিন্ট কী, ভুল হলে ঝাঁকুনি
private class PinSheet(
    private val act: MediaActivity,
    title: Int,
    subtitle: Int,
    private val fingerKey: Boolean,
    private val onFinger: (() -> Unit)?,
    private val onCancel: () -> Unit,
    private val onSubmit: (String, PinSheet) -> Unit,
    private val minLen: Int = 4,
    private val autoLen: Int = 0
) {
    private var busy = false

    fun setBusy(b: Boolean) { busy = b }

    private val dlg = android.app.Dialog(act)
    private val sb = StringBuilder()
    private val titleTv = TextView(act)
    private val subTv = TextView(act)
    private val errTv = TextView(act)
    private val dots = LinearLayout(act)
    private val acc = UiKit.accent(act)
    private val textC = UiKit.textColor(act)
    private val dens = act.resources.displayMetrics.density
    private fun d(v: Int) = UiKit.dp(act, v)

    init {
        setTitles(title, subtitle)
    }

    fun setTitles(t: Int, sub: Int) {
        titleTv.setText(t)
        if (sub != 0) { subTv.setText(sub); subTv.visibility = View.VISIBLE } else subTv.visibility = View.GONE
    }

    fun clear() {
        sb.clear()
        errTv.visibility = View.INVISIBLE
        refreshDots()
    }

    fun error(msg: Int) = error(act.getString(msg))

    fun error(msg: String) {
        errTv.text = msg
        errTv.visibility = View.VISIBLE
        sb.clear()
        refreshDots()
        dots.performHapticFeedback(HapticFeedbackConstants.REJECT.takeIf { android.os.Build.VERSION.SDK_INT >= 30 }
            ?: HapticFeedbackConstants.LONG_PRESS)
        ObjectAnimator.ofFloat(
            dots, "translationX", 0f, -18f * dens, 18f * dens, -12f * dens, 12f * dens, -6f * dens, 6f * dens, 0f
        ).setDuration(380).start()
    }

    fun dismiss() { try { dlg.dismiss() } catch (e: Exception) { } }

    private fun refreshDots() {
        dots.removeAllViews()
        val n = maxOf(if (autoLen > 0) autoLen else minLen, sb.length)
        for (i in 0 until n) {
            val dot = View(act)
            val filled = i < sb.length
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (filled) setColor(acc) else { setColor(0); setStroke(d(2), 0x66888888) }
            }
            dots.addView(dot, LinearLayout.LayoutParams(d(14), d(14)).apply { setMargins(d(7), 0, d(7), 0) })
        }
    }

    private fun addDigit(k: String) {
        if (busy || sb.length >= 12) return
        sb.append(k)
        errTv.visibility = View.INVISIBLE
        refreshDots()
        // PIN-এর দৈর্ঘ্য জানা থাকলে শেষ সংখ্যা দিতেই একবার যাচাই (প্রতি সংখ্যায় নয়)
        if (autoLen > 0 && sb.length == autoLen) onSubmit(sb.toString(), this)
    }

    private fun backspace() {
        if (busy) return
        if (sb.isNotEmpty()) { sb.deleteCharAt(sb.length - 1); refreshDots() }
    }

    private fun key(k: String): View {
        val cell = FrameLayout(act)
        when (k) {
            "F" -> if (fingerKey && onFinger != null) {
                val iv = ImageView(act).apply { setImageResource(R.drawable.ic_fingerprint); setColorFilter(acc) }
                cell.addView(iv, FrameLayout.LayoutParams(d(30), d(30), Gravity.CENTER))
                cell.setOnClickListener { onFinger.invoke() }
                Anim.pressable(cell, 0.9f)
            }
            "B" -> {
                val iv = ImageView(act).apply { setImageResource(R.drawable.ic_backspace); setColorFilter(textC) }
                cell.addView(iv, FrameLayout.LayoutParams(d(26), d(26), Gravity.CENTER))
                cell.setOnClickListener { backspace() }
                cell.setOnLongClickListener { sb.clear(); refreshDots(); true }
                Anim.pressable(cell, 0.9f)
            }
            else -> {
                cell.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ContextCompat.getColor(act, R.color.surface_alt))
                }
                val tv = TextView(act).apply {
                    text = k
                    textSize = 24f
                    setTextColor(textC)
                    gravity = Gravity.CENTER
                }
                cell.addView(tv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                cell.setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    addDigit(k)
                }
                Anim.pressable(cell, 0.9f)
            }
        }
        return cell
    }

    fun show() {
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(d(22), d(26), d(22), d(14))
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(act, R.color.dialog_bg))
                cornerRadius = d(28).toFloat()
            }
        }
        val badge = FrameLayout(act).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ColorUtils.setAlphaComponent(acc, 0x2A))
            }
        }
        badge.addView(
            ImageView(act).apply { setImageResource(R.drawable.ic_lock); setColorFilter(acc) },
            FrameLayout.LayoutParams(d(30), d(30), Gravity.CENTER)
        )
        root.addView(badge, LinearLayout.LayoutParams(d(68), d(68)))

        titleTv.apply {
            textSize = 20f
            setTextColor(textC)
            typeface = UiKit.mediumFont()
            gravity = Gravity.CENTER
        }
        root.addView(titleTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = d(14) })
        subTv.apply {
            textSize = 12.5f
            setTextColor(0xFF8A8F9A.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(subTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = d(6) })

        dots.orientation = LinearLayout.HORIZONTAL
        dots.gravity = Gravity.CENTER
        root.addView(dots, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = d(20) })
        errTv.apply {
            textSize = 12.5f
            setTextColor(0xFFE5484D.toInt())
            gravity = Gravity.CENTER
            visibility = View.INVISIBLE
            text = " "
        }
        root.addView(errTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = d(8) })

        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "F", "0", "B")
        for (r in 0 until 4) {
            val row = LinearLayout(act)
            row.orientation = LinearLayout.HORIZONTAL
            for (c in 0 until 3) {
                row.addView(key(keys[r * 3 + c]), LinearLayout.LayoutParams(0, d(60), 1f).apply {
                    setMargins(d(10), d(4), d(10), d(4))
                })
            }
            root.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val ok = TextView(act).apply {
            setText(R.string.btn_ok)
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = UiKit.mediumFont()
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(acc); cornerRadius = d(16).toFloat() }
            setOnClickListener {
                if (busy) return@setOnClickListener
                if (sb.length < minLen) this@PinSheet.error(act.getString(R.string.password_short_n, minLen))
                else onSubmit(sb.toString(), this@PinSheet)
            }
        }
        Anim.pressable(ok, 0.97f)
        root.addView(ok, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, d(50)).apply { topMargin = d(12) })

        val cancel = TextView(act).apply {
            setText(R.string.btn_cancel)
            textSize = 14f
            setTextColor(0xFF8A8F9A.toInt())
            gravity = Gravity.CENTER
            setPadding(0, d(10), 0, d(6))
            setOnClickListener { dismiss(); onCancel() }
        }
        root.addView(cancel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        refreshDots()
        dlg.setContentView(root)
        dlg.setCancelable(false)
        dlg.window?.apply {
            setBackgroundDrawable(ColorDrawable(0))
            addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            setLayout(minOf((act.resources.displayMetrics.widthPixels * 0.92f).toInt(), d(380)),
                ViewGroup.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.6f)
        }
        dlg.show()
        root.alpha = 0f
        root.translationY = 24f * dens
        root.animate().alpha(1f).translationY(0f).setDuration(280)
            .setInterpolator(android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f)).start()
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
        } catch (e: Exception) { Dbg.w("Privacy.realPath", e) }
        return null
    }

    private fun mimeOf(ctx: Context, uri: Uri, audio: Boolean): String {
        val m = try { ctx.contentResolver.getType(uri) } catch (e: Exception) { Dbg.w("Privacy.mimeOf", e); null }
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

    // এনক্রিপ্ট করে লক ফোল্ডারে রাখা; যাচাই ঠিক হলেই আসল ফাইল মোছা (ব্যর্থ হলে আসল অক্ষত থাকে)
    private fun encryptMove(master: ByteArray, src: File, dst: File): Boolean {
        dst.parentFile?.mkdirs()
        val tmp = File(dst.parentFile, dst.name + ".tmp")
        return try {
            VaultCrypto.encryptFile(master, src, tmp)
            if (VaultCrypto.plainSize(master, tmp) != src.length()) throw java.io.IOException("size mismatch")
            if (!tmp.renameTo(dst)) throw java.io.IOException("rename failed")
            if (!src.delete()) { dst.delete(); return false }
            true
        } catch (e: Exception) {
            tmp.delete()
            dst.delete()
            false
        }
    }

    // লক ফোল্ডার থেকে ডিক্রিপ্ট করে আগের জায়গায় ফেরত (পুরনো, এখনও এনক্রিপ্ট না হওয়া ফাইল হলে সরাসরি মুভ)
    private fun restoreFile(ctx: Context, src: File, target: File): Boolean {
        if (!VaultCrypto.isEncrypted(src)) return moveFile(src, target)
        val master = VaultKeys.master(ctx) ?: return false
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "." + target.name + ".hmtmp")
        return try {
            VaultCrypto.decryptToFile(master, src, tmp)
            if (!tmp.renameTo(target)) throw java.io.IOException("rename failed")
            src.delete()
            true
        } catch (e: Exception) {
            tmp.delete()
            false
        }
    }

    // পুরনো সংস্করণে লক করা (এনক্রিপ্ট না হওয়া) ফাইলগুলো এনক্রিপ্ট করা; PIN দিয়ে খোলার পর একবার চলে
    fun encryptPending(act: MediaActivity, then: () -> Unit) {
        val app = act.applicationContext
        val master = VaultKeys.master(app)
        if (!PrivacyStore.pendingEncrypt(app) || master == null) { then(); return }
        val dlg = progress(act, R.string.privacy_encrypting)
        act.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    var allDone = true
                    val dir = PrivacyStore.dir(app)
                    dir.listFiles()?.forEach { if (it.name.endsWith(".tmp") && it.name != ".index.json.tmp") it.delete() }
                    for (item in PrivacyStore.items(app)) {
                        val f = File(dir, item.file)
                        if (!f.exists() || VaultCrypto.isEncrypted(f)) continue
                        try {
                            val tf = PrivacyStore.thumbFile(app, item.file)
                            if (!item.audio && !tf.exists()) {
                                VaultThumbs.make(f.absolutePath)?.let { b -> VaultCrypto.encryptBytes(master, b, tf) }
                            }
                            val tmp = File(dir, item.file + ".tmp")
                            VaultCrypto.encryptFile(master, f, tmp)
                            if (VaultCrypto.plainSize(master, tmp) != f.length()) throw java.io.IOException("size mismatch")
                            if (!tmp.renameTo(f)) throw java.io.IOException("rename failed")
                        } catch (e: Exception) {
                            Dbg.w("vault encryptPending", e)
                            allDone = false
                            File(dir, item.file + ".tmp").delete()
                        }
                    }
                    if (allDone) PrivacyStore.setPendingEncrypt(app, false)
                }
            } finally {
                try { dlg.dismiss() } catch (e: Exception) { }
            }
            then()
        }
    }

    private suspend fun rescanAwait(ctx: Context, paths: List<String>) =
        suspendCancellableCoroutine<Unit> { cont ->
            rescan(ctx, paths) { if (cont.isActive) cont.resume(Unit) }
        }

    // পুরোনো জায়গার মিডিয়া এন্ট্রি সরাতে / নতুন জায়গা যোগ করতে স্ক্যান
    private fun rescan(ctx: Context, paths: List<String>, then: () -> Unit) {
        if (paths.isEmpty()) { then(); return }
        val left = java.util.concurrent.atomic.AtomicInteger(paths.size)
        MediaScannerConnection.scanFile(ctx, paths.toTypedArray(), null) { _, _ ->
            if (left.decrementAndGet() == 0) then()
        }
    }

    private fun progress(act: MediaActivity, msg: Int = R.string.privacy_working): AlertDialog =
        MaterialAlertDialogBuilder(act)
            .setMessage(msg)
            .setCancelable(false)
            .show()

    // ---------- লক (হাইড) ----------
    fun hide(act: MediaActivity, items: List<Video>, onDone: () -> Unit) {
        if (items.isEmpty()) return
        ensureAccess(act, onGranted = {
            if (!PrivacyStore.hasPassword(act)) {
                PrivacyUi.setPassword(act, onSet = { hide(act, items, onDone) }, onCancel = {})
                return@ensureAccess
            }
            // মাস্টার কী ফোনে না থাকলে (নতুন ইনস্টল ইত্যাদি) একবার PIN দিয়ে খুলতে হবে
            if (VaultKeys.master(act) == null) {
                PrivacyUi.enter(act, onOk = { hide(act, items, onDone) }, onCancel = {})
                return@ensureAccess
            }
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
        act.lifecycleScope.launch {
            val oldPaths = mutableListOf<String>()
            var ok = 0
            try {
                // ফাইল সরানোর কাজ থেমে না গিয়ে শেষ পর্যন্ত চলে (স্ক্রিন বন্ধ হলেও আধা-মুভ হয় না)
                withContext(Dispatchers.IO) {
                    val master = VaultKeys.master(app)
                    for (v in items) {
                        if (master == null) break
                        try {
                            val path = realPath(app, v.uri) ?: continue
                            val src = File(path)
                            if (!src.exists()) continue
                            val name = src.name
                            val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
                            // ডট-নাম, এক্সটেনশন নেই: ফাইল ম্যানেজারে ভিডিও/অডিও হিসেবে চেনা যায় না
                            val fname = "." + UUID.randomUUID().toString().replace("-", "")
                            val target = File(PrivacyStore.dir(app), fname)
                            val size = src.length()
                            val mime = mimeOf(app, v.uri, v.isAudio)
                            val dir = src.parent ?: ""
                            // এনক্রিপ্ট করার আগে থাম্বনেইল তৈরি (পরে ফাইল পড়া যাবে না)
                            val thumb = if (v.isAudio) null else VaultThumbs.make(path)
                            if (encryptMove(master, src, target)) {
                                if (thumb != null) try {
                                    VaultCrypto.encryptBytes(master, thumb, PrivacyStore.thumbFile(app, fname))
                                } catch (e: Exception) { Dbg.w("vault thumb", e) }
                                PrivacyStore.add(
                                    app, PrivacyStore.Item(
                                        fname, v.title, ext, mime, dir, v.isAudio,
                                        v.duration, size, v.width, v.height
                                    )
                                )
                                oldPaths += path
                                ok++
                            }
                        } catch (e: Exception) { Dbg.w("vault hide", e) }
                    }
                }
                rescanAwait(app, oldPaths)
                withContext(Dispatchers.IO) { Library.reload(app) }
                Toast.makeText(
                    act, if (ok > 0) R.string.hidden_done else R.string.action_failed,
                    Toast.LENGTH_SHORT
                ).show()
                onDone()
            } finally {
                try { dlg.dismiss() } catch (e: Exception) { }
            }
        }
    }

    // ---------- আনলক (আনহাইড): আগের ফোল্ডারে ফেরত মুভ ----------
    fun unhide(act: MediaActivity, videos: List<Video>, onDone: () -> Unit) {
        val list = videos.mapNotNull { PrivacyStore.find(act, it) }
        if (list.isEmpty()) return
        val dlg = progress(act)
        val app = act.applicationContext
        act.lifecycleScope.launch {
            try {
                val newPaths = withContext(Dispatchers.IO) {
                    val out = mutableListOf<String>()
                    list.forEach { restore(app, it)?.let { p -> out += p } }
                    out
                }
                rescanAwait(app, newPaths)
                withContext(Dispatchers.IO) { Library.reload(app) }
                Toast.makeText(
                    act, if (newPaths.isNotEmpty()) R.string.unhidden_done else R.string.action_failed,
                    Toast.LENGTH_SHORT
                ).show()
                onDone()
            } finally {
                try { dlg.dismiss() } catch (e: Exception) { }
            }
        }
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
        if (!restoreFile(ctx, src, target)) return null
        PrivacyStore.thumbFile(ctx, it.file).delete()
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
                    PrivacyStore.thumbFile(act, it.file).delete()
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
        PrivacyOps.encryptPending(this) {
            unlocked = true
            render()
        }
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
