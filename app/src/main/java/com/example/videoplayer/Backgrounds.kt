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
    private var cacheDark = false
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

    // ছবি পড়ে (বড় হলে ছোট করে)
    fun decode(ctx: Context, key: String, maxSide: Int): Bitmap? {
        return try {
            fun open() = if (key.startsWith("asset:")) {
                ctx.assets.open("$DIR/${key.removePrefix("asset:")}")
            } else {
                File(File(ctx.filesDir, DIR), key.removePrefix("file:")).inputStream()
            }
            val o = BitmapFactory.Options()
            o.inJustDecodeBounds = true
            open().use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (max(o.outWidth, o.outHeight) / s > maxSide) s *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = s
            open().use { BitmapFactory.decodeStream(it, null, o2) }
        } catch (e: Exception) {
            null
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
        if (cacheKey == key) {
            cacheKey = null
            cacheBmp = null
        }
    }

    private fun isDark(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    // স্ক্রিনের উইন্ডো ব্যাকগ্রাউন্ড বসায় (শুধু ট্রান্সপারেন্ট থিমে, প্লেয়ার বাদে)
    fun apply(act: Activity) {
        if (act is PlayerActivity) return
        if (!AppSettings.glass(act)) return
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
        val dark = isDark(act)
        var bmp = if (cacheKey == key) cacheBmp else null
        if (bmp == null) {
            bmp = decode(act, key, 1800)
            cacheKey = key
            cacheBmp = bmp
        }
        cacheDark = dark
        if (bmp == null) return
        val scrim = if (dark) 0x88000000.toInt() else 0x66FFFFFF
        act.window.setBackgroundDrawable(CropDrawable(bmp, scrim))
    }

    // ছবিকে স্ক্রিন ভরে (center-crop) আঁকে, ওপরে হালকা স্তর দেয় যাতে লেখা পড়া যায়
    private class CropDrawable(private val bmp: Bitmap, private val scrim: Int) : Drawable() {
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val scrimPaint = Paint().apply { color = scrim }

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
            c.drawRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), scrimPaint)
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
