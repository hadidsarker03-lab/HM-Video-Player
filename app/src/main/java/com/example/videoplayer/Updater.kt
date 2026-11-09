package com.example.videoplayer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

// অ্যাপ-ভেতরের আপডেট: নিজের GitHub রিপোর সর্বশেষ Release দেখে, SHA-256 মিলিয়ে APK ইনস্টল করায়
object Updater {
    private const val KEY_LAST = "upd_last_check"
    private const val SIX_HOURS = 6 * 60 * 60 * 1000L

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private class Info(val code: Int, val name: String, val url: String, val sha: String, val notes: String)

    fun check(act: AppCompatActivity, manual: Boolean) {
        val prefs = AppSettings.prefs(act)
        if (!manual && System.currentTimeMillis() - prefs.getLong(KEY_LAST, 0L) < SIX_HOURS) return
        if (manual) Toast.makeText(act, R.string.upd_checking, Toast.LENGTH_SHORT).show()
        Thread {
            val info = try { fetch() } catch (e: Exception) { Dbg.w("Updater.check", e); null }
            act.runOnUiThread {
                if (act.isFinishing || act.isDestroyed) return@runOnUiThread
                if (info != null) prefs.edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
                when {
                    info == null -> if (manual) Toast.makeText(act, R.string.upd_failed, Toast.LENGTH_LONG).show()
                    info.code > BuildConfig.VERSION_CODE -> showOffer(act, info)
                    else -> if (manual) Toast.makeText(act, R.string.upd_latest, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun fetch(): Info? {
        val repo = BuildConfig.UPDATE_REPO
        val req = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "HM-Player")
            .build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return null
            val j = JSONObject(r.body?.string() ?: return null)
            val tag = j.optString("tag_name")
            val code = Regex("(\\d+)\\D*$").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            val body = j.optString("body")
            val sha = Regex("SHA256:\\s*([0-9a-fA-F]{64})").find(body)?.groupValues?.get(1)?.lowercase() ?: return null
            val assets = j.optJSONArray("assets") ?: return null
            var url: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) { url = a.optString("browser_download_url"); break }
            }
            // শুধু নিজের রিপোর Release থেকেই ডাউনলোড
            if (url == null || !url.startsWith("https://github.com/$repo/releases/download/")) return null
            val notes = body.lines().filterNot { it.startsWith("SHA256:") }.joinToString("\n").trim().take(400)
            return Info(code, tag.removePrefix("v"), url, sha, notes)
        }
    }

    private fun showOffer(act: AppCompatActivity, info: Info) {
        val msg = act.getString(R.string.upd_msg, info.name) + if (info.notes.isNotEmpty()) "\n\n${info.notes}" else ""
        MaterialAlertDialogBuilder(act)
            .setTitle(R.string.upd_title)
            .setMessage(msg)
            .setPositiveButton(R.string.upd_btn) { _, _ -> download(act, info) }
            .setNegativeButton(R.string.upd_later, null)
            .show()
    }

    private fun download(act: AppCompatActivity, info: Info) {
        val bar = ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        val pad = (20 * act.resources.displayMetrics.density).toInt()
        val box = LinearLayout(act).apply {
            setPadding(pad, pad, pad, 0)
            addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val cancelled = AtomicBoolean(false)
        val dlg = MaterialAlertDialogBuilder(act)
            .setTitle(R.string.upd_downloading)
            .setView(box)
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { _, _ -> cancelled.set(true) }
            .show()
        Thread {
            var file: File? = null
            var ok = false
            var hashBad = false
            try {
                val dir = File(act.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val out = File(dir, "HM-player.apk")
                file = out
                val req = Request.Builder().url(info.url).header("User-Agent", "HM-Player").build()
                client.newCall(req).execute().use { r ->
                    val body = r.body
                    if (!r.isSuccessful || body == null) return@use
                    val total = body.contentLength()
                    val md = MessageDigest.getInstance("SHA-256")
                    var done = 0L
                    var lastPct = -1
                    body.byteStream().use { input ->
                        out.outputStream().use { os ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                if (cancelled.get()) return@use
                                val n = input.read(buf)
                                if (n < 0) break
                                os.write(buf, 0, n)
                                md.update(buf, 0, n)
                                done += n
                                if (total > 0) {
                                    val pct = (done * 100 / total).toInt()
                                    if (pct != lastPct) { lastPct = pct; act.runOnUiThread { bar.progress = pct } }
                                }
                            }
                        }
                    }
                    val got = md.digest().joinToString("") { "%02x".format(it) }
                    if (got == info.sha) ok = true else hashBad = true
                }
            } catch (e: Exception) {
                Dbg.w("Updater.download", e)
            }
            val f = file
            act.runOnUiThread {
                try { dlg.dismiss() } catch (e: Exception) { Dbg.w("Updater.dismiss", e) }
                if (act.isFinishing || act.isDestroyed || cancelled.get()) { f?.delete(); return@runOnUiThread }
                when {
                    ok && f != null -> install(act, f)
                    else -> {
                        f?.delete()
                        Toast.makeText(act, if (hashBad) R.string.upd_hash_bad else R.string.upd_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }.start()
    }

    private fun install(act: AppCompatActivity, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !act.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(act, R.string.upd_perm, Toast.LENGTH_LONG).show()
            act.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${act.packageName}"))
            )
            return
        }
        try {
            val uri = FileProvider.getUriForFile(act, "${act.packageName}.updates", file)
            act.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Dbg.w("Updater.install", e)
            Toast.makeText(act, R.string.upd_failed, Toast.LENGTH_LONG).show()
        }
    }
}
