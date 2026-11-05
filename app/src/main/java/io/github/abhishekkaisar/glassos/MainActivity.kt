package io.github.abhishekkaisar.glassos

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager

/** The Glass home activity. The home UI itself is [HomeScreen]; this hosts it and takes over from [HomeOverlay]. */
class MainActivity : Activity(), HomeHost {
    lateinit var home: HomeScreen
        private set

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { home.loadApps { HomeOverlay.appsChanged() } }
    }
    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { home.updateTime() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        screenSize(this)
        // A home built in the overlay after Home, if there is one, lends its slides so the first frame matches it.
        home = HomeScreen(this, this, HomeOverlay.screen)
        HomeShare.main = home
        setContentView(home.root)
        // The theme's night colour stays for the starting window; once the content is up the hero covers it.
        window.setBackgroundDrawable(null)
        HomeWatcher.sync(this)
        if (!home.prefs.setupDone) home.root.post { home.openSetup() }
        registerReceiver(timeReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
        registerReceiver(pkgReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        main.removeCallbacks(prepareOverlay)
        demo?.release(); demo = null
        if (HomeShare.main === home) HomeShare.main = null
        home.release()
        try { unregisterReceiver(pkgReceiver) } catch (e: Exception) { }
        try { unregisterReceiver(timeReceiver) } catch (e: Exception) { }
    }

    override fun onResume() {
        super.onResume()
        HomeShare.mainResumed = true
        main.removeCallbacks(prepareOverlay)
        HomeOverlay.discardSpare()
        home.onResumed()
        // Icons added to or changed in Pictures/Glass/icons show when Glass is back in front.
        if (AppRepo.customArtChanged(this)) home.loadApps { HomeOverlay.appsChanged() }
        HomeWatcher.sync(this)
        takeOverFromOverlay()
    }

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val prepareOverlay = Runnable { HomeOverlay.prepare(this) }

    override fun onStop() {
        super.onStop()
        // Another app is in front: get the Home overlay ready once that app has settled.
        main.removeCallbacks(prepareOverlay)
        main.postDelayed(prepareOverlay, 2000)
    }

    override fun onPause() {
        super.onPause()
        HomeShare.mainResumed = false
        home.onPaused()
    }

    /**
     * When the overlay shown after Home is up, copy its focus, drawer scroll and slide, then remove it only once
     * the window manager shows this window (onEnterAnimationComplete, after its first draw), so there is no
     * flash between them. A timer covers a missing callback. If the user opened an app from the overlay, the
     * overlay stays until that app is in front.
     */
    private fun takeOverFromOverlay() {
        val ov = HomeOverlay.screen ?: return
        if (HomeOverlay.launching) return
        home.restore(ov.state(), ov)
        handoverPending = true
        val decor = window.decorView
        decor.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (decor.viewTreeObserver.isAlive) decor.viewTreeObserver.removeOnPreDrawListener(this)
                main.removeCallbacks(handover)
                main.postDelayed(handover, HANDOVER_FALLBACK_MS)
                return true
            }
        })
        decor.invalidate()
    }

    private var handoverPending = false
    private val handover = Runnable {
        if (!handoverPending) return@Runnable
        handoverPending = false
        HomeOverlay.handedOver(this)
    }

    override fun onEnterAnimationComplete() {
        super.onEnterAnimationComplete()
        if (handoverPending) WLog.i("enter animation complete")
        // Shown on screen now; one more frame for the compositor, then the overlay can go.
        if (handoverPending) { main.removeCallbacks(handover); main.postDelayed(handover, 34) }
    }

    override fun startApp(i: Intent, pkg: String, from: View?) = home.startFrom(i, from)

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 7) home.onStoragePermission()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = home.onBack()

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Debug builds: `am start -n io.github.abhishekkaisar.glassos/.MainActivity --ei slide N` fades to slide N (for captures).
        val debug = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debug && intent?.hasExtra("slide") == true) {
            home.hero.jumpTo(intent.getIntExtra("slide", 0)); return
        }
        // Debug builds: `--ez demoMedia true|false` publishes a playing media session (Now Playing captures when no
        // app on the TV publishes one).
        if (debug && intent?.hasExtra("demoMedia") == true) { demoMedia(intent.getBooleanExtra("demoMedia", false)); return }
        // Debug builds: `--ei sleepSec N` starts the sleep timer for N seconds (end-to-end test of the alarm and lockNow).
        if (debug && intent?.hasExtra("sleepSec") == true) { SleepTimer.start(this, intent.getIntExtra("sleepSec", 30) * 1000L); return }
        val fromWatcher = intent?.getBooleanExtra(HomeWatcher.EXTRA_FROM_WATCHER, false) == true
        if (fromWatcher) {
            // A start held by the Home key's 5 s guard can land after the user opened an app from Amazon's home: step back.
            val sent = intent!!.getLongExtra(HomeWatcher.EXTRA_SENT_AT, 0L)
            if (HomeOverlay.screen == null && sent > 0 && System.currentTimeMillis() - sent > 1000 && HomeWatcher.otherAppSince(this, sent)) {
                moveTaskToBack(true)
                return
            }
        }
        // Taking over from the overlay restores its state in onResume.
        if (HomeOverlay.screen != null) return
        home.onHomeAgain(fromWatcher)
    }

    private var demo: android.media.session.MediaSession? = null

    private fun demoMedia(on: Boolean) {
        demo?.release(); demo = null
        if (!on) return
        val s = android.media.session.MediaSession(this, "glass-demo")
        s.setMetadata(android.media.MediaMetadata.Builder()
            .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, "Demo Track")
            .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, "Glass test session")
            .build())
        s.setPlaybackState(android.media.session.PlaybackState.Builder()
            .setActions(android.media.session.PlaybackState.ACTION_PLAY_PAUSE or android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT or
                android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS)
            .setState(android.media.session.PlaybackState.STATE_PLAYING, 0, 1f).build())
        s.isActive = true
        demo = s
    }

    companion object {
        private const val HANDOVER_FALLBACK_MS = 700L

        fun screenSize(ctx: Context) {
            val dm = DisplayMetrics()
            (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(dm)
            Backdrop.screenW = maxOf(dm.widthPixels, dm.heightPixels)
            Backdrop.screenH = minOf(dm.widthPixels, dm.heightPixels)
        }
    }
}
