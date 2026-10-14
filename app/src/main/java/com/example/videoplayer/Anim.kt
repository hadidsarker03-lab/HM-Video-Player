package com.example.videoplayer

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

// অ্যাপের সব অ্যানিমেশন এক জায়গায় (অফলাইনে কাজ করে, কোনো ফাইল/নেট লাগে না)
object Anim {

    // টাচ করলে চাপ খাওয়া, ছাড়লে স্প্রিং-এর মতো ফিরে আসা
    @SuppressLint("ClickableViewAccessibility")
    fun pressable(v: View, scaleTo: Float = 0.86f) {
        if (v.getTag(R.id.anim_attached) != null) return
        v.setTag(R.id.anim_attached, true)
        var dx = 0f
        var dy = 0f
        var pressed = false
        val slop = android.view.ViewConfiguration.get(v.context).scaledTouchSlop
        fun release(view: View) {
            if (!pressed) return
            pressed = false
            view.animate().scaleX(1f).scaleY(1f)
                .setDuration(220).setInterpolator(DecelerateInterpolator()).start()
        }
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dx = e.rawX; dy = e.rawY
                    pressed = true
                    view.animate().scaleX(scaleTo).scaleY(scaleTo)
                        .setDuration(90).setInterpolator(DecelerateInterpolator()).start()
                }
                // আঙুল সরালে (উপর-নিচ বা পাশে) ছোট হয়ে থাকে না, সাথে সাথে স্বাভাবিক হয়
                MotionEvent.ACTION_MOVE ->
                    if (Math.abs(e.rawX - dx) > slop || Math.abs(e.rawY - dy) > slop) release(view)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> release(view)
            }
            false
        }
    }

    // পর্দার সব ক্লিকযোগ্য আইকন ও শর্টকাটে প্রেস-অ্যানিমেশন
    fun attachTree(v: View) {
        if (v is RecyclerView) return
        if (v is ViewGroup) {
            if (v.isClickable && v.findViewById<View>(R.id.scCircle) != null) pressable(v, 0.95f)
            for (i in 0 until v.childCount) attachTree(v.getChildAt(i))
        } else if (v is ImageView && v.isClickable) {
            pressable(v)
        }
    }

    // নিচের ট্যাবের আইকন লাফিয়ে উঠে একটু দুলে থামে
    fun bounceNavIcon(nav: View, itemId: Int) {
        val icon = nav.findViewById<View>(itemId)
            ?.findViewById<View>(com.google.android.material.R.id.navigation_bar_item_icon_view) ?: return
        icon.animate().cancel()
        icon.scaleX = 0.55f
        icon.scaleY = 0.55f
        icon.rotation = -22f
        icon.animate().scaleX(1f).scaleY(1f).rotation(0f)
            .setDuration(480).setInterpolator(OvershootInterpolator(3.2f)).start()
    }

    // আইকন বদলানোর সময় ঘুরে-ছোট হয়ে নতুনটা ঘুরে-বড় হয়ে আসে (প্লে/পজ ইত্যাদি)
    fun swapIcon(iv: ImageView, res: Int) {
        val old = iv.getTag(R.id.anim_res)
        if (old == res) return
        iv.setTag(R.id.anim_res, res)
        if (old == null || !iv.isShown) {
            iv.setImageResource(res)
            return
        }
        iv.animate().cancel()
        iv.animate().scaleX(0f).scaleY(0f).rotation(90f).setDuration(110).withEndAction {
            iv.setImageResource(res)
            iv.rotation = -90f
            iv.animate().scaleX(1f).scaleY(1f).rotation(0f)
                .setDuration(260).setInterpolator(OvershootInterpolator(2f)).start()
        }.start()
    }

    // ট্যাব বদলালে কনটেন্ট দিক অনুযায়ী স্লাইড করে ফেড-ইন হয়
    fun slideIn(v: View, dir: Int) {
        val d = v.resources.displayMetrics.density
        v.animate().cancel()
        v.alpha = 0f
        v.translationX = dir * 36f * d
        v.translationY = if (dir == 0) 14f * d else 0f
        v.animate().alpha(1f).translationX(0f).translationY(0f)
            .setDuration(300).setInterpolator(DecelerateInterpolator(1.8f)).start()
    }
}
