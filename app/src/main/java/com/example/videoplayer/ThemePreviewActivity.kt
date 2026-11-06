package com.example.videoplayer

import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

// থিম প্রিভিউ: সোয়াইপ করে একের পর এক থিম দেখা যায়, নিচে "ব্যবহার করুন" বোতাম (PLAYit-এর মতো)
class ThemePreviewActivity : AppCompatActivity() {

    private lateinit var keys: List<String>
    private lateinit var bgView: ImageView
    private lateinit var titleView: TextView
    private lateinit var useBtn: TextView
    private lateinit var pager: ViewPager2
    private lateinit var delBtn: ImageView
    private var current = 0
    private var blurLevel = 0
    private lateinit var blurBox: LinearLayout
    private lateinit var blurLabel: TextView
    private val wpViews = ArrayList<java.lang.ref.WeakReference<ImageView>>()

    private fun dp(v: Int) = UiKit.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        window.setBackgroundDrawable(ColorDrawable(Color.parseColor("#14161A")))

        keys = Backgrounds.themeKeys(this)
        if (keys.isEmpty()) {
            finish()
            return
        }
        current = (savedInstanceState?.getInt("page") ?: intent.getIntExtra("index", 0)).coerceIn(0, keys.size - 1)

        val root = FrameLayout(this)

        // পেছনের ঝাপসা ছবি + অন্ধকার স্তর (থিম বদলালে বদলায়)
        bgView = ImageView(this)
        bgView.scaleType = ImageView.ScaleType.CENTER_CROP
        bgView.scaleX = 1.25f
        bgView.scaleY = 1.25f
        if (Build.VERSION.SDK_INT >= 31) {
            bgView.setRenderEffect(RenderEffect.createBlurEffect(60f, 60f, Shader.TileMode.CLAMP))
        }
        root.addView(bgView, FrameLayout.LayoutParams(-1, -1))
        val dim = View(this)
        dim.setBackgroundColor(Color.argb(if (Build.VERSION.SDK_INT >= 31) 110 else 170, 0, 0, 0))
        root.addView(dim, FrameLayout.LayoutParams(-1, -1))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.fitsSystemWindows = true

