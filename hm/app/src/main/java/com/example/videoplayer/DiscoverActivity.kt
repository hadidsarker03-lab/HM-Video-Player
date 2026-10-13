package com.example.videoplayer

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextUtils
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
import com.bumptech.glide.Glide
import com.google.android.material.color.MaterialColors

// ডিসকভার: AniList থেকে ট্রেন্ডিং/সার্চ (অ্যানিমে) + নিজের ওয়াচলিস্ট। ভিডিও স্ট্রিম নেই, শুধু বৈধ তথ্য ও লিংক।
class DiscoverActivity : AppCompatActivity() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var search: EditText
    private lateinit var tabBrowse: TextView
    private lateinit var tabList: TextView
    private var showList = false
    private var token = 0
    private val debounce = Runnable { load() }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applyTheme(this)
        super.onCreate(savedInstanceState)
        build()
        load()
    }

    override fun onResume() {
        super.onResume()
        if (showList) load()
    }

    override fun onDestroy() {
        token++
        main.removeCallbacks(debounce)
        super.onDestroy()
    }

    private fun dp(v: Int) = UiKit.dp(this, v)

    private fun tab(text: String, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 15f
        t.gravity = Gravity.CENTER
        t.setPadding(dp(8), dp(10), dp(8), dp(10))
        t.setOnClickListener { onClick() }
        return t
    }

    private fun build() {
        val fg = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.WHITE)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.fitsSystemWindows = true

        val top = LinearLayout(this)
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(dp(8), dp(8), dp(16), dp(4))
        val back = ImageView(this)
        back.setImageResource(R.drawable.ic_back)
        back.setColorFilter(fg)
        back.setPadding(dp(12), dp(12), dp(12), dp(12))
        back.setOnClickListener { finish() }
        top.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        val title = TextView(this)
        title.setText(R.string.tab_discover)
        title.setTextColor(fg)
        title.textSize = 21f
        title.setTypeface(title.typeface, Typeface.BOLD)
        top.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(top)

        val tabs = LinearLayout(this)
        tabBrowse = tab(getString(R.string.disc_browse)) { switchTab(false) }
        tabList = tab(getString(R.string.disc_watchlist)) { switchTab(true) }
        tabs.addView(tabBrowse, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        tabs.addView(tabList, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(tabs)

        search = EditText(this)
        search.setHint(R.string.disc_search)
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
                main.removeCallbacks(debounce)
                main.postDelayed(debounce, 450)
            }
        })
        root.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(16), dp(6), dp(16), dp(8))
        })

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

        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(this)
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        styleTabs()
    }

    private fun styleTabs() {
        val on = UiKit.accent(this)
        val off = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.GRAY)
        tabBrowse.setTextColor(if (showList) off else on)
        tabList.setTextColor(if (showList) on else off)
        tabBrowse.setTypeface(null, if (showList) Typeface.NORMAL else Typeface.BOLD)
        tabList.setTypeface(null, if (showList) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun switchTab(wl: Boolean) {
        if (wl == showList) return
        showList = wl
        styleTabs()
        load()
    }

    private fun load() {
        val my = ++token
        list.removeAllViews()
        val q = search.text.toString().trim()
        if (showList) {
            progress.visibility = View.GONE
            val items = Tracker.all(this).filter { q.isEmpty() || it.title.contains(q, true) }
            if (items.isEmpty()) {
                status.setText(R.string.disc_list_empty)
                status.visibility = View.VISIBLE
                return
            }
            status.visibility = View.GONE
            for (e in items) {
                val sub = Tracker.label(this, e.status) +
                    (if (e.total > 0) "  ·  ${e.progress}/${e.total}" else "  ·  ${e.progress}") +
                    (if (e.score > 0) "  ·  ★ ${e.score}/10" else "")
                addRow(e.id, e.title, sub, e.cover)
            }
            return
        }
        progress.visibility = View.VISIBLE
        status.visibility = View.GONE
        Thread {
            val res = try { AniApi.list(q, 1) } catch (e: Exception) { null }
            main.post {
                if (my != token || isDestroyed) return@post
                progress.visibility = View.GONE
                if (res == null) {
                    status.setText(R.string.disc_error)
                    status.visibility = View.VISIBLE
                    return@post
                }
                if (res.isEmpty()) {
                    status.setText(R.string.disc_empty)
                    status.visibility = View.VISIBLE
                    return@post
                }
                for (a in res) {
                    val sub = listOfNotNull(
                        if (a.score > 0) "★ ${a.score / 10.0}" else null,
                        if (a.episodes > 0) "${a.episodes} ep" else null,
                        a.genres.takeIf { it.isNotEmpty() }
                    ).joinToString("  ·  ")
                    addRow(a.id, a.title, sub, a.cover)
                }
            }
        }.start()
    }

    private fun addRow(id: Int, title: String, sub: String, cover: String?) {
        val fg = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.WHITE)
        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(16), dp(6), dp(16), dp(6))
        row.setBackgroundResource(UiKit.ripple(this))
        val img = ImageView(this)
        img.scaleType = ImageView.ScaleType.CENTER_CROP
        img.setBackgroundColor((fg and 0x00FFFFFF) or 0x22000000)
        if (cover != null) Glide.with(this).load(cover).into(img)
        row.addView(img, LinearLayout.LayoutParams(dp(56), dp(80)))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(14), 0, 0, 0)
        val t = TextView(this)
        t.text = title
        t.setTextColor(fg)
        t.textSize = 16f
        t.maxLines = 2
        t.ellipsize = TextUtils.TruncateAt.END
        col.addView(t)
        val s = TextView(this)
        s.text = sub
        s.setTextColor((fg and 0x00FFFFFF) or 0x99000000.toInt())
        s.textSize = 12.5f
        s.maxLines = 2
        s.ellipsize = TextUtils.TruncateAt.END
        col.addView(s)
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.setOnClickListener {
            startActivity(Intent(this, AnimeDetailActivity::class.java).putExtra(AnimeDetailActivity.EXTRA_ID, id))
        }
        list.addView(row)
    }
}
