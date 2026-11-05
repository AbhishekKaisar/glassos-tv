package io.github.abhishekkaisar.glassos

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.Choreographer
import android.view.KeyEvent
import android.view.View
import java.util.concurrent.Executors

/**
 * Full-screen rotating Top Shelf. Draws the current slide and, during a crossfade, the next one over it,
 * then the bottom gradient, title logo and page dots. A Choreographer callback advances the state before
 * each traversal and moves the shared blurred backdrop with it, so the glass follows without any
 * per-frame blur. At most 3 decoded slides are held.
 */
class HeroView(ctx: Context, private val loader: (Slide) -> Loaded?) : View(ctx), Choreographer.FrameCallback {
    /** Text of the capsule shown under a title while the hero has focus. */
    var openLabel: (Slide) -> String = { "Open" }

    var slides: List<Slide> = emptyList()
        private set
    /** Top of the glass shelf in screen pixels; the logo and dots sit above it. */
    var shelfTop = 0f
    var leftX = 0f
    /** Top of the title logo (tvOS 26 puts it top-left). */
    var topY = 0f
    /** Off: a slow zoom redraws the whole screen every frame for 10 of every 10.2 s, which this GPU cannot afford. */
    var kenBurns = false
    var onOpen: ((Slide) -> Unit)? = null
    var onFocusDown: (() -> Unit)? = null

    private val cache = LinkedHashMap<String, Loaded>()
    private var cur: Loaded? = null
    private var next: Loaded? = null
    private var curIdx = 0
    private var fadeStart = 0L
    private var fade = 0f
    private var curShownClock = 0L
    private val loading = HashSet<String>()
    private val exec = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var frameQueued = false
    private var gen = 0

