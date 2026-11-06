package com.example.videoplayer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ভার্টিক্যাল স্লাইডার (ইকুয়ালাইজারের ব্যান্ড)
class EqSlider(ctx: Context) : View(ctx) {
    var minV = -1500
    var maxV = 1500
    var accent = Color.parseColor("#FF8A1A")
    var onChange: ((Int) -> Unit)? = null
    var value = 0
        set(v) {
            field = v.coerceIn(minV, maxV)
            invalidate()
        }

    private val d = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#33808080") }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#23262B") }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f * d }

    private fun padV() = 16f * d
    private fun yFor(v: Int): Float {
        val top = padV()
        val bottom = height - padV()
        return top + (maxV - v).toFloat() / (maxV - minV) * (bottom - top)
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val tw = 5f * d
        c.drawRoundRect(RectF(cx - tw, padV(), cx + tw, height - padV()), tw, tw, trackPaint)
        val y = yFor(value)
        val y0 = yFor(0)
        fillPaint.color = accent
        c.drawRoundRect(RectF(cx - tw, min(y, y0), cx + tw, max(y, y0)), tw, tw, fillPaint)
        val kr = 13f * d
        c.drawCircle(cx, y, kr, knobPaint)
        linePaint.color = accent
        for (k in -1..1) {
            c.drawLine(cx - 5f * d, y + k * 4f * d, cx + 5f * d, y + k * 4f * d, linePaint)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val top = padV()
                val bottom = height - padV()
                val f = ((e.y - top) / (bottom - top)).coerceIn(0f, 1f)
                value = (maxV - f * (maxV - minV)).roundToInt()
                onChange?.invoke(value)
            }
        }
        return true
    }
}

// ঘোরানো নব (এনহান্সার)
class KnobView(ctx: Context) : View(ctx) {
    var maxV = 1000
    var accent = Color.parseColor("#FF8A1A")
    var onChange: ((Int) -> Unit)? = null
    var value = 0
        set(v) {
            field = v.coerceIn(0, maxV)
            invalidate()
        }

    private val d = resources.displayMetrics.density
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * d
        strokeCap = Paint.Cap.ROUND
    }
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3A3D42") }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastY = 0f

    override fun onDraw(c: Canvas) {
        val s = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val r = s / 2f - 6f * d
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        ring.color = Color.parseColor("#33808080")
        c.drawArc(rect, 135f, 270f, false, ring)
        ring.color = accent
        c.drawArc(rect, 135f, 270f * value / maxV, false, ring)
        c.drawCircle(cx, cy, r - 9f * d, body)
        val ang = Math.toRadians((135f + 270f * value / maxV).toDouble())
        val pr = r - 20f * d
        dot.color = accent
        c.drawCircle(cx + (pr * Math.cos(ang)).toFloat(), cy + (pr * Math.sin(ang)).toFloat(), 3.5f * d, dot)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastY = e.y
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val delta = (lastY - e.y) / (180f * d) * maxV
                lastY = e.y
                value = (value + delta).roundToInt()
                onChange?.invoke(value)
            }
        }
        return true
    }
}

object EqSheet {

    private fun dp(c: Context, v: Int) = UiKit.dp(c, v)

    fun show(act: AppCompatActivity, onDone: () -> Unit) {
        if (!AudioFx.eqSupported()) {
            Toast.makeText(act, R.string.eq_unsupported, Toast.LENGTH_SHORT).show()
            return
        }
        val accent = UiKit.accent(act)
        val fg = MaterialColors.getColor(act, com.google.android.material.R.attr.colorOnSurface, Color.WHITE)
        val sub = ColorUtils.setAlphaComponent(fg, 150)
        val prefs = AppSettings.prefs(act)

        val dialog = BottomSheetDialog(act)
        val root = LinearLayout(act)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(act, 18), dp(act, 16), dp(act, 18), dp(act, 24))

        // শিরোনাম + অন/অফ
        val head = LinearLayout(act)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(act)
        title.setText(R.string.eq_title)
        title.setTextColor(fg)
        title.textSize = 21f
        title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
        head.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = MaterialSwitch(act)
        sw.isChecked = AppSettings.eqOn(act)
        sw.setOnCheckedChangeListener { _, on ->
            AppSettings.setEqOn(act, on)
            AudioFx.applyAll(act)
        }
        head.addView(sw)
        root.addView(head)

