package com.example.videoplayer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat

// ভলিউম/ব্রাইটনেসের উল্লম্ব স্লাইডার-পিল: নিচ থেকে ভরে ওঠে, নিচে আইকন; পাশ থেকে স্লাইড করে আসে ও যায়
class LevelPill @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    private val d = resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD91E1F22.toInt() }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x38FFFFFF }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x38FFFFFF; strokeWidth = 1.5f * d }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER
        textSize = 13f * resources.displayMetrics.scaledDensity; isFakeBoldText = true
    }
    private val rect = RectF()
    private var icon: Drawable? = null
    private var shown = 0f      // অ্যানিমেটেড মান 0..1
    private var target = 0f
    private var anim: ValueAnimator? = null
    private var fromEnd = true  // ডান পাশ থেকে আসবে কি না

    fun setSide(end: Boolean) { fromEnd = end }

    fun setIcon(res: Int) {
        icon = ContextCompat.getDrawable(context, res)?.mutate()?.also { it.setTint(0xFFFFFFFF.toInt()) }
        invalidate()
    }

    // মান বদলালে ভরাট মসৃণভাবে ওঠানামা করে
    fun setLevel(v: Float) {
        target = v.coerceIn(0f, 1f)
        anim?.cancel()
        anim = ValueAnimator.ofFloat(shown, target).apply {
            duration = 80
            interpolator = DecelerateInterpolator()
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    // পাশ থেকে স্লাইড করে ঢোকে
    fun slideIn() {
        animate().cancel()
        if (visibility != VISIBLE) {
            visibility = VISIBLE
            alpha = 0f
            translationX = (if (fromEnd) 1 else -1) * 36 * d
            scaleX = 0.9f; scaleY = 0.9f
        }
        animate().alpha(1f).translationX(0f).scaleX(1f).scaleY(1f)
            .setDuration(260).setInterpolator(android.view.animation.OvershootInterpolator(0.9f)).withEndAction(null).start()
    }

    // পাশে স্লাইড করে বেরিয়ে যায়
    fun slideOut() {
        animate().cancel()
        animate().alpha(0f).translationX((if (fromEnd) 1 else -1) * 36 * d)
            .setDuration(240).setInterpolator(DecelerateInterpolator()).withEndAction { visibility = GONE }.start()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(0f, 0f, w, h)
        c.drawRoundRect(rect, w / 2f, w / 2f, bg)

        val tw = 6f * d
        val top = 40f * d
        val bottom = h - 66f * d
        val cx = w / 2f
        rect.set(cx - tw / 2, top, cx + tw / 2, bottom)
        c.drawRoundRect(rect, tw / 2, tw / 2, track)
        val fy = bottom - (bottom - top) * shown
        rect.set(cx - tw / 2, fy, cx + tw / 2, bottom)
        c.drawRoundRect(rect, tw / 2, tw / 2, fill)

        c.drawText("${Math.round(shown * 100)}", cx, 26f * d, txt)

        val ly = h - 54f * d
        c.drawLine(cx - 14f * d, ly, cx + 14f * d, ly, line)
        icon?.let {
            val s = (24f * d).toInt()
            val ix = (cx - s / 2f).toInt()
            val iy = (h - 28f * d - s / 2f).toInt()
            it.setBounds(ix, iy, ix + s, iy + s)
            it.draw(c)
        }
    }
}
