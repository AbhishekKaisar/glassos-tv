package io.github.abhishekkaisar.glassos

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.View
import android.view.WindowManager

/**
 * The Glass home in a full-screen overlay window, shown the moment Home is pressed. Android holds every
 * activity start from a background app for 5 s after Home (app-switch guard), so MainActivity cannot come
 * back sooner; adding a window is not held. The overlay is a full, working home ([HomeScreen]) seeded with
 * MainActivity's decoded art and app list, so it appears at once. When MainActivity comes to the front it
 * copies the overlay's state and removes it after its own first frame; if the user opens an app from the
 * overlay, the overlay stays (tile pressed) until that app is in front. Needs SYSTEM_ALERT_WINDOW (appops);
 * without it nothing is shown and the watcher works as before. All calls on the main thread.
 */
object HomeOverlay {
    var screen: HomeScreen? = null
        private set
    /** True once the user opened an app from the overlay; the overlay waits for that app. */
    var launching = false
        private set
    private var wm: WindowManager? = null
    private var appCtx: Context? = null
    private var shownAt = 0L
    /** Wall clock time of the last show; app foreground events after it end the overlay. */
    @Volatile var shownWall = Long.MAX_VALUE
        private set
    private val main = Handler(Looper.getMainLooper())

    fun allowed(ctx: Context): Boolean = Build.VERSION.SDK_INT < 23 || android.provider.Settings.canDrawOverlays(ctx)

    private val host = object : HomeHost {
        override fun startApp(i: Intent, pkg: String, from: View?) {
            val c = appCtx ?: return
            launching = true
            screen?.pressed(from)
            WLog.i("overlay launch $pkg")
            // Held by the app-switch guard until it ends (then it runs right after MainActivity's held start).
            try { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Throwable) { }
            main.removeCallbacks(giveUp)
            main.postDelayed(giveUp, LAUNCH_TIMEOUT_MS)
        }
    }