        // প্রিসেট চিপ
        val presetNames = AudioFx.presets()
        val chips = ArrayList<TextView>()
        val chipScroll = HorizontalScrollView(act)
        chipScroll.isHorizontalScrollBarEnabled = false
        val chipRow = LinearLayout(act)
        chipRow.orientation = LinearLayout.HORIZONTAL
        chipRow.setPadding(0, dp(act, 12), 0, dp(act, 6))
        chipScroll.addView(chipRow)
        root.addView(chipScroll)

        val sliders = ArrayList<EqSlider>()
        val dbLabels = ArrayList<TextView>()

        fun dbText(v: Int): String {
            val db = v / 100
            return "$db dB"
        }

        fun refreshChips() {
            val cur = prefs.getInt("eq_preset", -1)
            for (i in chips.indices) {
                val on = (i - 1) == cur
                val chip = chips[i]
                chip.setTextColor(if (on) Color.WHITE else fg)
                chip.background = GradientDrawable().apply {
                    cornerRadius = dp(act, 20).toFloat()
                    if (on) setColor(accent) else {
                        setColor(Color.TRANSPARENT)
                        setStroke(dp(act, 1), sub)
                    }
                }
            }
        }

        fun reloadBands() {
            val bands = AudioFx.bands()
            for (i in sliders.indices) {
                val lv = bands.getOrNull(i)?.level ?: 0
                sliders[i].value = lv
                dbLabels[i].text = dbText(lv)
            }
        }

