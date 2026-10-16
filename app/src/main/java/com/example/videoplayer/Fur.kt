package com.example.videoplayer

import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import org.xmlpull.v1.XmlPullParser
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

// ফার (পশমি) টেক্সচার: কোড দিয়ে আঁকা, টাইলের মতো পুনরাবৃত্তি হয়
object Fur {
    private var tile: Bitmap? = null
    private var tileDensity = 0f

    fun tile(density: Float): Bitmap {
        val t = tile
        if (t != null && tileDensity == density) return t
        val size = (150f * density).toInt().coerceIn(256, 560)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF0D0D0E.toInt())
        val rnd = Random(7)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = (0.6f * density).coerceAtLeast(1.3f)
        val count = (size.toFloat() * size / (density * 9f)).toInt()
        val path = Path()
        for (i in 0 until count) {
            val dark = i % 3 == 0
            val x = rnd.nextFloat() * size
            val y = rnd.nextFloat() * size
            val ang = Math.toRadians(90.0 + rnd.nextGaussian() * 55.0)
            val len = (2.5f + rnd.nextFloat() * 4.5f) * density
            val bend = (rnd.nextFloat() - 0.5f) * len * 0.6f
            val dx = (cos(ang) * len).toFloat()
            val dy = (sin(ang) * len).toFloat()
            val level = if (dark) rnd.nextInt(14) else 38 + rnd.nextInt(80)
            p.color = Color.argb(if (dark) 150 else 40 + rnd.nextInt(90), level, level, level + 2)
            for (ox in -1..1) for (oy in -1..1) {
                val sx = x + ox * size
                val sy = y + oy * size
                if (sx < -len * 2 || sy < -len * 2 || sx > size + len * 2 || sy > size + len * 2) continue
                path.reset()
                path.moveTo(sx, sy)
                path.quadTo(sx + dx / 2 + bend, sy + dy / 2, sx + dx, sy + dy)
                c.drawPath(path, p)
            }
        }
        tile = bmp
        tileDensity = density
        return bmp
    }

    fun window(res: Resources): Drawable {
        val d = BitmapDrawable(res, tile(res.displayMetrics.density))
        d.tileModeX = Shader.TileMode.REPEAT
        d.tileModeY = Shader.TileMode.REPEAT
        return d
    }
}

// কার্ড/ট্যাব/বোতামের উপরে বসানো পশমি আবরণ (ফোলা ভাব + হালকা পশমি কিনারা)
class FurOverlay : Drawable() {
    private var radiusDp = 20f
    private var density = 3f
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var ready = false

    override fun inflate(r: Resources, parser: XmlPullParser, attrs: AttributeSet, theme: Resources.Theme?) {
        super.inflate(r, parser, attrs, theme)
        radiusDp = attrs.getAttributeValue(null, "radius")?.toFloatOrNull() ?: 20f
        density = r.displayMetrics.density
        setup()
    }

    private fun setup() {
        val tile = Fur.tile(density)
        fill.shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        rim.shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        rim.colorFilter = LightingColorFilter(0xFFFFFFFF.toInt(), 0x00303034)
        rim.style = Paint.Style.STROKE
        rim.strokeWidth = 2.2f * density
        edge.style = Paint.Style.STROKE
        edge.strokeWidth = 1.2f * density
        edge.color = 0x99000000.toInt()
        ready = true
    }

    override fun onBoundsChange(b: Rect) {
        shade.shader = LinearGradient(
            0f, b.top.toFloat(), 0f, b.bottom.toFloat(),
            intArrayOf(0x2EFFFFFF, 0x00000000, 0x66000000),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
    }

    override fun draw(c: Canvas) {
        if (!ready) { density = 3f; setup() }
        val b = bounds
        if (b.isEmpty) return
        rect.set(b)
        val r = (radiusDp * density).coerceAtMost(minOf(rect.width(), rect.height()) / 2f)
        c.drawRoundRect(rect, r, r, fill)
        c.drawRoundRect(rect, r, r, shade)
        val h = rim.strokeWidth / 2f
        rect.inset(h, h)
        c.drawRoundRect(rect, r - h, r - h, rim)
        rect.set(b)
        rect.inset(edge.strokeWidth / 2f, edge.strokeWidth / 2f)
        c.drawRoundRect(rect, r, r, edge)
    }

    override fun setAlpha(a: Int) { fill.alpha = a; shade.alpha = a; rim.alpha = a; edge.alpha = a }
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
