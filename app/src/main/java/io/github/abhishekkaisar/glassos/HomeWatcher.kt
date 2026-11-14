package io.github.abhishekkaisar.glassos

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Process
import android.os.SystemClock

/**
 * Brings Glass back when the TV's own home screen comes to the front: Amazon's on Fire TV, or the stock Google TV /
 * Android TV home on builds where GlassOS is not the default home app. Not run at all when GlassOS is the default
 * home (Home then opens GlassOS directly).
 *
 * On Fire TV: when Amazon's home screen comes to the front (Back out of an app that starts the
 * Amazon home itself, or the Home button). Polls usage events every 120 ms with the screen on, reading only
 * the events since the last poll, and shows [HomeOverlay] at once. (The Home key's CLOSE_SYSTEM_DIALOGS
 * broadcast is sent with an Amazon signature permission, so apps never receive it.) Needs GET_USAGE_STATS (granted with appops); without it the service stops and
 * Glass works as before. Runs in the foreground with a silent notification, which Fire TV never shows.
 */
class HomeWatcher : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var usm: UsageStatsManager
    private lateinit var prefs: Prefs
    private var lastTs = 0L
    private var lastRedirect = 0L
    private var createdAt = 0L
    private var waitBootAnim = false
    private var fireTv = true
    private var homes: Set<String> = emptySet()

    private lateinit var power: android.os.PowerManager
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private val poll = object : Runnable {
        override fun run() {
            try { check() } catch (e: Throwable) { }
            // Fast while the screen is on, slow while it is off.
            @Suppress("DEPRECATION")
            val screenOn = if (Build.VERSION.SDK_INT >= 20) power.isInteractive else power.isScreenOn
            handler.postDelayed(this, if (screenOn) POLL_MS else IDLE_POLL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        WLog.init(this)
        usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        startForeground(1, notification())
        thread = HandlerThread("glass-watch", Process.THREAD_PRIORITY_FOREGROUND).apply { start() }
        handler = Handler(thread.looper)
        power = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        fireTv = Platform.isFireTv(this)
        homes = Platform.otherHomeActivities(this)
        lastTs = System.currentTimeMillis()
        createdAt = SystemClock.elapsedRealtime()
        handler.post(poll)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!wanted(this)) { stopSelf(); return START_NOT_STICKY }
        // After a boot, look back over the last events too: Amazon's home may have come up before the watcher.
        // Only for a watcher that has just started: a running one has already seen those events.
        if (intent?.getBooleanExtra(EXTRA_LOOK_BACK, false) == true && SystemClock.elapsedRealtime() - createdAt < 5000) handler.post {
            // Never before this boot (the wall clock may still jump when the network time arrives).
            val bootWall = System.currentTimeMillis() - SystemClock.elapsedRealtime()
            lastTs = minOf(lastTs, maxOf(System.currentTimeMillis() - LOOK_BACK_MS, bootWall))
            WLog.i("watcher start (look back), uptime ${SystemClock.elapsedRealtime()} ms")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        main.removeCallbacksAndMessages(null)
        main.post { HomeOverlay.hide() }
        handler.removeCallbacksAndMessages(null)
        thread.quitSafely()
    }

    private fun check() {
        val now = System.currentTimeMillis()
        // A short window: back 300 ms so an event written just after the previous poll is not missed; seen ones are skipped.
        val ev = usm.queryEvents(lastTs - 300, now + 1000)
        val e = UsageEvents.Event()
        var amazonHome = false
        var homeIsSettings = false
        var sawEvent = false
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.timeStamp <= lastTs || e.eventType != UsageEvents.Event.MOVE_TO_FOREGROUND) continue
            lastTs = e.timeStamp
            sawEvent = true
            val pkg = e.packageName ?: continue
            val cls = e.className ?: ""
            val isHome = if (fireTv) pkg == AMAZON && cls.contains("HomeActivity")
                else "$pkg/$cls" in homes || (pkg in Platform.ANDROID_TV_HOMES && cls.endsWith("MainActivity"))
            // Glass or any Fire TV Settings screen in front ends a visit to Amazon's home the user chose from Glass:
            // the only exception is staying on that home itself.
            if ((pkg == packageName || isFireSettings(pkg, cls)) && e.timeStamp > choseAmazonAt) userChoseAmazon = false
            if (isSettingsTrampoline(pkg, cls)) settingsTrampolineAt = e.timeStamp
            // Any other app in front ends the overlay (opened from it, or by a remote shortcut button).
            if (!isHome && pkg != packageName && e.timeStamp >= HomeOverlay.shownWall) main.post { HomeOverlay.otherAppInFront(pkg) }
            if (isHome) WLog.i("home event at ${e.timeStamp}, readable ${System.currentTimeMillis() - e.timeStamp} ms later")
            if (Platform.isStockHomePkg(this, pkg)) {
                amazonHome = isHome
                // android.settings.SETTINGS (the remote's or another app's Settings) opens the Settings tab inside
                // Amazon's home, which gives no event when the user leaves it.
                homeIsSettings = fireTv && isHome && e.timeStamp - settingsTrampolineAt in 0..TRAMPOLINE_MS
            } else {
                amazonHome = false
                // Leaving Amazon's home for any other app ends a visit the user chose from Glass.
                if (pkg != packageName) userChoseAmazon = false
            }
        }
        if (amazonHome) {
            // After a boot Fire OS keeps its logo on screen until Amazon's home signals it has drawn (or for about
            // 2 minutes). Covering the home before that leaves the logo up, so wait for the boot animation to end.
            if (bootAnimRunning()) {
                if (!waitBootAnim) WLog.i("home up, waiting for the boot animation to end")
                waitBootAnim = true
            } else if (homeIsSettings) showSettings() else bringBack("usage")
        } else if (sawEvent) waitBootAnim = false
        if (waitBootAnim && !amazonHome && !bootAnimRunning()) {
            waitBootAnim = false
            WLog.i("boot animation ended, uptime ${SystemClock.elapsedRealtime()} ms")
            bringBack("boot")
        }
    }

    private fun redirectWanted(): Boolean =
        prefs.replaceHome && !userChoseAmazon && SystemClock.elapsedRealtime() >= allowAmazonUntil

    private fun bringBack(why: String) {
        if (!redirectWanted()) return
        val t = SystemClock.elapsedRealtime()
        if (t - lastRedirect < DEBOUNCE_MS) return
        lastRedirect = t
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra(EXTRA_FROM_WATCHER, true)
        // After the Home key Android holds background starts for about 5 s (app-switch guard); PendingIntent and
        // AlarmManager starts are held the same way on Fire OS 6, so a plain start it is. MainActivity drops a late one.
        i.putExtra(EXTRA_SENT_AT, System.currentTimeMillis())
        WLog.i("bring back ($why)")
        try { startActivity(i) } catch (e: Throwable) { }
        // After the Home key that start is held for 5 s. A window add is not, so Glass shows at once in an overlay
        // that MainActivity replaces when it arrives (straight away after Back, where nothing is held).
        main.post { if (!HomeShare.mainResumed && redirectWanted()) HomeOverlay.show(this) }
    }

    /**
     * Settings opened through Amazon's home: show the stand-alone Settings grid over it instead of covering it with
     * Glass. Leaving that grid brings Amazon's home back to the front, which the watcher sees, so Glass returns.
     */
    private fun showSettings() {
        if (!redirectWanted()) return
        WLog.i("settings tab opened in Amazon's home: showing the Settings grid")
        try { startActivity(FireIntents.settings().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Throwable) { bringBack("settings failed") }
    }

    private var settingsTrampolineAt = 0L

    private fun notification(): Notification {
        val b = if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Home watcher", NotificationManager.IMPORTANCE_MIN).apply {
                setSound(null, null); setShowBadge(false)
            })
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setPriority(Notification.PRIORITY_MIN).setSound(null)
        }
        return b.setSmallIcon(R.mipmap.ic_launcher).setContentTitle("GlassOS").setOngoing(true).build()
    }

    companion object {
        const val AMAZON = Platform.FIRE_HOME
        const val EXTRA_FROM_WATCHER = "fromWatcher"
        const val EXTRA_SENT_AT = "sentAt"
        private const val EXTRA_LOOK_BACK = "lookBack"
        private const val LOOK_BACK_MS = 120_000L
        private const val CHANNEL = "watch"
        private const val POLL_MS = 120L
        private const val IDLE_POLL_MS = 2000L
        private const val DEBOUNCE_MS = 1500L

        /** Set when Glass opens Amazon's home on purpose: no redirect for 3 s, and none until another app comes up. */
        @Volatile var allowAmazonUntil = 0L
        @Volatile var userChoseAmazon = false
        @Volatile private var choseAmazonAt = 0L
        private const val TRAMPOLINE_MS = 3000L

        /** A Fire TV Settings screen: the settings app's pages, or the launcher's own settings activities. */
        fun isFireSettings(pkg: String, cls: String) = pkg == FireIntents.SETTINGS_PKG ||
            (pkg == AMAZON && cls.endsWith("SettingsActivity"))

        /** Activities that only forward a Settings request to the Settings tab of Amazon's home. */
        fun isSettingsTrampoline(pkg: String, cls: String) =
            (pkg == FireIntents.SETTINGS_PKG && cls.endsWith("CTSDummySettingsHandler")) ||
            (pkg == AMAZON && (cls.endsWith(".ui.SettingsActivity") || cls.endsWith("QuickSettingsProxyActivity")))

        /** True while Fire OS's boot logo is still on screen (init.svc.bootanim running). */
        fun bootAnimRunning(): Boolean = try {
            val sp = Class.forName("android.os.SystemProperties")
            sp.getMethod("get", String::class.java, String::class.java).invoke(null, "init.svc.bootanim", "") == "running"
        } catch (e: Throwable) { false }

        /** On, allowed, and needed: not when GlassOS is the default home app. */
        fun wanted(ctx: Context): Boolean = Prefs(ctx).replaceHome && hasAccess(ctx) && !Platform.isDefaultHome(ctx)

        fun hasAccess(ctx: Context): Boolean = try {
            val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) == AppOpsManager.MODE_ALLOWED
        } catch (e: Throwable) { false }

        /** Starts the watcher when it is on and allowed, else makes sure it is stopped. */
        fun sync(ctx: Context, lookBack: Boolean = false) {
            val i = Intent(ctx, HomeWatcher::class.java).putExtra(EXTRA_LOOK_BACK, lookBack)
            try {
                if (wanted(ctx)) {
                    if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
                } else ctx.stopService(i)
            } catch (e: Throwable) { }
        }

        /** True when an app other than Amazon's home and Glass came to the front after [since] (wall clock ms). */
        fun otherAppSince(ctx: Context, since: Long): Boolean = try {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val ev = usm.queryEvents(since, System.currentTimeMillis() + 1000)
            val e = UsageEvents.Event()
            var other = false
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                if (e.eventType != UsageEvents.Event.MOVE_TO_FOREGROUND || e.timeStamp < since) continue
                if (!Platform.isStockHomePkg(ctx, e.packageName) && e.packageName != ctx.packageName) other = true
            }
            other
        } catch (e: Throwable) { false }

        fun openAmazonHome(ctx: Context) {
            val home = Platform.stockHome(ctx) ?: return
            allowAmazonUntil = SystemClock.elapsedRealtime() + 3000
            userChoseAmazon = true
            choseAmazonAt = System.currentTimeMillis()
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(home)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { ctx.startActivity(i) } catch (e: Throwable) { userChoseAmazon = false }
        }
    }
}
