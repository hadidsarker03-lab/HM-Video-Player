package com.example.videoplayer

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.color.MaterialColors

// ডিটেইল: (২) ট্রেইলার অ্যাপের ভেতরে, (১) বৈধ প্ল্যাটফর্মে "Watch on" লিংক, (৫) ওয়াচলিস্ট ট্র্যাকার।
class AnimeDetailActivity : AppCompatActivity() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var body: LinearLayout
    private lateinit var progress: ProgressBar
    private var detail: AniApi.Detail? = null
    private var fg = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applyTheme(this)
        super.onCreate(savedInstanceState)
        fg = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.WHITE)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.fitsSystemWindows = true
        val back = ImageView(this)
        back.setImageResource(R.drawable.ic_back)
        back.setColorFilter(fg)
        back.setPadding(dp(12), dp(12), dp(12), dp(12))
        back.setOnClickListener { finish() }
        root.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)).apply { setMargins(dp(8), dp(8), 0, 0) })
        progress = ProgressBar(this)
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(24)
        })
        body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(16), dp(4), dp(16), dp(24))
        val scroll = ScrollView(this)
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        val id = intent.getIntExtra(EXTRA_ID, 0)
        Thread {
            val d = try { AniApi.detail(id) } catch (e: Exception) { null }
            main.post {
                if (isDestroyed) return@post
                progress.visibility = View.GONE
                if (d == null) {
                    text(getString(R.string.disc_error), 15f, false)
                } else {
                    detail = d
                    render(d)
                }
            }
        }.start()
    }

    private fun dp(v: Int) = UiKit.dp(this, v)

    private fun text(s: String, size: Float, bold: Boolean, alpha: Int = 0xFF): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor((fg and 0x00FFFFFF) or (alpha shl 24))
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        t.setPadding(0, dp(4), 0, dp(4))
        body.addView(t)
        return t
    }

    private fun button(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        body.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
        })
        return b
    }

    private fun render(d: AniApi.Detail) {
        body.removeAllViews()
        val img = ImageView(this)
        img.scaleType = ImageView.ScaleType.CENTER_CROP
        val pic = d.banner ?: d.item.cover
        if (pic != null) Glide.with(this).load(pic).into(img)
        body.addView(img, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(190)))
        text(d.item.title, 22f, true)
        val meta = listOfNotNull(
            if (d.item.score > 0) "★ ${d.item.score / 10.0}" else null,
            if (d.year > 0) d.year.toString() else null,
            if (d.item.episodes > 0) "${d.item.episodes} ep" else null,
            d.status.takeIf { it.isNotEmpty() }?.replace('_', ' ')
        ).joinToString("  ·  ")
        if (meta.isNotEmpty()) text(meta, 13.5f, false, 0xB3)
        if (d.item.genres.isNotEmpty()) text(d.item.genres, 13f, false, 0x99)

        // (২) ট্রেইলার
        if (d.trailerId != null) button(getString(R.string.det_trailer)) { playTrailer(d) }

        // (৫) ট্র্যাকার
        text(getString(R.string.det_tracker), 16f, true).setPadding(0, dp(14), 0, dp(2))
        val tr = LinearLayout(this)
        tr.orientation = LinearLayout.VERTICAL
        body.addView(tr)
        renderTracker(tr, d)

        // (১) বৈধ স্ট্রিমিং লিংক
        text(getString(R.string.det_watch), 16f, true).setPadding(0, dp(14), 0, dp(2))
        if (d.links.isEmpty()) {
            text(getString(R.string.det_no_links), 13f, false, 0x99)
        } else {
            for (l in d.links) {
                button(getString(R.string.det_watch_on, l.site)) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(l.url)))
                    } catch (e: Exception) {
                        Toast.makeText(this, R.string.disc_error, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        if (d.description.isNotEmpty()) {
            text(getString(R.string.det_about), 16f, true).setPadding(0, dp(14), 0, dp(2))
            text(d.description, 14f, false, 0xD9)
        }
        text(getString(R.string.det_credit), 11f, false, 0x77).setPadding(0, dp(16), 0, 0)
    }

    private fun renderTracker(box: LinearLayout, d: AniApi.Detail) {
        box.removeAllViews()
        val cur = Tracker.get(this, d.item.id)
        fun add(label: String, onClick: () -> Unit) {
            val b = Button(this)
            b.text = label
            b.isAllCaps = false
            b.setOnClickListener { onClick() }
            box.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        fun entry(status: String) = cur ?: Tracker.Entry(d.item.id, d.item.title, d.item.cover, d.item.episodes, status, 0, 0)

        add(if (cur == null) getString(R.string.trk_add) else Tracker.label(this, cur.status)) {
            val labels = Tracker.STATUSES.map { Tracker.label(this, it) }.toTypedArray()
            AlertDialog.Builder(this).setItems(labels) { _, i ->
                val e = entry(Tracker.STATUSES[i])
                e.status = Tracker.STATUSES[i]
                if (e.status == Tracker.COMPLETED && e.total > 0) e.progress = e.total
                Tracker.save(this, e)
                renderTracker(box, d)
            }.show()
        }
        if (cur != null) {
            val total = if (cur.total > 0) "/${cur.total}" else ""
            add(getString(R.string.trk_progress, cur.progress.toString() + total)) {
                if (cur.total > 0 && cur.progress >= cur.total) cur.progress = 0 else cur.progress++
                if (cur.status == Tracker.PLANNING) cur.status = Tracker.WATCHING
                if (cur.total > 0 && cur.progress >= cur.total) cur.status = Tracker.COMPLETED
                Tracker.save(this, cur)
                renderTracker(box, d)
            }
            add(if (cur.score > 0) getString(R.string.trk_score, cur.score) else getString(R.string.trk_rate)) {
                val nums = (1..10).map { it.toString() }.toTypedArray()
                AlertDialog.Builder(this).setItems(nums) { _, i ->
                    cur.score = i + 1
                    Tracker.save(this, cur)
                    renderTracker(box, d)
                }.show()
            }
            add(getString(R.string.trk_remove)) {
                Tracker.remove(this, cur.id)
                renderTracker(box, d)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun playTrailer(d: AniApi.Detail) {
        val id = d.trailerId ?: return
        val site = (d.trailerSite ?: "").lowercase()
        val embed = when (site) {
            "youtube" -> "https://www.youtube-nocookie.com/embed/$id?autoplay=1&playsinline=1"
            "dailymotion" -> "https://www.dailymotion.com/embed/video/$id?autoplay=1"
            else -> {
                Toast.makeText(this, R.string.disc_error, Toast.LENGTH_SHORT).show()
                return
            }
        }
        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.webChromeClient = WebChromeClient()
        web.setBackgroundColor(Color.BLACK)
        val html = "<html><body style='margin:0;background:#000'><iframe width='100%' height='100%' " +
            "src='$embed' frameborder='0' allow='autoplay; encrypted-media; fullscreen' allowfullscreen></iframe></body></html>"
        web.loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "utf-8", null)
        val dlg = AlertDialog.Builder(this).setView(web).create()
        dlg.setOnDismissListener { web.destroy() }
        dlg.show()
        dlg.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, dp(260))
    }

    companion object {
        const val EXTRA_ID = "anime_id"
    }
}
