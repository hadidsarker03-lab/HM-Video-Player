package com.example.videoplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.webkit.URLUtil
import androidx.core.app.NotificationCompat
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

// ব্রাউজারের ডাউনলোড: OkHttp + Cloudflare DNS-over-HTTPS; প্রগ্রেস নোটিফিকেশনসহ, ব্যাকগ্রাউন্ডেও চলে
class DownloadService : Service() {

    private val pool = Executors.newFixedThreadPool(2)
    private val cancels = ConcurrentHashMap<Int, AtomicBoolean>()
    private val active = AtomicInteger(0)
    private lateinit var nm: NotificationManager

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CH, getString(R.string.dl_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun base(): Notification =
        NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.dl_running))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(FG_ID, base())
        if (intent?.action == ACTION_CANCEL) {
            cancels[intent.getIntExtra(EXTRA_ID, -1)]?.set(true)
            return START_NOT_STICKY
        }
        val url = intent?.getStringExtra(EXTRA_URL)
        if (url == null) {
            if (active.get() == 0) stopSelf()
            return START_NOT_STICKY
        }
        val id = NEXT.incrementAndGet()
        val flag = AtomicBoolean(false)
        cancels[id] = flag
        active.incrementAndGet()
        val ua = intent.getStringExtra(EXTRA_UA)
        val cookie = intent.getStringExtra(EXTRA_COOKIE)
        val referer = intent.getStringExtra(EXTRA_REFERER)
        val cd = intent.getStringExtra(EXTRA_CD)
        val mime = intent.getStringExtra(EXTRA_MIME)
        pool.execute {
            try {
                download(id, flag, url, ua, cookie, referer, cd, mime)
            } finally {
                cancels.remove(id)
                if (active.decrementAndGet() == 0) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun progressNote(id: Int, name: String, pct: Int, indeterminate: Boolean): Notification {
        val cancel = PendingIntent.getService(
            this, id, Intent(this, DownloadService::class.java)
                .setAction(ACTION_CANCEL).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(name)
            .setContentText(if (indeterminate) getString(R.string.dl_running) else "$pct%")
            .setProgress(100, pct, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.dl_cancel), cancel)
            .build()
    }

    private fun finalNote(id: Int, name: String, ok: Boolean, open: Uri?) {
        val b = NotificationCompat.Builder(this, CH)
            .setSmallIcon(if (ok) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(name)
            .setContentText(getString(if (ok) R.string.dl_done else R.string.dl_failed))
            .setAutoCancel(true)
        if (ok && open != null) {
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(open, contentType(name))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            b.setContentIntent(
                PendingIntent.getActivity(this, id, view, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
        }
        try { nm.notify(id, b.build()) } catch (e: Exception) { }
    }

    private fun contentType(name: String): String =
        android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "*/*"

    private fun isAudio(mime: String, name: String) =
        mime.startsWith("audio") || listOf(".mp3", ".m4a", ".aac", ".flac", ".wav", ".ogg").any { name.lowercase().endsWith(it) }

    private fun isVideo(mime: String, name: String) =
        mime.startsWith("video") || listOf(".mp4", ".mkv", ".webm", ".3gp", ".mov", ".avi", ".m3u8").any { name.lowercase().endsWith(it) }

    private fun download(
        id: Int, cancel: AtomicBoolean, url: String, ua: String?, cookie: String?,
        referer: String?, cd: String?, mimeIn: String?
    ) {
        var name = URLUtil.guessFileName(url, cd, mimeIn)
        var outUri: Uri? = null
        var outFile: File? = null
        try {
            val rb = Request.Builder().url(url)
            if (!ua.isNullOrEmpty()) rb.header("User-Agent", ua)
            if (!cookie.isNullOrEmpty()) rb.header("Cookie", cookie)
            if (!referer.isNullOrEmpty()) rb.header("Referer", referer)
            nm.notify(id, progressNote(id, name, 0, true))
            CfHttp.client.newCall(rb.build()).execute().use { resp ->
                if (!resp.isSuccessful) throw java.io.IOException("HTTP " + resp.code)
                val body = resp.body ?: throw java.io.IOException("empty")
                name = URLUtil.guessFileName(url, resp.header("Content-Disposition") ?: cd,
                    resp.header("Content-Type") ?: mimeIn)
                val mime = (resp.header("Content-Type") ?: mimeIn ?: contentType(name)).substringBefore(';')
                val total = body.contentLength()

                val os = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val (coll, rel) = when {
                        isVideo(mime, name) -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "Movies/"
                        isAudio(mime, name) -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "Music/"
                        else -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "Download/"
                    }
                    val cv = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, mime)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    outUri = contentResolver.insert(coll, cv) ?: throw java.io.IOException("insert")
                    contentResolver.openOutputStream(outUri!!)!!
                } else {
                    @Suppress("DEPRECATION")
                    val dir = Environment.getExternalStoragePublicDirectory(
                        if (isVideo(mime, name)) Environment.DIRECTORY_MOVIES
                        else if (isAudio(mime, name)) Environment.DIRECTORY_MUSIC
                        else Environment.DIRECTORY_DOWNLOADS
                    )
                    dir.mkdirs()
                    var f = File(dir, name)
                    var n = 1
                    while (f.exists()) f = File(dir, name.substringBeforeLast('.') + " (" + n++ + ")." + name.substringAfterLast('.', "bin"))
                    outFile = f
                    f.outputStream()
                }
                var done = 0L
                var last = 0L
                os.use { o ->
                    body.byteStream().use { i ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            if (cancel.get()) throw java.io.IOException("cancelled")
                            val n = i.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n)
                            done += n
                            val now = System.currentTimeMillis()
                            if (now - last > 600) {
                                last = now
                                val pct = if (total > 0) (done * 100 / total).toInt() else 0
                                nm.notify(id, progressNote(id, name, pct, total <= 0))
                            }
                        }
                    }
                }
            }
            if (outUri != null) {
                val cv = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                contentResolver.update(outUri!!, cv, null, null)
                finalNote(id, name, true, outUri)
            } else {
                MediaScannerConnection.scanFile(this, arrayOf(outFile!!.absolutePath), null, null)
                finalNote(id, name, true, null)
            }
            try { Library.reload(applicationContext) } catch (e: Exception) { }
        } catch (e: Exception) {
            try { outUri?.let { contentResolver.delete(it, null, null) } } catch (x: Exception) { }
            outFile?.delete()
            if (cancel.get()) nm.cancel(id) else finalNote(id, name, false, null)
        }
    }

    override fun onDestroy() {
        pool.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val CH = "downloads"
        private const val FG_ID = 9001
        private val NEXT = AtomicInteger(9100)
        const val ACTION_CANCEL = "com.example.videoplayer.DL_CANCEL"
        const val EXTRA_ID = "id"
        const val EXTRA_URL = "url"
        const val EXTRA_UA = "ua"
        const val EXTRA_COOKIE = "cookie"
        const val EXTRA_REFERER = "referer"
        const val EXTRA_CD = "cd"
        const val EXTRA_MIME = "mime"
    }
}
