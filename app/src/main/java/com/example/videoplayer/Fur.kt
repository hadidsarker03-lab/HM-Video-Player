package com.example.videoplayer

import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import org.xmlpull.v1.XmlPullParser
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// নরম পশমি (প্লাশ) টেক্সচার: ঘন, ছোট, প্রায় একই দিকের লোম + বড় আকারের হালকা ছায়ার ছোপ
object Fur {
    private var tile: Bitmap? = null
    private var tileDensity = 0f

    fun tile(density: Float): Bitmap {
        val t = tile
        if (t != null && tileDensity == density) return t
        val size = (150f * density).toInt().coerceIn(256, 560)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF0C0C0D.toInt())
        val rnd = Random(11)

        // বড় ছোপ: লোমের গোছার মতো হালকা-গাঢ় অংশ (ওয়ার্প করে পুনরাবৃত্তির জন্য)
        val blob = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until 70) {
            val r = (14f + rnd.nextFloat() * 26f) * density
            val x = rnd.nextFloat() * size
            val y = rnd.nextFloat() * size
            val light = rnd.nextBoolean()
            val col = if (light) 0x14FFFFFF else 0x30000000
            for (ox in -1..1) for (oy in -1..1) {
                val sx = x + ox * size
                val sy = y + oy * size
                if (sx < -r || sy < -r || sx > size + r || sy > size + r) continue
                blob.shader = RadialGradient(sx, sy, r, col, 0x00000000, Shader.TileMode.CLAMP)
                c.drawCircle(sx, sy, r, blob)
            }
        }

        // ঘন, ছোট, নরম লোম: কম কনট্রাস্ট, বেশির ভাগ নিচের দিকে হেলানো
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = (0.55f * density).coerceAtLeast(1.2f)
        val count = (size.toFloat() * size / (density * 3.2f)).toInt()
        val path = Path()
        for (i in 0 until count) {
            val x = rnd.nextFloat() * size
            val y = rnd.nextFloat() * size
            val ang = Math.toRadians(90.0 + rnd.nextGaussian() * 20.0)
            val len = (1.8f + rnd.nextFloat() * 2.6f) * density
            val bend = (rnd.nextFloat() - 0.5f) * len * 0.35f
            val dx = (cos(ang) * len).toFloat()
            val dy = (sin(ang) * len).toFloat()
            val k = i % 5
            val level: Int
            val alpha: Int
            if (k < 2) { level = rnd.nextInt(10); alpha = 70 + rnd.nextInt(60) }
            else { level = 24 + rnd.nextInt(34); alpha = 22 + rnd.nextInt(44) }
            p.color = Color.argb(alpha, level, level, level + 2)
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

// ফোলা পশমি প্যাড: নরম ছায়া + পশমি ফিল + কিনারায় বাইরের দিকে বেরোনো লোম (কোনো শক্ত বর্ডার নেই)
class FurOverlay : Drawable() {
    private var radiusDp = 20f
    private var lit = false
    private var density = 3f
    private var ready = false

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hairLight = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hairDark = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pad = RectF()
    private val tmp = RectF()
    private var radius = 0f
    private var linesLight = FloatArray(0)
    private var linesDark = FloatArray(0)

    override fun inflate(r: Resources, parser: XmlPullParser, attrs: AttributeSet, theme: Resources.Theme?) {
        super.inflate(r, parser, attrs, theme)
        radiusDp = attrs.getAttributeValue(null, "radius")?.toFloatOrNull() ?: 20f
        lit = attrs.getAttributeValue(null, "lit") == "true"
        density = r.displayMetrics.density
        setup()
    }

    private fun setup() {
        fill.shader = BitmapShader(Fur.tile(density), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        if (lit) fill.colorFilter = LightingColorFilter(0xFFFFFFFF.toInt(), 0x00262630)
        shadow.color = 0x26000000
        for (p in listOf(hairLight, hairDark)) {
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = (0.85f * density).coerceAtLeast(1.2f)
        }
        hairLight.color = if (lit) 0x9A8E8E96.toInt() else 0x7A66666C
        hairDark.color = 0x99141416.toInt()
        ready = true
    }

    // বাউন্ড বদলালে প্যাডের মাপ ও কিনারার লোমের অবস্থান নতুন করে বানায়
    override fun onBoundsChange(b: Rect) {
        if (!ready) { density = 3f; setup() }
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        if (w <= 0f || h <= 0f) return
        val m = minOf(5f * density, minOf(w, h) / 6f)
        pad.set(m, m, w - m, h - m)
        radius = minOf(radiusDp * density, minOf(pad.width(), pad.height()) / 2f)
        shade.shader = LinearGradient(
            0f, pad.top, 0f, pad.bottom,
            intArrayOf(0x24FFFFFF, 0x00000000, 0x55000000),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        buildHairs()
    }

    private fun buildHairs() {
        val pw = pad.width()
        val ph = pad.height()
        val r = radius
        val sw = (pw - 2 * r).coerceAtLeast(0f)
        val sh = (ph - 2 * r).coerceAtLeast(0f)
        val arc = (PI / 2).toFloat() * r
        val total = 2 * sw + 2 * sh + 4 * arc
        val n = (total / (0.8f * density)).toInt().coerceIn(20, 2600)
        val rnd = Random(5)
        val light = ArrayList<Float>(n * 2)
        val dark = ArrayList<Float>(n * 2)
        val pt = FloatArray(4)
        for (i in 0 until n) {
            edgePoint(rnd.nextFloat() * total, sw, sh, arc, r, pt)
            val jitter = Math.toRadians(rnd.nextGaussian() * 28.0)
            val cj = cos(jitter).toFloat()
            val sj = sin(jitter).toFloat()
            val dx = pt[2] * cj - pt[3] * sj
            val dy = pt[2] * sj + pt[3] * cj
            val len = (1.2f + rnd.nextFloat() * 3.0f) * density
            val inset = 1.6f * density
            val x0 = pad.left + pt[0] - pt[2] * inset
            val y0 = pad.top + pt[1] - pt[3] * inset
            val list = if (i % 5 < 3) light else dark
            list.add(x0); list.add(y0)
            list.add(pad.left + pt[0] + dx * len); list.add(pad.top + pt[1] + dy * len)
        }
        linesLight = light.toFloatArray()
        linesDark = dark.toFloatArray()
    }

    // গোল-কোণার আয়তের পরিধির উপর একটা বিন্দু + বাইরের দিকের লম্ব (x, y, nx, ny); স্থানাঙ্ক প্যাডের বাঁ-উপর থেকে
    private fun edgePoint(s0: Float, sw: Float, sh: Float, arc: Float, r: Float, o: FloatArray) {
        var s = s0
        val w = sw + 2 * r
        val h = sh + 2 * r
        fun corner(cx: Float, cy: Float, a0: Float, t: Float) {
            val a = Math.toRadians((a0 + t / arc * 90f).toDouble())
            val ca = cos(a).toFloat()
            val sa = sin(a).toFloat()
            o[0] = cx + r * ca; o[1] = cy + r * sa; o[2] = ca; o[3] = sa
        }
        if (s < sw) { o[0] = r + s; o[1] = 0f; o[2] = 0f; o[3] = -1f; return }
        s -= sw
        if (s < arc) { corner(w - r, r, -90f, s); return }
        s -= arc
        if (s < sh) { o[0] = w; o[1] = r + s; o[2] = 1f; o[3] = 0f; return }
        s -= sh
        if (s < arc) { corner(w - r, h - r, 0f, s); return }
        s -= arc
        if (s < sw) { o[0] = w - r - s; o[1] = h; o[2] = 0f; o[3] = 1f; return }
        s -= sw
        if (s < arc) { corner(r, h - r, 90f, s); return }
        s -= arc
        if (s < sh) { o[0] = 0f; o[1] = h - r - s; o[2] = -1f; o[3] = 0f; return }
        s -= sh
        corner(r, r, 180f, s.coerceAtMost(arc))
    }

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.isEmpty || pad.isEmpty) return
        c.save()
        c.translate(b.left.toFloat(), b.top.toFloat())
        // নরম ছায়া: নিচের দিকে সরানো কয়েক স্তর
        for (i in 3 downTo 1) {
            tmp.set(pad)
            tmp.inset(-i * 0.9f * density, -i * 0.9f * density)
            tmp.offset(0f, 1.6f * density)
            c.drawRoundRect(tmp, radius + i * density, radius + i * density, shadow)
        }
        c.drawRoundRect(pad, radius, radius, fill)
        c.drawRoundRect(pad, radius, radius, shade)
        c.drawLines(linesDark, hairDark)
        c.drawLines(linesLight, hairLight)
        c.restore()
    }

    override fun setAlpha(a: Int) { fill.alpha = a; shade.alpha = a; shadow.alpha = a / 6; hairLight.alpha = a; hairDark.alpha = a }
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
