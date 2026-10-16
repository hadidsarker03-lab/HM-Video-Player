package com.example.videoplayer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

// গ্লাস স্লাইডিং ট্যাব: প্রতিটা ট্যাব উঁচু গ্লাস বোতাম; নির্বাচিতটা একটা ভেতরে-বসা স্লট, যেটা
// ট্যাব বদলালে বাউন্সি অ্যানিমেশনে (cubic-bezier .5,1.4,.5,1) পাশের ট্যাবে সরে যায়।
class SlidingTabs @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : FrameLayout(ctx, attrs) {

    class Item(val itemId: Int, val label: Int, val icon: Int = 0)

    private val row = LinearLayout(ctx)
    private val slider = LinearLayout(ctx)
    private val sIcon = ImageView(ctx)
    private val sText = TextView(ctx)
    private var items: List<Item> = emptyList()
    private val cells = mutableListOf<LinearLayout>()
    private var index = 0
    private var stacked = false
    private var textSp = 16f
    private var listener: ((Item) -> Boolean)? = null
    private var anim: ValueAnimator? = null
    private var curLeft = 0f
    private var curWidth = 0f

    init {
        clipChildren = false
        clipToPadding = false
        row.orientation = LinearLayout.HORIZONTAL
        addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        slider.background = SunkenDrawable(ctx)
        slider.gravity = Gravity.CENTER
        slider.visibility = View.INVISIBLE
        addView(slider, LayoutParams(0, 0))
        // সারির মাপ বদলালে স্লট ঠিক জায়গায় বসে
        row.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> placeNow() }
    }

    // ---- compat API (আগের BottomNavigationView-এর মতো) ----
    var selectedItemId: Int
        get() = items.getOrNull(index)?.itemId ?: 0
        set(v) {
            val i = items.indexOfFirst { it.itemId == v }
            if (i >= 0) select(i, animate = isLaidOut)
        }

    fun setOnItemSelectedListener(l: (Item) -> Boolean) { listener = l }

    fun setItems(newItems: List<Item>, selected: Int, stacked: Boolean = false, textSp: Float = 16f) {
        val same = newItems.size == items.size &&
            newItems.indices.all { newItems[it].itemId == items[it].itemId && newItems[it].label == items[it].label } &&
            stacked == this.stacked
        if (same) {
            select(selected, animate = isLaidOut)
            return
        }
        this.items = newItems
        this.stacked = stacked
        this.textSp = textSp
        build()
        select(selected, animate = false)
    }

    private fun content(parent: LinearLayout, icon: Int, text: Int, tv: TextView, iv: ImageView) {
        parent.orientation = if (stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        parent.gravity = Gravity.CENTER
        iv.visibility = if (icon != 0) View.VISIBLE else View.GONE
        if (icon != 0) {
            iv.setImageResource(icon)
            iv.setColorFilter(UiKit.boxTextColor(context))
        }
        tv.setText(text)
        tv.textSize = textSp
        tv.setTextColor(UiKit.boxTextColor(context))
        tv.setSingleLine(true)
        tv.ellipsize = null
        tv.includeFontPadding = true
        tv.typeface = Typeface.DEFAULT
        if (stacked) tv.textSize = 12f
    }

    private fun build() {
        row.removeAllViews()
        cells.clear()
        val d = { v: Int -> UiKit.dp(context, v) }
        items.forEachIndexed { i, it ->
            val cell = LinearLayout(context)
            val iv = ImageView(context)
            val tv = TextView(context)
            cell.addView(iv, LinearLayout.LayoutParams(d(24), d(24)))
            cell.addView(tv, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            content(cell, it.icon, it.label, tv, iv)
            if (stacked) cell.setPadding(d(10), d(10), d(10), d(8)) else cell.setPadding(d(18), d(9), d(18), d(9))
            cell.setBackgroundResource(R.drawable.bg_tab)
            cell.setOnClickListener { choose(i) }
            Anim.pressable(cell, 0.95f)
            val lp = if (stacked)
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { setMargins(d(14), d(6), d(14), d(6)) }
            else LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = d(8); topMargin = d(6); bottomMargin = d(6) }
            row.addView(cell, lp)
            cells += cell
        }
        // স্লটের ভেতরের লেখা/আইকন
        slider.removeAllViews()
        slider.addView(sIcon, LinearLayout.LayoutParams(d(24), d(24)))
        slider.addView(sText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content(slider, 0, items[0].label, sText, sIcon)
    }

    private fun choose(i: Int) {
        if (i == index) return
        val ok = listener?.invoke(items[i]) ?: true
        if (ok) select(i, animate = true)
    }

    // নির্বাচিত ট্যাব বদলায়: পুরোনোটা আবার উঁচু বোতাম, নতুনটার জায়গায় স্লট স্লাইড করে আসে
    fun select(i: Int, animate: Boolean) {
        if (items.isEmpty()) return
        index = i.coerceIn(0, items.size - 1)
        cells.forEachIndexed { k, c ->
            val active = k == index
            c.setBackgroundResource(if (active) 0 else R.drawable.bg_tab)
            for (n in 0 until c.childCount) c.getChildAt(n).alpha = if (active) 0f else 1f
            c.isSelected = active
        }
        val it = items[index]
        content(slider, it.icon, it.label, sText, sIcon)
        val cell = cells[index]
        if (cell.width == 0) { post { placeNow() }; return }
        val toL = cell.left.toFloat()
        val toW = cell.width.toFloat()
        anim?.cancel()
        if (!animate || slider.visibility != View.VISIBLE) {
            sIcon.alpha = 1f
            sText.alpha = 1f
            apply(toL, toW, cell)
            return
        }
        val fromL = curLeft
        val fromW = curWidth
        slider.layoutParams = (slider.layoutParams as LayoutParams).also {
            it.height = cell.height
            it.topMargin = cell.top
        }
        sIcon.alpha = 0f
        sText.alpha = 0f
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 340
            // নরম ডিসেলারেট (কোনো ঝাঁকুনি/ওভারশুট নেই), লেখা ধীরে ফুটে ওঠে
            interpolator = PathInterpolator(0.22f, 1f, 0.36f, 1f)
            addUpdateListener { a ->
                val f = a.animatedValue as Float
                apply(fromL + (toL - fromL) * f, (fromW + (toW - fromW) * f).coerceAtLeast(0f), cell, false)
                val t = ((f - 0.3f) / 0.7f).coerceIn(0f, 1f)
                sIcon.alpha = t
                sText.alpha = t
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(an: Animator) { sIcon.alpha = 1f; sText.alpha = 1f }
                override fun onAnimationCancel(an: Animator) { sIcon.alpha = 1f; sText.alpha = 1f }
            })
            start()
        }
    }

    private fun placeNow() {
        if (items.isEmpty() || cells.isEmpty()) return
        val cell = cells[index.coerceIn(0, cells.size - 1)]
        if (cell.width == 0) return
        if (anim?.isRunning == true) return
        apply(cell.left.toFloat(), cell.width.toFloat(), cell)
    }

    private fun apply(left: Float, width: Float, cell: View, resizeHeight: Boolean = true) {
        curLeft = left
        curWidth = width
        val lp = slider.layoutParams as LayoutParams
        lp.leftMargin = left.toInt()
        lp.width = width.toInt()
        if (resizeHeight) {
            lp.height = cell.height
            lp.topMargin = cell.top
        }
        slider.layoutParams = lp
        slider.visibility = View.VISIBLE
    }
}


