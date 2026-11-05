package io.github.abhishekkaisar.glassos

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

/**
 * Draws a Liquid Glass style rounded rect: the pre-blurred wallpaper region behind the shape,
 * a white tint, a top sheen, grain and a gradient rim. No per-frame blur: the blur was done once.
 * Each painter owns its own shaders, because on API 25 shader matrices are shared by reference.
 */
class GlassPainter {
    companion object {
        /**
         * Settings > Reduce transparency (tvOS's accessibility option): every glass surface draws a solid base instead
         * of the blurred art behind it, under the same tint and rim. Easier to read, and one textured pass less.
         */
        @JvmStatic var solid = false
        /** The base without art: the solid panels, and glass before the first slide has loaded. */
        const val SOLID_BASE = 0xFF141A30.toInt()
    }

    var tint = 0.14f
    var tintColor = Color.WHITE
    var dark = 0f
    var border = 0.26f
    var highlight = 0.42f
    var sheen = 0.09f
    var grain = 0.55f
    var stroke = 1.5f
    /**
     * Draw the parts that do not follow the backdrop (dark, tint, grain, sheen, rim) from one cached bitmap: one
     * textured pass instead of five full-size passes per frame. For large, static glass (the shelf).
     */
    var cacheStatic = false

    private val back = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val backB = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var bmpA: Bitmap? = null
    private var bmpB: Bitmap? = null
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = Grain.newShader() }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val m = Matrix()
    private val inv = Matrix()
    private val lm = Matrix()
    private val inset = RectF()
    private var key = ""
    private var staticBmp: Bitmap? = null
    private var staticKey = ""
    private val staticPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private fun layer(c: Canvas, r: RectF, radius: Float, l: BackLayer, p: Paint, isA: Boolean) {
        val b = l.bmp ?: return
        if (isA) {
            if (b !== bmpA) { bmpA = b; p.shader = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        } else {
            if (b !== bmpB) { bmpB = b; p.shader = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        }
        lm.set(inv)
        lm.preConcat(l.m)
        p.shader.setLocalMatrix(lm)
        p.alpha = (l.alpha.coerceIn(0f, 1f) * 255).toInt()
        c.drawRoundRect(r, radius, radius, p)
    }

    fun draw(c: Canvas, v: View, r: RectF, radius: Float) {
        val a = Backdrop.a
        if (a.bmp != null && !solid) {
            viewToWindow(v, m)
            m.invert(inv)
            layer(c, r, radius, a, back, true)
            val b = Backdrop.b
            if (b.bmp != null && b.alpha > 0.004f) layer(c, r, radius, b, backB, false)
        } else {
            fill.color = SOLID_BASE
            c.drawRoundRect(r, radius, radius, fill)
        }
        if (cacheStatic) { drawStatic(c, r, radius); return }
        drawOverlay(c, r, radius)
    }

    private fun drawStatic(c: Canvas, r: RectF, radius: Float) {
        val sk = "${r.left},${r.top},${r.width()},${r.height()},$radius,$dark,$tint,$tintColor,$grain,$sheen,$highlight,$border,$stroke"
        if (sk != staticKey || staticBmp == null) {
            staticKey = sk
            // Not recycled: the last recorded frame may still draw the old one.
            val bw = Math.ceil(r.width().toDouble()).toInt().coerceAtLeast(1)
            val bh = Math.ceil(r.height().toDouble()).toInt().coerceAtLeast(1)
            val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val bc = Canvas(b)
            // Same coordinates as on screen, so the grain tile and gradients land exactly where they did.
            bc.translate(-r.left, -r.top)
            drawOverlay(bc, r, radius)
            staticBmp = b
        }
        c.drawBitmap(staticBmp!!, r.left, r.top, staticPaint)
    }

    private fun drawOverlay(c: Canvas, r: RectF, radius: Float) {
        if (dark > 0f) {
            fill.color = Color.argb((dark * 255).toInt(), 4, 8, 20)
            c.drawRoundRect(r, radius, radius, fill)
        }
        if (tint > 0f) {
            fill.color = alphaOf(tintColor, tint)
            c.drawRoundRect(r, radius, radius, fill)
        }
        if (grain > 0f) {
            grainPaint.alpha = (grain * 255).toInt()
            c.drawRoundRect(r, radius, radius, grainPaint)
        }
        val k = "${r.top},${r.height()},$sheen,$highlight,$border"
        if (k != key) {
            key = k
            sheenPaint.shader = if (sheen > 0f) LinearGradient(0f, r.top, 0f, r.top + r.height() * 0.6f,
                intArrayOf(alphaOf(Color.WHITE, sheen), alphaOf(Color.WHITE, sheen * 0.25f), 0), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP) else null
            edge.shader = if (highlight + border > 0f) LinearGradient(0f, r.top, 0f, r.bottom,
                intArrayOf(alphaOf(Color.WHITE, highlight), alphaOf(Color.WHITE, border * 0.45f), alphaOf(Color.WHITE, border * 0.7f)),
                floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP) else null
        }
        if (sheenPaint.shader != null) c.drawRoundRect(r, radius, radius, sheenPaint)
        if (edge.shader != null) {
            edge.strokeWidth = stroke
            val h = stroke / 2f
            inset.set(r.left + h, r.top + h, r.right - h, r.bottom - h)
            c.drawRoundRect(inset, radius - h, radius - h, edge)
        }
    }
}
