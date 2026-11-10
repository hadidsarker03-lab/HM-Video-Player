package com.example.videoplayer

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors

// থিম পেজ: প্রিভিউ কার্ড (লিস্ট/গ্রিড/কম্প্যাক্ট), ডার্ক/লাইট/সিস্টেম, অ্যাকসেন্ট রং
class ThemeActivity : AppCompatActivity() {

    // নিজের ছবি আপলোড (গ্যালারি থেকে)
    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val app = applicationContext
            lifecycleScope.launch {
                val key = withContext(Dispatchers.IO) { Backgrounds.addCustom(app, uri) }
                if (key != null) {
                    Backgrounds.select(this@ThemeActivity, key)
                    afterBgChange()
                }
            }
        }
    }

    // অনলাইন ওয়ালপেপার থেকে বাছলে ফিরে এসে নতুন ব্যাকগ্রাউন্ড বসায়
    private val onlineLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            if (r.resultCode == RESULT_OK) afterBgChange()
        }

    // ০ = থিম, ১ = টেক্সট কালার
    private var tab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applyTheme(this)
        super.onCreate(savedInstanceState)
        tab = savedInstanceState?.getInt("tab", 0) ?: 0
        build()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", tab)
    }

    private fun dp(v: Int) = UiKit.dp(this, v)

    // ব্যাকগ্রাউন্ড বদলালে: সাধারণ মোডে থিম নতুন করে বসে, ট্রান্সপারেন্টে শুধু পেজ আবার আঁকে
    private fun afterBgChange() {
        Backgrounds.apply(this)
        if (AppSettings.glass(this)) build() else recreate()
    }

    private fun round(color: Int, radius: Int, strokeColor: Int = 0, strokeDp: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (strokeDp > 0) setStroke(dp(strokeDp), strokeColor)
        }

    private fun accentColor(): Int {
        return UiKit.accent(this)
    }

    private fun isDarkNow(): Boolean {
        val mode = AppSettings.themeMode(this)
        if (mode == "dark") return true
        if (mode == "light") return false
        val night = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return night == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun build() {
        val fg = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.WHITE)
        val accent = accentColor()
        val dark = isDarkNow()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        // উপরের বার
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(dp(8), dp(8), dp(12), dp(8))
        val back = ImageView(this)
        back.setImageResource(R.drawable.ic_back)
        back.setColorFilter(fg)
        back.setPadding(dp(12), dp(12), dp(12), dp(12))
        back.setOnClickListener { finish() }
        top.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        val title = TextView(this)
        title.setText(R.string.settings_theme)
        title.setTextColor(fg)
        title.textSize = 22f
        title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
        top.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(top)

        // ট্যাব: থিম | টেক্সট কালার
        val tabRow = LinearLayout(this)
        tabRow.orientation = LinearLayout.HORIZONTAL
        tabRow.gravity = Gravity.CENTER
        tabRow.setPadding(dp(12), dp(4), dp(12), dp(4))
        val tabNames = intArrayOf(R.string.settings_theme, R.string.tab_text_colour)
        for (k in tabNames.indices) {
            val on = tab == k
            val t = TextView(this)
            t.setText(tabNames[k])
            t.gravity = Gravity.CENTER
            t.textSize = 15f
            t.setTextColor(if (on) Color.WHITE else fg)
            t.background = round(if (on) accent else Color.TRANSPARENT, 22)
            t.setPadding(dp(20), dp(11), dp(20), dp(11))
            t.setOnClickListener {
                if (tab != k) {
                    tab = k
                    build()
                }
            }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = dp(4)
            lp.marginEnd = dp(4)
            tabRow.addView(t, lp)
        }
        root.addView(tabRow)

        if (tab == 0) {
            // ডার্ক / লাইট / সিস্টেম
            val modes = listOf("dark", "light", "system")
            val modeNames = intArrayOf(R.string.mode_dark, R.string.mode_light, R.string.mode_system)
            val modeRow = LinearLayout(this)
            modeRow.orientation = LinearLayout.HORIZONTAL
            modeRow.gravity = Gravity.CENTER
            modeRow.setPadding(dp(12), dp(8), dp(12), dp(8))
            for (k in modes.indices) {
                val on = AppSettings.themeMode(this) == modes[k]
                val t = TextView(this)
                t.setText(modeNames[k])
                t.gravity = Gravity.CENTER
                t.textSize = 15f
                t.setTextColor(if (on) Color.WHITE else fg)
                t.background = round(if (on) accent else Color.TRANSPARENT, 22)
                t.setPadding(dp(20), dp(11), dp(20), dp(11))
                t.setOnClickListener {
                    AppSettings.setThemeMode(this, modes[k])
                    build()
                }
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                lp.marginStart = dp(4)
                lp.marginEnd = dp(4)
                modeRow.addView(t, lp)
            }
            root.addView(modeRow)
            root.addView(backgroundStrip(accent, fg))
        } else {
            // টেক্সট কালারের বৃত্ত (আলাদা ট্যাবে)
            val ctitle = TextView(this)
            ctitle.setText(R.string.tab_text_colour)
            ctitle.setTextColor(fg)
            ctitle.textSize = 16f
            ctitle.setTypeface(ctitle.typeface, android.graphics.Typeface.BOLD)
            ctitle.setPadding(dp(20), dp(16), dp(20), dp(6))
            root.addView(ctitle)

            val colorScroll = HorizontalScrollView(this)
            colorScroll.isHorizontalScrollBarEnabled = false
            val colorRow = LinearLayout(this)
            colorRow.orientation = LinearLayout.HORIZONTAL
            colorRow.gravity = Gravity.CENTER_VERTICAL
            colorRow.setPadding(dp(20), dp(10), dp(20), dp(24))
            for (i in AppSettings.textStyles.indices) {
                val color = if (i == 0) ContextCompat.getColor(this, R.color.text_primary) else AppSettings.textColors[i]
                val usable = i == 0 || AppSettings.textColorUsable(this, color)
                val sel = AppSettings.textIndex(this) == i && usable
                val circle = View(this)
                circle.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke(dp(if (sel) 3 else 1), if (sel) accent else (fg and 0x00FFFFFF) or 0x55000000)
                }
                val name = TextView(this)
                name.text = if (i == 0) getString(R.string.bg_default) else AppSettings.textNames[i]
                name.setTextColor(fg)
                name.textSize = 11f
                name.gravity = Gravity.CENTER
                name.maxLines = 1
                val cell = LinearLayout(this)
                cell.orientation = LinearLayout.VERTICAL
                cell.gravity = Gravity.CENTER_HORIZONTAL
                cell.addView(circle, LinearLayout.LayoutParams(dp(48), dp(48)))
                cell.addView(name, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
                if (!usable) cell.alpha = 0.3f
                cell.setOnClickListener {
                    if (!usable) {
                        android.widget.Toast.makeText(this, R.string.text_color_unreadable, android.widget.Toast.LENGTH_SHORT).show()
                    } else if (AppSettings.textIndex(this) != i) {
                        AppSettings.setTextIndex(this, i)
                        recreate()
                    }
                }
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.marginEnd = dp(14)
                colorRow.addView(cell, lp)
            }
            colorScroll.addView(colorRow)
            root.addView(colorScroll)
        }

        val page = ScrollView(this)
        page.isFillViewport = true
        page.fitsSystemWindows = true
        page.addView(root)
        setContentView(page)
    }

    @Volatile
    private var gen = 0

    // থিম গ্রিড (২ কলাম): কার্ডে চাপলে সোয়াইপ-প্রিভিউ খোলে, সেখানে "ব্যবহার করুন"
    private fun backgroundStrip(accent: Int, fg: Int): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val title = TextView(this)
        title.setText(R.string.theme_background)
        title.setTextColor(fg)
        title.textSize = 16f
        title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
        title.setPadding(dp(20), dp(12), dp(20), dp(6))
        box.addView(title)

        val sel = Backgrounds.selected(this)
        val faint = (fg and 0x00FFFFFF) or 0x33000000
        val keys = Backgrounds.themeKeys(this)
        val side = dp(16)
        val gap = dp(12)
        val cardW = (resources.displayMetrics.widthPixels - side * 2 - gap) / 2
        val cardH = (cardW * 1.5f).toInt()
        val myGen = ++gen
        val loads = ArrayList<Pair<String, ImageView>>()

        // key == null → "আপলোড"/"অনলাইন" কার্ড
        fun card(key: String?, label: String, onClick: () -> Unit, onLong: (() -> Unit)?): View {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            val on = key != null && key == sel
            val t = FrameLayout(this)
            t.setPadding(dp(3), dp(3), dp(3), dp(3))
            t.background = round(Color.TRANSPARENT, 16, if (on) accent else faint, if (on) 3 else 1)
            if (key != null && key != Backgrounds.NONE) {
                val iv = ImageView(this)
                iv.scaleType = ImageView.ScaleType.CENTER_CROP
                iv.background = round(Color.DKGRAY, 13)
                iv.clipToOutline = true
                t.addView(iv, FrameLayout.LayoutParams(-1, -1))
                loads.add(key to iv)
            } else if (key == Backgrounds.NONE) {
                val v = View(this)
                v.background = round(ContextCompat.getColor(this, R.color.bg), 13)
                t.addView(v, FrameLayout.LayoutParams(-1, -1))
            } else {
                val tv = TextView(this)
                tv.text = "+"
                tv.setTextColor(fg)
                tv.textSize = 34f
                tv.gravity = Gravity.CENTER
                tv.background = round(faint, 13)
                t.addView(tv, FrameLayout.LayoutParams(-1, -1))
            }
            if (on) {
                val ck = TextView(this)
                ck.text = "\u2713"
                ck.setTextColor(Color.WHITE)
                ck.textSize = 13f
                ck.gravity = Gravity.CENTER
                ck.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(accent)
                }
                val lp = FrameLayout.LayoutParams(dp(22), dp(22), Gravity.END or Gravity.BOTTOM)
                lp.setMargins(0, 0, dp(8), dp(8))
                t.addView(ck, lp)
            }
            t.setOnClickListener { onClick() }
            if (onLong != null) {
                t.setOnLongClickListener {
                    onLong()
                    true
                }
                // নিজের ছবিতে সবসময় দেখা যায় এমন ডিলিট বোতাম (ওপরে ডানে)
                val del = ImageView(this)
                del.setImageResource(R.drawable.ic_delete)
                del.setColorFilter(Color.WHITE)
                del.setPadding(dp(7), dp(7), dp(7), dp(7))
                del.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(170, 0, 0, 0))
                }
                del.setOnClickListener { onLong() }
                val dlp = FrameLayout.LayoutParams(dp(32), dp(32), Gravity.END or Gravity.TOP)
                dlp.setMargins(0, dp(8), dp(8), 0)
                t.addView(del, dlp)
            }
            col.addView(t, LinearLayout.LayoutParams(cardW, cardH))
            val lb = TextView(this)
            lb.text = label
            lb.setTextColor(fg)
            lb.textSize = 12f
            lb.setPadding(dp(4), dp(6), dp(4), dp(2))
            col.addView(lb)
            return col
        }

        fun preview(key: String) {
            onlineLauncher.launch(
                android.content.Intent(this, ThemePreviewActivity::class.java)
                    .putExtra("index", keys.indexOf(key).coerceAtLeast(0))
            )
        }

        val cards = ArrayList<View>()
        if (!AppSettings.glass(this)) {
            cards.add(card(Backgrounds.NONE, getString(R.string.bg_default), { preview(Backgrounds.NONE) }, null))
        }
        cards.add(card(null, getString(R.string.bg_upload), { picker.launch("image/*") }, null))
        cards.add(card(null, getString(R.string.bg_online), {
            onlineLauncher.launch(android.content.Intent(this, WallpaperActivity::class.java))
        }, null))
        for (k in keys) {
            if (k == Backgrounds.NONE) continue
            val longCb: (() -> Unit)? = if (k.startsWith("file:")) {
                {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.bg_remove_q)
                        .setPositiveButton(R.string.btn_ok) { _, _ ->
                            Backgrounds.removeCustom(this, k)
                            afterBgChange()
                        }
                        .setNegativeButton(R.string.btn_cancel, null)
                        .show()
                }
            } else null
            cards.add(card(k, Backgrounds.label(this, k), { preview(k) }, longCb))
        }

        var i = 0
        while (i < cards.size) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(side, 0, side, dp(10))
            row.addView(cards[i], LinearLayout.LayoutParams(cardW, ViewGroup.LayoutParams.WRAP_CONTENT))
            if (i + 1 < cards.size) {
                val lp = LinearLayout.LayoutParams(cardW, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.marginStart = gap
                row.addView(cards[i + 1], lp)
            }
            box.addView(row)
            i += 2
        }

        // ছবিগুলো পেছনে লোড হয় (পেজ আটকায় না)
        val app = applicationContext
        lifecycleScope.launch {
            for ((k, iv) in loads) {
                if (myGen != gen) break
                val b = withContext(Dispatchers.IO) { Backgrounds.thumb(app, k) }
                if (myGen == gen && b != null) iv.setImageBitmap(b)
            }
        }

        return box
    }

    // অ্যাপের ছোট নকল স্ক্রিন
    private fun previewCard(style: Int, dark: Boolean, accent: Int, selected: Boolean, fg: Int): View {
        val bg = if (dark) Color.parseColor("#14161B") else Color.parseColor("#F6F7FA")
        val ink = if (dark) Color.WHITE else Color.parseColor("#15171C")
        val faint = if (dark) Color.parseColor("#33FFFFFF") else Color.parseColor("#26000000")
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(14), dp(14), dp(14), dp(14))
        card.background = round(bg, 22, if (selected) accent else faint, if (selected) 3 else 1)

        val head = TextView(this)
        head.text = getString(R.string.app_name)
        head.setTextColor(ink)
        head.textSize = 15f
        head.setTypeface(head.typeface, android.graphics.Typeface.BOLD)
        card.addView(head)

        val tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.HORIZONTAL
        tabs.setPadding(0, dp(10), 0, dp(10))
        for (n in 0 until 3) {
            val p = View(this)
            p.background = round(if (n == 1) accent else faint, 10)
            val lp = LinearLayout.LayoutParams(dp(if (n == 1) 44 else 36), dp(18))
            lp.marginEnd = dp(6)
            tabs.addView(p, lp)
        }
        card.addView(tabs)

        val thumbs = intArrayOf(
            Color.parseColor("#F59E0B"), Color.parseColor("#EF4444"), Color.parseColor("#3B82F6"),
            Color.parseColor("#10B981"), Color.parseColor("#8B5CF6"), Color.parseColor("#EC4899")
        )
        if (style == AppSettings.VIEW_GRID) {
            for (r in 0 until 3) {
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                for (c in 0 until 2) {
                    val col = LinearLayout(this)
                    col.orientation = LinearLayout.VERTICAL
                    val t = View(this)
                    t.background = round(thumbs[(r * 2 + c) % thumbs.size], 10)
                    col.addView(t, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
                    val b = View(this)
                    b.background = round(faint, 3)
                    val blp = LinearLayout.LayoutParams(dp(50), dp(5))
                    blp.topMargin = dp(5)
                    col.addView(b, blp)
                    val clp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    clp.marginEnd = dp(if (c == 0) 8 else 0)
                    row.addView(col, clp)
                }
                val rlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                rlp.bottomMargin = dp(10)
                card.addView(row, rlp)
            }
        } else {
            val size = if (style == AppSettings.VIEW_COMPACT) 24 else 38
            val count = if (style == AppSettings.VIEW_COMPACT) 9 else 5
            for (r in 0 until count) {
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                val t = View(this)
                t.background = round(thumbs[r % thumbs.size], 8)
                row.addView(t, LinearLayout.LayoutParams(dp(size), dp(size)))
                val txt = LinearLayout(this)
                txt.orientation = LinearLayout.VERTICAL
                val a = View(this)
                a.background = round(ink, 3)
                a.alpha = 0.75f
                txt.addView(a, LinearLayout.LayoutParams(dp(90), dp(6)))
                val b = View(this)
                b.background = round(faint, 3)
                val blp = LinearLayout.LayoutParams(dp(56), dp(5))
                blp.topMargin = dp(5)
                txt.addView(b, blp)
                val tlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                tlp.marginStart = dp(10)
                row.addView(txt, tlp)
                val rlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                rlp.bottomMargin = dp(if (style == AppSettings.VIEW_COMPACT) 7 else 10)
                card.addView(row, rlp)
            }
        }
        return card
    }
}
