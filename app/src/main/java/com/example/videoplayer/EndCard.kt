package com.example.videoplayer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide

// ভিডিও শেষ হলে: ছবি অন্ধকার হয়ে "Up Next" কার্ড আসে (পরের ভিডিও, কাউন্টডাউন রিং, Replay ও Next)
class EndCard(
    ctx: Context,
    nextTitle: String?,
    nextUri: Uri?,
    autoNext: Boolean,
    private val onNext: () -> Unit,
    private val onReplay: () -> Unit,
    private val onBack: () -> Unit
) : FrameLayout(ctx) {

    private val d = resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()
    private var countdown: ValueAnimator? = null
    private var done = false

    private class Ring(c: Context) : View(c) {
        var sweep = 360f
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE }
        private val box = RectF()
        override fun onDraw(canvas: Canvas) {
            val w = resources.displayMetrics.density * 3f
            p.strokeWidth = w
            box.set(w, w, width - w, height - w)
            p.alpha = 70
            canvas.drawOval(box, p)
            p.alpha = 255
            canvas.drawArc(box, -90f, sweep, false, p)
        }
    }

    init {
        isClickable = true
        isFocusable = true
        setBackgroundColor(0xE6000000.toInt())

        val back = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_back)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            contentDescription = ctx.getString(R.string.desc_back)
            setOnClickListener { onBack() }
        }
        addView(back, LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.START).apply {
            topMargin = dp(30); leftMargin = dp(8)
        })

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
        }
        addView(col, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        val animViews = ArrayList<View>()

        if (nextUri != null) {
            val label = TextView(ctx).apply {
                text = ctx.getString(R.string.end_up_next)
                setTextColor(Color.WHITE)
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            col.addView(label)
            animViews += label

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val thumbBox = FrameLayout(ctx).apply {
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(v: View, o: android.graphics.Outline) {
                        o.setRoundRect(0, 0, v.width, v.height, 6f * d)
                    }
                }
                setBackgroundColor(0xFF222222.toInt())
                setOnClickListener { finishWith(onNext) }
            }
            val iv = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            thumbBox.addView(iv, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            try { Glide.with(ctx.applicationContext).load(nextUri).into(iv) } catch (e: Exception) { Dbg.w("EndCard.glide", e) }
            val ring = Ring(ctx)
            thumbBox.addView(ring, LayoutParams(dp(58), dp(58), Gravity.CENTER))
            val play = ImageView(ctx).apply {
                setImageResource(R.drawable.ic_play)
                setColorFilter(Color.WHITE)
            }
            thumbBox.addView(play, LayoutParams(dp(26), dp(26), Gravity.CENTER))
            row.addView(thumbBox, LinearLayout.LayoutParams(dp(160), dp(90)))
            val name = TextView(ctx).apply {
                text = nextTitle ?: ""
                setTextColor(Color.WHITE)
                textSize = 14f
                maxLines = 3
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(16), 0, 0, 0)
            }
            row.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            col.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
            animViews += row

            if (autoNext) {
                countdown = ValueAnimator.ofFloat(360f, 0f).apply {
                    duration = 6000
                    interpolator = LinearInterpolator()
                    addUpdateListener { ring.sweep = it.animatedValue as Float; ring.invalidate() }
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        var cancelled = false
                        override fun onAnimationCancel(a: android.animation.Animator) { cancelled = true }
                        override fun onAnimationEnd(a: android.animation.Animator) { if (!cancelled) finishWith(onNext) }
                    })
                    startDelay = 700
                }
            }
        }

        val btns = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        fun pill(text: String, color: Int, action: () -> Unit) = TextView(ctx).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = 10f * d; setColor(color) }
            setOnClickListener { finishWith(action) }
            Anim.pressable(this, 0.94f)
        }
        btns.addView(pill(ctx.getString(R.string.end_replay), 0x44FFFFFF, onReplay),
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(6) })
        if (nextUri != null) {
            btns.addView(pill(ctx.getString(R.string.end_next), 0x88FFFFFF.toInt(), onNext),
                LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(6) })
        }
        col.addView(btns, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18) })
        animViews += btns

        // ধীরে ফুটে ওঠা: প্রথমে অন্ধকার, তারপর একটার পর একটা উপাদান নিচ থেকে উঠে আসে
        if (Anim.enabled(this)) {
            alpha = 0f
            animate().alpha(1f).setDuration(600).start()
            animViews.forEachIndexed { i, v ->
                v.alpha = 0f
                v.translationY = dp(36).toFloat()
                v.animate().alpha(1f).translationY(0f)
                    .setStartDelay(350L + i * 220L).setDuration(650)
                    .setInterpolator(DecelerateInterpolator(1.8f)).start()
            }
        }
        countdown?.start()
    }

    private fun finishWith(action: () -> Unit) {
        if (done) return
        done = true
        countdown?.cancel()
        action()
    }

    override fun onDetachedFromWindow() {
        countdown?.cancel()
        super.onDetachedFromWindow()
    }
}