        fun addChip(label: String, idx: Int) {
            val t = TextView(act)
            t.text = label
            t.textSize = 14f
            t.setPadding(dp(act, 16), dp(act, 8), dp(act, 16), dp(act, 8))
            t.setOnClickListener {
                if (idx >= 0) {
                    AudioFx.usePreset(act, idx)
                    if (!sw.isChecked) sw.isChecked = true
                    reloadBands()
                }
                prefs.edit().putInt("eq_preset", idx).apply()
                refreshChips()
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = dp(act, 8)
            chipRow.addView(t, lp)
            chips.add(t)
        }
        addChip(act.getString(R.string.eq_custom), -1)
        for (i in presetNames.indices) addChip(presetNames[i], i)

        // ব্যান্ড স্লাইডার
        val range = AudioFx.bandRange()
        val bandRow = LinearLayout(act)
        bandRow.orientation = LinearLayout.HORIZONTAL
        bandRow.setPadding(0, dp(act, 10), 0, 0)
        for (b in AudioFx.bands()) {
            val col = LinearLayout(act)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER_HORIZONTAL
            val db = TextView(act)
            db.text = dbText(b.level)
            db.setTextColor(accent)
            db.textSize = 12f
            db.gravity = Gravity.CENTER
            col.addView(db)
            val s = EqSlider(act)
            s.minV = range[0]
            s.maxV = range[1]
            s.accent = accent
            s.value = b.level
            s.onChange = { v ->
                AudioFx.setBand(act, b.index, v)
                db.text = dbText(v)
                if (!sw.isChecked) sw.isChecked = true
                if (prefs.getInt("eq_preset", -1) != -1) {
                    prefs.edit().putInt("eq_preset", -1).apply()
                    refreshChips()
                }
            }
            col.addView(s, LinearLayout.LayoutParams(dp(act, 44), dp(act, 230)))
            val hz = TextView(act)
            hz.text = if (b.freqHz >= 1000) {
                val k = b.freqHz / 1000f
                (if (k == k.toInt().toFloat()) k.toInt().toString() else String.format("%.1f", k)) + " kHz"
            } else "${b.freqHz} Hz"
            hz.setTextColor(sub)
            hz.textSize = 11f
            hz.gravity = Gravity.CENTER
            col.addView(hz)
            sliders.add(s)
            dbLabels.add(db)
            bandRow.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(bandRow)
        refreshChips()

        // রিভার্ব
        val reverbRow = LinearLayout(act)
        reverbRow.orientation = LinearLayout.HORIZONTAL
        reverbRow.gravity = Gravity.CENTER_VERTICAL
        reverbRow.setPadding(0, dp(act, 18), 0, dp(act, 6))
        val rl = TextView(act)
        rl.setText(R.string.eq_reverb)
        rl.setTextColor(fg)
        rl.textSize = 17f
        reverbRow.addView(rl, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val rv = TextView(act)
        val names = AudioFx.reverbNames()
        rv.text = names[AppSettings.fx(act, "reverb").coerceIn(0, names.size - 1)] + "  ▾"
        rv.setTextColor(fg)
        rv.textSize = 14f
        rv.setPadding(dp(act, 16), dp(act, 8), dp(act, 16), dp(act, 8))
        rv.background = GradientDrawable().apply {
            cornerRadius = dp(act, 18).toFloat()
            setColor(ColorUtils.setAlphaComponent(fg, 28))
        }
        rv.alpha = if (AudioFx.reverbSupported()) 1f else 0.4f
        rv.setOnClickListener {
            if (!AudioFx.reverbSupported()) return@setOnClickListener
            MaterialAlertDialogBuilder(act)
                .setTitle(R.string.eq_reverb)
                .setSingleChoiceItems(
                    names.toTypedArray(), AppSettings.fx(act, "reverb").coerceIn(0, names.size - 1)
                ) { d, which ->
                    AudioFx.setReverb(act, which)
                    rv.text = names[which] + "  ▾"
                    d.dismiss()
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }
        reverbRow.addView(rv)
        root.addView(reverbRow)

        // এনহান্সার
        val enhHead = LinearLayout(act)
        enhHead.orientation = LinearLayout.HORIZONTAL
        enhHead.gravity = Gravity.CENTER_VERTICAL
        enhHead.setPadding(0, dp(act, 10), 0, dp(act, 6))
        val el = TextView(act)
        el.setText(R.string.eq_enhancer)
        el.setTextColor(fg)
        el.textSize = 17f
        enhHead.addView(el, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val reset = TextView(act)
        reset.setText(R.string.eq_reset_all)
        reset.setTextColor(accent)
        reset.textSize = 14f
        reset.setPadding(dp(act, 10), dp(act, 6), dp(act, 10), dp(act, 6))
        reset.setOnClickListener {
            for (b in AudioFx.bands()) AudioFx.setBand(act, b.index, 0)
            AppSettings.setFx(act, "bass", 0)
            AppSettings.setFx(act, "virt", 0)
            AppSettings.setFx(act, "loud", 0)
            AudioFx.setReverb(act, 0)
            prefs.edit().putInt("eq_preset", -1).apply()
            dialog.dismiss()
            show(act, onDone)
        }
        enhHead.addView(reset)
        root.addView(enhHead)

        val knobRow = LinearLayout(act)
        knobRow.orientation = LinearLayout.HORIZONTAL
        fun addKnob(label: Int, key: String, maxV: Int, supported: Boolean) {
            val col = LinearLayout(act)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER_HORIZONTAL
            val k = KnobView(act)
            k.maxV = maxV
            k.accent = accent
            k.value = AppSettings.fx(act, key)
            k.alpha = if (supported) 1f else 0.4f
            k.onChange = { v ->
                if (supported) {
                    AppSettings.setFx(act, key, v)
                    AudioFx.applyAll(act)
                }
            }
            col.addView(k, LinearLayout.LayoutParams(dp(act, 84), dp(act, 84)))
            val t = TextView(act)
            t.setText(label)
            t.setTextColor(sub)
            t.textSize = 12f
            t.gravity = Gravity.CENTER
            col.addView(t)
            knobRow.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        addKnob(R.string.fx_bass, "bass", 1000, AudioFx.bassSupported())
        addKnob(R.string.fx_virtual, "virt", 1000, AudioFx.virtSupported())
        addKnob(R.string.fx_loud, "loud", 1500, AudioFx.loudSupported())
        root.addView(knobRow)

        val scroll = ScrollView(act)
        scroll.addView(root)
        dialog.setContentView(scroll)
        dialog.setOnDismissListener { onDone() }
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.show()
    }
}
