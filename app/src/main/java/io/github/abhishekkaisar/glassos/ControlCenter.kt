package io.github.abhishekkaisar.glassos

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/** Line glyphs for Control Center, drawn in a box of side s centred on (cx, cy). No vector assets. */
enum class Glyph { NONE, POWER, WIFI, MOON, SPEAKER, TIMER, GAMEPAD, ACCESS, LOCK, GEAR, PEOPLE, PLAY, PAUSE, PREV, NEXT, SHIELD, TOGGLES, NOTE, CHECK, CLOSE, OPEN, INPUT }

object Glyphs {
    private val path = Path()
    private val path2 = Path()
    private val r = RectF()

    fun draw(c: Canvas, g: Glyph, cx: Float, cy: Float, s: Float, color: Int, p: Paint) {
        val h = s / 2
        p.color = color
        p.strokeWidth = s * 0.09f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.style = Paint.Style.STROKE
        when (g) {
            Glyph.NONE -> {}
            Glyph.POWER -> {
                r.set(cx - h * 0.8f, cy - h * 0.72f, cx + h * 0.8f, cy + h * 0.88f)
                c.drawArc(r, 300f, 300f, false, p)
                c.drawLine(cx, cy - h * 0.95f, cx, cy - h * 0.12f, p)
            }
            Glyph.WIFI -> {
                val by = cy + h * 0.62f
                for (k in 1..3) {
                    val rr = h * (0.36f + 0.42f * (k - 1))
                    r.set(cx - rr, by - rr, cx + rr, by + rr)
                    c.drawArc(r, 225f, 90f, false, p)
                }
                p.style = Paint.Style.FILL
                c.drawCircle(cx, by, s * 0.075f, p)
            }
            Glyph.MOON -> {
                p.style = Paint.Style.FILL
                path.reset(); path2.reset()
                path.addCircle(cx, cy, h * 0.82f, Path.Direction.CW)
                path2.addCircle(cx + h * 0.48f, cy - h * 0.36f, h * 0.68f, Path.Direction.CW)
                path.op(path2, Path.Op.DIFFERENCE)
                c.drawPath(path, p)
            }
            Glyph.SPEAKER -> {
                p.style = Paint.Style.FILL
                path.reset()
                path.moveTo(cx - h * 0.9f, cy - h * 0.28f)
                path.lineTo(cx - h * 0.5f, cy - h * 0.28f)
                path.lineTo(cx - h * 0.05f, cy - h * 0.72f)
                path.lineTo(cx - h * 0.05f, cy + h * 0.72f)
                path.lineTo(cx - h * 0.5f, cy + h * 0.28f)
                path.lineTo(cx - h * 0.9f, cy + h * 0.28f)
                path.close()
                c.drawPath(path, p)
                p.style = Paint.Style.STROKE
                for (k in 1..2) {
                    val rr = h * (0.28f + 0.36f * (k - 1))
                    r.set(cx + h * 0.12f - rr, cy - rr, cx + h * 0.12f + rr, cy + rr)
                    c.drawArc(r, -50f, 100f, false, p)
                }
            }
            Glyph.TIMER -> {
                c.drawCircle(cx, cy + h * 0.1f, h * 0.78f, p)
                c.drawLine(cx, cy + h * 0.1f, cx, cy - h * 0.38f, p)
                c.drawLine(cx, cy + h * 0.1f, cx + h * 0.3f, cy + h * 0.3f, p)
                c.drawLine(cx - h * 0.22f, cy - h * 0.92f, cx + h * 0.22f, cy - h * 0.92f, p)
            }
            Glyph.GAMEPAD -> {
                r.set(cx - h * 0.95f, cy - h * 0.48f, cx + h * 0.95f, cy + h * 0.52f)
                c.drawRoundRect(r, h * 0.48f, h * 0.48f, p)
                c.drawLine(cx - h * 0.62f, cy, cx - h * 0.24f, cy, p)
                c.drawLine(cx - h * 0.43f, cy - h * 0.19f, cx - h * 0.43f, cy + h * 0.19f, p)
                p.style = Paint.Style.FILL
                c.drawCircle(cx + h * 0.36f, cy - h * 0.08f, s * 0.055f, p)
                c.drawCircle(cx + h * 0.6f, cy + h * 0.14f, s * 0.055f, p)
            }
            Glyph.ACCESS -> {
                p.style = Paint.Style.FILL
                c.drawCircle(cx, cy - h * 0.72f, s * 0.085f, p)
                p.style = Paint.Style.STROKE
                c.drawLine(cx - h * 0.7f, cy - h * 0.34f, cx + h * 0.7f, cy - h * 0.34f, p)
                c.drawLine(cx, cy - h * 0.34f, cx, cy + h * 0.18f, p)
                c.drawLine(cx, cy + h * 0.18f, cx - h * 0.38f, cy + h * 0.86f, p)
                c.drawLine(cx, cy + h * 0.18f, cx + h * 0.38f, cy + h * 0.86f, p)
            }
            Glyph.LOCK -> {
                r.set(cx - h * 0.42f, cy - h * 0.9f, cx + h * 0.42f, cy + h * 0.1f)
                c.drawArc(r, 180f, 180f, false, p)
                c.drawLine(cx - h * 0.42f, cy - h * 0.4f, cx - h * 0.42f, cy - h * 0.05f, p)
                c.drawLine(cx + h * 0.42f, cy - h * 0.4f, cx + h * 0.42f, cy - h * 0.05f, p)
                p.style = Paint.Style.FILL
                r.set(cx - h * 0.68f, cy - h * 0.12f, cx + h * 0.68f, cy + h * 0.86f)
                c.drawRoundRect(r, h * 0.16f, h * 0.16f, p)
            }
            Glyph.GEAR -> {
                p.style = Paint.Style.FILL
                path.reset(); path2.reset()
                path.addCircle(cx, cy, h * 0.66f, Path.Direction.CW)
                for (k in 0 until 8) {
                    val a = Math.toRadians(k * 45.0)
                    val tx = cx + Math.cos(a).toFloat() * h * 0.74f
                    val ty = cy + Math.sin(a).toFloat() * h * 0.74f
                    path.addCircle(tx, ty, h * 0.2f, Path.Direction.CW)
                }
                path2.addCircle(cx, cy, h * 0.28f, Path.Direction.CW)
                path.op(path2, Path.Op.DIFFERENCE)
                c.drawPath(path, p)
            }
            Glyph.PEOPLE -> {
                p.style = Paint.Style.FILL
                c.drawCircle(cx - h * 0.3f, cy - h * 0.38f, h * 0.27f, p)
                r.set(cx - h * 0.85f, cy + h * 0.02f, cx + h * 0.25f, cy + h * 1.1f)
                c.drawArc(r, 180f, 180f, true, p)
                p.alpha = (p.alpha * 0.6f).toInt()
                c.drawCircle(cx + h * 0.42f, cy - h * 0.48f, h * 0.22f, p)
                r.set(cx + h * 0.02f, cy - h * 0.16f, cx + h * 0.9f, cy + h * 0.72f)
                c.drawArc(r, 180f, 180f, true, p)
            }
            Glyph.PLAY -> {
                p.style = Paint.Style.FILL
                path.reset()
                path.moveTo(cx - h * 0.45f, cy - h * 0.75f)
                path.lineTo(cx + h * 0.75f, cy)
                path.lineTo(cx - h * 0.45f, cy + h * 0.75f)
                path.close()
                c.drawPath(path, p)
            }
            Glyph.PAUSE -> {
                p.style = Paint.Style.FILL
                r.set(cx - h * 0.6f, cy - h * 0.7f, cx - h * 0.18f, cy + h * 0.7f); c.drawRoundRect(r, h * 0.08f, h * 0.08f, p)
                r.set(cx + h * 0.18f, cy - h * 0.7f, cx + h * 0.6f, cy + h * 0.7f); c.drawRoundRect(r, h * 0.08f, h * 0.08f, p)
            }
            Glyph.PREV, Glyph.NEXT -> {
                p.style = Paint.Style.FILL
                val d = if (g == Glyph.NEXT) 1f else -1f
                for (k in 0..1) {
                    val ox = cx + d * (k * h * 0.62f - h * 0.62f)
                    path.reset()
                    path.moveTo(ox, cy - h * 0.55f)
                    path.lineTo(ox + d * h * 0.62f, cy)
                    path.lineTo(ox, cy + h * 0.55f)
                    path.close()
                    c.drawPath(path, p)
                }
            }
            Glyph.SHIELD -> {
                path.reset()
                path.moveTo(cx, cy - h * 0.9f)
                path.lineTo(cx + h * 0.72f, cy - h * 0.6f)
                path.cubicTo(cx + h * 0.72f, cy + h * 0.2f, cx + h * 0.4f, cy + h * 0.66f, cx, cy + h * 0.92f)
                path.cubicTo(cx - h * 0.4f, cy + h * 0.66f, cx - h * 0.72f, cy + h * 0.2f, cx - h * 0.72f, cy - h * 0.6f)
                path.close()
                c.drawPath(path, p)
            }
            Glyph.TOGGLES -> {
                for ((k, on) in listOf(true, false).withIndex()) {
                    val y = cy + (k - 0.5f) * h * 0.95f
                    r.set(cx - h * 0.85f, y - h * 0.3f, cx + h * 0.85f, y + h * 0.3f)
                    c.drawRoundRect(r, h * 0.3f, h * 0.3f, p)
                    p.style = Paint.Style.FILL
                    c.drawCircle(if (on) cx + h * 0.55f else cx - h * 0.55f, y, h * 0.18f, p)
                    p.style = Paint.Style.STROKE
                }
            }
            Glyph.NOTE -> {
                p.style = Paint.Style.FILL
                c.drawCircle(cx - h * 0.42f, cy + h * 0.52f, h * 0.26f, p)
                c.drawCircle(cx + h * 0.5f, cy + h * 0.36f, h * 0.26f, p)
                p.style = Paint.Style.STROKE
                c.drawLine(cx - h * 0.18f, cy + h * 0.52f, cx - h * 0.18f, cy - h * 0.62f, p)
                c.drawLine(cx + h * 0.74f, cy + h * 0.36f, cx + h * 0.74f, cy - h * 0.82f, p)
                c.drawLine(cx - h * 0.18f, cy - h * 0.62f, cx + h * 0.74f, cy - h * 0.82f, p)
            }
            Glyph.CHECK -> {
                path.reset()
                path.moveTo(cx - h * 0.6f, cy + h * 0.02f)
                path.lineTo(cx - h * 0.15f, cy + h * 0.48f)
                path.lineTo(cx + h * 0.66f, cy - h * 0.5f)
                c.drawPath(path, p)
            }
            Glyph.CLOSE -> {
                c.drawLine(cx - h * 0.5f, cy - h * 0.5f, cx + h * 0.5f, cy + h * 0.5f, p)
                c.drawLine(cx + h * 0.5f, cy - h * 0.5f, cx - h * 0.5f, cy + h * 0.5f, p)
            }
            Glyph.OPEN -> {
                c.drawLine(cx - h * 0.45f, cy + h * 0.45f, cx + h * 0.55f, cy - h * 0.55f, p)
                c.drawLine(cx - h * 0.1f, cy - h * 0.55f, cx + h * 0.55f, cy - h * 0.55f, p)
                c.drawLine(cx + h * 0.55f, cy - h * 0.55f, cx + h * 0.55f, cy + h * 0.1f, p)
            }
            Glyph.INPUT -> {
                // A screen with an arrow coming into it from the left.
                r.set(cx - h * 0.4f, cy - h * 0.7f, cx + h * 0.92f, cy + h * 0.6f)
                c.drawRoundRect(r, h * 0.16f, h * 0.16f, p)
                c.drawLine(cx - h * 0.95f, cy - h * 0.05f, cx + h * 0.38f, cy - h * 0.05f, p)
                c.drawLine(cx + h * 0.08f, cy - h * 0.35f, cx + h * 0.38f, cy - h * 0.05f, p)
                c.drawLine(cx + h * 0.08f, cy + h * 0.25f, cx + h * 0.38f, cy - h * 0.05f, p)
            }
        }
        p.style = Paint.Style.FILL
    }
}

