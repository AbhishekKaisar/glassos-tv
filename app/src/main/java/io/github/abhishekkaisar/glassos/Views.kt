package io.github.abhishekkaisar.glassos

import android.animation.ValueAnimator
import android.content.Context
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
import android.text.TextPaint
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Focus animation shared by every focusable glass element: a spring scale plus a 0..1 focus amount. */
class FocusAnim(private val v: View, private val scale: Float, private val onUpdate: (Float) -> Unit) {
    companion object {
        /** Set while a home screen restores carried-over focus: focus changes jump to their end state. */
        @JvmStatic var forceInstant = false
    }
    var amount = 0f
        private set
    private var anim: ValueAnimator? = null

    fun set(focused: Boolean, instant: Boolean = forceInstant) {
        anim?.cancel()
        val target = if (focused) 1f else 0f
        val s = if (focused) scale else 1f
        if (instant) {
            // A scale still animating from an earlier focus change would overwrite a plain assignment on its next
            // frame and leave the view enlarged while unfocused. Queuing the scale again replaces only that part of
            // the running animation (rotation and translation keep going); a zero duration ends it at once.
            v.animate().scaleX(s).scaleY(s).setDuration(0).setUpdateListener(null).start()
            amount = target; v.scaleX = s; v.scaleY = s; onUpdate(amount); return
        }
        v.animate().scaleX(s).scaleY(s)
            .setInterpolator(if (focused) OvershootInterpolator(1.8f) else DecelerateInterpolator())
            .setDuration(if (focused) 340 else 220)
            .setUpdateListener { v.invalidate() }
            .start()
        anim = ValueAnimator.ofFloat(amount, target).apply {
            duration = if (focused) 220 else 180
            addUpdateListener { amount = it.animatedValue as Float; onUpdate(amount) }
            start()
        }
    }
}

/** A FrameLayout drawn as a glass slab with a soft shadow. */
open class GlassBox(ctx: Context, var radius: Float) : FrameLayout(ctx) {
    val glass = GlassPainter()
    var shadowAlpha = 0.38f
    private val rect = RectF()
    private val sp = Paint()

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        glass.stroke = ctx.dp(0.9f)
    }

    override fun onDraw(c: Canvas) {
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        // The glass's backdrop is opaque inside the rounded rect, so the shadow under its middle is never seen.
        GlowCache.draw(c, rect.width(), rect.height(), radius, dp(26f), Color.BLACK, shadowAlpha, dp(12f), sp, hollow = Backdrop.a.bmp != null)
        glass.draw(c, this, rect, radius)
    }
}

/**
 * A tvOS style app tile: the app's TV banner fills the rounded rect edge to edge (or, without a banner,
 * a soft gradient of the icon's colour with the icon centred). Focus scales it with a spring, lifts a
 * soft shadow, sweeps a specular glare across and tilts slightly in the direction focus arrived from.
 */
class TileView(ctx: Context, val cardW: Int, val cardH: Int, private val labelH: Int) : View(ctx) {
    /** Labels sit under every drawer tile (dimmer at rest); the top row has labelH = 0 and shows none. */
    var labelAlpha = 1f
        set(v) { if (field != v) { field = v; invalidate() } }
    var app: AppEntry? = null
        set(v) { field = v; buildShaders(); invalidate() }
    var badge = false
        set(v) { field = v; invalidate() }
    var picked = false
        set(v) { field = v; applyPicked(); invalidate() }
    var onClick: (() -> Unit)? = null
    var onLongPress: (() -> Unit)? = null

