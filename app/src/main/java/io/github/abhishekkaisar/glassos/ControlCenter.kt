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
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

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
class CcButton(ctx: Context, private val size: Int, private val cellW: Int, glyph: Glyph, label: String,
               private val square: Boolean = false, private val labelH: Int = ctx.dpi(30f)) : View(ctx) {
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
            val rest = if (on) 0.78f else if (current) 0.26f else 0.14f
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
 * tvOS 26 style Control Center: a Liquid Glass panel that grows out of the profile pill, with a row of round tab
 * icons (Profile, General, and Now Playing and VPN while active) over the tab's buttons. Opened with OK on the
 * pill or Menu anywhere on the home; Back closes it and focus returns where it was. The hero is paused while it
 * is open, so the screen only redraws when focus moves.
 */
class ControlCenter(private val home: HomeScreen) : FrameLayout(home.ctx) {
    enum class Tab(val title: String, val glyph: Glyph) {
        PROFILE("Profile", Glyph.NONE), GENERAL("General", Glyph.TOGGLES), NOW_PLAYING("Now Playing", Glyph.NOTE), VPN("VPN", Glyph.SHIELD)
    }

    var isOpen = false
        private set
    /** Where focus was when the panel opened. */
    var returnFocus: View? = null
        private set

    private val ui = Handler(Looper.getMainLooper())
    private val dim = View(context).apply { setBackgroundColor(0x47000000) }
    private val panelW = dpi(372f)
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
    private val tabRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false; gravity = Gravity.CENTER_VERTICAL }
    private val title = TextView(context).apply {
        textSize = 15f; typeface = Fonts.medium; setTextColor(0xB3FFFFFF.toInt())
    }
    private val body = FrameLayout(context).apply { clipChildren = false; clipToPadding = false }

    private val tabButtons = LinkedHashMap<Tab, CcButton>()
    private var tab = Tab.GENERAL
    private var timerButton: CcButton? = null
    private var dndButton: CcButton? = null
    private var playButton: CcButton? = null
    /** A sub-view (timer choices, profile confirm) is shown; Back returns to the tab's buttons. */
    private var subView: (() -> Unit)? = null
    private var playing: NowPlaying? = null
    private val sessionCb = object : android.media.session.MediaController.Callback() {
        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) { ui.post { refreshPlaying() } }
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) { ui.post { refreshPlaying() } }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!isOpen) return
            timerButton?.let { b -> b.text = SleepTimer.remaining(context); b.on = b.text != null }
            ui.postDelayed(this, 1000)
        }
    }

    init {
        visibility = View.GONE
        clipChildren = false
        addView(dim, LayoutParams(-1, -1))
        val pad = dpi(22f)
        column.setPadding(pad, dpi(18f), pad, dpi(14f))
        panel.addView(column, LayoutParams(-1, -2))
        column.addView(tabRow, LinearLayout.LayoutParams(-1, -2))
        column.addView(title, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(10f); leftMargin = dpi(4f) })
        column.addView(body, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(10f) })
        addView(panel, LayoutParams(panelW, -2, Gravity.TOP or Gravity.END).apply {
            rightMargin = Backdrop.screenW * 48 / 1920; topMargin = Backdrop.screenH * 36 / 1080
        })
    }

    // ---------- Open and close ----------

    fun open(instant: Boolean = false, from: View? = null) {
        if (isOpen) return
        isOpen = true
        returnFocus = from
        buildTabs()
        showTab(Tab.GENERAL)
        visibility = View.VISIBLE
        firstButton()?.requestFocus()
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

    /** Back: leaves a sub-view first, then closes. */
    fun back() {
        val s = subView
        if (s != null) { subView = null; s() } else close()
    }

    // ---------- Tabs ----------

    private fun buildTabs() {
        tabRow.removeAllViews()
        tabButtons.clear()
        playing = NowPlaying.current(context)
        val tabs = ArrayList<Tab>()
        tabs.add(Tab.PROFILE)
        tabs.add(Tab.GENERAL)
        if (playing != null) tabs.add(Tab.NOW_PLAYING)
        if (Net.vpnActive(context)) tabs.add(Tab.VPN)
        val sz = dpi(34f)
        for ((i, t) in tabs.withIndex()) {
            val b = CcButton(context, sz, sz + dpi(8f), t.glyph, "", labelH = 0)
            b.id = View.generateViewId()
            if (t == Tab.PROFILE) b.image = HomeShare.avatar ?: Profile.silhouette(dpi(64f))
            b.onFocus = { if (!rebuilding && (tab != t || subView != null)) showTab(t) }
            b.onClick = { firstButton()?.requestFocus() }
            tabRow.addView(b, LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) leftMargin = dpi(6f) })
            tabButtons[t] = b
        }
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
        subView = null
        tabButtons.forEach { (k, b) -> b.current = k == t }
        title.text = t.title
        body.removeAllViews()
        timerButton = null; dndButton = null; playButton = null
        if (t != Tab.NOW_PLAYING) unwatchSession()
        when (t) {
            Tab.PROFILE -> buildProfile()
            Tab.GENERAL -> buildGeneral()
            Tab.NOW_PLAYING -> buildNowPlaying()
            Tab.VPN -> buildVpn()
        }
        linkUp()
        invalidateDeep(panel)
    }

    /** Up from the buttons' top row goes to the shown tab; Down from any tab goes to the first button. */
    private fun linkUp() {
        val tabId = tabButtons[tab]?.id ?: View.NO_ID
        val first = firstButton()
        tabButtons.values.forEach { it.nextFocusDownId = first?.id ?: View.NO_ID }
        (gridTop ?: buttons(body)).forEach { it.nextFocusUpId = tabId }
        gridTop = null
    }

    /** The first row of the last [grid] built (views are not laid out yet, so rows come from the structure). */
    private var gridTop: List<CcButton>? = null

    private fun buttons(v: View): List<CcButton> = when (v) {
        is CcButton -> listOf(v)
        is ViewGroup -> (0 until v.childCount).flatMap { buttons(v.getChildAt(it)) }
        else -> emptyList()
    }

    private fun firstButton(): CcButton? = buttons(body).firstOrNull()

    // ---------- Grids ----------

    private val btn get() = dpi(46f)
    private val cell get() = (panelW - dpi(44f)) / 4

    private fun grid(items: List<CcButton>, cols: Int = 4): LinearLayout {
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        gridTop = items.take(cols)
        items.chunked(cols).forEachIndexed { r, row ->
            val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
            row.forEach { line.addView(it, LinearLayout.LayoutParams(-2, -2)) }
            col.addView(line, LinearLayout.LayoutParams(-2, -2).apply { if (r > 0) topMargin = dpi(8f) })
        }
        return col
    }

    private fun button(g: Glyph, label: String, click: () -> Unit) = CcButton(context, btn, cell, g, label).apply {
        id = View.generateViewId(); onClick = click
    }

    private fun open(i: Intent, pkg: String, hint: String? = null) {
        close(instant = true) { home.startIntent(i, pkg, hint) }
    }

    private fun buildGeneral() {
        val ctx = context
        val fireTv = Platform.isFireTv(ctx)
        // Without device admin at all (the Nvidia Shield) an Android TV app cannot sleep the TV; Fire TV still opens
        // its own sleep menu.
        val canSleep = fireTv || Power.supported(ctx)
        val power = if (!canSleep) null else button(Glyph.POWER, "Sleep") { close(instant = true) { Power.sleep(ctx) } }
        val wifi = button(Glyph.WIFI, Net.networkName(ctx)) {
            if (fireTv) open(FireIntents.wifi(), FireIntents.SETTINGS_PKG) else openStd(android.provider.Settings.ACTION_WIFI_SETTINGS)
        }
        val dnd = button(Glyph.MOON, "Do Not Disturb") { toggleDnd() }.apply { on = Dnd.isOn(ctx) }
        dndButton = dnd
        val audio = button(Glyph.SPEAKER, "Audio") {
            if (fireTv) open(FireIntents.audio(), FireIntents.SETTINGS_PKG, "Choose Display & Audio") else openStd(android.provider.Settings.ACTION_SOUND_SETTINGS)
        }
        // HDMI and other inputs, only on TVs that have them (Fire TV Edition TVs, the Cube): one opens at once.
        val inputs = TvInputs.list(ctx)
        val input = if (inputs.isEmpty()) null else button(Glyph.INPUT, "Inputs") {
            val only = inputs.singleOrNull()
            if (only != null) open(only.intent, Platform.handler(ctx, only.intent)) else showInputs(inputs)
        }
        val timer = if (!canSleep) null else button(Glyph.TIMER, "Sleep Timer") { showTimerChoices() }.apply {
            text = SleepTimer.remaining(ctx); on = text != null
        }
        timerButton = timer
        // Android TV: Bluetooth settings or the "pair remote or accessory" page where GlassOS may open them, else
        // Settings, where Remotes & Accessories is.
        val pads = button(Glyph.GAMEPAD, "Controllers") {
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
        val acc = button(Glyph.ACCESS, "Accessibility") {
            if (fireTv) open(FireIntents.accessibility(), FireIntents.SETTINGS_PKG, "Choose Accessibility") else openStd(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }
        // Stock Android TV has no parental controls page of its own.
        val pc = if (!fireTv) null
            else button(Glyph.LOCK, "Parental") { open(FireIntents.parental(), "com.amazon.tv.parentalcontrols", "Choose Preferences, then Parental Controls") }
        val fire = button(Glyph.OPEN, "TV Settings") { close(instant = true) { home.openFireSettings() } }
        val gear = button(Glyph.GEAR, "GlassOS") { close(instant = true) { home.openSettings() } }
        body.addView(grid(listOfNotNull(power, wifi, dnd, audio, input, timer, pads, acc, pc, fire, gear)))
    }

    /** The TV's inputs, by name; OK opens one in the TV's player, Back returns to General. */
    private fun showInputs(inputs: List<TvInputs.Input>) = rebuild {
        body.removeAllViews()
        title.text = "Inputs"
        val ctx = context
        val items = inputs.map { inp ->
            CcButton(ctx, btn, cell, Glyph.INPUT, inp.label).apply {
                id = View.generateViewId()
                onClick = { open(inp.intent, Platform.handler(ctx, inp.intent)) }
            }
        }
        body.addView(grid(items))
        linkUp()
        subView = { showTab(Tab.GENERAL) }
        firstButton()?.requestFocus()
        invalidateDeep(panel)
    }

    /** A standard Settings page (Android TV and Google TV). */
    private fun openStd(action: String) {
        val i = Intent(action)
        open(i, Platform.handler(context, i))
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

    private fun showTimerChoices() = rebuild {
        body.removeAllViews()
        title.text = "Sleep Timer"
        val ctx = context
        val running = SleepTimer.endsAt(ctx) != 0L
        val items = ArrayList<CcButton>()
        for (m in listOf(15, 30, 60, 90)) {
            items.add(CcButton(ctx, btn, cell, Glyph.NONE, "$m minutes").apply {
                id = View.generateViewId()
                text = "$m"
                onClick = { SleepTimer.start(ctx, m * 60_000L); showTab(Tab.GENERAL); timerButton?.requestFocus() }
            })
        }
        if (running) items.add(CcButton(ctx, btn, cell, Glyph.CLOSE, "Turn Off").apply {
            id = View.generateViewId()
            onClick = { SleepTimer.cancel(ctx); showTab(Tab.GENERAL); timerButton?.requestFocus() }
        })
        body.addView(grid(items))
        linkUp()
        subView = { showTab(Tab.GENERAL); timerButton?.requestFocus() }
        firstButton()?.requestFocus()
        invalidateDeep(panel)
    }

    private fun buildProfile() {
        val ctx = context
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; clipChildren = false; clipToPadding = false }
        val av = AvatarView(ctx, dpi(84f), home.prefs.name)
        box.addView(av, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(4f) })
        box.addView(TextView(ctx).apply {
            text = home.prefs.name.ifEmpty { "Profile" }; textSize = 24f; typeface = Fonts.medium; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(10f) })
        // Fire TV profiles, or the account and profile settings on Google TV; hidden when this TV has neither.
        if (Platform.isFireTv(ctx) || Platform.accountsIntent(ctx) != null) {
            val sw = button(Glyph.PEOPLE, "Switch Profile") { confirmSwitch() }
            box.addView(sw, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(14f) })
        }
        body.addView(box, LayoutParams(-1, -2))
    }

    /** Never leaves for Fire TV profiles without a second, explicit choice (Cancel is focused). The picker itself needs an Amazon signature, so this opens Settings, where the user picks Profiles. */
    private fun confirmSwitch() = rebuild {
        val ctx = context
        body.removeAllViews()
        title.text = "Switch Profile"
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; clipChildren = false; clipToPadding = false }
        box.addView(TextView(ctx).apply {
            text = if (Platform.isFireTv(ctx)) "Open Fire TV Profiles?" else "Open ${Platform.name(ctx)} accounts and profiles?"; textSize = 17f; typeface = Fonts.regular; setTextColor(0xE6FFFFFF.toInt()); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(6f) })
        val cancel = button(Glyph.CLOSE, "Cancel") { showTab(Tab.PROFILE); firstButton()?.requestFocus() }
        val go = button(Glyph.CHECK, "Open") {
            val acc = Platform.accountsIntent(ctx)
            if (Platform.isFireTv(ctx) || acc == null) open(FireIntents.profiles(), "com.amazon.ftv.profilepicker", "Choose Profiles")
            else open(acc, Platform.handler(ctx, acc))
        }
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        row.addView(cancel); row.addView(go)
        box.addView(row, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(14f) })
        body.addView(box, LayoutParams(-1, -2))
        linkUp()
        subView = { showTab(Tab.PROFILE); firstButton()?.requestFocus() }
        cancel.requestFocus()
        invalidateDeep(panel)
    }

    private var watched: android.media.session.MediaController? = null

    private fun unwatchSession() {
        try { watched?.unregisterCallback(sessionCb) } catch (e: Throwable) { }
        watched = null
    }

    private fun buildNowPlaying() {
        val np = playing ?: NowPlaying.current(context)
        if (np == null) { body.addView(TextView(context).apply { text = "Nothing is playing"; setTextColor(0xB3FFFFFF.toInt()) }); return }
        unwatchSession()
        watched = np.controller
        try { np.controller.registerCallback(sessionCb, ui) } catch (e: Throwable) { }
        val ctx = context
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        val top = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val icon = try { ctx.packageManager.getApplicationIcon(np.pkg) } catch (e: Throwable) { null }
        val artSize = dpi(76f)
        top.addView(ArtView(ctx, artSize, np.art, icon), LinearLayout.LayoutParams(artSize, artSize))
        val text = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val appName = try { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(np.pkg, 0)).toString() } catch (e: Throwable) { np.pkg }
        text.addView(TextView(ctx).apply { this.text = appName.uppercase(); textSize = 11f; typeface = Fonts.medium; setTextColor(0x99FFFFFF.toInt()); letterSpacing = 0.06f })
        text.addView(TextView(ctx).apply { this.text = np.title; textSize = 18f; typeface = Fonts.medium; setTextColor(Color.WHITE); maxLines = 2; ellipsize = TextUtils.TruncateAt.END })
        if (np.subtitle.isNotEmpty()) text.addView(TextView(ctx).apply { this.text = np.subtitle; textSize = 14f; setTextColor(0xB3FFFFFF.toInt()); maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        top.addView(text, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dpi(14f) })
        box.addView(top, LinearLayout.LayoutParams(-1, -2))
        val tc = np.controller.transportControls
        val prev = button(Glyph.PREV, "Previous") { tc.skipToPrevious() }
        val play = button(if (np.playing) Glyph.PAUSE else Glyph.PLAY, if (np.playing) "Pause" else "Play") {
            val p = NowPlaying.current(ctx)?.playing ?: np.playing
            if (p) tc.pause() else tc.play()
        }
        playButton = play
        val next = button(Glyph.NEXT, "Next") { tc.skipToNext() }
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_HORIZONTAL; clipChildren = false; clipToPadding = false }
        row.addView(prev); row.addView(play); row.addView(next)
        box.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(12f) })
        body.addView(box, LayoutParams(-1, -2))
    }

    private fun refreshPlaying() {
        if (!isOpen || tab != Tab.NOW_PLAYING) return
        val np = NowPlaying.current(context) ?: return
        playing = np
        playButton?.let { it.glyph = if (np.playing) Glyph.PAUSE else Glyph.PLAY; it.label = if (np.playing) "Pause" else "Play" }
    }

    private fun buildVpn() {
        val ctx = context
        // The first installed VPN app from a list of common ones (Android does not say which app owns the VPN).
        val pkg = VPN_APPS.firstOrNull { try { ctx.packageManager.getApplicationInfo(it, 0); true } catch (e: Throwable) { false } }
        val appName = pkg?.let { p -> try { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(p, 0)).toString() } catch (e: Throwable) { null } }
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        box.addView(TextView(ctx).apply {
            text = appName ?: "VPN"; textSize = 20f; typeface = Fonts.medium; setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dpi(4f) })
        box.addView(TextView(ctx).apply { text = "Connected"; textSize = 14f; setTextColor(0xFF7CE38B.toInt()) },
            LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dpi(4f); topMargin = dpi(2f) })
        if (pkg != null) {
            val b = button(Glyph.SHIELD, "Open ${appName ?: "VPN"}") { close(instant = true) { home.launch(pkg, null) } }
            box.addView(b, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(12f) })
        }
        body.addView(box, LayoutParams(-1, -2))
    }
}