/**
 * A Control Center button in tvOS style: a glass circle (or rounded square) with a glyph and a short label under
 * it. Focused it turns white with a dark glyph; [on] (an active toggle) shows a lighter fill. [text] replaces
 * the glyph (the sleep timer's countdown), [image] fills the circle (the avatar).
 */
class CcButton(ctx: Context, private val size: Int, cellW: Int, glyph: Glyph, label: String,
               private val square: Boolean = false, private val labelH: Int = ctx.dpi(30f)) : View(ctx) {
    /** The width it takes in its row: the grid sets it once it knows how many columns it has. */
    var cellW = cellW
        set(v) { if (field != v) { field = v; requestLayout() } }
    var glyph = glyph
        set(v) { if (field != v) { field = v; invalidate() } }
    var label = label
        set(v) { if (field != v) { field = v; invalidate() } }
    var text: String? = null
        set(v) { if (field != v) { field = v; invalidate() } }
    var on = false
        set(v) { if (field != v) { field = v; invalidate() } }
    /** Selected tab: a faint fill even without focus. */
    var current = false
        set(v) { if (field != v) { field = v; invalidate() } }
    var image: Bitmap? = null
        set(v) {
            field = v
            imgPaint.shader = v?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
            invalidate()
        }
    var onClick: (() -> Unit)? = null
    var onFocus: (() -> Unit)? = null

    private val focus = FocusAnim(this, 1.1f) { invalidate() }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gp = Paint()
    private val gl = Paint(Paint.ANTI_ALIAS_FLAG)
    private val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val m = Matrix()
    private val rect = RectF()
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Fonts.medium; textSize = ctx.dp(11.5f); color = Color.WHITE; textAlign = Paint.Align.CENTER
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Fonts.medium; textSize = size * 0.24f; textAlign = Paint.Align.CENTER
    }

    init {
        isFocusable = true
        isClickable = true
        outlineProvider = null
        setOnClickListener { onClick?.invoke() }
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(cellW, size + labelH)
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { pivotX = w / 2f; pivotY = size / 2f }

    override fun onFocusChanged(gained: Boolean, direction: Int, previous: Rect?) {
        super.onFocusChanged(gained, direction, previous)
        focus.set(gained)
        if (gained) onFocus?.invoke()
    }

    override fun onDraw(c: Canvas) {
        val f = focus.amount
        val s = size.toFloat()
        val left = (width - s) / 2f
        rect.set(left, 0f, left + s, s)
        val rad = if (square) s * 0.26f else s / 2
        if (f > 0.01f) {
            c.save(); c.translate(left, 0f)
            GlowCache.draw(c, s, s, rad, dp(8f), Color.BLACK, 0.35f * f, dp(4f), gp)
            c.restore()
        }
        val img = image
        if (img != null && imgPaint.shader != null) {
            val sc = maxOf(s / img.width, s / img.height)
            m.setScale(sc, sc)
            m.postTranslate(left + (s - img.width * sc) / 2, (s - img.height * sc) / 2)
            imgPaint.shader.setLocalMatrix(m)
            c.drawRoundRect(rect, rad, rad, imgPaint)
            if (f > 0.01f || current) {
                gl.style = Paint.Style.STROKE
                gl.strokeWidth = dp(1.5f)
                gl.color = alphaOf(Color.WHITE, maxOf(0.9f * f, if (current) 0.55f else 0f))
                val hs = gl.strokeWidth / 2
                rect.inset(-hs - dp(2f), -hs - dp(2f))
                c.drawRoundRect(rect, rad + dp(2f), rad + dp(2f), gl)
                gl.style = Paint.Style.FILL
            }
        } else {
            val rest = if (on) 0.78f else if (current) 0.4f else 0.14f
            fill.color = alphaOf(Color.WHITE, rest + (0.97f - rest) * f)
            c.drawRoundRect(rect, rad, rad, fill)
            if (f < 0.99f && !on) {
                // Thin rim, as on the other glass.
                gl.style = Paint.Style.STROKE
                gl.strokeWidth = dp(0.6f)
                gl.color = alphaOf(Color.WHITE, 0.22f * (1 - f))
                c.drawRoundRect(rect, rad, rad, gl)
                gl.style = Paint.Style.FILL
            }
            val dark = maxOf(f, if (on) 1f else 0f)
            val col = lerpColor(Color.WHITE, 0xFF1C1C1E.toInt(), dark)
            val t = text
            if (t != null) {
                textPaint.color = col
                c.drawText(t, width / 2f, s / 2 - (textPaint.ascent() + textPaint.descent()) / 2, textPaint)
            } else Glyphs.draw(c, glyph, width / 2f, s / 2, s * 0.42f, col, gl)
        }
        if (label.isNotEmpty() && labelH > 0) {
            labelPaint.alpha = ((0.7f + 0.3f * f) * 255).toInt()
            val l = TextUtils.ellipsize(label, labelPaint, width.toFloat() - dp(4f), TextUtils.TruncateAt.END).toString()
            c.drawText(l, width / 2f, s + labelH * 0.62f + s * 0.05f * f, labelPaint)
        }
    }
}

