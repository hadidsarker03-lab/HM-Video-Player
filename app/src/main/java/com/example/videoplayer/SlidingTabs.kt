package com.example.videoplayer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Typeface
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
        slider.setBackgroundResource(R.drawable.bg_tab_on)
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
            iv.setColorFilter(UiKit.textColor(context))
        }
        tv.setText(text)
        tv.textSize = textSp
        tv.setTextColor(UiKit.textColor(context))
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