    private val radius = ctx.dp(12.5f)
    private val focus = FocusAnim(this, 1.2f) { invalidate() }
    private val card = RectF()
    private val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val glarePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val gp = Paint()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconRect = RectF()
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Fonts.medium
        textSize = ctx.dp(13.5f)
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        setShadowLayer(ctx.dp(4f), 0f, ctx.dp(1f), 0x80000000.toInt())
    }
    private var glare = 0.3f
    private var glareAnim: ValueAnimator? = null
    private var longFired = false
    private var jiggle: ValueAnimator? = null

    init {
        isFocusable = true
        isClickable = true
        outlineProvider = null
        cameraDistance = 9000f * ctx.resources.displayMetrics.density
        setOnClickListener { onClick?.invoke() }
    }

    private fun buildShaders() {
        val a = app ?: return
        val art = a.art
        if (art != null && a.isBanner) {
            val sh = BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val sc = maxOf(cardW / art.width.toFloat(), cardH / art.height.toFloat())
            val m = Matrix()
            m.setScale(sc, sc)
            m.postTranslate((cardW - art.width * sc) / 2f, (cardH - art.height * sc) / 2f)
            sh.setLocalMatrix(m)
            artPaint.shader = sh
        } else {
            artPaint.shader = null
            bgPaint.shader = android.graphics.LinearGradient(0f, 0f, cardW * 0.4f, cardH.toFloat(),
                lerpColor(a.color, Color.WHITE, 0.18f), lerpColor(a.color, Color.BLACK, 0.35f), Shader.TileMode.CLAMP)
        }
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(cardW, cardH + labelH)

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        pivotX = cardW / 2f
        pivotY = cardH / 2f
    }

    override fun onFocusChanged(gained: Boolean, direction: Int, previous: Rect?) {
        super.onFocusChanged(gained, direction, previous)
        translationZ = if (gained) 2f else 0f
        if (!picked) focus.set(gained)
        if (gained) {
            when (direction) {
                View.FOCUS_RIGHT -> tilt(0f, -5f, -0.35f)
                View.FOCUS_LEFT -> tilt(0f, 5f, 0.95f)
                View.FOCUS_DOWN -> tilt(5f, 0f, 0.1f)
                View.FOCUS_UP -> tilt(-5f, 0f, 0.6f)
                else -> tilt(0f, 0f, 0.3f)
            }
        } else {
            animate().rotationX(0f).rotationY(0f)
        }
    }

    /** Start tilted towards where focus came from, then spring back; the glare sweeps across meanwhile. */
    fun tilt(rx: Float, ry: Float, glareFrom: Float) {
        if (FocusAnim.forceInstant) { rotationX = 0f; rotationY = 0f; glare = 0.3f; return }
        rotationX = rx
        rotationY = ry
        animate().rotationX(0f).rotationY(0f)
        glareAnim?.cancel()
        glareAnim = ValueAnimator.ofFloat(glareFrom, 0.3f).apply {
            duration = 520
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { glare = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    /** Jumps to the end state of the current focus, with no spring, tilt or glare sweep. */
    fun settleFocus() {
        if (picked) return
        focus.set(isFocused, instant = true)
        glareAnim?.cancel()
        animate().rotationX(0f).rotationY(0f).setDuration(0).start()
        rotationX = 0f; rotationY = 0f; glare = 0.3f
        invalidate()
    }

    /** A small bump when D-pad is pressed against an edge with nowhere to go. */
    fun nudge(dx: Int, dy: Int) {
        tilt(-dy * 4f, dx * 4f, if (dx > 0) 0.6f else if (dx < 0) 0f else 0.3f)
    }

    private fun applyPicked() {
        if (picked) {
            animate().scaleX(1.22f).scaleY(1.22f).translationY(-dp(8f)).setDuration(200).setUpdateListener { invalidate() }.start()
        } else {
            animate().translationY(0f).setDuration(200).start()
            focus.set(isFocused)
        }
    }

    /**
     * Edit top row jiggles every shelf tile; [gentle] is the slower wiggle of the one tile being moved. Only the
     * view's rotation changes, so the tile is not redrawn per frame.
     */
    fun setJiggle(on: Boolean, gentle: Boolean = false) {
        jiggle?.cancel()
        jiggle = null
        if (!on) { rotation = 0f; return }
        val half = if (gentle) 260L else 150L
        val phase = (Math.random() * 1000).toLong()
        val deg = if (gentle) 1.1f else 1.3f
        jiggle = ValueAnimator.ofFloat(-deg, deg).apply {
            duration = half
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            currentPlayTime = phase % half
            addUpdateListener { rotation = it.animatedValue as Float }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        jiggle?.cancel()
        glareAnim?.cancel()
    }

    /**
     * A hold of OK fires [onLongPress] once the key's own repeats show it held for [HOLD_MS] (a little longer than
     * the system long press, like tvOS); no timer, so a quick click is never mistaken for a hold.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (event.repeatCount == 0) longFired = false
            else if (!longFired && onLongPress != null && event.eventTime - event.downTime >= HOLD_MS) {
                longFired = true
                onLongPress?.invoke()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (!longFired && !event.isCanceled) onClick?.invoke()
            longFired = false
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    companion object {
        const val HOLD_MS = 700L
    }

    override fun onDraw(c: Canvas) {
        val f = if (picked) 1f else focus.amount
        val w = cardW.toFloat()
        val h = cardH.toFloat()
        card.set(0f, 0f, w, h)
        // Blur in 4 px steps: each new blur size is a CPU blur and a texture upload, 6 sizes instead of 21.
        val blur = Math.round((dp(10f) + dp(10f) * f) / 4f) * 4f
        GlowCache.draw(c, w, h, radius, blur, Color.BLACK, 0.30f + 0.40f * f, dp(3f) + dp(12f) * f, gp)

        val a = app
        if (a != null && artPaint.shader != null) {
            c.drawRoundRect(card, radius, radius, artPaint)
        } else {
            if (bgPaint.shader == null) bgPaint.color = 0xFF2A3350.toInt()
            c.drawRoundRect(card, radius, radius, bgPaint)
            val art = a?.art
            if (art != null) {
                val s = h * 0.55f
                iconRect.set((w - s) / 2, (h - s) / 2, (w + s) / 2, (h + s) / 2)
                c.drawBitmap(art, null, iconRect, iconPaint)
            }
        }

        // Specular glare: a soft diagonal band that sweeps with the tilt, stronger when focused.
        val ga = 0.06f + 0.24f * f
        val gx = w * glare
        glarePaint.shader = android.graphics.LinearGradient(gx - w * 0.55f, -h * 0.2f, gx + w * 0.25f, h * 0.9f,
            intArrayOf(0, alphaOf(Color.WHITE, ga), 0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(card, radius, radius, glarePaint)

        if (f > 0.01f) {
            fill.style = Paint.Style.STROKE
            fill.strokeWidth = dp(0.75f)
            fill.color = alphaOf(0xFFD8D8DC.toInt(), 0.9f * f)
            val bs = fill.strokeWidth / 2
            c.drawRoundRect(bs, bs, w - bs, h - bs, radius - bs, radius - bs, fill)
            fill.style = Paint.Style.FILL
        }

        // Thin specular rim, brightest along the top edge.
        rim.strokeWidth = dp(0.8f)
        rim.shader = android.graphics.LinearGradient(0f, 0f, 0f, h,
            intArrayOf(alphaOf(Color.WHITE, 0.22f + 0.35f * f), alphaOf(Color.WHITE, 0.05f + 0.08f * f)), null, Shader.TileMode.CLAMP)
        val hs = rim.strokeWidth / 2
        c.drawRoundRect(hs, hs, w - hs, h - hs, radius - hs, radius - hs, rim)

        if (badge) {
            val r = dp(9f)
            val cx = w - dp(4f)
            val cy = dp(4f)
            fill.color = 0xFF0A84FF.toInt()
            c.drawCircle(cx, cy, r, fill)
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1.8f); color = Color.WHITE }
            val p = Path()
            p.moveTo(cx - r * 0.45f, cy + r * 0.02f)
            p.lineTo(cx - r * 0.1f, cy + r * 0.38f)
            p.lineTo(cx + r * 0.5f, cy - r * 0.32f)
            c.drawPath(p, ring)
        }

        if (a != null && labelH > 0 && labelAlpha > 0.004f) {
            text.alpha = ((0.72f + 0.28f * f) * labelAlpha * 255).toInt()
            // At most 1.1x the card: wider labels on the first column ran off the screen's left edge when focused.
            val label = TextUtils.ellipsize(a.label, text, w * 1.1f, TextUtils.TruncateAt.END).toString()
            // Label sits below the scaled tile: compensate so it clears the 1.15x card.
            c.drawText(label, w / 2f, h + labelH * 0.62f + h * 0.06f * f, text)
        }
    }
}

/** tvOS 26 profile capsule: the time on the left, the round avatar at the right end. OK opens Control Center. */
class UserPill(ctx: Context) : View(ctx) {
    var onClick: (() -> Unit)? = null
    var onLongPress: (() -> Unit)? = null
    private var longFired = false
    /** Not shown in the pill (tvOS shows only the avatar); kept for accessibility. */
    var name = ""
        set(v) { field = v; invalidate() }
    var avatar: Bitmap? = null
        set(v) {
            field = v
            avatarPaint.shader = v?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
            invalidate()
        }
    var time = ""
        set(v) { if (v != field) { field = v; requestLayout(); invalidate() } }

    private val glass = GlassPainter().apply {
        tint = 0.20f; tintColor = 0xFFFFF4E8.toInt(); sheen = 0.10f; highlight = 0.40f; border = 0.14f; stroke = ctx.dp(0.6f)
    }
    private val focus = FocusAnim(this, 1.08f) { invalidate() }
    private val rect = RectF()
    private val gp = Paint()
    private val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val circle = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val m = Matrix()
    private val timeText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Fonts.medium; textSize = ctx.dp(13f); color = Color.WHITE
    }
    private val initialText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Fonts.medium; textSize = ctx.dp(12f); color = Color.WHITE; textAlign = Paint.Align.CENTER
    }
    private var shadowStep = -1
    private val h = ctx.dpi(29f)
    private val av = ctx.dp(24f)
    private val inset = ctx.dp(2.5f)
    private val padL = ctx.dp(11f)
    private val gapT = ctx.dp(7f)

    init {
        isFocusable = true
        isClickable = true
        outlineProvider = null
        setOnClickListener { onClick?.invoke() }
    }

    override fun onMeasure(w: Int, hs: Int) {
        val width = padL + timeText.measureText(time) + gapT + av + inset
        setMeasuredDimension(Math.ceil(width.toDouble()).toInt(), h)
    }

    override fun onSizeChanged(w: Int, hh: Int, ow: Int, oh: Int) {
        pivotX = w.toFloat()
        pivotY = hh / 2f
    }

    // OK opens Control Center; a long press opens Glass settings.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (event.repeatCount == 0) { longFired = false; event.startTracking() }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) && onLongPress != null) {
            longFired = true
            onLongPress?.invoke()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (!longFired && !event.isCanceled) onClick?.invoke()
            longFired = false
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onFocusChanged(gained: Boolean, direction: Int, previous: Rect?) {
        super.onFocusChanged(gained, direction, previous)
        focus.set(gained)
    }

    override fun onDraw(c: Canvas) {
        val f = focus.amount
        val r = height / 2f
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        GlowCache.draw(c, rect.width(), rect.height(), r, dp(8f), Color.BLACK, 0.22f + 0.2f * f, dp(3f), gp)
        GlowCache.draw(c, rect.width(), rect.height(), r, dp(10f), Color.WHITE, 0.28f * f, 0f, gp)
        // Adapts to the art behind it: light glass on dark art, a darker glass with a softer rim on bright art.
        val g = Tone.pillGlass(Tone.pill)
        glass.tint = g.tint + 0.15f * f
        glass.dark = g.dark
        glass.highlight = 0.40f - 0.18f * g.t
        glass.border = 0.14f - 0.06f * g.t
        glass.draw(c, this, rect, r)

        val cy = height / 2f
        // Shadow opacity in 8 steps (a new text shadow every frame is a CPU blur per frame).
        val q = (g.shadow * 8).toInt()
        if (q != shadowStep) {
            shadowStep = q
            if (q > 0) timeText.setShadowLayer(dp(3f), 0f, dp(0.5f), alphaOf(Color.BLACK, 0.35f * q / 8f)) else timeText.clearShadowLayer()
        }
        c.drawText(time, padL, cy - (timeText.ascent() + timeText.descent()) / 2, timeText)

        val cx = width - inset - av / 2
        val bmp = avatar
        if (bmp != null && avatarPaint.shader != null) {
            // Centre-crop into the circle: a square photo shows whole.
            val sc = maxOf(av / bmp.width, av / bmp.height)
            m.setScale(sc, sc)
            m.postTranslate(cx - bmp.width * sc / 2, cy - bmp.height * sc / 2)
            avatarPaint.shader.setLocalMatrix(m)
            c.drawCircle(cx, cy, av / 2, avatarPaint)
        } else {
            Profile.drawSilhouette(c, cx, cy, av / 2)
        }
        ring.strokeWidth = dp(0.6f)
        ring.color = 0x33FFFFFF
        c.drawCircle(cx, cy, av / 2 - ring.strokeWidth / 2, ring)
        if (f > 0.01f) {
            ring.strokeWidth = dp(1.2f)
            ring.color = alphaOf(Color.WHITE, 0.85f * f)
            val hs = ring.strokeWidth / 2
            c.drawRoundRect(hs, hs, width - hs, height - hs, r - hs, r - hs, ring)
        }
    }
}

/** Thin "︿" hint above the shelf: Up goes to the Top Shelf. */
class Chevron(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = ctx.dp(1.3f); color = 0x66FFFFFF; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = ctx.dp(3.2f); color = Color.WHITE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        path.reset()
        path.moveTo(w * 0.1f, h * 0.75f)
        path.lineTo(w * 0.5f, h * 0.25f)
        path.lineTo(w * 0.9f, h * 0.75f)
    }
    override fun onDraw(c: Canvas) {
        // White on dark art, easing to a dark stroke with a light halo on bright art.
        val t = Tone.brightness(Tone.chevron)
        if (t > 0.01f) {
            // A faint light halo under the dark stroke (a wider stroke, not a shadow layer, which re-blurs per frame).
            halo.alpha = (0.22f * t * 255).toInt()
            c.drawPath(path, halo)
        }
        p.color = lerpColor(0x66FFFFFF, 0x8C1C1F26.toInt(), t)
        c.drawPath(path, p)
    }
}

/** A settings row in tvOS style: transparent at rest, white with dark text when focused. */
class RowView(ctx: Context, title: String) : LinearLayout(ctx) {
    val titleView = TextView(ctx)
    val valueView = TextView(ctx)
    var onClick: (() -> Unit)? = null
    private val focus = FocusAnim(this, 1.03f) { f -> applyColours(f); invalidate() }
    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gp = Paint()

    init {
        orientation = HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        setWillNotDraw(false)
        val pad = ctx.dpi(20f)
        setPadding(pad, 0, pad, 0)
        titleView.text = title
        titleView.textSize = 19f
        titleView.typeface = Fonts.regular
        valueView.textSize = 17f
        valueView.typeface = Fonts.regular
        addView(titleView, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(valueView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        applyColours(0f)
        setOnClickListener { onClick?.invoke() }
    }

    private fun applyColours(f: Float) {
        titleView.setTextColor(lerpColor(0xF2FFFFFF.toInt(), 0xFF101218.toInt(), f))
        valueView.setTextColor(lerpColor(0x99FFFFFF.toInt(), 0xFF50545E.toInt(), f))
    }

    override fun onFocusChanged(gained: Boolean, direction: Int, previous: Rect?) {
        super.onFocusChanged(gained, direction, previous)
        focus.set(gained)
    }

    override fun onDraw(c: Canvas) {
        val f = focus.amount
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        val r = dp(16f)
        GlowCache.draw(c, rect.width(), rect.height(), r, dp(10f), Color.BLACK, 0.25f * f, dp(5f), gp)
        fill.color = alphaOf(Color.WHITE, 0.07f + 0.86f * f)
        c.drawRoundRect(rect, r, r, fill)
    }
}

/** Wallpaper thumbnail in the background picker. */
class ThumbView(ctx: Context, private val tw: Int, private val th: Int, val wallId: String, private val label: String) : View(ctx) {
    var bitmap: Bitmap? = null
        set(v) {
            field = v
            shaderPaint.shader = v?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
            invalidate()
        }
    var chosen = false
        set(v) { field = v; invalidate() }
    var onClick: (() -> Unit)? = null
    private val labelH = ctx.dpi(24f)
    private val focus = FocusAnim(this, 1.1f) { invalidate() }
    private val rect = RectF()
    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val gp = Paint()
    private val m = Matrix()
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Fonts.regular; textSize = ctx.dp(13f); color = Color.WHITE; textAlign = Paint.Align.CENTER
    }

    init {
        isFocusable = true
        isClickable = true
        outlineProvider = null
        setOnClickListener { onClick?.invoke() }
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(tw, th + labelH)
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { pivotX = tw / 2f; pivotY = th / 2f }

    override fun onFocusChanged(gained: Boolean, direction: Int, previous: Rect?) {
        super.onFocusChanged(gained, direction, previous)
        translationZ = if (gained) 2f else 0f
        focus.set(gained)
    }

    override fun onDraw(c: Canvas) {
        val f = focus.amount
        val r = dp(14f)
        rect.set(0f, 0f, tw.toFloat(), th.toFloat())
        GlowCache.draw(c, rect.width(), rect.height(), r, dp(10f), Color.BLACK, 0.3f + 0.3f * f, dp(5f), gp)
        GlowCache.draw(c, rect.width(), rect.height(), r, dp(10f), Color.WHITE, 0.3f * f, 0f, gp)
        val b = bitmap
        if (b != null) {
            val s = maxOf(tw / b.width.toFloat(), th / b.height.toFloat())
            m.setScale(s, s)
            m.postTranslate((tw - b.width * s) / 2, (th - b.height * s) / 2)
            shaderPaint.shader?.setLocalMatrix(m)
            c.drawRoundRect(rect, r, r, shaderPaint)
        } else {
            fill.color = 0x33FFFFFF
            c.drawRoundRect(rect, r, r, fill)
        }
        ring.strokeWidth = dp(if (chosen) 2.2f else 1f)
        ring.color = if (chosen) Color.WHITE else alphaOf(Color.WHITE, 0.25f + 0.6f * f)
        val h = ring.strokeWidth / 2
        c.drawRoundRect(h, h, tw - h, th - h, r - h, r - h, ring)
        if (chosen) {
            val cr = dp(10f)
            val cx = tw - dp(14f)
            val cy = th - dp(14f)
            fill.color = Color.WHITE
            c.drawCircle(cx, cy, cr, fill)
            ring.strokeWidth = dp(2f)
            ring.color = 0xFF101218.toInt()
            val p = Path()
            p.moveTo(cx - cr * 0.45f, cy + cr * 0.02f)
            p.lineTo(cx - cr * 0.1f, cy + cr * 0.38f)
            p.lineTo(cx + cr * 0.5f, cy - cr * 0.32f)
            c.drawPath(p, ring)
        }
        text.alpha = ((0.65f + 0.35f * f) * 255).toInt()
        val t = TextUtils.ellipsize(label, text, tw.toFloat(), TextUtils.TruncateAt.END).toString()
        c.drawText(t, tw / 2f, th + labelH * 0.72f + dp(2f) * f, text)
    }
}
