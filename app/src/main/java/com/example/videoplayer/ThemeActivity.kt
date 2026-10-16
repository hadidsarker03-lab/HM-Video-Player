package com.example.videoplayer

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
            Thread {
                val key = Backgrounds.addCustom(app, uri)
                runOnUiThread {
                    if (key != null && !isDestroyed) {
                        Backgrounds.select(this, key)
                        afterBgChange()
                    }
                }
            }.start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applyTheme(this)
        super.onCreate(savedInstanceState)
        build()
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

        // ফার (প্লাশ) থিম চালু/বন্ধ
        val furSw = com.google.android.material.materialswitch.MaterialSwitch(this)
        furSw.setText(R.string.theme_fur)
        furSw.setTextColor(fg)
        furSw.textSize = 16f
        furSw.isChecked = AppSettings.fur(this)
        furSw.setPadding(dp(20), dp(10), dp(20), dp(10))
        furSw.setOnCheckedChangeListener { _, on ->
            AppSettings.setFur(this, on)
            Backgrounds.apply(this)
            recreate()
        }
        root.addView(furSw)
        val furOn = AppSettings.fur(this)

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
        if (!furOn) root.addView(modeRow)

        if (!furOn) root.addView(backgroundStrip(accent, fg))

        // অ্যাকসেন্ট রঙের বৃত্ত
        val colorScroll = HorizontalScrollView(this)
        colorScroll.isHorizontalScrollBarEnabled = false
        val colorRow = LinearLayout(this)
        colorRow.orientation = LinearLayout.HORIZONTAL
        colorRow.gravity = Gravity.CENTER_VERTICAL
        colorRow.setPadding(dp(20), dp(10), dp(20), dp(24))
        for (i in AppSettings.textStyles.indices) {
            val color = if (i == 0) ContextCompat.getColor(this, R.color.text_primary) else AppSettings.textColors[i]
            val sel = AppSettings.textIndex(this) == i
            val circle = View(this)
            circle.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke(dp(if (sel) 3 else 1), if (sel) accent else (fg and 0x00FFFFFF) or 0x55000000)
            }
            circle.setOnClickListener {
                if (AppSettings.textIndex(this) != i) {
                    AppSettings.setTextIndex(this, i)
                    recreate()
                }
            }
            val lp = LinearLayout.LayoutParams(dp(48), dp(48))
            lp.marginEnd = dp(14)
            colorRow.addView(circle, lp)
        }
        colorScroll.addView(colorRow)
        root.addView(colorScroll)

        val page = ScrollView(this)
        page.isFillViewport = true
        page.fitsSystemWindows = true
        page.addView(root)
        setContentView(page)
    }

    // ব্যাকগ্রাউন্ড বাছাই: সিস্টেম ওয়ালপেপার, অ্যাপের ছবি, নিজের আপলোড
    private fun backgroundStrip(accent: Int, fg: Int): View {
        val dark = Backgrounds.isDark(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val title = TextView(this)
        title.setText(R.string.theme_background)
        title.setTextColor(fg)
        title.textSize = 16f
        title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
        title.setPadding(dp(20), dp(12), dp(20), dp(6))
        box.addView(title)

        val scroll = HorizontalScrollView(this)
        scroll.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(dp(17), dp(4), dp(17), dp(8))
        val sel = Backgrounds.selected(this)
        val faint = (fg and 0x00FFFFFF) or 0x33000000

        fun tile(key: String?, label: Int, bmp: Bitmap?, onClick: () -> Unit, onLong: (() -> Unit)?) {
            val on = key != null && key == sel
            val t = FrameLayout(this)
            t.setPadding(dp(3), dp(3), dp(3), dp(3))
            t.background = round(Color.TRANSPARENT, 14, if (on) accent else faint, if (on) 3 else 1)
            if (bmp != null) {
                val iv = ImageView(this)
                iv.scaleType = ImageView.ScaleType.CENTER_CROP
                iv.setImageBitmap(bmp)
                iv.background = round(Color.DKGRAY, 11)
                iv.clipToOutline = true
                t.addView(iv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            } else {
                val tv = TextView(this)
                tv.setText(label)
                tv.setTextColor(fg)
                tv.textSize = 11f
                tv.gravity = Gravity.CENTER
                t.addView(tv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            t.setOnClickListener { onClick() }
            if (onLong != null) {
                t.setOnLongClickListener {
                    onLong()
                    true
                }
            }
            val lp = LinearLayout.LayoutParams(dp(68), dp(104))
            lp.marginEnd = dp(10)
            row.addView(t, lp)
        }

        fun choose(key: String) {
            Backgrounds.select(this, key)
            afterBgChange()
        }

        // ট্রান্সপারেন্ট মোডে "ডিফল্ট/সিস্টেম" ঘর নেই
        if (!AppSettings.glass(this)) {
            tile(Backgrounds.NONE, R.string.bg_default, null, { choose(Backgrounds.NONE) }, null)
        }
        for (k in Backgrounds.assetKeys(this)) {
            // ডার্ক মোডে সাদাটে ওয়ালপেপার দেখানো/বাছা যাবে না
            if (dark && k == Backgrounds.WHITE) continue
            tile(k, 0, Backgrounds.decode(this, k, 220), { choose(k) }, null)
        }
        for (k in Backgrounds.customKeys(this)) {
            tile(k, 0, Backgrounds.decode(this, k, 220), { choose(k) }, {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.bg_remove_q)
                    .setPositiveButton(R.string.btn_ok) { _, _ ->
                        Backgrounds.removeCustom(this, k)
                        afterBgChange()
                    }
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show()
            })
        }
        tile(null, R.string.bg_upload, null, { picker.launch("image/*") }, null)
        scroll.addView(row)
        box.addView(scroll)

        // ব্লার কম/বেশি করার লাইন (স্লাইডার) — ছবি বাছা থাকলে দেখায়
        if (sel != Backgrounds.NONE) {
            val label = TextView(this)
            label.setTextColor(fg)
            label.textSize = 14f
            label.setPadding(dp(20), dp(8), dp(20), 0)
            fun setLabel(v: Int) {
                label.text = getString(R.string.theme_blur) + ": " + v + "%"
            }
            setLabel(Backgrounds.blur(this))
            box.addView(label)
            val bar = android.widget.SeekBar(this)
            bar.max = 100
            bar.progress = Backgrounds.blur(this)
            bar.progressTintList = android.content.res.ColorStateList.valueOf(accent)
            bar.thumbTintList = android.content.res.ColorStateList.valueOf(accent)
            bar.setPadding(dp(32), dp(8), dp(32), dp(8))
            bar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                    setLabel(p)
                    if (fromUser) {
                        Backgrounds.setBlur(this@ThemeActivity, p)
                        Backgrounds.apply(this@ThemeActivity)
                    }
                }
                override fun onStartTrackingTouch(s: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(s: android.widget.SeekBar?) {}
            })
            box.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
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