/**
 * tvOS style Control Center: a Liquid Glass panel that grows out of the profile pill. The time and date head it,
 * with the tabs beside them as round icons (Profile, Controls, and Now Playing and VPN while active); the shown tab
 * fills the rest. Opened with OK on the pill or Menu anywhere on the home. Back leaves a page (Sleep Timer, Inputs,
 * Switch Profile), then closes, and focus returns where it was. The hero is paused while it is open, so the screen
 * only redraws when focus moves.
 */
class ControlCenter(private val home: HomeScreen) : FrameLayout(home.ctx) {
    enum class Tab(val glyph: Glyph) { PROFILE(Glyph.NONE), CONTROLS(Glyph.TOGGLES), NOW_PLAYING(Glyph.NOTE), VPN(Glyph.SHIELD) }

    var isOpen = false
        private set
    /** Where focus was when the panel opened. */
    var returnFocus: View? = null
        private set

    private val ui = Handler(Looper.getMainLooper())
    private val dim = View(context).apply { setBackgroundColor(0x47000000) }
    private val margin = Backdrop.screenW * 48 / 1920
    /** Narrower only on a screen too small for it. */
    private val panelW = minOf(dpi(392f), Backdrop.screenW - 2 * margin)
    private val pad = dpi(22f)
    val panel = object : GlassBox(context, dp(30f)) {
        override fun onDraw(c: Canvas) {
            // The pill's adaptive tint, a little denser: the panel holds text and many buttons.
            val g = Tone.pillGlass(Tone.pill)
            glass.tint = 0.04f + g.tint * 0.5f
            glass.dark = (0.36f + g.dark * 0.7f).coerceAtMost(0.66f)
            super.onDraw(c)
        }
    }.apply {
        glass.sheen = 0.06f
        glass.highlight = 0.45f
        glass.border = 0.12f
        glass.cacheStatic = true
        shadowAlpha = 0.45f
    }
    private val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
    private val clock = TextView(context).apply {
        textSize = 30f; typeface = Fonts.medium; setTextColor(Color.WHITE); includeFontPadding = false; maxLines = 1
    }
    private val date = TextView(context).apply {
        textSize = 13f; typeface = Fonts.regular; setTextColor(0xB3FFFFFF.toInt()); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
    }
    private val datePattern = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM")
    private val tabRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false; gravity = Gravity.CENTER_VERTICAL }
    /** A page's name (Sleep Timer, Inputs, Switch Profile). Tabs have none: their icon is lit instead. */
    private val pageTitle = TextView(context).apply {
        textSize = 15f; typeface = Fonts.medium; setTextColor(0xB3FFFFFF.toInt()); visibility = View.GONE
    }
    private val body = FrameLayout(context).apply { clipChildren = false; clipToPadding = false }

    private val tabButtons = LinkedHashMap<Tab, CcButton>()
    private var tab = Tab.CONTROLS
    /** The body's top line: Up from it goes to the shown tab's icon. */
    private var topRow: List<View> = emptyList()
    /** Where Down from the tabs lands, when not the body's first button (Play/Pause on Now Playing). */
    private var entry: View? = null
    private var timerButton: CcButton? = null
    private var timerOff: RowView? = null
    private var dndButton: CcButton? = null
    private var playing: PlayingViews? = null
    /** A page is shown; Back runs this to return to the tab it came from. */
    private var subView: (() -> Unit)? = null
    private val silhouette by lazy { Profile.silhouette(dpi(64f)) }
    private val sessionCb = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) { ui.post { refreshPlaying(false) } }
        override fun onMetadataChanged(metadata: MediaMetadata?) { ui.post { refreshPlaying(false) } }
        override fun onSessionDestroyed() { ui.post { refreshPlaying(true) } }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!isOpen) return
            updateClock()
            val left = SleepTimer.remaining(context)
            timerButton?.let { b -> b.text = left; b.on = left != null }
            timerOff?.valueView?.let { v -> v.update(left?.let { "$it left" } ?: "") }
            updateProgress()
            ui.postDelayed(this, 1000)
        }
    }

    init {
        visibility = View.GONE
        clipChildren = false
        addView(dim, LayoutParams(-1, -1))
        column.setPadding(pad, dpi(18f), pad, dpi(20f))
        panel.addView(column, LayoutParams(-1, -2))
        val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false; clipToPadding = false }
        val time = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        time.addView(clock, LinearLayout.LayoutParams(-2, -2))
        time.addView(date, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(3f) })
        header.addView(time, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dpi(4f) })
        header.addView(tabRow, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dpi(12f) })
        column.addView(header, LinearLayout.LayoutParams(-1, -2))
        column.addView(pageTitle, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(16f); leftMargin = dpi(4f) })
        column.addView(body, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(16f) })
        addView(panel, LayoutParams(panelW, -2, Gravity.TOP or Gravity.END).apply {
            rightMargin = margin; topMargin = Backdrop.screenH * 36 / 1080
        })
    }

    // ---------- Open and close ----------

    fun open(instant: Boolean = false, from: View? = null) {
        if (isOpen) return
        isOpen = true
        returnFocus = from
        updateClock()
        buildTabs()
        showTab(Tab.CONTROLS)
        visibility = View.VISIBLE
        firstFocusable()?.requestFocus()
        ui.removeCallbacks(tick); ui.post(tick)
        panel.pivotX = panelW.toFloat()
        panel.pivotY = 0f
        if (instant) {
            dim.alpha = 1f; panel.alpha = 1f; panel.scaleX = 1f; panel.scaleY = 1f
            return
        }
        dim.alpha = 0f
        dim.animate().alpha(1f).setDuration(220).start()
        panel.alpha = 0f
        panel.scaleX = 0.35f; panel.scaleY = 0.2f
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260)
            .setInterpolator(OvershootInterpolator(0.9f))
            .setUpdateListener { invalidateDeep(panel) }
            .withEndAction { invalidateDeep(panel) }
            .start()
    }

    fun close(instant: Boolean = false, after: (() -> Unit)? = null) {
        if (!isOpen) return
        isOpen = false
        subView = null
        ui.removeCallbacks(tick)
        unwatchSession()
        val done = {
            visibility = View.GONE
            body.removeAllViews()
            playing = null; timerButton = null; timerOff = null; dndButton = null
            home.onControlCenterClosed(returnFocus)
            returnFocus = null
            after?.invoke()
        }
        if (instant) { done(); return }
        dim.animate().alpha(0f).setDuration(180).start()
        panel.animate().alpha(0f).scaleX(0.35f).scaleY(0.2f).setDuration(190)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .setUpdateListener { invalidateDeep(panel) }
            .withEndAction { done() }
            .start()
    }

    /**
     * Its home is going away, possibly with the panel open (the overlay hands over to MainActivity that way): stops
     * the once-a-second countdown, which would otherwise repost itself for good and keep the home alive, and the
     * media session callback.
     */
    fun release() {
        isOpen = false
        subView = null
        ui.removeCallbacksAndMessages(null)
        unwatchSession()
    }

    /** Back: leaves a page first, then closes. */
    fun back() {
        val s = subView
        if (s != null) { subView = null; s() } else close()
    }

    private fun updateClock() {
        val now = System.currentTimeMillis()
        val fmt = if (android.text.format.DateFormat.is24HourFormat(context)) "H:mm" else "h:mm"
        clock.update(android.text.format.DateFormat.format(fmt, now).toString())
        date.update(android.text.format.DateFormat.format(datePattern, now).toString())
    }

    // ---------- Tabs ----------

    private fun buildTabs() {
        tabRow.removeAllViews()
        tabButtons.clear()
        val tabs = ArrayList<Tab>()
        tabs.add(Tab.PROFILE)
        tabs.add(Tab.CONTROLS)
        if (NowPlaying.current(context) != null) tabs.add(Tab.NOW_PLAYING)
        if (Net.vpnActive(context)) tabs.add(Tab.VPN)
        val sz = dpi(36f)
        for ((i, t) in tabs.withIndex()) {
            val b = CcButton(context, sz, sz, t.glyph, "", labelH = 0)
            b.id = View.generateViewId()
            if (t == Tab.PROFILE) b.image = HomeShare.avatar ?: silhouette
            b.onFocus = { if (!rebuilding && (tab != t || subView != null)) showTab(t) }
            b.onClick = { (entry ?: firstFocusable())?.requestFocus() }
            tabRow.addView(b, LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) leftMargin = dpi(8f) })
            tabButtons[t] = b
        }
        // The body is below and to the left of the icons: Left and Right stay on them.
        tabButtons.values.first().let { it.nextFocusLeftId = it.id }
        tabButtons.values.last().let { it.nextFocusRightId = it.id }
    }

    /**
     * True while the body is rebuilt. Removing the focused button moves focus to the first focusable (the Profile
     * tab), which must not switch tabs in the middle of a rebuild.
     */
    private var rebuilding = false

    private inline fun rebuild(block: () -> Unit) {
        val was = rebuilding
        rebuilding = true
        try { block() } finally { rebuilding = was }
    }

    private fun showTab(t: Tab) = rebuild {
        tab = t
        tabButtons.forEach { (k, b) -> b.current = k == t }
        clearBody(null)
        when (t) {
            Tab.PROFILE -> buildProfile()
            Tab.CONTROLS -> buildControls()
            Tab.NOW_PLAYING -> buildNowPlaying()
            Tab.VPN -> buildVpn()
        }
        linkUp()
        invalidateDeep(panel)
    }

    /** Empties the body for a tab ([title] null) or a page. */
    private fun clearBody(title: String?) {
        subView = null
        pageTitle.text = title ?: ""
        pageTitle.visibility = if (title == null) View.GONE else View.VISIBLE
        body.removeAllViews()
        topRow = emptyList()
        entry = null
        timerButton = null; timerOff = null; dndButton = null; playing = null
        unwatchSession()
    }

    /** A page's content is in: Back runs [back], focus goes to its first button. */
    private fun showPage(back: () -> Unit) {
        subView = back
        linkUp()
        firstFocusable()?.requestFocus()
        invalidateDeep(panel)
    }

    /** Up from the body's top line goes to the shown tab; Down from any tab goes to the body's first button. */
    private fun linkUp() {
        val tabId = tabButtons[tab]?.id ?: View.NO_ID
        val first = entry ?: firstFocusable()
        tabButtons.values.forEach { it.nextFocusDownId = first?.id ?: View.NO_ID }
        topRow.forEach { it.nextFocusUpId = tabId }
    }

    private fun focusables(v: View): List<View> = when {
        v.visibility != View.VISIBLE -> emptyList()
        v is RowView -> if (v.isFocusable) listOf(v) else emptyList()
        v is ViewGroup -> (0 until v.childCount).flatMap { focusables(v.getChildAt(it)) }
        v.isFocusable -> listOf(v)
        else -> emptyList()
    }

    private fun firstFocusable(): View? = focusables(body).firstOrNull()

    /** Focus back on the Controls button a page was opened from. */
    private fun refocus(label: String) {
        focusables(body).firstOrNull { (it as? CcButton)?.label == label }?.requestFocus()
    }

    // ---------- Building blocks ----------

    private val btn get() = dpi(52f)

    /**
     * Four columns, or three when that leaves fewer gaps in the last row: nine buttons make a square rather than
     * two full rows and one button on its own.
     */
    private fun grid(items: List<CcButton>): LinearLayout {
        val cols = if ((3 - items.size % 3) % 3 < (4 - items.size % 4) % 4) 3 else 4
        val cellW = (panelW - 2 * pad) / cols
        items.forEach { it.cellW = cellW }
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        topRow = items.take(cols)
        items.chunked(cols).forEachIndexed { r, row ->
            val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
            row.forEach { line.addView(it, LinearLayout.LayoutParams(-2, -2)) }
            col.addView(line, LinearLayout.LayoutParams(-2, -2).apply { if (r > 0) topMargin = dpi(6f) })
        }
        return col
    }

    /** A round Controls button with its label under it. */
    private fun circle(g: Glyph, label: String, click: () -> Unit) = CcButton(context, btn, (panelW - 2 * pad) / 4, g, label, labelH = dpi(28f)).apply {
        id = View.generateViewId(); onClick = click
    }

    /** A list row, as in a tvOS menu: pages and the Profile and VPN tabs use these. */
    private fun row(title: String, value: String = "", click: () -> Unit) = RowView(context, title).apply {
        id = View.generateViewId()
        titleView.textSize = 17f
        titleView.maxLines = 1
        titleView.ellipsize = TextUtils.TruncateAt.END
        valueView.textSize = 15f
        valueView.text = value
        onClick = click
    }

    /** [rows] one under another, scrolling when the screen is too short for them all. */
    private fun list(rows: List<View>): View {
        val rowH = dpi(46f)
        val gap = dpi(6f)
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        rows.forEachIndexed { i, r -> col.addView(r, LinearLayout.LayoutParams(-1, rowH).apply { if (i > 0) topMargin = gap }) }
        topRow = rows.take(1)
        val maxH = Backdrop.screenH / 2
        if (rows.size * (rowH + gap) <= maxH) return col
        // Inset so a focused row's lift is not cut off by the scroll's edges.
        col.setPadding(dpi(6f), dpi(4f), dpi(6f), dpi(4f))
        return object : ScrollView(context) {
            override fun onMeasure(w: Int, h: Int) = super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST))
        }.apply {
            isFocusable = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(col, ViewGroup.LayoutParams(-1, -2))
        }
    }

    private fun note(text: String) = TextView(context).apply {
        this.text = text; textSize = 15f; typeface = Fonts.regular; setTextColor(0xCCFFFFFF.toInt())
    }

    private fun appLabel(pkg: String): String? = try {
        context.packageManager.let { it.getApplicationLabel(it.getApplicationInfo(pkg, 0)).toString() }
    } catch (e: Throwable) { null }

    private fun open(i: Intent, pkg: String, hint: String? = null) {
        close(instant = true) { home.startIntent(i, pkg, hint) }
    }

    /** A standard Settings page (Android TV and Google TV). */
    private fun openStd(action: String) {
        val i = Intent(action)
        open(i, Platform.handler(context, i))
    }

    // ---------- Controls ----------

    private fun buildControls() {
        val ctx = context
        val fireTv = Platform.isFireTv(ctx)
        // Without device admin at all (the Nvidia Shield) an Android TV app cannot sleep the TV; Fire TV still opens
        // its own sleep menu.
        val canSleep = fireTv || Power.supported(ctx)
        val power = if (!canSleep) null else circle(Glyph.POWER, "Sleep") { close(instant = true) { Power.sleep(ctx) } }
        val wifi = circle(Glyph.WIFI, Net.networkName(ctx)) {
            if (fireTv) open(FireIntents.wifi(), FireIntents.SETTINGS_PKG) else openStd(android.provider.Settings.ACTION_WIFI_SETTINGS)
        }
        val dnd = circle(Glyph.MOON, "Do Not Disturb") { toggleDnd() }.apply { on = Dnd.isOn(ctx) }
        dndButton = dnd
        val audio = circle(Glyph.SPEAKER, "Audio") {
            if (fireTv) open(FireIntents.audio(), FireIntents.SETTINGS_PKG, "Choose Display & Audio") else openStd(android.provider.Settings.ACTION_SOUND_SETTINGS)
        }
        // HDMI and other inputs, only on TVs that have them (Fire TV Edition TVs, the Cube): one opens at once.
        val inputs = TvInputs.list(ctx)
        val input = if (inputs.isEmpty()) null else circle(Glyph.INPUT, "Inputs") {
            val only = inputs.singleOrNull()
            if (only != null) open(only.intent, Platform.handler(ctx, only.intent)) else showInputs(inputs)
        }
        val timer = if (!canSleep) null else circle(Glyph.TIMER, "Sleep Timer") { showTimerChoices() }.apply {
            text = SleepTimer.remaining(ctx); on = text != null
        }
        timerButton = timer
        // Android TV: Bluetooth settings or the "pair remote or accessory" page where GlassOS may open them, else
        // Settings, where Remotes & Accessories is.
        val pads = circle(Glyph.GAMEPAD, "Controllers") {
            val i = if (fireTv) null else Platform.openable(ctx, CONTROLLER_ACTIONS)
            when {
                fireTv -> open(FireIntents.controllers(), FireIntents.SETTINGS_PKG)
                i != null -> open(i, Platform.handler(ctx, i))
                else -> {
                    Toast.makeText(ctx, "Choose Remotes & Accessories", Toast.LENGTH_LONG).show()
                    openStd(android.provider.Settings.ACTION_SETTINGS)
                }
            }
        }
        val acc = circle(Glyph.ACCESS, "Accessibility") {
            if (fireTv) open(FireIntents.accessibility(), FireIntents.SETTINGS_PKG, "Choose Accessibility") else openStd(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }
        // Stock Android TV has no parental controls page of its own.
        val pc = if (!fireTv) null
            else circle(Glyph.LOCK, "Parental") { open(FireIntents.parental(), "com.amazon.tv.parentalcontrols", "Choose Preferences, then Parental Controls") }
        val fire = circle(Glyph.OPEN, "TV Settings") { close(instant = true) { home.openFireSettings() } }
        val gear = circle(Glyph.GEAR, "GlassOS") { close(instant = true) { home.openSettings() } }
        body.addView(grid(listOfNotNull(power, wifi, dnd, audio, input, timer, pads, acc, pc, fire, gear)))
    }

    private fun toggleDnd() {
        val b = dndButton ?: return
        val want = !Dnd.isOn(context)
        if (!Dnd.set(context, want)) {
            Toast.makeText(context, "Do Not Disturb needs notification access (GlassOS Settings > Permissions)", Toast.LENGTH_LONG).show()
            return
        }
        b.on = want
        // The system applies it asynchronously; read it back.
        ui.postDelayed({ if (isOpen) dndButton?.on = Dnd.isOn(context) }, 400)
    }

    /** The TV's inputs, by name; OK opens one in the TV's player, Back returns to Controls. */
    private fun showInputs(inputs: List<TvInputs.Input>) = rebuild {
        clearBody("Inputs")
        val ctx = context
        body.addView(list(inputs.map { inp -> row(inp.label) { open(inp.intent, Platform.handler(ctx, inp.intent)) } }), LayoutParams(-1, -2))
        showPage { showTab(Tab.CONTROLS); refocus("Inputs") }
    }

    private fun showTimerChoices() = rebuild {
        clearBody("Sleep Timer")
        val ctx = context
        val back = { showTab(Tab.CONTROLS); refocus("Sleep Timer") }
        val rows = ArrayList<View>()
        for (m in listOf(15, 30, 60, 90)) {
            rows.add(row(if (m == 60) "1 hour" else "$m minutes") { SleepTimer.start(ctx, m * 60_000L); back() })
        }
        val left = SleepTimer.remaining(ctx)
        if (left != null) rows.add(row("Turn Off", "$left left") { SleepTimer.cancel(ctx); back() }.also { timerOff = it })
        body.addView(list(rows), LayoutParams(-1, -2))
        showPage(back)
    }

    // ---------- Profile ----------

    private fun buildProfile() {
        val ctx = context
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        val who = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val av = dpi(56f)
        who.addView(AvatarView(ctx, av, HomeShare.avatar ?: silhouette), LinearLayout.LayoutParams(av, av))
        who.addView(TextView(ctx).apply {
            text = home.prefs.name.ifEmpty { "Profile" }; textSize = 22f; typeface = Fonts.medium; setTextColor(Color.WHITE)
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dpi(14f) })
        col.addView(who, LinearLayout.LayoutParams(-1, -2).apply { leftMargin = dpi(4f) })
        val rows = ArrayList<View>()
        // Fire TV profiles, or the account and profile settings on Google TV, where the TV has them.
        if (Platform.isFireTv(ctx) || Platform.accountsIntent(ctx) != null) rows.add(row("Switch Profile") { confirmSwitch() })
        rows.add(row("Name and Photo") { close(instant = true) { home.openSettings() } })
        col.addView(list(rows), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(16f) })
        body.addView(col, LayoutParams(-1, -2))
    }

    /** Never leaves for Fire TV profiles without a second, explicit choice (Cancel is focused). The picker itself needs an Amazon signature, so this opens Settings, where the user picks Profiles. */
    private fun confirmSwitch() = rebuild {
        clearBody("Switch Profile")
        val ctx = context
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        col.addView(note(if (Platform.isFireTv(ctx)) "Open Fire TV Profiles?" else "Open ${Platform.name(ctx)} accounts and profiles?"),
            LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dpi(4f) })
        val back = { showTab(Tab.PROFILE); firstFocusable()?.requestFocus(); Unit }
        val go = row("Open") {
            val acc = Platform.accountsIntent(ctx)
            if (Platform.isFireTv(ctx) || acc == null) open(FireIntents.profiles(), "com.amazon.ftv.profilepicker", "Choose Profiles")
            else open(acc, Platform.handler(ctx, acc))
        }
        col.addView(list(listOf(row("Cancel") { back() }, go)), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(14f) })
        body.addView(col, LayoutParams(-1, -2))
        showPage(back)
    }

    // ---------- Now Playing ----------

    private var watched: MediaController? = null

    private fun watch(c: MediaController) {
        unwatchSession()
        watched = c
        try { c.registerCallback(sessionCb, ui) } catch (e: Throwable) { }
    }

    private fun unwatchSession() {
        try { watched?.unregisterCallback(sessionCb) } catch (e: Throwable) { }
        watched = null
    }

    private fun buildNowPlaying() {
        val now = NowPlaying.current(context)
        if (now == null) { body.addView(note("Nothing is playing"), LayoutParams(-2, -2).apply { leftMargin = dpi(4f) }); return }
        watch(now.controller)
        val ctx = context
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        val top = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val artSize = dpi(84f)
        val art = ArtView(ctx, artSize)
        top.addView(art, LinearLayout.LayoutParams(artSize, artSize))
        val text = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val app = TextView(ctx).apply { textSize = 11f; typeface = Fonts.medium; setTextColor(0x99FFFFFF.toInt()); letterSpacing = 0.06f; maxLines = 1 }
        val title = TextView(ctx).apply { textSize = 18f; typeface = Fonts.medium; setTextColor(Color.WHITE); maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        val sub = TextView(ctx).apply { textSize = 14f; setTextColor(0xB3FFFFFF.toInt()); maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        text.addView(app)
        text.addView(title, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(2f) })
        text.addView(sub, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(1f) })
        top.addView(text, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dpi(14f) })
        box.addView(top, LinearLayout.LayoutParams(-1, -2))
        val progress = ProgressLine(ctx)
        box.addView(progress, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(14f) })
        // Each press acts on the session shown now, which may have changed since the tab opened.
        val size = dpi(46f)
        val cellW = dpi(66f)
        val prev = CcButton(ctx, size, cellW, Glyph.PREV, "", labelH = 0).apply {
            id = View.generateViewId(); onClick = { watched?.transportControls?.skipToPrevious() }
        }
        val play = CcButton(ctx, size, cellW, Glyph.PAUSE, "", labelH = 0).apply { id = View.generateViewId(); onClick = { togglePlay() } }
        val next = CcButton(ctx, size, cellW, Glyph.NEXT, "", labelH = 0).apply {
            id = View.generateViewId(); onClick = { watched?.transportControls?.skipToNext() }
        }
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_HORIZONTAL; clipChildren = false; clipToPadding = false }
        row.addView(prev); row.addView(play); row.addView(next)
        box.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(10f) })
        body.addView(box, LayoutParams(-1, -2))
        topRow = listOf(prev, play, next)
        entry = play
        playing = PlayingViews(art, app, title, sub, progress, play)
        fillPlaying(now)
    }

    private fun togglePlay() {
        val c = watched ?: return
        val st = c.playbackState?.state
        if (st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING) c.transportControls.pause() else c.transportControls.play()
    }

    /**
     * The session changed: the card follows its title, art and state, and the session playing now if another took
     * over. A session between tracks (skipping, connecting) keeps the card as it is; one that [ended] with none
     * left says so.
     */
    private fun refreshPlaying(ended: Boolean) {
        if (!isOpen || playing == null) return
        val now = NowPlaying.current(context)
        if (now == null) {
            if (ended) {
                showTab(Tab.NOW_PLAYING)
                tabButtons[Tab.NOW_PLAYING]?.requestFocus()
            }
            return
        }
        if (now.controller.sessionToken != watched?.sessionToken) watch(now.controller)
        fillPlaying(now)
    }

    private fun fillPlaying(now: NowPlaying) {
        val v = playing ?: return
        if (v.pkg != now.pkg) {
            v.pkg = now.pkg
            v.app.update((appLabel(now.pkg) ?: now.pkg).uppercase())
            v.icon = try { context.packageManager.getApplicationIcon(now.pkg) } catch (e: Throwable) { null }
        }
        v.title.update(now.title)
        v.sub.update(now.subtitle)
        v.sub.visibility = if (now.subtitle.isEmpty()) View.GONE else View.VISIBLE
        v.art.set(now.art, v.icon)
        v.play.glyph = if (now.playing) Glyph.PAUSE else Glyph.PLAY
        updateProgress()
    }

    /** Elapsed and remaining time, ticking while it plays. Hidden for live streams and apps that give no duration. */
    private fun updateProgress() {
        val v = playing ?: return
        val st = watched?.playbackState
        val dur = watched?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        if (st == null || dur <= 0L || st.position < 0L) {
            if (v.progress.visibility != View.GONE) v.progress.visibility = View.GONE
            return
        }
        var pos = st.position
        if (st.state == PlaybackState.STATE_PLAYING && st.lastPositionUpdateTime > 0L) {
            pos += ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
        }
        if (v.progress.visibility != View.VISIBLE) v.progress.visibility = View.VISIBLE
        v.progress.set(pos.coerceIn(0L, dur), dur)
    }

    // ---------- VPN ----------

    private fun buildVpn() {
        val ctx = context
        // The first installed VPN app from a list of common ones (Android does not say which app owns the VPN).
        val pkg = VPN_APPS.firstOrNull { try { ctx.packageManager.getApplicationInfo(it, 0); true } catch (e: Throwable) { false } }
        val appName = pkg?.let { appLabel(it) }
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        col.addView(TextView(ctx).apply {
            text = appName ?: "VPN"; textSize = 20f; typeface = Fonts.medium; setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dpi(4f) })
        col.addView(TextView(ctx).apply { text = "Connected"; textSize = 14f; setTextColor(0xFF7CE38B.toInt()) },
            LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dpi(4f); topMargin = dpi(2f) })
        if (pkg != null) {
            val open = row("Open ${appName ?: "VPN"}") { close(instant = true) { home.launch(pkg, null) } }
            col.addView(list(listOf(open)), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(16f) })
        }
        body.addView(col, LayoutParams(-1, -2))
    }
}

