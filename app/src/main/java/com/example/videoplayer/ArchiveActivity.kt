package com.example.videoplayer

import android.app.AlertDialog
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
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.color.MaterialColors

// ফ্রি সিনেমা: Internet Archive-এর বৈধ ভিডিও অ্যাপের ভেতরেই চলে (ExoPlayer, সাবটাইটেল/স্পিড সহ আগের প্লেয়ারে)।
class ArchiveActivity : AppCompatActivity() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var search: EditText
    private val chips = ArrayList<TextView>()
    private var cat = 0
    private var token = 0
    private val debounce = Runnable { load() }
    private var fg = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applyTheme(this)
        super.onCreate(savedInstanceState)
        fg = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.WHITE)
        build()
        load()
    }

    override fun onDestroy() {
        token++
        main.removeCallbacks(debounce)
        super.onDestroy()
    }

    private fun dp(v: Int) = UiKit.dp(this, v)

    private fun build() {
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
        title.setText(R.string.arc_title)
        title.setTextColor(fg)
        title.textSize = 21f
        title.setTypeface(title.typeface, Typeface.BOLD)
        top.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(top)

        val chipRow = LinearLayout(this)
        chipRow.setPadding(dp(12), 0, dp(12), 0)
        ArchiveApi.CATEGORIES.forEachIndexed { i, c ->
            val t = TextView(this)
            t.setText(c.second)
            t.textSize = 14f
            t.setPadding(dp(14), dp(7), dp(14), dp(7))
            t.setOnClickListener {
                cat = i
                styleChips()
                load()
            }
            chips.add(t)
            chipRow.addView(t, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(4), 0, dp(4), 0)
            })
        }
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(chipRow)
        root.addView(hs)

        search = EditText(this)
        search.setHint(R.string.arc_search)
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
            setMargins(dp(16), dp(8), dp(16), dp(8))
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
        styleChips()
    }

    private fun styleChips() {
        val on = UiKit.accent(this)
        chips.forEachIndexed { i, t ->
            val sel = i == cat
            t.setTextColor(if (sel) Color.WHITE else fg)
            t.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(if (sel) on else (fg and 0x00FFFFFF) or 0x1A000000)
            }
        }
    }

    private fun load() {
        val my = ++token
        list.removeAllViews()
        progress.visibility = View.VISIBLE
        status.visibility = View.GONE
        val q = search.text.toString().trim()
        val coll = ArchiveApi.CATEGORIES[cat].first
        Thread {
            val res = try { ArchiveApi.list(coll, q, 1) } catch (e: Exception) { null }
            main.post {
                if (my != token || isDestroyed) return@post
                progress.visibility = View.GONE
                if (res == null || res.isEmpty()) {
                    status.setText(if (res == null) R.string.disc_error else R.string.disc_empty)
                    status.visibility = View.VISIBLE
                    return@post
                }
                res.forEach { addRow(it) }
            }
        }.start()
    }

    private fun addRow(it: ArchiveApi.Item) {
        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(16), dp(6), dp(16), dp(6))
        row.setBackgroundResource(UiKit.ripple(this))
        val img = ImageView(this)
        img.scaleType = ImageView.ScaleType.CENTER_CROP
        img.setBackgroundColor((fg and 0x00FFFFFF) or 0x22000000)
        Glide.with(this).load(it.thumb).into(img)
        row.addView(img, LinearLayout.LayoutParams(dp(96), dp(64)))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(14), 0, 0, 0)
        val t = TextView(this)
        t.text = it.title
        t.setTextColor(fg)
        t.textSize = 16f
        t.maxLines = 2
        t.ellipsize = TextUtils.TruncateAt.END
        col.addView(t)
        if (it.year.isNotEmpty()) {
            val s = TextView(this)
            s.text = it.year
            s.setTextColor((fg and 0x00FFFFFF) or 0x99000000.toInt())
            s.textSize = 12.5f
            col.addView(s)
        }
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.setOnClickListener { _ -> open(it) }
        list.addView(row)
    }

    private fun open(item: ArchiveApi.Item) {
        Toast.makeText(this, R.string.arc_loading, Toast.LENGTH_SHORT).show()
        Thread {
            val vids = try { ArchiveApi.videos(item.id) } catch (e: Exception) { null }
            main.post {
                if (isDestroyed) return@post
                when {
                    vids == null -> Toast.makeText(this, R.string.disc_error, Toast.LENGTH_SHORT).show()
                    vids.isEmpty() -> Toast.makeText(this, R.string.arc_none, Toast.LENGTH_SHORT).show()
                    vids.size == 1 -> play(item, vids, 0)
                    else -> AlertDialog.Builder(this).setTitle(item.title)
                        .setItems(vids.map { it.name }.toTypedArray()) { _, i -> play(item, vids, i) }.show()
                }
            }
        }.start()
    }

    // সিরিজ/একাধিক ফাইল হলে পুরো তালিকা প্লেলিস্ট হিসেবে চলে (পরের এপিসোডে অটো)
    private fun play(item: ArchiveApi.Item, vids: List<ArchiveApi.Video>, index: Int) {
        val p = Intent(this, PlayerActivity::class.java)
        p.putStringArrayListExtra(PlayerActivity.EXTRA_URIS, ArrayList(vids.map { it.url }))
        p.putStringArrayListExtra(
            PlayerActivity.EXTRA_TITLES,
            ArrayList(vids.map { if (vids.size == 1) item.title else it.name.substringBeforeLast('.') })
        )
        p.putExtra(PlayerActivity.EXTRA_INDEX, index)
        p.putExtra(PlayerActivity.EXTRA_FLAGS, BooleanArray(vids.size))
        p.putExtra(PlayerActivity.EXTRA_AUDIO, false)
        startActivity(p)
    }
}