    private var running = false
    private var clockBase = 0L
    private var resumedAt = 0L
    private fun clock(): Long = clockBase + if (running) SystemClock.uptimeMillis() - resumedAt else 0L

    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val gradPaint = Paint()
    private val logoPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Fonts.medium; color = Color.WHITE; textSize = ctx.dp(30f)
        setShadowLayer(ctx.dp(10f), 0f, ctx.dp(1f), 0x99000000.toInt())
    }
    private val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Fonts.medium; color = 0xD9FFFFFF.toInt(); textSize = ctx.dp(13f)
        setShadowLayer(ctx.dp(6f), 0f, ctx.dp(1f), 0x80000000.toInt())
    }
    private val hintPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Fonts.medium; color = 0xFF111318.toInt(); textSize = ctx.dp(12f)
    }
    private var subShadowStep = -1
    private val kCur = Matrix()
    private val kNext = Matrix()
    private val rect = RectF()
    private val tmpRect = Rect()
    private var gradH = 0
    private val focus = FocusAnim(this, 1f) { invalidate() }

    init {
        isFocusable = true
        setWillNotDraw(false)
        live.add(this)
    }

    private fun holds(l: Loaded) = l === cur || l === next || l === warm || cache.containsValue(l)

    // ---------- Public control ----------

    /** Starts or pauses rotation and Ken Burns (paused off the home row, under Settings, or in the background). */
    fun setRunning(on: Boolean) {
        if (on == running) return
        if (on) { resumedAt = SystemClock.uptimeMillis(); running = true }
        else { clockBase = clock(); running = false }
        kick()
    }

    /** Replaces the slide list. Keeps the shown slide if it is still in the list, else fades to the first. */
    fun setSlides(list: List<Slide>) {
        val same = list.map { it.key } == slides.map { it.key }
        slides = list
        if (same && cur != null) return
        gen++
        val keep = cur?.slide?.key?.let { k -> list.indexOfFirst { it.key == k } } ?: -1
        if (keep >= 0) {
            curIdx = keep
            cur?.index = keep
            prefetch((keep + 1) % list.size)
        } else if (list.isNotEmpty()) {
            goTo(0)
        }
        invalidate()
    }

    fun step(dir: Int) {
        if (slides.size < 2) return
        goTo(((curIdx + dir) % slides.size + slides.size) % slides.size)
    }

    fun current(): Slide? = cur?.slide

    /** Debug: fade to slide [i] now. */
    fun jumpTo(i: Int) { if (i in slides.indices) goTo(i) }

    /**
     * Shows exactly what [o] shows (same slide, crossfade and timing) using its decoded slides, which are shared,
     * not copied. Used when one home screen takes over from the other.
     */
    fun adopt(o: HeroView) {
        gen++
        pendingIdx = -1
        slides = o.slides
        val old = ArrayList(cache.values).apply { add(cur); add(next) }
        cache.clear()
        cache.putAll(o.cache)
        cur = o.cur
        next = o.next
        curIdx = o.curIdx
        fade = o.fade
        fadeStart = o.fadeStart
        curShownClock = o.curShownClock
        clockBase = o.clock()
        resumedAt = SystemClock.uptimeMillis()
        updateBackdrop()
        Backdrop.changed()
        invalidate()
        kick()
        old.forEach { if (it != null) drop(it) }
    }

    // ---------- Loading ----------

    private fun prefetch(i: Int) {
        if (released) return
        val s = slides.getOrNull(i) ?: return
        if (cache.containsKey(s.key) || s.key in loading) return
        loading.add(s.key)
        val g = gen
        exec.execute {
            val l = loader(s)
            handler.post {
                loading.remove(s.key)
                if (l == null) return@post
                cache[s.key] = l
                trim()
                if (l === cache[slides.getOrNull((curIdx + 1) % slides.size.coerceAtLeast(1))?.key]) kick()
                if (g == gen && pendingIdx >= 0 && slides.getOrNull(pendingIdx)?.key == s.key) {
                    val p = pendingIdx
                    pendingIdx = -1
                    beginFade(l, p)
                } else kick()
            }
        }
    }

    private var pendingIdx = -1
    private var warm: Loaded? = null

    private fun goTo(i: Int) {
        val s = slides.getOrNull(i) ?: return
        val l = cache[s.key]
        if (l != null) { pendingIdx = -1; beginFade(l, i) }
        else { pendingIdx = i; prefetch(i) }
    }

    private fun beginFade(l: Loaded, i: Int) {
        if (next != null) finishFade()
        if (l === cur) { curIdx = i; return }
        l.index = i
        l.bornClock = clock()
        next = l
        fadeStart = SystemClock.uptimeMillis()
        fade = 0f
        curShownClock = clock()
        kick()
    }

    private fun finishFade() {
        val n = next ?: return
        cur = n
        curIdx = n.index
        next = null
        fade = 0f
        trim()
        if (slides.size > 1) prefetch((curIdx + 1) % slides.size)
    }

    /** Keeps the shown slide, the incoming one and the prefetched next one; drops the rest. */
    private fun trim() {
        if (cache.size <= 3) return
        val nextKey = slides.getOrNull((curIdx + 1) % slides.size.coerceAtLeast(1))?.key
        val keep = setOf(cur?.slide?.key, next?.slide?.key, nextKey)
        val it = cache.entries.iterator()
        while (it.hasNext() && cache.size > 3) {
            val e = it.next()
            if (e.key !in keep) { it.remove(); drop(e.value) }
        }
    }

    // ---------- Frame loop ----------

    private fun kick() {
        if (frameQueued || released) return
        frameQueued = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    private val wake = Runnable { kick() }

    override fun doFrame(frameTimeNanos: Long) {
        frameQueued = false
        if (released) return
        handler.removeCallbacks(wake)
        val now = SystemClock.uptimeMillis()
        if (next != null) {
            fade = ((now - fadeStart) / FADE_MS.toFloat()).coerceIn(0f, 1f)
            if (fade >= 1f) finishFade()
        }
        val t = clock()
        var waitMs = -1L
        if (next == null && running && slides.size > 1 && cur != null) {
            val left = HOLD_MS - (t - curShownClock)
            if (left <= 0) {
                val ni = (curIdx + 1) % slides.size
                val l = cache[slides[ni].key]
                if (l != null) beginFade(l, ni) else { prefetch(ni); waitMs = 400 }
            } else {
                waitMs = left
                if (left < 3000) prefetch((curIdx + 1) % slides.size)
            }
        }
        // Upload the next slide's texture while the screen is still, so the crossfade's first frame has no 8 MB upload.
        if (next == null && slides.size > 1) {
            val nl = cache[slides[(curIdx + 1) % slides.size].key]
            if (nl != null && !nl.warmed && nl !== cur) warm = nl
        }
        val kb = running && kenBurns && (kbActive(cur) || kbActive(next))
        updateBackdrop()
        invalidate()
        Backdrop.frame()
        if (next != null || kb) kick()
        else if (waitMs >= 0) handler.postDelayed(wake, waitMs)
    }

    private fun kbActive(l: Loaded?): Boolean = l != null && l.slide.kind == Slide.PHOTO && clock() - l.bornClock < KB_MS

    private fun kbMatrix(l: Loaded, out: Matrix) {
        out.reset()
        if (!kenBurns || l.slide.kind != Slide.PHOTO) return
        val p = ((clock() - l.bornClock) / KB_MS.toFloat()).coerceIn(0f, 1f)
        val e = p * p * (3 - 2 * p) * 0.35f + p * 0.65f
        val s = 1f + 0.07f * e
        val w = width.coerceAtLeast(1).toFloat()
        val h = height.coerceAtLeast(1).toFloat()
        out.setScale(s, s, if (l.index % 2 == 0) w * 0.3f else w * 0.7f, h * 0.45f)
    }

    private fun updateBackdrop() {
        val c = cur
        val n = next
        Tone.blend(c, n, if (n != null) fade else 0f)
        if (c != null) kbMatrix(c, kCur)
        if (n != null) kbMatrix(n, kNext)
        when {
            c != null -> {
                Backdrop.place(Backdrop.a, c.blurred, kCur, 1f)
                Backdrop.place(Backdrop.b, n?.blurred, kNext, fade)
            }
            n != null -> {
                Backdrop.place(Backdrop.a, n.blurred, kNext, 1f)
                Backdrop.place(Backdrop.b, null, null, 0f)
            }
        }
    }

    // ---------- Drawing ----------

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        // A soft dark wash in the top-left corner keeps the title logo readable on bright art.
        gradH = (h * 0.5f).toInt()
        gradPaint.shader = washPaint(w).shader
    }

    override fun onDraw(c: Canvas) {
        val a = cur?.takeIf { !it.sharp.isRecycled }
        val b = next?.takeIf { !it.sharp.isRecycled }
        warm?.takeIf { !it.sharp.isRecycled }?.let { w ->
            // One pixel at alpha 1/255: uploads the texture without anything visible.
            bmpPaint.alpha = 1
            rect.set(0f, 0f, 1f, 1f)
            c.drawBitmap(w.sharp, null, rect, bmpPaint)
            w.warmed = true
            warm = null
        }
        // Without art under it (first load), the night colour; the art itself is opaque and covers the screen.
        if (a == null) c.drawColor(NIGHT)
        if (a == null && b == null) return
        val f = if (b != null) fade else 0f
        if (a != null) { kbMatrix(a, kCur); bmpPaint.alpha = 255; c.drawBitmap(a.sharp, kCur, bmpPaint) }
        if (b != null) { kbMatrix(b, kNext); bmpPaint.alpha = (f * 255).toInt(); c.drawBitmap(b.sharp, kNext, bmpPaint) }
        // Title slides get a soft dark gradient at the bottom; it fades with its slide.
        // Decoded title art has the wash baked in (HeroRepo.bakeWash); only unbaked title slides draw it here.
        fun needsWash(l: Loaded?) = l?.slide?.kind == Slide.TITLE && !l.washed
        val ga = (if (needsWash(a)) 1f - f else 0f) + (if (needsWash(b)) f else 0f)
        if (ga > 0.004f) {
            gradPaint.alpha = (ga.coerceIn(0f, 1f) * 255).toInt()
            // The whole radius, not a clipped rect: the wash fades out on its own (a rect cut it off in a visible
            // full-width line at y = 0.62 h, just above the shelf, and a vertical one at x = 0.62 w).
            val rad = width * 0.62f
            c.drawRect(0f, 0f, rad, minOf(rad, height.toFloat()), gradPaint)
        }
        if (a != null) drawTitle(c, a, 1f - f)
        if (b != null) drawTitle(c, b, f)
    }

    private fun drawTitle(c: Canvas, l: Loaded, alpha: Float) {
        if (l.slide.kind != Slide.TITLE || alpha <= 0.004f) return
        val fa = focus.amount
        val logo = l.logo
        val lift = 1f + 0.05f * fa
        var bottom: Float
        c.save()
        c.scale(lift, lift, leftX, topY)
        if (logo != null) {
            logoPaint.alpha = (alpha * 255).toInt()
            rect.set(leftX, topY, leftX + logo.width, topY + logo.height)
            c.drawBitmap(logo, null, rect, logoPaint)
            bottom = rect.bottom
        } else {
            titlePaint.alpha = (alpha * 255).toInt()
            val t = TextUtils.ellipsize(l.slide.title, titlePaint, width * 0.45f, TextUtils.TruncateAt.END).toString()
            c.drawText(t, leftX, topY - titlePaint.ascent(), titlePaint)
            bottom = topY + titlePaint.descent() - titlePaint.ascent()
        }
        c.restore()
        bottom = topY + (bottom - topY) * lift
        // Subtitle under the logo, or when the hero has focus a small white capsule inviting OK.
        val subY = bottom + dp(20f)
        if (fa > 0.01f) {
            val label = openLabel(l.slide)
            hintPaint.getTextBounds(label, 0, label.length, tmpRect)
            val ph = dp(26f)
            val pw = tmpRect.width() + dp(28f)
            rect.set(leftX, subY - ph + dp(7f), leftX + pw, subY + dp(7f))
            pillPaint.color = Color.WHITE
            pillPaint.alpha = (alpha * fa * 242).toInt()
            c.drawRoundRect(rect, ph / 2, ph / 2, pillPaint)
            hintPaint.alpha = (alpha * fa * 255).toInt()
            c.drawText(label, leftX + dp(14f), rect.centerY() - tmpRect.exactCenterY(), hintPaint)
        }
        if (fa < 0.99f && l.slide.subtitle.isNotEmpty()) {
            // Over bright art the soft shadow deepens so the line stays readable.
            // Only the shadow's opacity changes, in 8 steps: HWUI caches text shadows by radius and colour, and a
            // new shadow every frame of a crossfade costs a CPU blur per frame.
            val q = (Tone.brightness(Tone.subtitle) * 8).toInt()
            if (q != subShadowStep) {
                subShadowStep = q
                subPaint.setShadowLayer(dp(6f), 0f, dp(1f), alphaOf(android.graphics.Color.BLACK, 0.5f + 0.35f * q / 8f))
            }
            subPaint.alpha = (alpha * (1f - fa) * 217).toInt()
            c.drawText(l.slide.subtitle, leftX, subY, subPaint)
        }
    }

    // ---------- Focus and keys ----------

    override fun onFocusChanged(gained: Boolean, direction: Int, previous: Rect?) {
        super.onFocusChanged(gained, direction, previous)
        focus.set(gained)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { step(-1); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { step(1); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { onFocusDown?.invoke(); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (event.repeatCount == 0) cur?.slide?.let { onOpen?.invoke(it) }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private var released = false

    /** Stops the frame loop and loading for good. Its decoded slides are recycled once no other hero holds them. */
    fun release() {
        released = true
        frameQueued = false
        live.remove(this)
        val old = ArrayList(cache.values).apply { add(cur); add(next) }
        old.forEach { if (it != null) drop(it) }
        cache.clear()
        cur = null
        next = null
        warm = null
        handler.removeCallbacksAndMessages(null)
        Choreographer.getInstance().removeFrameCallback(this)
        exec.shutdownNow()
    }

    companion object {
        private val live = java.util.Collections.newSetFromMap(java.util.WeakHashMap<HeroView, Boolean>())
        private val mainHandler = Handler(Looper.getMainLooper())

        /**
         * Frees a dropped slide's 8 MB bitmap (and with it its GPU texture) once no hero holds it. Without this the
         * texture stayed in HWUI's 48 MB cache until a GC, and three slides plus stale ones filled it. Delayed so
         * the last recorded frame that drew it is long gone.
         */
        private fun drop(l: Loaded) {
            mainHandler.postDelayed({
                if (live.none { it.holds(l) } && !l.sharp.isRecycled) l.sharp.recycle()
            }, 2000)
        }

        /** The soft dark wash in the top-left corner that keeps the title logo readable on bright art. */
        fun washPaint(w: Int): Paint = Paint().apply {
            shader = android.graphics.RadialGradient(0f, 0f, w * 0.62f,
                intArrayOf(0x8C000000.toInt(), 0x40000000, 0x00000000), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        }
        const val NIGHT = 0xFF05060C.toInt()
        const val HOLD_MS = 8000L
        const val FADE_MS = 1100L
        const val KB_MS = HOLD_MS + 2 * FADE_MS
    }
}

/** The drawer's backdrop: the shared blurred layers full screen under a dark tint, crossfading with the hero. */
class BlurScrim(ctx: Context) : View(ctx) {
    private val pa = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pb = Paint(Paint.FILTER_BITMAP_FLAG)
    private val tint = Paint().apply { color = 0x66000008 }

    override fun onDraw(c: Canvas) {
        // Reduce transparency: a solid dark ground under the drawer instead of the blurred art.
        if (GlassPainter.solid) { c.drawColor(SOLID); return }
        val a = Backdrop.a
        val b = Backdrop.b
        a.bmp?.let { pa.alpha = 255; c.drawBitmap(it, a.m, pa) }
        b.bmp?.let { if (b.alpha > 0.004f) { pb.alpha = (b.alpha * 255).toInt(); c.drawBitmap(it, b.m, pb) } }
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), tint)
    }

    private companion object {
        const val SOLID = 0xFF0B0E1A.toInt()
    }
}
