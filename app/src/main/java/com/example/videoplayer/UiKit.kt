package com.example.videoplayer

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

// একাধিক স্ক্রিনে ব্যবহৃত UI সহায়ক ফাংশন
object UiKit {

    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun ripple(ctx: Context): Int {
        val tv = TypedValue()
        ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        return tv.resourceId
    }

    // সিস্টেম বার স্বচ্ছ; হেডার স্ট্যাটাস বারের নিচে নামে
    fun applyInsets(activity: AppCompatActivity, root: View, header: View, padBottom: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val baseTop = header.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, 0, bars.right, if (padBottom) bars.bottom else 0)
            header.setPadding(
                header.paddingLeft, baseTop + bars.top, header.paddingRight, header.paddingBottom
            )
            insets
        }
    }

    // ---------- সারি (আইকন + শিরোনাম + সাবটাইটেল) ----------
    fun row(
        ctx: Context, parent: ViewGroup, icon: Int, title: String, sub: String?,
        onClick: (() -> Unit)?
    ): View {
        val v = LayoutInflater.from(ctx).inflate(R.layout.item_row, parent, false)
        v.findViewById<ImageView>(R.id.rowIcon).setImageResource(icon)
        v.findViewById<TextView>(R.id.rowTitle).text = title
        val s = v.findViewById<TextView>(R.id.rowSub)
        if (sub.isNullOrEmpty()) s.visibility = View.GONE else s.text = sub
        val chevron = v.findViewById<View>(R.id.rowChevron)
        if (onClick != null) {
            v.setOnClickListener { onClick() }
        } else {
            chevron.visibility = View.GONE
            v.isClickable = false
        }
        parent.addView(v)
        return v
    }

    fun switchRow(
        ctx: Context, parent: ViewGroup, icon: Int, title: String, sub: String?,
        checked: Boolean, onChange: (Boolean) -> Unit
    ) {
        val v = row(ctx, parent, icon, title, sub, null)
        v.findViewById<View>(R.id.rowChevron).visibility = View.GONE
        val sw = MaterialSwitch(ctx)
        sw.isChecked = checked
        sw.setOnCheckedChangeListener { _, b -> onChange(b) }
        v.findViewById<FrameLayout>(R.id.rowTrailing).addView(sw)
        v.isClickable = true
        v.setOnClickListener { sw.toggle() }
    }

    // ---------- নাম ----------
    fun languageName(c: Context) = if (AppSettings.lang(c) == "bn") "বাংলা" else "English"

    fun themeName(c: Context): String = c.getString(
        when (AppSettings.themeMode(c)) {
            "light" -> R.string.mode_light
            "dark" -> R.string.mode_dark
            else -> R.string.mode_system
        }
    )

    fun layoutName(c: Context): String = c.getString(
        when (AppSettings.viewMode(c)) {
            AppSettings.VIEW_GRID -> R.string.layout_grid
            AppSettings.VIEW_COMPACT -> R.string.layout_compact
            else -> R.string.layout_list
        }
    )

    fun orientationName(c: Context): String = c.getString(
        when (AppSettings.rotation(c)) {
            1 -> R.string.rot_landscape
            2 -> R.string.rot_portrait
            else -> R.string.rot_auto
        }
    )

    // ---------- বাছাইয়ের ডায়ালগ ----------
    private fun choose(
        act: AppCompatActivity, title: Int, labels: List<String>, checked: Int,
        onPick: (Int) -> Unit
    ) {
        MaterialAlertDialogBuilder(act)
            .setTitle(title)
            .setSingleChoiceItems(labels.toTypedArray(), checked) { d, which ->
                d.dismiss()
                onPick(which)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    fun chooseLanguage(act: AppCompatActivity, onDone: () -> Unit = {}) {
        val codes = listOf("en", "bn")
        val cur = codes.indexOf(AppSettings.lang(act)).coerceAtLeast(0)
        choose(act, R.string.settings_language, listOf("English", "বাংলা"), cur) { i ->
            AppSettings.setLang(act, codes[i])
            onDone()
        }
    }

    // 3-dot মেনু: প্রতিটা লেখা আলাদা বর্ডার (Neumorphism) বক্সে
    fun borderedMenu(
        act: AppCompatActivity, anchor: View, items: List<Pair<Int, Int>>, onPick: (Int) -> Unit
    ) {
        val box = android.widget.LinearLayout(act)
        box.orientation = android.widget.LinearLayout.VERTICAL
        box.setPadding(dp(act, 6), dp(act, 6), dp(act, 6), dp(act, 6))
        val scroll = android.widget.ScrollView(act)
        scroll.addView(box)
        val pw = android.widget.PopupWindow(
            scroll, dp(act, 190), ViewGroup.LayoutParams.WRAP_CONTENT, true
        )
        pw.setBackgroundDrawable(
            android.graphics.drawable.GradientDrawable().apply {
                setColor(com.google.android.material.color.MaterialColors.getColor(
                    act, com.google.android.material.R.attr.colorSurfaceContainer, 0xFFE6EAF1.toInt()))
                cornerRadius = dp(act, 22).toFloat()
            }
        )
        pw.elevation = dp(act, 8).toFloat()
        for ((id, res) in items) {
            val tv = TextView(act)
            tv.setText(res)
            tv.textSize = 14f
            tv.setTextColor(textColor(act))
            tv.gravity = android.view.Gravity.CENTER_VERTICAL
            tv.setBackgroundResource(R.drawable.bg_tab)
            tv.setPadding(dp(act, 18), dp(act, 11), dp(act, 18), dp(act, 11))
            tv.setOnClickListener {
                pw.dismiss()
                onPick(id)
            }
            val lp = android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, dp(act, 2), 0, dp(act, 2))
            box.addView(tv, lp)
        }
        val maxH = (act.resources.displayMetrics.heightPixels * 0.8f).toInt()
        scroll.measure(
            View.MeasureSpec.makeMeasureSpec(dp(act, 190), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        pw.height = minOf(scroll.measuredHeight, maxH)
        pw.showAsDropDown(anchor, 0, 0, android.view.Gravity.END)
    }


    // সাজানোর মেনু: প্রতিটা লেখা আলাদা বর্ডার বক্সে + ডানে রেডিও চিহ্ন
    fun borderedRadioMenu(
        act: AppCompatActivity, anchor: View, labels: List<Int>, selected: Int, onPick: (Int) -> Unit
    ) {
        val w = dp(act, 250)
        val box = android.widget.LinearLayout(act)
        box.orientation = android.widget.LinearLayout.VERTICAL
        box.setPadding(dp(act, 8), dp(act, 8), dp(act, 8), dp(act, 8))
        val scroll = android.widget.ScrollView(act)
        scroll.addView(box)
        val pw = android.widget.PopupWindow(scroll, w, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        pw.setBackgroundDrawable(
            android.graphics.drawable.GradientDrawable().apply {
                setColor(com.google.android.material.color.MaterialColors.getColor(
                    act, com.google.android.material.R.attr.colorSurfaceContainer, 0xFFE6EAF1.toInt()))
                cornerRadius = dp(act, 22).toFloat()
                setStroke(dp(act, 2), com.google.android.material.color.MaterialColors.getColor(
                    act, R.attr.hmDivider, 0x55888888))
            }
        )
        pw.elevation = dp(act, 8).toFloat()
        labels.forEachIndexed { i, res ->
            val row = android.widget.LinearLayout(act)
            row.orientation = android.widget.LinearLayout.HORIZONTAL
            row.gravity = android.view.Gravity.CENTER_VERTICAL
            row.setBackgroundResource(R.drawable.bg_tab)
            row.setPadding(dp(act, 18), dp(act, 12), dp(act, 16), dp(act, 12))
            val tv = TextView(act)
            tv.setText(res)
            tv.textSize = 14f
            tv.setTextColor(textColor(act))
            row.addView(tv, android.widget.LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val dot = View(act)
            dot.setBackgroundResource(if (i == selected) R.drawable.bg_radio_on else R.drawable.bg_radio_off)
            row.addView(dot, android.widget.LinearLayout.LayoutParams(dp(act, 24), dp(act, 24)))
            row.setOnClickListener {
                pw.dismiss()
                onPick(i)
            }
            val lp = android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, dp(act, 3), 0, dp(act, 3))
            box.addView(row, lp)
        }
        val maxH = (act.resources.displayMetrics.heightPixels * 0.8f).toInt()
        scroll.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        pw.height = minOf(scroll.measuredHeight, maxH)
        pw.showAsDropDown(anchor, 0, 0, android.view.Gravity.END)
    }

    // বর্তমান থিমের টেক্সট রং (থিম পেজ থেকে বাছা)
    fun textColor(ctx: Context): Int = com.google.android.material.color.MaterialColors.getColor(
        ctx, R.attr.hmText,
        androidx.core.content.ContextCompat.getColor(ctx, R.color.text_primary)
    )

    // বক্সের (ট্যাব, বোতাম, চিপ) ভিতরের লেখা/আইকনের রং
    fun boxTextColor(ctx: Context): Int = com.google.android.material.color.MaterialColors.getColor(
        ctx, R.attr.hmBoxText, textColor(ctx)
    )

    // বর্তমান থিমের অ্যাকসেন্ট রং
    fun accent(ctx: Context): Int = com.google.android.material.color.MaterialColors.getColor(
        ctx, com.google.android.material.R.attr.colorPrimary,
        androidx.core.content.ContextCompat.getColor(ctx, R.color.accent)
    )

    // থিম পেজ খোলে (ডার্ক/লাইট/সিস্টেম, অ্যাকসেন্ট রং, লেআউট প্রিভিউ)
    fun chooseTheme(act: AppCompatActivity, onDone: () -> Unit = {}) {
        act.startActivity(android.content.Intent(act, ThemeActivity::class.java))
    }

    private fun chooseThemeOld(act: AppCompatActivity, onDone: () -> Unit = {}) {
        val modes = listOf("system", "light", "dark")
        val labels = listOf(
            act.getString(R.string.mode_system),
            act.getString(R.string.mode_light),
            act.getString(R.string.mode_dark)
        )
        val cur = modes.indexOf(AppSettings.themeMode(act)).coerceAtLeast(0)
        choose(act, R.string.settings_theme, labels, cur) { i ->
            AppSettings.setThemeMode(act, modes[i])
            onDone()
        }
    }

    fun chooseLayout(act: AppCompatActivity, onDone: () -> Unit = {}) {
        val labels = listOf(
            act.getString(R.string.layout_list),
            act.getString(R.string.layout_grid),
            act.getString(R.string.layout_compact)
        )
        choose(act, R.string.settings_layout, labels, AppSettings.viewMode(act)) { i ->
            AppSettings.setViewMode(act, i)
            onDone()
        }
    }

    fun chooseOrientation(act: AppCompatActivity, onDone: () -> Unit = {}) {
        val labels = listOf(
            act.getString(R.string.rot_auto),
            act.getString(R.string.rot_landscape),
            act.getString(R.string.rot_portrait)
        )
        choose(act, R.string.row_orientation, labels, AppSettings.rotation(act)) { i ->
            AppSettings.setRotation(act, i)
            onDone()
        }
    }
}
