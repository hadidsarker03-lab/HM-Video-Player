package com.example.videoplayer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView

// ভাসমান প্লেয়ার: অন্য অ্যাপের উপরে ছোট ভিডিও উইন্ডো (টেনে সরানো, চিমটিতে ছোট-বড়)।
// প্লেয়ার থাকে PlaybackService-এ, তাই ভিডিও না থেমে চলতে থাকে।
object FloatingPlayer {
    var active = false
        private set
    private var root: FrameLayout? = null
    private var pv: PlayerView? = null
    private var listener: Player.Listener? = null

    fun canDraw(c: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(c)

    fun requestPermission(c: Context) {
        val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + c.packageName))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { c.startActivity(i) } catch (e: Exception) { Dbg.w("FloatingPlayer.requestPermission", e) }
    }

    private fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    @SuppressLint("ClickableViewAccessibility")
    fun show(app: Context): Boolean {
        val player = PlaybackService.current ?: return false
        if (!canDraw(app)) return false
        hide(false)
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val themed = ContextThemeWrapper(app, R.style.Theme_HMPlayer)

        val screenW = app.resources.displayMetrics.widthPixels
        val minW = dp(app, 150)
        val maxW = (screenW * 0.95f).toInt()
        val lp = WindowManager.LayoutParams(
            minOf(maxOf((screenW * 0.6f).toInt(), minW), maxW),
            0,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.height = lp.width * 9 / 16
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = dp(app, 16)
        lp.y = dp(app, 120)

        val box = FrameLayout(themed)
        box.setBackgroundColor(Color.BLACK)

        val view = PlayerView(themed)
        view.useController = false
        view.player = player
        box.addView(view, FrameLayout.LayoutParams(-1, -1))

        // টাচ লেয়ার: টানলে সরে, দুই আঙুলে ছোট-বড়
        val touch = View(themed)
        box.addView(touch, FrameLayout.LayoutParams(-1, -1))
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        val scaler = ScaleGestureDetector(themed, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                val w = (lp.width * d.scaleFactor).toInt().coerceIn(minW, maxW)
                lp.width = w
                lp.height = w * 9 / 16
                try { wm.updateViewLayout(box, lp) } catch (e: Exception) { Dbg.w("FloatingPlayer.onScale", e) }
                return true
            }
        })
        touch.setOnTouchListener { _, ev ->
            scaler.onTouchEvent(ev)
            if (!scaler.isInProgress && ev.pointerCount == 1) {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = ev.rawX; downY = ev.rawY; startX = lp.x; startY = lp.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = startX + (ev.rawX - downX).toInt()
                        lp.y = startY + (ev.rawY - downY).toInt()
                        try { wm.updateViewLayout(box, lp) } catch (e: Exception) { Dbg.w("FloatingPlayer.onScale", e) }
                    }
                }
            } else if (ev.actionMasked == MotionEvent.ACTION_POINTER_UP) {
                downX = ev.rawX; downY = ev.rawY; startX = lp.x; startY = lp.y
            }
            true
        }

        // নিচের কন্ট্রোল: প্লে/পজ, পুরো স্ক্রিনে ফেরা, বন্ধ
        val bar = LinearLayout(themed)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER
        bar.setBackgroundColor(Color.parseColor("#99000000"))
        fun btn(icon: Int, action: () -> Unit): ImageView {
            val b = ImageView(themed)
            b.setImageResource(icon)
            b.setColorFilter(Color.WHITE)
            b.setPadding(dp(app, 8), dp(app, 6), dp(app, 8), dp(app, 6))
            b.setOnClickListener { action() }
            bar.addView(b, LinearLayout.LayoutParams(0, dp(app, 36), 1f))
            return b
        }
        val playBtn = btn(if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play) {
            val p = PlaybackService.current ?: return@btn
            if (p.isPlaying) p.pause() else p.play()
        }
        btn(R.drawable.ic_pip) {
            hide(true)
            app.startActivity(
                Intent(app, PlayerActivity::class.java)
                    .putExtra(PlayerActivity.EXTRA_RESUME, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
        btn(R.drawable.ic_close) {
            PlaybackService.current?.pause()
            hide(true)
        }
        box.addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playBtn.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
            }
        }
        player.addListener(l)

        return try {
            wm.addView(box, lp)
            root = box
            pv = view
            listener = l
            active = true
            true
        } catch (e: Exception) {
            player.removeListener(l)
            view.player = null
            false
        }
    }

    // uiOnly=true: শুধু উইন্ডো সরায় (active বন্ধ হয়)
    fun hide(@Suppress("UNUSED_PARAMETER") uiOnly: Boolean = true) {
        val r = root
        val v = pv
        val l = listener
        if (l != null) PlaybackService.current?.removeListener(l)
        v?.player = null
        if (r != null) {
            try {
                val wm = r.context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(r)
            } catch (e: Exception) { Dbg.w("FloatingPlayer.hide", e) }
        }
        root = null
        pv = null
        listener = null
        active = false
    }
}