    /** Shows the overlay (or, when already shown, treats it as another Home press). */
    fun show(ctx: Context, onlyIfHidden: Boolean = false) {
        if (!allowed(ctx)) return
        if (onlyIfHidden && (screen != null || HomeShare.mainResumed)) return
        shownAt = SystemClock.uptimeMillis()
        shownWall = System.currentTimeMillis()
        scheduleChecks()
        val cur = screen
        if (cur != null) {
            launching = false
            cur.onHomeAgain(true)
            return
        }
        val t0 = SystemClock.uptimeMillis()
        WLog.i("overlay show")
        val w = ctx.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val ready = spare != null
        val s = spare ?: build(ctx)
        spare = null
        // Same slide, focus and drawer position as MainActivity had.
        HomeShare.main?.let { s.restore(it.state(), it) }
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE)
        lp.title = "GlassHome"
        lp.windowAnimations = 0
        try {
            w.addView(s.root, lp)
        } catch (e: Throwable) {
            android.util.Log.w("GlassWatch", "overlay refused: $e")
            s.release()
            return
        }
        screen = s
        wm = w
        appCtx = ctx.applicationContext
        launching = false
        s.resumed = true
        s.onResumed()
        if (s.root.findFocus() == null) s.focusDock()
        WLog.i("overlay focus: ${s.state().focusPkg ?: "none"}")
        val added = SystemClock.uptimeMillis()
        s.root.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                s.root.viewTreeObserver.removeOnPreDrawListener(this)
                val pre = SystemClock.uptimeMillis()
                s.root.post { WLog.i("overlay ${if (ready) "prepared" else "built"}: added in ${added - t0} ms, " +
                    "first draw at ${pre - t0} ms, drawn by ${SystemClock.uptimeMillis() - t0} ms") }
                return true
            }
        })
    }

    private var spare: HomeScreen? = null

    private fun build(ctx: Context): HomeScreen {
        MainActivity.screenSize(ctx)
        val c = ContextThemeWrapper(ctx.applicationContext, R.style.GlassTheme)
        return HomeScreen(c, host, HomeShare.main)
    }

    /**
     * Builds the overlay's home ahead of time (detached, not drawing) while Glass is in the background, so the
     * Home key only has to add the window. Bitmaps are shared with MainActivity, so it costs only the views.
     */
    fun prepare(ctx: Context) {
        if (!allowed(ctx) || screen != null || spare != null || HomeShare.mainResumed || HomeShare.apps == null) return
        // Only the watcher shows the overlay. Without it (GlassOS is the default home, no usage access, or Replace
        // home is off) a prepared home is never used, and it holds a full home's views while every app runs.
        if (!HomeWatcher.wanted(ctx)) return
        val t0 = SystemClock.uptimeMillis()
        val s = build(ctx)
        layOut(s)
        spare = s
        WLog.i("overlay prepared in ${SystemClock.uptimeMillis() - t0} ms")
    }

    /** Measures and lays out a home that is not in a window yet, so adding the window only has to draw it. */
    private fun layOut(s: HomeScreen) {
        s.root.measure(View.MeasureSpec.makeMeasureSpec(Backdrop.screenW, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(Backdrop.screenH, View.MeasureSpec.EXACTLY))
        s.root.layout(0, 0, Backdrop.screenW, Backdrop.screenH)
    }

    /**
     * MainActivity loaded the app list again after a package broadcast: the prepared home takes it and stays ready.
     * Most broadcasts change nothing shown (Fire OS sends them for updates and component changes), and then its
     * tiles only get the new art and labels. Building the home again on Home instead is slower to cover the stock
     * home.
     */
    fun appsChanged() {
        val s = spare ?: return
        s.takeApps()
        layOut(s)
    }

    /** Drops the prepared home (Glass is in front again). */
    fun discardSpare() {
        spare?.release()
        spare = null
    }

    /** MainActivity has drawn the overlay's state: the overlay goes. */
    fun handedOver(act: MainActivity) {
        if (screen == null || launching) return
        WLog.i("overlay handed over after ${SystemClock.uptimeMillis() - shownAt} ms")
        hide()
    }

    /**
     * Another app (not Glass, not Amazon's home) came to the front: the overlay must not cover it. The usage event
     * arrives before that app has drawn, so the overlay stays a little longer; going at once shows Amazon's home.
     */
    fun otherAppInFront(pkg: String) {
        if (screen == null) return
        WLog.i("overlay: $pkg in front, removing after $APP_DRAW_MS ms")
        main.removeCallbacks(giveUp)
        main.postDelayed(giveUp, APP_DRAW_MS)
    }

    fun hide() {
        main.removeCallbacks(giveUp)
        main.removeCallbacks(retryStart)
        val s = screen ?: return
        screen = null
        shownWall = Long.MAX_VALUE
        launching = false
        try { wm?.removeViewImmediate(s.root) } catch (e: Throwable) { }
        s.release()
        wm = null
        appCtx = null
    }

    private fun scheduleChecks() {
        main.removeCallbacks(retryStart)
        main.postDelayed(retryStart, RETRY_MS)
        main.removeCallbacks(giveUp)
        main.postDelayed(giveUp, GIVE_UP_MS)
    }

    /** The guard has ended: if MainActivity still is not in front (its held start was dropped), start it now. */
    private val retryStart = Runnable {
        val c = appCtx ?: return@Runnable
        if (screen == null || launching || HomeShare.mainResumed) return@Runnable
        WLog.i("overlay: starting Glass again")
        try {
            c.startActivity(Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        } catch (e: Throwable) { }
    }

    /** Safety net: never leave the overlay up for long. */
    private val giveUp = Runnable {
        if (screen != null) android.util.Log.w("GlassWatch", "overlay timed out")
        hide()
    }

    private const val RETRY_MS = 5600L
    private const val GIVE_UP_MS = 9000L
    private const val APP_DRAW_MS = 800L
    private const val LAUNCH_TIMEOUT_MS = 9000L
}