/** Large round avatar for the Profile tab (the glass silhouette without a photo). */
private class AvatarView(ctx: Context, private val size: Int, private val name: String) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = ctx.dp(1f); color = 0x40FFFFFF }
    private val t = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.medium; textSize = size * 0.42f; color = Color.WHITE; textAlign = Paint.Align.CENTER }
    private val m = Matrix()
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(size, size)
    override fun onDraw(c: Canvas) {
        val r = size / 2f
        val b = HomeShare.avatar
        if (b != null) {
            val sh = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val sc = maxOf(size / b.width.toFloat(), size / b.height.toFloat())
            m.setScale(sc, sc); m.postTranslate((size - b.width * sc) / 2, (size - b.height * sc) / 2)
            sh.setLocalMatrix(m)
            p.shader = sh
            c.drawCircle(r, r, r, p)
        } else {
            Profile.drawSilhouette(c, r, r, r)
        }
        c.drawCircle(r, r, r - ring.strokeWidth / 2, ring)
    }
}

/** Now Playing artwork (or the app's icon on a dark tile). */
private class ArtView(ctx: Context, private val size: Int, private val art: Bitmap?, private val icon: android.graphics.drawable.Drawable?) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val m = Matrix()
    override fun onDraw(c: Canvas) {
        val s = size.toFloat()
        val rad = dp(10f)
        rect.set(0f, 0f, s, s)
        if (art != null) {
            val sh = BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val sc = maxOf(s / art.width, s / art.height)
            m.setScale(sc, sc); m.postTranslate((s - art.width * sc) / 2, (s - art.height * sc) / 2)
            sh.setLocalMatrix(m)
            p.shader = sh
            c.drawRoundRect(rect, rad, rad, p)
            return
        }
        p.shader = null
        p.color = 0x33FFFFFF
        c.drawRoundRect(rect, rad, rad, p)
        icon?.let {
            val i = (s * 0.2f).toInt()
            it.setBounds(i, i, size - i, size - i)
            it.draw(c)
        }
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