/** Sets the text only when it changed: a TextView lays out again on every setText. */
private fun TextView.update(s: String) { if (text.toString() != s) text = s }

/** The Now Playing card's views, refreshed in place when the session changes. */
private class PlayingViews(val art: ArtView, val app: TextView, val title: TextView, val sub: TextView, val progress: ProgressLine, val play: CcButton) {
    var pkg: String? = null
    var icon: android.graphics.drawable.Drawable? = null
}

/** A round avatar for the Profile tab: the photo, or the glass silhouette, made once. */
private class AvatarView(ctx: Context, private val size: Int, img: Bitmap) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        val sc = maxOf(size / img.width.toFloat(), size / img.height.toFloat())
        shader = BitmapShader(img, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setScale(sc, sc); postTranslate((size - img.width * sc) / 2, (size - img.height * sc) / 2) })
        }
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = ctx.dp(1f); color = 0x40FFFFFF }
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(size, size)
    override fun onDraw(c: Canvas) {
        val r = size / 2f
        c.drawCircle(r, r, r, p)
        c.drawCircle(r, r, r - ring.strokeWidth / 2, ring)
    }
}

/** Now Playing artwork, or the app's icon on a glass tile. */
private class ArtView(ctx: Context, private val size: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private var art: Bitmap? = null
    private var icon: android.graphics.drawable.Drawable? = null

    fun set(a: Bitmap?, i: android.graphics.drawable.Drawable?) {
        if (a === art && i === icon) return
        art = a; icon = i
        p.shader = a?.let { b ->
            val s = size.toFloat()
            val sc = maxOf(s / b.width, s / b.height)
            BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply { setScale(sc, sc); postTranslate((s - b.width * sc) / 2, (s - b.height * sc) / 2) })
            }
        }
        invalidate()
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(size, size)

    override fun onDraw(c: Canvas) {
        val s = size.toFloat()
        val rad = dp(12f)
        rect.set(0f, 0f, s, s)
        if (p.shader != null) { p.color = Color.WHITE; c.drawRoundRect(rect, rad, rad, p); return }
        p.color = 0x33FFFFFF
        c.drawRoundRect(rect, rad, rad, p)
        icon?.let {
            val i = (s * 0.2f).toInt()
            it.setBounds(i, i, size - i, size - i)
            it.draw(c)
        }
    }
}

