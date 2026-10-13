package com.example.videoplayer

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.TypedValue
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.math.max

// ট্রান্সপারেন্ট থিমের নিজের ব্যাকগ্রাউন্ড: অ্যাপের ভেতরের ছবি + নিজের আপলোড
object Backgrounds {
    const val NONE = "none"
    private const val DIR = "backgrounds"

    private var cacheKey: String? = null
    private var cacheBmp: Bitmap? = null

    fun assetKeys(ctx: Context): List<String> =
        (ctx.assets.list(DIR) ?: emptyArray()).sorted().map { "asset:$it" }

    fun customKeys(ctx: Context): List<String> =
        (File(ctx.filesDir, DIR).listFiles() ?: emptyArray()).sortedBy { it.name }.map { "file:${it.name}" }

    fun selected(ctx: Context): String =
        AppSettings.prefs(ctx).getString("bg_sel", NONE) ?: NONE

    fun select(ctx: Context, key: String) {
        AppSettings.prefs(ctx).edit().putString("bg_sel", key).apply()
    }

    // ব্যাকগ্রাউন্ড ব্লার: 0..100 (০ = ঝাপসা নয়)
    fun blur(ctx: Context): Int = AppSettings.prefs(ctx).getInt("bg_blur", 0).coerceIn(0, 100)

    fun setBlur(ctx: Context, v: Int) {
        AppSettings.prefs(ctx).edit().putInt("bg_blur", v.coerceIn(0, 100)).apply()
    }

    // সাধারণ (নন-ট্রান্সপারেন্ট) মোডেও ছবি ব্যাকগ্রাউন্ড হিসেবে বসানো আছে কি
    fun hasWallpaper(ctx: Context): Boolean = selected(ctx) != NONE

    // ছোট করা ছবিকে স্ট্যাক-ব্লার দিয়ে ঝাপসা করে (সব অ্যান্ড্রয়েড ভার্সনে চলে)
    private fun blurred(src: Bitmap, level: Int): Bitmap {
        if (level <= 0) return src
        val scale = 360f / max(src.width, src.height)
        val w = max(8, (src.width * scale).toInt())
        val h = max(8, (src.height * scale).toInt())
        val small = Bitmap.createScaledBitmap(src, w, h, true).copy(Bitmap.Config.ARGB_8888, true)
        val radius = max(1, (level * 0.24f).toInt())
        stackBlur(small, radius)
        return small
    }

    // বক্স ব্লার (৩ বার = গাউসিয়ানের কাছাকাছি), রানিং-সাম দিয়ে দ্রুত
    private fun stackBlur(bmp: Bitmap, radius: Int) {
        val w = bmp.width
        val h = bmp.height
        val pix = IntArray(w * h)
        bmp.getPixels(pix, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        repeat(3) {
            boxPass(pix, tmp, w, h, radius, true)
            boxPass(tmp, pix, w, h, radius, false)
        }
        bmp.setPixels(pix, 0, w, 0, 0, w, h)
    }

    private fun boxPass(src: IntArray, dst: IntArray, w: Int, h: Int, rad: Int, horiz: Boolean) {
        val len = if (horiz) w else h
        val lines = if (horiz) h else w
        val step = if (horiz) 1 else w
        val win = 2 * rad + 1
        for (l in 0 until lines) {
            val base = if (horiz) l * w else l
            var ra = 0; var ga = 0; var ba = 0
            for (i in -rad..rad) {
                val p = src[base + i.coerceIn(0, len - 1) * step]
                ra += (p shr 16) and 0xff; ga += (p shr 8) and 0xff; ba += p and 0xff
            }
            for (i in 0 until len) {
                dst[base + i * step] =
                    (0xff shl 24) or ((ra / win) shl 16) or ((ga / win) shl 8) or (ba / win)
                val add = src[base + (i + rad + 1).coerceAtMost(len - 1) * step]
                val rem = src[base + (i - rad).coerceAtLeast(0) * step]
                ra += ((add shr 16) and 0xff) - ((rem shr 16) and 0xff)
                ga += ((add shr 8) and 0xff) - ((rem shr 8) and 0xff)
                ba += (add and 0xff) - (rem and 0xff)
            }
        }
    }

    // গ্যালারি থেকে ছবি যোগ করে, সেভ করে। সফল হলে কী ফেরত দেয়।
    fun addCustom(ctx: Context, uri: Uri): String? {
        return try {
            val o = BitmapFactory.Options()
            o.inJustDecodeBounds = true
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (max(o.outWidth, o.outHeight) / s > 1800) s *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = s
            val bmp = ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, o2)
            } ?: return null
            val dir = File(ctx.filesDir, DIR)
            dir.mkdirs()
            val name = "u" + System.currentTimeMillis() + ".jpg"
            File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            bmp.recycle()
            "file:$name"
        } catch (e: Exception) {
            null
        }
    }

    fun removeCustom(ctx: Context, key: String) {
        try {
            File(File(ctx.filesDir, DIR), key.removePrefix("file:")).delete()
        } catch (e: Exception) { }
        if (selected(ctx) == key) select(ctx, NONE)
        cacheKey = null
        cacheBmp = null
    }

    // স্ক্রিনের উইন্ডো ব্যাকগ্রাউন্ড বসায় (সাধারণ ও ট্রান্সপারেন্ট দুই মোডেই, প্লেয়ার বাদে)
    fun apply(act: Activity) {
        if (act is PlayerActivity) return
        val key = selected(act)
        if (key == NONE) {
            // সিস্টেম ওয়ালপেপার: থিমের নিজের ব্যাকগ্রাউন্ডে ফিরে যায়
            val tv = TypedValue()
            if (act.theme.resolveAttribute(android.R.attr.windowBackground, tv, true)) {
                if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                    act.window.setBackgroundDrawable(ColorDrawable(tv.data))
                } else if (tv.resourceId != 0) {
                    ContextCompat.getDrawable(act, tv.resourceId)?.let { act.window.setBackgroundDrawable(it) }
                }
            }
            return
        }
        val level = blur(act)
        val ck = "$key|$level"
        var bmp = if (cacheKey == ck) cacheBmp else null
        if (bmp == null) {
            val raw = decode(act, key, 1800)
            bmp = if (raw != null) blurred(raw, level) else null
            cacheKey = ck
            cacheBmp = bmp
        }
        if (bmp == null) return
        act.window.setBackgroundDrawable(CropDrawable(bmp))
    }

    // ছবিকে স্ক্রিন ভরে (center-crop) আঁকে — কোনো ঝাপসা/ধূসর স্তর নেই
    private class CropDrawable(private val bmp: Bitmap) : Drawable() {
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        override fun draw(c: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val s = max(b.width().toFloat() / bmp.width, b.height().toFloat() / bmp.height)
            val dx = b.left + (b.width() - bmp.width * s) / 2f
            val dy = b.top + (b.height() - bmp.height * s) / 2f
            c.save()
            c.clipRect(b)
            c.translate(dx, dy)
            c.scale(s, s)
            c.drawBitmap(bmp, 0f, 0f, paint)
            c.restore()
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(cf: ColorFilter?) {
            paint.colorFilter = cf
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.OPAQUE
    }
}