        // উপরের বার
        val top = FrameLayout(this)
        val back = ImageView(this)
        back.setImageResource(R.drawable.ic_back)
        back.setColorFilter(Color.WHITE)
        back.setPadding(dp(12), dp(12), dp(12), dp(12))
        back.setOnClickListener { finish() }
        top.addView(back, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.START or Gravity.CENTER_VERTICAL))
        titleView = TextView(this)
        titleView.setTextColor(Color.WHITE)
        titleView.textSize = 17f
        titleView.gravity = Gravity.CENTER
        titleView.setTypeface(titleView.typeface, android.graphics.Typeface.BOLD)
        top.addView(titleView, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        // নিজের/ডাউনলোড করা ছবি হলে ডিলিট বোতাম (ওপরে ডানে)
        delBtn = ImageView(this)
        delBtn.setImageResource(R.drawable.ic_delete)
        delBtn.setColorFilter(Color.WHITE)
        delBtn.setPadding(dp(12), dp(12), dp(12), dp(12))
        delBtn.visibility = View.GONE
        delBtn.setOnClickListener { confirmDelete() }
        top.addView(delBtn, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.END or Gravity.CENTER_VERTICAL))
        col.addView(top, LinearLayout.LayoutParams(-1, dp(56)))

        // সোয়াইপ করার পেজার (দুই পাশের থিম একটু দেখা যায়)
        pager = ViewPager2(this)
        pager.offscreenPageLimit = 2
        pager.adapter = Adapter()
        (pager.getChildAt(0) as RecyclerView).apply {
            clipToPadding = false
            clipChildren = false
            setPadding(dp(58), dp(12), dp(58), dp(12))
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val tr = CompositePageTransformer()
        tr.addTransformer(MarginPageTransformer(dp(10)))
        tr.addTransformer { page, pos ->
            val a = abs(pos).coerceAtMost(1f)
            val s = 1f - 0.08f * a
            page.scaleX = s
            page.scaleY = s
            page.alpha = 1f - 0.45f * a
        }
        pager.setPageTransformer(tr)
        col.addView(pager, LinearLayout.LayoutParams(-1, 0, 1f))

        // ব্যাকগ্রাউন্ড ব্লার (Use বোতামের ঠিক ওপরে)
        blurLevel = Backgrounds.blur(this)
        blurBox = LinearLayout(this)
        blurBox.orientation = LinearLayout.VERTICAL
        blurLabel = TextView(this)
        blurLabel.setTextColor(Color.WHITE)
        blurLabel.textSize = 13f
        blurLabel.setPadding(dp(44), dp(4), dp(44), 0)
        blurBox.addView(blurLabel)
        val bar = android.widget.SeekBar(this)
        bar.max = 100
        bar.progress = blurLevel
        // ব্লার বারের নতুন ডিজাইন: মোটা গোল ট্র্যাক + সাদা গোল হাতল
        bar.progressDrawable = ContextCompat.getDrawable(this, R.drawable.seek_blur_track)
        bar.thumb = ContextCompat.getDrawable(this, R.drawable.seek_blur_thumb)
        bar.splitTrack = false
        bar.thumbOffset = 0
        bar.setPadding(dp(46), dp(10), dp(46), dp(10))
        bar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                blurLevel = p
                setBlurLabel()
                for (r in wpViews) r.get()?.let { applyBlur(it) }
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })
        blurBox.addView(bar, LinearLayout.LayoutParams(-1, -2))
        setBlurLabel()
        col.addView(blurBox, LinearLayout.LayoutParams(-1, -2))

        // "ব্যবহার করুন" বোতাম
        useBtn = TextView(this)
        useBtn.textSize = 15f
        useBtn.gravity = Gravity.CENTER
        useBtn.setTextColor(Color.WHITE)
        useBtn.background = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = dp(26).toFloat()
            setStroke(dp(1), Color.argb(200, 255, 255, 255))
        }
        useBtn.setOnClickListener {
            if (keys[current] != Backgrounds.selected(this)) {
                Backgrounds.select(this, keys[current])
            }
            if (keys[current] != Backgrounds.NONE) Backgrounds.setBlur(this, blurLevel)
            setResult(RESULT_OK)
            finish()
        }
        val lp = LinearLayout.LayoutParams(-1, dp(50))
        lp.setMargins(dp(40), dp(8), dp(40), dp(24))
        col.addView(useBtn, lp)

        root.addView(col, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        pager.setCurrentItem(current, false)
        onPage(current)
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                onPage(position)
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("page", current)
    }

    private fun confirmDelete() {
        val key = keys.getOrNull(current) ?: return
        if (!key.startsWith("file:")) return
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.bg_remove_q)
            .setPositiveButton(R.string.btn_ok) { _, _ ->
                Backgrounds.removeCustom(this, key)
                setResult(RESULT_OK)
                finish()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun setBlurLabel() {
        blurLabel.text = getString(R.string.theme_blur) + ": " + blurLevel + "%"
    }

    // প্রিভিউতে ব্লার দেখানো (অ্যান্ড্রয়েড ১২+); ছবি বসানোর সময় আসল ব্লার প্রয়োগ হয়
    private fun applyBlur(iv: ImageView) {
        if (Build.VERSION.SDK_INT < 31) return
        if (blurLevel > 0) {
            val r = blurLevel * 0.35f
            iv.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP))
        } else {
            iv.setRenderEffect(null)
        }
    }

    private fun onPage(i: Int) {
        current = i
        val key = keys[i]
        blurBox.visibility = if (key == Backgrounds.NONE) View.GONE else View.VISIBLE
        titleView.text = Backgrounds.label(this, key)
        delBtn.visibility = if (key.startsWith("file:")) View.VISIBLE else View.GONE
        val inUse = key == Backgrounds.selected(this)
        useBtn.setText(if (inUse) R.string.theme_in_use else R.string.theme_use)
        useBtn.alpha = if (inUse) 0.5f else 1f
        val b = if (key == Backgrounds.NONE) null else Backgrounds.thumb(this, key)
        if (b != null) bgView.setImageBitmap(b) else bgView.setImageDrawable(ColorDrawable(Color.parseColor("#2A2D34")))
    }

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = keys.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val f = FrameLayout(parent.context)
            f.layoutParams = ViewGroup.LayoutParams(-1, -1)
            return object : RecyclerView.ViewHolder(f) {}
        }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
            val f = h.itemView as FrameLayout
            f.removeAllViews()
            f.addView(mock(keys[position]), FrameLayout.LayoutParams(-1, -1))
        }
    }

    // অ্যাপের নকল স্ক্রিন: ঠিক যেমন দেখাবে (ওয়ালপেপার থিমে বক্স ছাড়া, শুধু লেখা ও আইকন)
    private fun mock(key: String): View {
        val none = key == Backgrounds.NONE
        val lightImg = !none && Backgrounds.isLight(this, key)
        val ink = when {
            none -> ContextCompat.getColor(this, R.color.text_primary)
            lightImg -> Color.parseColor("#15171C")
            else -> Color.WHITE
        }
        val soft = (ink and 0x00FFFFFF) or 0xB3000000.toInt()
        val accent = UiKit.accent(this)
        val boxColor = ContextCompat.getColor(this, R.color.surface_alt)

        fun round(c: Int, r: Int) = GradientDrawable().apply {
            setColor(c)
            cornerRadius = dp(r).toFloat()
        }

        val frame = FrameLayout(this)
        frame.clipToOutline = true
        frame.background = round(ContextCompat.getColor(this, R.color.bg), 22)
        frame.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(v: View, o: android.graphics.Outline) {
                o.setRoundRect(0, 0, v.width, v.height, dp(22).toFloat())
            }
        }
        if (!none) {
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setImageBitmap(Backgrounds.thumb(this, key))
            applyBlur(iv)
            wpViews.add(java.lang.ref.WeakReference(iv))
            frame.addView(iv, FrameLayout.LayoutParams(-1, -1))
            if (!lightImg) {
                val sc = View(this)
                sc.setBackgroundColor(Color.argb(46, 0, 0, 0))
                frame.addView(sc, FrameLayout.LayoutParams(-1, -1))
            }
        }

        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(12), dp(14), dp(12), dp(6))

        // শিরোনাম + সার্চ
        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        val t = TextView(this)
        t.setText(R.string.app_name)
        t.setTextColor(accent)
        t.textSize = 14f
        t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
        head.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        val sIc = ImageView(this)
        sIc.setImageResource(R.drawable.ic_search)
        sIc.setColorFilter(ink)
        head.addView(sIc, LinearLayout.LayoutParams(dp(16), dp(16)))
        c.addView(head)

        // ট্যাব লেখা
        val tabs = LinearLayout(this)
        tabs.setPadding(0, dp(8), 0, dp(8))
        for ((n, name) in listOf("Video", "Folder", "Playlist").withIndex()) {
            val tv = TextView(this)
            tv.text = name
            tv.textSize = 9f
            tv.setTextColor(if (n == 0) accent else ink)
            tv.setPadding(dp(8), dp(4), dp(8), dp(4))
            if (none) tv.background = round(boxColor, 14)
            val lp = LinearLayout.LayoutParams(-2, -2)
            lp.marginEnd = dp(6)
            tabs.addView(tv, lp)
        }
        c.addView(tabs)

        // হিস্ট্রি থাম্বনেইল
        val colors = intArrayOf(
            Color.parseColor("#F59E0B"), Color.parseColor("#3B82F6"), Color.parseColor("#10B981"),
            Color.parseColor("#EC4899"), Color.parseColor("#8B5CF6")
        )
        val hist = LinearLayout(this)
        for (n in 0 until 3) {
            val v = View(this)
            v.background = round(colors[n], 8)
            val lp = LinearLayout.LayoutParams(0, dp(42), 1f)
            lp.marginEnd = dp(if (n < 2) 6 else 0)
            hist.addView(v, lp)
        }
        c.addView(hist)

        // ভিডিও সারি
        for (r in 0 until 4) {
            val row = LinearLayout(this)
            row.gravity = Gravity.CENTER_VERTICAL
            if (none) {
                row.background = round(boxColor, 12)
                row.setPadding(dp(6), dp(5), dp(6), dp(5))
            }
            val th = View(this)
            th.background = round(colors[(r + 1) % colors.size], 8)
            row.addView(th, LinearLayout.LayoutParams(dp(54), dp(34)))
            val txt = LinearLayout(this)
            txt.orientation = LinearLayout.VERTICAL
            val a = View(this)
            a.background = round(ink, 3)
            txt.addView(a, LinearLayout.LayoutParams(dp(64), dp(5)))
            val b = View(this)
            b.background = round(soft, 3)
            val blp = LinearLayout.LayoutParams(dp(38), dp(4))
            blp.topMargin = dp(5)
            txt.addView(b, blp)
            val tlp = LinearLayout.LayoutParams(-2, -2)
            tlp.marginStart = dp(8)
            row.addView(txt, tlp)
            val rlp = LinearLayout.LayoutParams(-1, -2)
            rlp.topMargin = dp(8)
            c.addView(row, rlp)
        }

        val spacer = View(this)
        c.addView(spacer, LinearLayout.LayoutParams(-1, 0, 1f))

        // নিচের নেভিগেশন
        val nav = LinearLayout(this)
        nav.gravity = Gravity.CENTER
        nav.setPadding(0, dp(6), 0, dp(6))
        val icons = intArrayOf(R.drawable.ic_video, R.drawable.ic_audiotrack, R.drawable.ic_person)
        for ((n, ic) in icons.withIndex()) {
            val v = ImageView(this)
            v.setImageResource(ic)
            v.setColorFilter(if (n == 0) accent else soft)
            if (none) {
                v.background = round(boxColor, 14)
                v.setPadding(dp(8), dp(5), dp(8), dp(5))
            }
            val lp = LinearLayout.LayoutParams(0, dp(28), 1f)
            lp.marginStart = dp(4)
            lp.marginEnd = dp(4)
            nav.addView(v, lp)
        }
        c.addView(nav, LinearLayout.LayoutParams(-1, -2))

        frame.addView(c, FrameLayout.LayoutParams(-1, -1))
        return frame
    }
}
