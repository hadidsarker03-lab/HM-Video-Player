package com.example.videoplayer

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.MaterialColors
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder

// লাইভ টিভি: সাইট থেকে সার্ভার/চ্যানেলের লিংক লোড করে। ফোল্ডার বা প্লেলিস্টে ঢোকা যায়, স্ট্রিম চাপলে প্লেয়ারে চলে।
class LiveTvActivity : AppCompatActivity() {

    private class Entry(val title: String, val url: String, val kind: Int, val logo: String?)

    private val main = Handler(Looper.getMainLooper())
    private var all: List<Entry> = emptyList()
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var retry: TextView
    private lateinit var search: EditText
    private lateinit var url: String
    private var token = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applyTheme(this)
        super.onCreate(savedInstanceState)
        url = intent.getStringExtra(EXTRA_URL) ?: HOME_URL
        build(intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.tab_live_tv))
        load()
    }

    override fun onDestroy() {
        token++
        super.onDestroy()
    }

    private fun dp(v: Int) = UiKit.dp(this, v)

    private fun build(titleText: String) {
        val fg = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.WHITE)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.fitsSystemWindows = true

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(dp(8), dp(8), dp(16), dp(4))
        val back = ImageView(this)
        back.setImageResource(R.drawable.ic_back)
        back.setColorFilter(fg)
        back.setPadding(dp(12), dp(12), dp(12), dp(12))
        back.setOnClickListener { finish() }
        top.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        val title = TextView(this)
        title.text = titleText
        title.setTextColor(fg)
        title.textSize = 21f
        title.maxLines = 1
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
        top.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(top)

        search = EditText(this)
        search.setHint(R.string.tv_search)
        search.setTextColor(fg)
        search.setHintTextColor((fg and 0x00FFFFFF) or 0x66000000)
        search.setSingleLine(true)
        search.textSize = 15f
        search.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0)
        search.compoundDrawablePadding = dp(10)
        search.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(fg)
        search.setPadding(dp(14), dp(10), dp(14), dp(10))
        search.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            setColor((fg and 0x00FFFFFF) or 0x1A000000)
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                render()
            }
        })
        val slp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        slp.setMargins(dp(16), dp(6), dp(16), dp(8))
        root.addView(search, slp)

        progress = ProgressBar(this)
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(24)
        })
        status = TextView(this)
        status.setTextColor(fg)
        status.gravity = Gravity.CENTER
        status.setPadding(dp(24), dp(12), dp(24), dp(12))
        status.visibility = View.GONE
        root.addView(status)
        retry = TextView(this)
        retry.setText(R.string.tv_retry)
        retry.setTextColor(UiKit.accent(this))
        retry.textSize = 16f
        retry.gravity = Gravity.CENTER
        retry.setPadding(dp(16), dp(10), dp(16), dp(10))
        retry.visibility = View.GONE
        retry.setOnClickListener { load() }
        root.addView(retry, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })

        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(this)
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    // ---------- লোড ----------
    private class Result(val entries: List<Entry>, val playUrl: String?, val error: Boolean)

    private fun load() {
        val my = ++token
        progress.visibility = View.VISIBLE
        retry.visibility = View.GONE
        status.setText(R.string.tv_loading)
        status.visibility = View.VISIBLE
        list.removeAllViews()
        val target = url
        Thread {
            val res = fetch(target)
            main.post {
                if (my != token || isDestroyed) return@post
                progress.visibility = View.GONE
                if (res.playUrl != null) {
                    playOne(res.playUrl, target)
                    finish()
                    return@post
                }
                if (res.error) {
                    status.setText(R.string.tv_error)
                    retry.visibility = View.VISIBLE
                    return@post
                }
                all = res.entries
                status.visibility = View.GONE
                render()
            }
        }.start()
    }

    private fun fetch(target: String): Result {
        return try {
            var u = target
            var conn: HttpURLConnection? = null
            for (hop in 0 until 5) {
                val c = URL(u).openConnection() as HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 20000
                c.instanceFollowRedirects = false
                c.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
                )
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location")
                    c.disconnect()
                    if (loc == null) break
                    u = URI(u).resolve(loc).toString()
                    continue
                }
                conn = c
                break
            }
            val c = conn ?: return Result(emptyList(), null, true)
            if (c.responseCode !in 200..299) {
                c.disconnect()
                return Result(emptyList(), null, true)
            }
            val type = (c.contentType ?: "").lowercase()
            if (type.startsWith("video/") || type.startsWith("audio/") || type.contains("mp2t")) {
                c.disconnect()
                return Result(emptyList(), u, false)
            }
            val bytes = c.inputStream.use { ins ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16384)
                var total = 0
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    total += n
                    if (total > 4_000_000) break
                }
                out.toByteArray()
            }
            c.disconnect()
            val body = String(bytes, Charsets.UTF_8)
            if (body.contains("#EXT-X-")) return Result(emptyList(), u, false)
            Result(parse(u, body), null, false)
        } catch (e: Exception) {
            Result(emptyList(), null, true)
        }
    }

    // ---------- পার্সিং ----------
    private fun kindOf(u: String): Int {
        val path = try { URI(u).path ?: "" } catch (e: Exception) { u }
        val p = path.lowercase()
        return when {
            p.endsWith(".m3u8") || p.endsWith(".ts") || p.endsWith(".mp4") || p.endsWith(".mkv") ||
                p.endsWith(".flv") || p.endsWith(".avi") || p.endsWith(".mov") || p.endsWith(".webm") ||
                p.endsWith(".mpd") || p.endsWith(".mp3") || p.endsWith(".aac") -> KIND_STREAM
            p.endsWith(".m3u") -> KIND_PLAYLIST
            else -> KIND_FOLDER
        }
    }

    private fun resolve(base: String, href: String): String? {
        return try {
            val r = URI(base).resolve(href.trim())
            if (r.scheme == null || (r.scheme != "http" && r.scheme != "https")) null else r.toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun clean(raw: String): String =
        raw.replace("\\u003d", "=").replace("\\u0026", "&").replace("\\u002F", "/")
            .replace("\\/", "/").replace("&amp;", "&")

    private fun unwrapGoogle(u: String): String {
        return try {
            val uri = URI(u)
            if ((uri.host ?: "").contains("google.") && (uri.path ?: "") == "/url") {
                val q = (uri.rawQuery ?: "").split("&").firstOrNull { it.startsWith("q=") }
                if (q != null) URLDecoder.decode(q.substring(2), "UTF-8") else u
            } else u
        } catch (e: Exception) {
            u
        }
    }

    private fun skipHost(u: String): Boolean {
        val host = try { URI(u).host ?: "" } catch (e: Exception) { return true }
        val h = host.lowercase()
        return h.isEmpty() || h.contains("google.") || h.contains("gstatic.") ||
            h.contains("googleusercontent") || h.contains("schema.org") || h.contains("w3.org") ||
            h.contains("googleapis") || h.contains("youtube.") || h.contains("ggpht")
    }

    private fun isAsset(u: String): Boolean {
        val p = (try { URI(u).path ?: "" } catch (e: Exception) { "" }).lowercase()
        return p.endsWith(".js") || p.endsWith(".css") || p.endsWith(".png") || p.endsWith(".jpg") ||
            p.endsWith(".jpeg") || p.endsWith(".gif") || p.endsWith(".svg") || p.endsWith(".ico") ||
            p.endsWith(".woff") || p.endsWith(".woff2") || p.endsWith(".ttf") || p.endsWith(".webp")
    }

    private fun parse(base: String, body: String): List<Entry> {
        val out = ArrayList<Entry>()
        val seen = HashSet<String>()
        if (body.contains("#EXTINF")) {
            var name: String? = null
            var logo: String? = null
            for (raw in body.lines()) {
                val line = raw.trim()
                if (line.startsWith("#EXTINF")) {
                    name = line.substringAfterLast(",", "").trim()
                    logo = Regex("tvg-logo=\"([^\"]*)\"").find(line)?.groupValues?.get(1)
                } else if (line.isNotEmpty() && !line.startsWith("#")) {
                    val u = resolve(base, line)
                    if (u != null && seen.add(u)) {
                        out.add(Entry(if (name.isNullOrEmpty()) u.substringAfterLast("/") else name, u, kindOf(u).let { if (it == KIND_FOLDER) KIND_STREAM else it }, logo))
                    }
                    name = null
                    logo = null
                }
            }
            return out
        }
        val text = clean(body)
        val anchor = Regex("<a\\s[^>]*?href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        for (m in anchor.findAll(text)) {
            val href = m.groupValues[1]
            if (href.startsWith("#") || href.startsWith("?") || href.startsWith("javascript") || href.startsWith("mailto")) continue
            if (href.startsWith("../") || href == "..") continue
            val r = resolve(base, href) ?: continue
            val u = unwrapGoogle(r)
            if (skipHost(u) || isAsset(u) || !seen.add(u)) continue
            val label = m.groupValues[2].replace(Regex("<[^>]*>"), " ").replace("&nbsp;", " ")
                .replace(Regex("\\s+"), " ").trim()
            out.add(Entry(if (label.isEmpty()) niceName(u) else label, u, kindOf(u), null))
        }
        val raw = Regex("https?://[^\\s\"'<>\\\\)\\]]+")
        for (m in raw.findAll(text)) {
            val u = unwrapGoogle(m.value.trimEnd('.', ',', ';'))
            if (skipHost(u) || isAsset(u) || !seen.add(u)) continue
            out.add(Entry(niceName(u), u, kindOf(u), null))
        }
        return out
    }

    private fun niceName(u: String): String {
        return try {
            val uri = URI(u)
            val last = (uri.path ?: "").trimEnd('/').substringAfterLast('/')
            val host = uri.host ?: u
            if (last.isEmpty()) host else "$last  ·  $host"
        } catch (e: Exception) {
            u
        }
    }

    // ---------- তালিকা ----------
    private fun render() {
        list.removeAllViews()
        val q = search.text.toString().trim().lowercase()
        val shown = if (q.isEmpty()) all else all.filter {
            it.title.lowercase().contains(q) || it.url.lowercase().contains(q)
        }
        if (shown.isEmpty()) {
            status.setText(R.string.tv_empty)
            status.visibility = if (progress.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            return
        }
        status.visibility = View.GONE
        val streams = shown.filter { it.kind == KIND_STREAM }.take(300)
        for (e in shown.take(500)) {
            val icon = when (e.kind) {
                KIND_STREAM -> R.drawable.ic_play_circle
                KIND_PLAYLIST -> R.drawable.ic_playlist
                else -> R.drawable.ic_folder
            }
            UiKit.row(this, list, icon, e.title, e.url, {
                if (e.kind == KIND_STREAM) {
                    val idx = streams.indexOfFirst { it === e }
                    val p = Intent(this, PlayerActivity::class.java)
                    p.putStringArrayListExtra(PlayerActivity.EXTRA_URIS, ArrayList(streams.map { it.url }))
                    p.putStringArrayListExtra(PlayerActivity.EXTRA_TITLES, ArrayList(streams.map { it.title }))
                    p.putExtra(PlayerActivity.EXTRA_INDEX, if (idx < 0) 0 else idx)
                    p.putExtra(PlayerActivity.EXTRA_FLAGS, BooleanArray(streams.size))
                    p.putExtra(PlayerActivity.EXTRA_AUDIO, false)
                    startActivity(p)
                } else {
                    val i = Intent(this, LiveTvActivity::class.java)
                    i.putExtra(EXTRA_URL, e.url)
                    i.putExtra(EXTRA_TITLE, e.title)
                    startActivity(i)
                }
            })
        }
    }

    private fun playOne(streamUrl: String, fallbackTitle: String) {
        val p = Intent(this, PlayerActivity::class.java)
        p.putExtra(PlayerActivity.EXTRA_URI, streamUrl)
        p.putExtra(PlayerActivity.EXTRA_TITLE, niceName(fallbackTitle))
        startActivity(p)
    }

    companion object {
        const val EXTRA_URL = "tv_url"
        const val EXTRA_TITLE = "tv_title"
        const val HOME_URL = "https://sites.google.com/view/bdixftpserverlist/live-tv-servers?pli=1&authuser=0"
        private const val KIND_STREAM = 0
        private const val KIND_PLAYLIST = 1
        private const val KIND_FOLDER = 2
    }
}