/** Now Playing progress: a thin track, with the elapsed time under its start and the time left under its end. */
private class ProgressLine(ctx: Context) : View(ctx) {
    private var pos = 0L
    private var dur = 0L
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val t = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.medium; textSize = ctx.dp(11f); color = 0x99FFFFFF.toInt() }
    private val r = RectF()

    fun set(position: Long, duration: Long) {
        if (position / 1000 == pos / 1000 && duration == dur) return
        pos = position; dur = duration
        invalidate()
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), dpi(26f))

    override fun onDraw(c: Canvas) {
        val bh = dp(4f)
        val w = width.toFloat()
        r.set(0f, 0f, w, bh)
        p.color = 0x40FFFFFF
        c.drawRoundRect(r, bh / 2, bh / 2, p)
        val f = if (dur > 0L) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
        r.set(0f, 0f, maxOf(bh, w * f), bh)
        p.color = 0xE6FFFFFF.toInt()
        c.drawRoundRect(r, bh / 2, bh / 2, p)
        val y = bh + dp(16f)
        t.textAlign = Paint.Align.LEFT
        c.drawText(time(pos), 0f, y, t)
        t.textAlign = Paint.Align.RIGHT
        c.drawText("-" + time(dur - pos), w, y, t)
    }

    private fun time(ms: Long): String {
        val s = ms.coerceAtLeast(0L) / 1000
        return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
        else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    }
}

/** Common VPN apps on Fire TV and Android TV, in the order the VPN tab looks for them. */
private val VPN_APPS = listOf(
    "com.surfshark.vpnclient.android", "com.nordvpn.android", "com.expressvpn.vpn", "ch.protonvpn.android",
    "com.privateinternetaccess.android", "com.ipvanish.vpn", "com.cyberghostvpn.android", "net.mullvad.mullvadvpn"
)

/** Android TV pages for the Controllers button, in order of preference. */
private val CONTROLLER_ACTIONS = listOf(
    android.provider.Settings.ACTION_BLUETOOTH_SETTINGS,
    "com.google.android.intent.action.CONNECT_INPUT"
)