// ভেতরে-বসা (debossed) স্লট: ওপর-বাঁয়ে গাঢ় ভেতরের ছায়া, নিচ-ডানে হালকা আলো, ধার ঘেঁষে সরু রেখা।
// গভীরতা থিম পেজের স্লাইডার (AppSettings.depth) থেকে সরাসরি নিয়ন্ত্রিত হয়।
class SunkenDrawable(private val ctx: Context) : Drawable() {
    private val dens = ctx.resources.displayMetrics.density
    private val path = Path()
    private val rect = RectF()
    private val rr = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shade = Paint()
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        val d = AppSettings.depth(ctx)
        rect.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        val r = minOf(rect.width(), rect.height()) / 2f
        path.reset()
        path.addRoundRect(rect, r, r, Path.Direction.CW)
        val alt = ContextCompat.getColor(ctx, R.color.surface_alt)
        val dark = ColorUtils.calculateLuminance(alt) < 0.5
        val k = if (dark) 1f else 0.7f

        c.save()
        c.clipPath(path)
        fill.color = ColorUtils.blendARGB(alt, Color.BLACK, (0.10f + 0.32f * d) * k)
        c.drawRect(rect, fill)

        val band = minOf((6f + 16f * d) * dens, rect.height() * 0.46f)
        val dk = Color.argb((235 * d * k).toInt(), 0, 0, 0)
        shade.shader = LinearGradient(0f, rect.top, 0f, rect.top + band, dk, 0, Shader.TileMode.CLAMP)
        c.drawRect(rect.left, rect.top, rect.right, rect.top + band, shade)
        shade.shader = LinearGradient(rect.left, 0f, rect.left + band, 0f, dk, 0, Shader.TileMode.CLAMP)
        c.drawRect(rect.left, rect.top, rect.left + band, rect.bottom, shade)

        val band2 = band * 0.55f
        val lt = Color.argb(((if (dark) 52 else 140) * d).toInt(), 255, 255, 255)
        shade.shader = LinearGradient(0f, rect.bottom, 0f, rect.bottom - band2, lt, 0, Shader.TileMode.CLAMP)
        c.drawRect(rect.left, rect.bottom - band2, rect.right, rect.bottom, shade)
        shade.shader = LinearGradient(rect.right, 0f, rect.right - band2, 0f, lt, 0, Shader.TileMode.CLAMP)
        c.drawRect(rect.right - band2, rect.top, rect.right, rect.bottom, shade)
        shade.shader = null
        c.restore()

        rim.strokeWidth = 1.2f * dens
        rim.color = Color.argb((130 * d).toInt(), 0, 0, 0)
        rr.set(rect)
        rr.inset(0.6f * dens, 0.6f * dens)
        c.drawRoundRect(rr, r, r, rim)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
