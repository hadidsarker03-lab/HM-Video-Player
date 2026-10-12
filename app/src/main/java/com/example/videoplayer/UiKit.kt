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

    fun chooseTheme(act: AppCompatActivity, onDone: () -> Unit = {}) {
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
