package com.example.videoplayer

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.provider.Settings
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

// অ্যাপের সব অ্যানিমেশন এক জায়গায় (অফলাইনে কাজ করে, কোনো ফাইল/নেট লাগে না)
object Anim {

    // টাচ করলে চাপ খাওয়া, ছাড়লে স্প্রিং-এর মতো ফিরে আসা
    @SuppressLint("ClickableViewAccessibility")
    fun pressable(v: View, scaleTo: Float = 0.84f) {
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
                .setDuration(520).setInterpolator(OvershootInterpolator(2.6f)).start()
        }
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dx = e.rawX; dy = e.rawY
                    pressed = true
                    view.animate().scaleX(scaleTo).scaleY(scaleTo)
                        .setDuration(130).setInterpolator(DecelerateInterpolator()).start()
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
            // শুধু শর্টকাটের নিজের সেল (যার সরাসরি সন্তান scCircle) চাপ খাবে;
            // ভেতরে শর্টকাট আছে এমন বড় ক্লিকযোগ্য প্যানেল (যেমন "আরও" প্যানেল) যেন পুরোটা ছোট না হয়
            if (v.isClickable && (0 until v.childCount).any { v.getChildAt(it).id == R.id.scCircle }) pressable(v, 0.95f)
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
            .setDuration(760).setInterpolator(OvershootInterpolator(3.2f)).start()
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
        iv.animate().scaleX(0f).scaleY(0f).rotation(90f).setDuration(200).withEndAction {
            iv.setImageResource(res)
            iv.rotation = -90f
            iv.animate().scaleX(1f).scaleY(1f).rotation(0f)
                .setDuration(420).setInterpolator(OvershootInterpolator(2f)).start()
        }.start()
    }

    // ট্যাব বদলালে কনটেন্ট দিক অনুযায়ী স্লাইড করে ফেড-ইন হয়
    fun slideIn(v: View, dir: Int) {
        val d = v.resources.displayMetrics.density
        v.animate().cancel()
        v.alpha = 0f
        v.translationX = dir * 60f * d
        v.translationY = if (dir == 0) 10f * d else 0f
        v.animate().alpha(1f).translationX(0f).translationY(0f)
            .setDuration(620).setInterpolator(PathInterpolator(0.22f, 1f, 0.36f, 1f))
            .withLayer().start()
    }

    // ফোনের "অ্যানিমেশন বন্ধ" সেটিং মানা হয়
    fun enabled(v: View): Boolean =
        Settings.Global.getFloat(v.context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

    // লিস্টের আইটেম নিচ থেকে ভেসে আসে (প্রথম স্ক্রিনে একটার পর একটা)
    fun listEntry(v: View, index: Int) {
        if (!enabled(v)) return
        val d = v.resources.displayMetrics.density
        v.animate().cancel()
        v.alpha = 0f
        v.translationY = 44f * d
        v.animate().alpha(1f).translationY(0f)
            .setStartDelay(index.coerceIn(0, 9) * 80L)
            .setDuration(620).setInterpolator(PathInterpolator(0.22f, 1f, 0.36f, 1f)).start()
    }

    // রিসাইকেল হওয়া আইটেমে আগের অ্যানিমেশনের চিহ্ন মুছে স্বাভাবিক অবস্থায় আনে
    fun resetEntry(v: View) {
        v.animate().cancel()
        v.alpha = 1f
        v.translationY = 0f
    }

    // কন্ট্রোল দেখানোর সময় বাটনগুলো একটার পর একটা পপ করে আসে
    fun popIn(views: List<View>) {
        if (views.isEmpty() || !enabled(views[0])) return
        views.forEachIndexed { i, v ->
            v.animate().cancel()
            v.alpha = 0f
            v.scaleX = 0.5f
            v.scaleY = 0.5f
            v.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(i * 110L).setDuration(520)
                .setInterpolator(OvershootInterpolator(2.4f)).start()
        }
    }
}
