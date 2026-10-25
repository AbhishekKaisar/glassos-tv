package io.github.abhishekkaisar.glassos

import android.Manifest
import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

/** What a home screen needs from the window that shows it (MainActivity, or the overlay window after Home). */
interface HomeHost {
    /** Starts an app. [from] is the tile it was opened from, if any. */
    fun startApp(i: Intent, pkg: String, from: View?)
}

/** Where the user was on a home screen, carried between the overlay and MainActivity. */
class HomeState(val focusPkg: String?, val focusGear: Boolean, val focusHero: Boolean, val lastDockIndex: Int,
                val settingsOpen: Boolean, val ccOpen: Boolean = false)

/** Process-wide data both home screens share, so the overlay can show the home at once without loading anything. */
object HomeShare {
    @Volatile var apps: List<AppEntry>? = null
    @Volatile var avatar: Bitmap? = null
    @Volatile var mainResumed = false
    /** The home screen of MainActivity, while it exists. */
    var main: HomeScreen? = null
    @Volatile private var snd: Sound? = null
    fun sound(ctx: Context): Sound? = snd
    fun ensureSound(ctx: Context) {
        if (snd != null) return
        synchronized(this) { if (snd == null) snd = try { Sound(ctx.applicationContext) } catch (e: Throwable) { null } }
    }
}

/**
 * The Glass home: the rotating Top Shelf, the dock shelf, the app drawer, the user pill and Settings.
 * One instance lives in MainActivity; a second, short-lived one is shown in an overlay window right after
 * the Home key (see [HomeOverlay]). Both share the decoded art and app icons through [HomeShare].
 */
class HomeScreen(val ctx: Context, private val host: HomeHost, seed: HomeScreen? = null) {
    val prefs = Prefs(ctx)
    lateinit var root: FrameLayout
        private set
    lateinit var hero: HeroView
        private set
    private lateinit var home: FrameLayout
    private lateinit var scrim: View
    private lateinit var header: FrameLayout
    private lateinit var content: FrameLayout
    private lateinit var shelf: GlassBox
    private lateinit var gear: UserPill
    private lateinit var chevron: View
    private lateinit var hint: TextView
    lateinit var settings: SettingsView
        private set
    lateinit var cc: ControlCenter
        private set
    lateinit var setup: SetupView
        private set
    lateinit var appMenu: AppMenu
        private set
    private lateinit var movePill: GlassBox

    private var apps: List<AppEntry> = emptyList()
    private val dockTiles = ArrayList<TileView>()
    private val dockPkgs = ArrayList<String>()
    /** The drawer's apps in their order (the saved order, then new installs). Hidden apps are in neither list. */
    private val drawerPkgs = ArrayList<String>()
    /** The app being moved after a hold of OK, and the two lists as they were, for Back. */
    private var movingPkg: String? = null
    private var moveFrom: Pair<List<String>, List<String>>? = null
    /** The OK press that dropped a move: its release must not reach the tile, or the app would open. */
    private var eatOkUp = false
    private val rows = ArrayList<ArrayList<TileView>>()
    private var currentRow = 0
    private var lastDockIndex = 0
    var editMode = false
        private set
    private var pickedIndex = -1
    private var wallToken = 0
    private var appsToken = 0
    private var focusedOnce = false
    /** Uptime of the last key this home received; see [KEY_FOCUS_MS]. */
    private var lastKeyAt = 0L
    /** True while the window showing this home is in front (activity resumed, or the overlay shown). */
    var resumed = false
        set(v) { field = v; updateHeroRunning() }
    private var released = false
    private val refreshing = java.util.concurrent.atomic.AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val sound get() = HomeShare.sound(ctx)
    private val backdropListener: () -> Unit = {
        if (::root.isInitialized) invalidateDeep(root)
    }
    /** Called once per hero frame: only the glass surfaces that sample the backdrop redraw. */
    private val frameListener: () -> Unit = {
        if (::root.isInitialized) {
            shelf.invalidate()
            gear.invalidate()
            chevron.invalidate()
            if (scrim.alpha > 0f) scrim.invalidate()
            if (settings.isOpen) invalidateDeep(settings.panel)
            if (cc.isOpen) invalidateDeep(cc.panel)
            if (setup.isOpen) invalidateDeep(setup.panel)
            if (appMenu.isOpen) invalidateDeep(appMenu.panel)
            if (movePill.visibility == View.VISIBLE) movePill.invalidate()
        }
    }

    init {
        Backdrop.addListener(backdropListener)
        Backdrop.addFrameListener(frameListener)
        buildUi()
        worker.execute { HomeShare.ensureSound(ctx) }
        val seedHero = seed?.hero
        if (seedHero != null && seedHero.slides.isNotEmpty()) hero.adopt(seedHero) else setWallpaper(prefs.scene, save = false)
        HomeShare.avatar?.let { gear.avatar = it }
        val cached = HomeShare.apps
        if (cached != null) applyApps(cached) else loadApps()
    }

    fun release() {
        if (released) return
        released = true
        Backdrop.removeListener(backdropListener)
        Backdrop.removeFrameListener(frameListener)
        hero.release()
        labelAnim?.cancel()
        main.removeCallbacksAndMessages(null)
        worker.shutdownNow()
    }

    private fun ui(r: () -> Unit) { main.post { if (!released) r() } }
    /** True when a key reached this home in the last [KEY_FOCUS_MS], so a focus change now counts as the user's. */
    private fun byKey() = SystemClock.uptimeMillis() - lastKeyAt < KEY_FOCUS_MS
    private fun focused(): View? = root.findFocus()

    // ---------- UI ----------

    private val sw get() = Backdrop.screenW
    private val sh get() = Backdrop.screenH
    // tvOS 26 (measured from a real 1080p screenshot): shelf 12px from the sides and 25px above the bottom,
    // radius 48px, 38px side and 40px top/bottom padding, 5:3 tiles with 52px gaps.
    private val shelfMargin get() = sw * 12 / 1920
    private val shelfPad get() = sw * 38 / 1920
    private val shelfPadV get() = sh * 40 / 1080
    private val margin get() = shelfMargin + shelfPad
    private val gap get() = sw * 52 / 1920
    private val tileW get() = (sw - 2 * margin - 5 * gap) / COLS
    private val tileH get() = tileW * 3 / 5
    private val labelH get() = ctx.dpi(28f)
    private val rowH get() = tileH + labelH + ctx.dpi(22f)
    /** About 30% of the first drawer row peeks in at the bottom edge, 22px below the shelf (labels hidden while peeking). */
    private val peek get() = tileH * 30 / 100
    private val peekGap get() = sh * 22 / 1080
    private val row0Top get() = sh - peek - peekGap - shelfPadV - tileH
    private fun rowTop(r: Int) = if (r == 0) row0Top else row0Top + tileH + shelfPadV + peekGap + (r - 1) * rowH

    private fun buildUi() {
        // No background here: the hero paints the night colour itself only while it has no art, so the full-screen
        // art is not drawn over a full-screen fill every frame (overdraw on a fill-rate bound GPU).
        root = KeyRoot(ctx).apply { clipChildren = false }
        // Rotating Top Shelf behind everything (aerials, Apple TV titles, or one static background).
        hero = HeroView(ctx) { s -> HeroRepo.load(ctx, s, sw, sh) }.apply {
            id = View.generateViewId()
            shelfTop = (row0Top - shelfPadV).toFloat()
            leftX = sw * 60f / 1920f
            topY = sh * 30f / 1080f
            onOpen = { openSlide(it) }
            openLabel = { s -> s.pkg?.let { p -> HomeShare.apps?.firstOrNull { it.pkg == p }?.label }?.let { "Open in $it" } ?: "Not on your apps" }
            onFocusDown = { focusDock() }
        }
        root.addView(hero, FrameLayout.LayoutParams(-1, -1))
        // Behind the grid: the pre-blurred backdrop with a dark tint, faded in when scrolled (tvOS style).
        scrim = BlurScrim(ctx).apply { alpha = 0f }
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))

        home = FrameLayout(ctx).apply { clipChildren = false; clipToPadding = false }
        root.addView(home, FrameLayout.LayoutParams(-1, -1))

        header = FrameLayout(ctx).apply { clipChildren = false }
        home.addView(header, FrameLayout.LayoutParams(-1, -1))
        // tvOS keeps the Top Shelf clean: no big clock, the time lives in the user pill.
        gear = UserPill(ctx).apply {
            id = View.generateViewId()
            nextFocusDownId = hero.id
            name = prefs.name
            onClick = { openControlCenter() }
            onLongPress = { openSettings() }
        }
        header.addView(gear, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
            rightMargin = sw * 60 / 1920; topMargin = sh * 48 / 1080
        })
        hero.nextFocusUpId = gear.id
        updateTime()
        if (HomeShare.avatar == null) loadAvatar()

        // Scrolling app grid: row 0 is the dock on a glass shelf, the rest are every other app.
        // content clips, so rows scrolled out of view are culled by the renderer (a big win on the Fire TV GPU).
        content = FrameLayout(ctx).apply { clipChildren = true; clipToPadding = false }
        home.addView(content, FrameLayout.LayoutParams(-1, sh * 4))
        // Clear glass: the art shows through softly blurred and mildly darkened, with a bright thin top rim.
        shelf = GlassBox(ctx, sw * 48f / 1920f).apply {
            glass.tint = 0.03f
            glass.dark = 0.16f
            glass.sheen = 0.05f
            glass.highlight = 0.62f
            glass.border = 0.10f
            glass.cacheStatic = true
            shadowAlpha = 0.18f
        }
        content.addView(shelf, FrameLayout.LayoutParams(sw - 2 * shelfMargin, tileH + 2 * shelfPadV).apply {
            leftMargin = shelfMargin
            topMargin = row0Top - shelfPadV
        })
        chevron = Chevron(ctx)
        home.addView(chevron, FrameLayout.LayoutParams(ctx.dpi(18f), ctx.dpi(9f), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = row0Top - shelfPadV - ctx.dpi(17f)
        })

        hint = TextView(ctx).apply {
            text = "Edit top row: press OK to pick up an app, Left or Right to move it, Menu to remove it, Back when done"
            textSize = 15f
            typeface = Fonts.regular
            setTextColor(0xE6FFFFFF.toInt())
            setShadowLayer(ctx.dp(8f), 0f, ctx.dp(1f), 0x80000000.toInt())
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        home.addView(hint, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply { topMargin = row0Top - ctx.dpi(56f) })

        // Move mode hint: a small glass pill at the top centre, clear of the shelf and the drawer rows.
        val pillH = ctx.dpi(30f)
        movePill = GlassBox(ctx, pillH / 2f).apply {
            glass.tint = 0.10f
            glass.dark = 0.34f
            glass.sheen = 0.06f
            shadowAlpha = 0.25f
            visibility = View.GONE
            setPadding(ctx.dpi(18f), 0, ctx.dpi(18f), 0)
        }
        movePill.addView(TextView(ctx).apply {
            text = "Arrows to move · OK to drop · Menu for options · Back to cancel"
            textSize = 14f
            typeface = Fonts.regular
            setTextColor(0xF2FFFFFF.toInt())
            gravity = Gravity.CENTER
            maxLines = 1
        }, FrameLayout.LayoutParams(-2, -1, Gravity.CENTER))
        root.addView(movePill, FrameLayout.LayoutParams(-2, pillH, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = sh * 48 / 1080
        })

        settings = SettingsView(this)
        root.addView(settings, FrameLayout.LayoutParams(-1, -1))
        cc = ControlCenter(this)
        root.addView(cc, FrameLayout.LayoutParams(-1, -1))
        setup = SetupView(this)
        root.addView(setup, FrameLayout.LayoutParams(-1, -1))
        appMenu = AppMenu(this)
        root.addView(appMenu, FrameLayout.LayoutParams(-1, -1))

        root.viewTreeObserver.addOnGlobalFocusChangeListener { old, new ->
            // Only a key moves focus audibly. A change no key caused (the app list reloaded, a window came back)
            // is silent, and tiles land without the spring (TileView.quietFocus), so a tile never seems to be
            // focused again on its own.
            if (old != null && new != null && old !== new && byKey() && prefs.sound && !FocusAnim.forceInstant) sound?.tick()
            if (new === hero || old === hero) updateHeroRunning()
            if (::chevron.isInitialized) chevron.animate().alpha(if (new is TileView && (new.tag as? Int) == 0) 1f else 0f).setDuration(200).start()
            if (new is TileView) {
                val r = (new.tag as? Int) ?: 0
                if (r == 0) lastDockIndex = dockTiles.indexOf(new).coerceAtLeast(0)
                scrollToRow(r)
            } else if (new === gear || new === hero) scrollToRow(0)
        }
        root.viewTreeObserver.addOnGlobalLayoutListener { invalidateDeep(root) }
    }

    private fun scrollToRow(r: Int) {
        if (r == currentRow) return
        currentRow = r
        val target = if (r == 0) 0f else -(rowTop(r) - sh * 0.30f)
        val instant = FocusAnim.forceInstant
        if (instant) {
            content.animate().cancel(); header.animate().cancel(); scrim.animate().cancel()
            content.translationY = target
            header.alpha = if (r == 0) 1f else 0f
            header.translationY = if (r == 0) 0f else -ctx.dp(40f)
            scrim.alpha = if (r == 0) 0f else 1f
            hero.visibility = if (r == 0) View.VISIBLE else View.INVISIBLE
        } else {
            content.animate().translationY(target).setDuration(380)
                .setInterpolator(android.view.animation.DecelerateInterpolator(2f))
                .setUpdateListener { shelf.invalidate() }
                .withEndAction { shelf.invalidate() }
                .start()
            header.animate().alpha(if (r == 0) 1f else 0f).translationY(if (r == 0) 0f else -ctx.dp(40f)).setDuration(300).start()
            // The opaque scrim hides the hero completely once faded in: stop drawing the hero under it.
            if (r == 0) hero.visibility = View.VISIBLE
            scrim.animate().alpha(if (r == 0) 0f else 1f).setDuration(380)
                .withEndAction { if (currentRow != 0 && scrim.alpha >= 1f) hero.visibility = View.INVISIBLE }
                .start()
        }
        // The move hint rises above the row over the moving one while the drawer is scrolled.
        val pillY = if (r == 0) 0f else -(sh * 40 / 1080).toFloat()
        if (instant) movePill.translationY = pillY else movePill.animate().translationY(pillY).setDuration(380).start()
        fadeLabels(r > 0, instant)
        updateHeroRunning()
    }

    private var labelAnim: android.animation.ValueAnimator? = null
    private var labelsShown = false

    /** Drawer labels stay hidden while the first row only peeks in, and fade in once the drawer scrolls up. */
    private fun fadeLabels(show: Boolean, instant: Boolean = false) {
        if (show == labelsShown) return
        labelsShown = show
        labelAnim?.cancel()
        if (instant) {
            val v = if (show) 1f else 0f
            for (i in 1 until rows.size) rows[i].forEach { it.labelAlpha = v }
            return
        }
        val from = rows.getOrNull(1)?.firstOrNull()?.labelAlpha ?: 0f
        labelAnim = android.animation.ValueAnimator.ofFloat(from, if (show) 1f else 0f).apply {
            duration = 260
            addUpdateListener { a -> val v = a.animatedValue as Float; for (i in 1 until rows.size) rows[i].forEach { it.labelAlpha = v } }
            start()
        }
    }

    /** The hero rotates only while the home row is on screen, nothing covers it and it is not focused. */
    fun updateHeroRunning() {
        if (!::hero.isInitialized || released) return
        hero.setRunning(resumed && currentRow == 0 && !settings.isOpen && !cc.isOpen && !setup.isOpen && !hero.isFocused && !editMode &&
            movingPkg == null && !appMenu.isOpen)
    }

    /**
     * OK on a title: opens the title in its app when a deep link is known (the Apple TV source gives one), else
     * opens the streaming service's app. TMDB gives no app-specific title ids, so TMDB titles open the app.
     */
    private fun openSlide(s: Slide) {
        if (s.kind != Slide.TITLE) return
        val pkg = s.pkg
        if (pkg == null || HomeShare.apps?.any { it.pkg == pkg } == false) {
            Toast.makeText(ctx, "None of your apps streams this title here", Toast.LENGTH_SHORT).show()
            return
        }
        val url = s.url
        if (url != null) {
            val i = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).setPackage(pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (i.resolveActivity(ctx.packageManager) != null) {
                host.startApp(i, pkg, null)
                return
            }
        }
        launch(pkg, null)
    }

    /**
     * Fire TV's stand-alone Settings grid. android.settings.SETTINGS opens the Settings tab inside Amazon's home
     * instead, which the watcher covers at once and which gives no event when the user leaves it. From
     * MainActivity it opens in Glass's own task, so Back from it returns straight to Glass.
     */
    fun openFireSettings() {
        val i = Platform.settingsIntent(ctx)
        startIntent(i, Platform.handler(ctx, i))
    }

    /**
     * Opens a system screen from Control Center (no tile to scale from). Several Fire TV pages (Display & Audio,
     * Accessibility, Parental Controls, the profile picker) only open for Amazon-signed apps: for those the
     * Settings grid opens instead, with [hint] naming the tile to choose there.
     */
    fun startIntent(i: Intent, pkg: String, hint: String? = null) {
        val ri = ctx.packageManager.resolveActivity(i, 0)
        val perm = ri?.activityInfo?.permission
        if (ri == null || (perm != null && ctx.checkCallingOrSelfPermission(perm) != PackageManager.PERMISSION_GRANTED)) {
            val grid = Platform.settingsIntent(ctx)
            if (grid.resolveActivity(ctx.packageManager) == null) {
                Toast.makeText(ctx, "Not available on this TV", Toast.LENGTH_SHORT).show()
                return
            }
            if (hint != null) Toast.makeText(ctx, hint, Toast.LENGTH_LONG).show()
            if (ownTask()) grid.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            host.startApp(grid, Platform.handler(ctx, grid), null)
            return
        }
        if (ownTask()) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        host.startApp(i, pkg, null)
    }

    /**
     * Settings pages open in their own task from the overlay, and on Android TV, where GlassOS may be the default
     * home: a page left in the home task would come back on Home instead of GlassOS. On Fire TV they stay in
     * Glass's task, so Back from them returns straight to Glass.
     */
    private fun ownTask() = ctx !is Activity || !Platform.isFireTv(ctx)

    fun openAmazonHome() = HomeWatcher.openAmazonHome(ctx)

    fun setReplaceHome(on: Boolean) {
        prefs.replaceHome = on
        HomeWatcher.sync(ctx)
        if (on && !HomeWatcher.hasAccess(ctx)) {
            Toast.makeText(ctx, "Needs usage access: adb shell appops set ${ctx.packageName} GET_USAGE_STATS allow", Toast.LENGTH_LONG).show()
        }
    }

    // ---------- Wallpaper ----------

    fun setWallpaper(id: String, save: Boolean = true) {
        if (save) prefs.scene = id
        val token = ++wallToken
        worker.execute {
            val list = slidesFor(id)
            ui { if (token == wallToken) hero.setSlides(list) }
            refreshTitles(id, token)
        }
    }

    /** Switches the Top Shelf source and reloads it (fetching at once when the cache is stale). */
    fun setTopShelf(src: String) {
        prefs.topShelf = src
        setWallpaper(prefs.scene, save = false)
    }

    /** Refetches the Top Shelf now (after a new country, key or favourite apps). */
    fun refetchTopShelf() {
        HeroRepo.invalidate(ctx, prefs.topShelf)
        setWallpaper(prefs.scene, save = false)
    }

    /** Top Shelf titles (live or cached); without any, the fallback: a scene, the user's photos or an image, then the desert. */
    private fun slidesFor(id: String): List<Slide> = HeroRepo.cachedTitles(ctx, prefs.topShelf).ifEmpty { fallbackSlides(id) }

    private fun fallbackSlides(id: String): List<Slide> = when {
        HeroRepo.isHero(id) -> HeroRepo.photoSlides(ctx, storageGranted())
        id.startsWith("file:") -> {
            val f = java.io.File(id.removePrefix("file:"))
            listOf(Slide("f:" + f.path + ":" + f.lastModified(), Slide.STATIC, art = f))
        }
        else -> listOf(Slide(id, Slide.STATIC, sceneId = id))
    }.ifEmpty { listOf(Slide("scene:desert", Slide.STATIC, sceneId = "scene:desert")) }

    /** Refreshes the Top Shelf titles when the cache is older than 6 hours, then swaps in the new list. Runs on [worker]. */
    private fun refreshTitles(id: String, token: Int) {
        val src = prefs.topShelf
        if (!HeroRepo.isStale(ctx, src) || !refreshing.compareAndSet(false, true)) return
        Thread {
            val changed = try { HeroRepo.refresh(ctx, src) } finally { refreshing.set(false) }
            if (changed) {
                val list = slidesFor(id)
                ui { if (token == wallToken) hero.setSlides(list) }
            }
        }.apply { priority = Thread.MIN_PRIORITY }.start()
    }

    /** The window went to the back (an app, the screensaver, Settings): a move in progress is put back. */
    fun onPaused() {
        resumed = false
        if (::appMenu.isInitialized) leaveMove()
    }

    fun onResumed() {
        resumed = true
        if (::setup.isInitialized) setup.onReturned()
        updateTime()
        if (!worker.isShutdown) worker.execute { refreshTitles(prefs.scene, wallToken) }
    }

    fun updateTime() {
        if (!::gear.isInitialized) return
        val fmt = if (android.text.format.DateFormat.is24HourFormat(ctx)) "H:mm" else "h:mm a"
        gear.time = android.text.format.DateFormat.format(fmt, System.currentTimeMillis()).toString().uppercase()
    }

    private fun storageGranted() = Build.VERSION.SDK_INT < 23 ||
        ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /**
     * The profile photo chosen in setup, kept in private storage (see [Profile]). Without one, the glass
     * silhouette is drawn.
     */
    fun loadAvatar() {
        worker.execute {
            val priv = Profile.photoFile(ctx)
            val b = if (priv.exists()) try {
                android.graphics.BitmapFactory.decodeFile(priv.path)?.let { src ->
                    val side = minOf(src.width, src.height)
                    val sq = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
                    Bitmap.createScaledBitmap(sq, 160, 160, true)
                }
            } catch (e: Throwable) { null } else null
            HomeShare.avatar = b
            ui { gear.avatar = b }
        }
    }

    /** Re-reads the name, photo and top row after setup changed them. */
    fun reloadProfile() {
        gear.name = prefs.name
        loadAvatar()
        reloadDock()
    }

    fun userImageIds(): List<String> {
        if (!storageGranted()) return emptyList()
        return WallpaperStore.userImages().map { "file:" + it.path }
    }

    fun loadThumbs(thumbs: List<ThumbView>) {
        val tw = ctx.dpi(136f)
        val th = ctx.dpi(76f)
        worker.execute {
            for (t in thumbs) {
                val b = try {
                    if (HeroRepo.isHero(t.wallId)) HeroRepo.thumb(ctx, t.wallId, storageGranted(), tw, th) ?: WallpaperStore.thumb(ctx, "scene:desert", tw, th)
                    else WallpaperStore.thumb(ctx, t.wallId, tw, th)
                } catch (e: Throwable) { null }
                ui { t.bitmap = b }
            }
        }
    }

    fun onStoragePermission() {
        setup.onStoragePermission()
        loadAvatar()
        if (settings.isOpen) settings.refreshThumbs()
    }

    // ---------- Apps and grid ----------

    fun loadApps() {
        val token = ++appsToken
        worker.execute {
            val list = try { AppRepo.load(ctx) } catch (e: Exception) { emptyList() }
            HomeShare.apps = list
            ui { if (token == appsToken) applyApps(list) }
        }
    }

    private fun applyApps(list: List<AppEntry>) {
        apps = list
        val visible = visibleApps()
        val shown = visible.map { it.pkg }.toSet()
        val wanted = prefs.dock ?: AppRepo.defaultDock(visible)
        val dock = ArrayList(wanted.filter { it in shown }.distinct().take(COLS))
        if (dock.isEmpty()) dock.addAll(AppRepo.defaultDock(visible))
        val drawer = AppRepo.arrange(visible.filter { it.pkg !in dock }, prefs.order).map { it.pkg }
        // Same apps in the same places (Fire OS sends package broadcasts that change nothing shown): refresh the
        // tiles' art and labels in place. Rebuilding would remove the focused tile and focus it again.
        if (dockTiles.isNotEmpty() && dock == dockPkgs && drawer == drawerPkgs) {
            setLists(dock, drawer, refresh = true)
            return
        }
        // While an app is being moved the lists on screen differ from the saved ones; with the same apps installed
        // and shown, the move carries on where it is.
        if (movingPkg != null && dockPkgs.size + drawerPkgs.size == shown.size && shown.containsAll(dockPkgs) &&
            shown.containsAll(drawerPkgs)) {
            setLists(ArrayList(dockPkgs), ArrayList(drawerPkgs), refresh = true)
            return
        }
        // The app list changed under a move (an install or uninstall): the move ends unsaved.
        if (movingPkg != null) endMove()
        if (appMenu.isOpen) appMenu.close()
        dockPkgs.clear()
        dockPkgs.addAll(dock)
        drawerPkgs.clear()
        drawerPkgs.addAll(drawer)
        rebuildGrid()
    }

    /** Installed apps the user has not hidden, alphabetically. */
    private fun visibleApps(): List<AppEntry> {
        val hidden = prefs.hidden
        return if (hidden.isEmpty()) apps else apps.filter { it.pkg !in hidden }
    }

    private fun saveDock() { prefs.dock = dockPkgs.toList() }
    private fun saveOrder() { prefs.order = drawerPkgs.toList() }

    /**
     * An app leaving the top row goes to the front of the drawer once the user has arranged it, or back to its
     * alphabetical place while the drawer is still alphabetical.
     */
    private fun returnToDrawer(pkg: String) {
        if (prefs.order == null) {
            drawerPkgs.clear()
            drawerPkgs.addAll(visibleApps().map { it.pkg }.filter { it !in dockPkgs })
        } else {
            drawerPkgs.remove(pkg)
            drawerPkgs.add(0, pkg)
            saveOrder()
        }
    }

    private fun makeTile(app: AppEntry, row: Int): TileView {
        val t = TileView(ctx, tileW, tileH, if (row == 0) 0 else labelH)
        t.app = app
        t.id = View.generateViewId()
        t.tag = row
        // Read the tile's app at click time: a move swaps apps between tiles in place.
        t.onClick = { val a = t.app; if (!editMode && a != null) launch(a.pkg, t) }
        t.onLongPress = { if (!editMode) startMove(t) }
        t.quietFocus = { !byKey() }
        return t
    }

    /**
     * Each row is full width and padded so a focused (1.2x) tile and its shadow stay inside the row's bounds.
     * content clips its rows to their bounds, which lets the renderer cull rows that are off screen.
     */
    private fun addRow(tiles: List<TileView>, r: Int) {
        val padTop = ctx.dpi(18f)
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(margin, padTop, 0, 0)
        }
        tiles.forEachIndexed { i, t -> row.addView(t, LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) leftMargin = gap }) }
        content.addView(row, FrameLayout.LayoutParams(-1, padTop + tileH + ctx.dpi(60f)).apply { topMargin = rowTop(r) - padTop })
    }

    /**
     * Rebuilds every row. Keeps focus on [focusPkg] (or on whatever was focused) when possible. Removing the rows
     * hands focus to the hero for a moment before it comes back; unless the user chose [focusPkg], or this is the
     * first focus of a home being built (off screen, in the overlay or before the activity shows), that happens
     * with no sound and no animation.
     */
    private fun rebuildGrid(focusPkg: String? = null) {
        val quiet = focusPkg == null || !focusedOnce
        val was = FocusAnim.forceInstant
        if (quiet) FocusAnim.forceInstant = true
        try {
            buildRows(focusPkg ?: (focused() as? TileView)?.app?.pkg)
        } finally {
            FocusAnim.forceInstant = was
        }
    }

    private fun buildRows(keep: String?) {
        while (content.childCount > 1) content.removeViewAt(content.childCount - 1)
        dockTiles.clear()
        rows.clear()
        val byPkg = apps.associateBy { it.pkg }
        val dockRow = ArrayList<TileView>()
        for (pkg in dockPkgs) byPkg[pkg]?.let { val t = makeTile(it, 0); t.nextFocusUpId = hero.id; dockRow.add(t) }
        dockTiles.addAll(dockRow)
        rows.add(dockRow)
        addRow(dockRow, 0)
        val rest = drawerPkgs.mapNotNull { byPkg[it] }
        rest.chunked(COLS).forEachIndexed { i, chunk ->
            val r = i + 1
            val list = ArrayList(chunk.map { makeTile(it, r).apply { labelAlpha = if (labelsShown) 1f else 0f } })
            rows.add(list)
            addRow(list, r)
        }
        content.layoutParams = (content.layoutParams as FrameLayout.LayoutParams).apply {
            height = rowTop(rows.size) + rowH + ctx.dpi(120f)
        }
        if (editMode) dockTiles.forEach { it.setJiggle(true) }
        if (settings.isOpen || appMenu.isOpen) return
        val target = rows.flatten().firstOrNull { it.app?.pkg == keep }
        val cur = focused()
        when {
            target != null -> target.requestFocus()
            !focusedOnce && dockTiles.isNotEmpty() -> dockTiles[0].requestFocus()
            cur == null || cur.parent == null -> focusDock()
        }
        if (dockTiles.isNotEmpty()) focusedOnce = true
    }

    fun launch(pkg: String, from: View?) {
        val pm = ctx.packageManager
        val i = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        if (i == null) {
            Toast.makeText(ctx, "Cannot open this app", Toast.LENGTH_SHORT).show()
            return
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        host.startApp(i, pkg, from)
    }

    /** The default start, used by MainActivity: a scale-up from the tile. */
    fun startFrom(i: Intent, from: View?) {
        try {
            val opts = from?.let { ActivityOptions.makeScaleUpAnimation(it, 0, 0, it.width, it.height).toBundle() }
            ctx.startActivity(i, opts)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Cannot open this app", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleDock(pkg: String) {
        if (pkg in dockPkgs) {
            if (dockPkgs.size <= 1) {
                Toast.makeText(ctx, "The top row needs at least one app", Toast.LENGTH_SHORT).show()
                return
            }
            dockPkgs.remove(pkg)
            returnToDrawer(pkg)
            Toast.makeText(ctx, "Removed from the top row", Toast.LENGTH_SHORT).show()
        } else {
            if (dockPkgs.size >= COLS) {
                Toast.makeText(ctx, "The top row holds $COLS apps. Remove one first.", Toast.LENGTH_SHORT).show()
                return
            }
            dockPkgs.add(pkg)
            if (drawerPkgs.remove(pkg) && prefs.order != null) saveOrder()
            Toast.makeText(ctx, "Added to the top row", Toast.LENGTH_SHORT).show()
        }
        saveDock()
        rebuildGrid(pkg)
        syncOther()
    }

    /** The other home screen (activity or overlay) follows the saved dock, order and hidden apps. */
    private fun syncOther() { HomeShare.main?.takeIf { it !== this }?.reloadDock() }

    /** Leaves [pkg] out of the drawer and the top row (Settings > Hidden apps shows it again). */
    fun hideApp(pkg: String) {
        if (pkg in dockPkgs && dockPkgs.size <= 1) {
            Toast.makeText(ctx, "The top row needs at least one app", Toast.LENGTH_SHORT).show()
            return
        }
        val flat = dockPkgs + drawerPkgs
        val i = flat.indexOf(pkg)
        val next = flat.getOrNull(i + 1)?.takeIf { (it in dockPkgs) == (pkg in dockPkgs) } ?: flat.getOrNull(i - 1)
        prefs.hidden = prefs.hidden + pkg
        if (dockPkgs.remove(pkg)) saveDock()
        drawerPkgs.remove(pkg)
        rebuildGrid(next)
        Toast.makeText(ctx, "Hidden. Settings > Hidden apps shows it again.", Toast.LENGTH_LONG).show()
        syncOther()
    }

    // ---------- Move mode (hold OK on an app) ----------

    private fun tileOf(pkg: String?): TileView? {
        if (pkg == null) return null
        for (r in rows) for (t in r) if (t.app?.pkg == pkg) return t
        return null
    }

    /** Hold OK on a tile: it lifts and wiggles; arrows move it across the shelf and the drawer. */
    private fun startMove(t: TileView) {
        val pkg = t.app?.pkg ?: return
        if (editMode || movingPkg != null || settings.isOpen || cc.isOpen) return
        movingPkg = pkg
        moveFrom = Pair(dockPkgs.toList(), drawerPkgs.toList())
        t.picked = true
        t.setJiggle(true, gentle = true)
        movePill.alpha = 0f
        movePill.visibility = View.VISIBLE
        movePill.animate().alpha(1f).setDuration(200).start()
        sound?.takeIf { prefs.sound }?.tick()
        updateHeroRunning()
    }

    /** Settles the moving tile and leaves move mode, without saving. */
    private fun endMove() {
        tileOf(movingPkg)?.let { it.setJiggle(false); it.picked = false }
        movingPkg = null
        moveFrom = null
        movePill.animate().cancel()
        movePill.translationY = if (currentRow == 0) 0f else -(sh * 40 / 1080).toFloat()
        movePill.visibility = View.GONE
        updateHeroRunning()
    }

    /** OK while moving: the app stays where it is and the order is saved. */
    private fun dropMove() {
        if (movingPkg == null) return
        val changed = moveFrom?.let { it.first != dockPkgs || it.second != drawerPkgs } ?: false
        endMove()
        if (!changed) return
        saveDock()
        saveOrder()
        syncOther()
    }

    /** Back while moving: everything goes back where it was. */
    private fun cancelMove() {
        val from = moveFrom ?: return
        val pkg = movingPkg
        endMove()
        setLists(from.first, from.second)
        tileOf(pkg)?.requestFocus()
    }

    /**
     * Puts new lists on screen. When the shelf and the drawer keep their sizes (a move within either, or a swap
     * across them) the tiles stay and only their apps change: no views are made or removed.
     */
    private fun setLists(dock: List<String>, drawer: List<String>, refresh: Boolean = false) {
        val same = dock.size == dockPkgs.size && drawer.size == drawerPkgs.size && rows.size == 1 + (drawer.size + COLS - 1) / COLS
        dockPkgs.clear(); dockPkgs.addAll(dock)
        drawerPkgs.clear(); drawerPkgs.addAll(drawer)
        if (!same) { rebuildGrid(movingPkg); return }
        val byPkg = apps.associateBy { it.pkg }
        // [refresh]: the entries were reloaded, so the same app may come with new art or a new label.
        dockTiles.forEachIndexed { i, t -> val a = byPkg[dockPkgs[i]]; if (t.app !== a || refresh) t.app = a }
        var k = 0
        for (r in 1 until rows.size) for (t in rows[r]) { val a = byPkg[drawerPkgs[k++]]; if (t.app !== a || refresh) t.app = a }
    }

    /**
     * Arrows while moving. Left and Right step through the shelf, or through the drawer in reading order; Down
     * and Up move a row. Up from the drawer's first row puts the app on the shelf (a full shelf passes its last app
     * down to the front of the drawer); Down from the shelf puts it in the drawer's first row.
     */
    private fun moveBy(code: Int) {
        val pkg = movingPkg ?: return
        val d = ArrayList(dockPkgs)
        val w = ArrayList(drawerPkgs)
        val inDock = pkg in d
        val i = if (inDock) d.indexOf(pkg) else w.indexOf(pkg)
        fun shift(list: ArrayList<String>, to: Int): Boolean {
            if (to < 0 || to >= list.size) return false
            list.removeAt(i); list.add(to, pkg); return true
        }
        val moved = when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> shift(if (inDock) d else w, i - 1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> shift(if (inDock) d else w, i + 1)
            KeyEvent.KEYCODE_DPAD_DOWN -> when {
                inDock && d.size > 1 -> { d.removeAt(i); w.add(minOf(i, w.size), pkg); true }
                inDock -> { Toast.makeText(ctx, "The top row needs at least one app", Toast.LENGTH_SHORT).show(); false }
                i / COLS < (w.size - 1) / COLS -> shift(w, minOf(i + COLS, w.size - 1))
                else -> false
            }
            KeyEvent.KEYCODE_DPAD_UP -> when {
                inDock -> false
                i >= COLS -> shift(w, i - COLS)
                else -> {
                    w.removeAt(i)
                    d.add(minOf(i, d.size), pkg)
                    if (d.size > COLS) w.add(0, d.removeAt(d.size - 1))
                    true
                }
            }
            else -> false
        }
        val old = tileOf(pkg)
        if (!moved) {
            when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> old?.nudge(-1, 0)
                KeyEvent.KEYCODE_DPAD_RIGHT -> old?.nudge(1, 0)
                KeyEvent.KEYCODE_DPAD_UP -> old?.nudge(0, -1)
                KeyEvent.KEYCODE_DPAD_DOWN -> old?.nudge(0, 1)
            }
            return
        }
        setLists(d, w)
        val t = tileOf(pkg) ?: return
        if (t !== old) {
            t.picked = true
            t.setJiggle(true, gentle = true)
            old?.setJiggle(false)
            old?.picked = false
        }
        t.requestFocus()
        if ((t.tag as? Int) == 0) lastDockIndex = dockTiles.indexOf(t).coerceAtLeast(0)
    }

    /** Menu while moving: add to or remove from the top row, or hide. Each action first drops the app where it is. */
    private fun openAppMenu() {
        val pkg = movingPkg ?: return
        val app = apps.firstOrNull { it.pkg == pkg } ?: return
        val onShelf = pkg in dockPkgs
        blockHome(true)
        movePill.visibility = View.INVISIBLE
        appMenu.open(app.label, listOf(
            (if (onShelf) "Remove from top row" else "Add to top row") to { dropMove(); toggleDock(pkg) },
            "Hide app" to { dropMove(); hideApp(pkg) }
        )) {
            blockHome(false)
            if (movingPkg != null) {
                movePill.visibility = View.VISIBLE
                tileOf(movingPkg)?.requestFocus()
            }
            updateHeroRunning()
        }
        updateHeroRunning()
    }

    /** Ends move mode and the app menu (another panel is opening, or the home is being reset). */
    private fun leaveMove() {
        if (appMenu.isOpen) appMenu.close()
        if (movingPkg != null) cancelMove()
    }

    /** Re-reads the saved dock (changed on the other home screen). */
    fun reloadDock() { if (apps.isNotEmpty()) applyApps(apps) }

    // ---------- Overlays and modes ----------

    private fun blockHome(block: Boolean) {
        home.descendantFocusability = if (block) ViewGroup.FOCUS_BLOCK_DESCENDANTS else ViewGroup.FOCUS_AFTER_DESCENDANTS
        hero.isFocusable = !block
    }

    fun focusDock() {
        if (dockTiles.isNotEmpty()) dockTiles[lastDockIndex.coerceIn(0, dockTiles.size - 1)].requestFocus()
        else gear.requestFocus()
    }

    fun focusAllApps() {
        val first = rows.getOrNull(1)?.firstOrNull()
        if (first != null) first.requestFocus() else focusDock()
    }

    fun openSettings() {
        if (cc.isOpen) cc.close(instant = true)
        leaveMove()
        if (editMode) exitEdit()
        val a = ctx as? Activity
        if (a != null && Build.VERSION.SDK_INT >= 23 && !prefs.askedStorage && !storageGranted()) {
            prefs.askedStorage = true
            a.requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 7)
        }
        blockHome(true)
        settings.open()
        updateHeroRunning()
    }

    /** Opens first-run setup, or one of its steps from Settings (Settings comes back when it closes). */
    fun openSetup(step: SetupView.Step = SetupView.Step.WELCOME, single: Boolean = false) {
        if (ctx !is Activity) return
        val fromSettings = settings.isOpen
        if (cc.isOpen) cc.close(instant = true)
        if (settings.isOpen) settings.close(instant = true)
        leaveMove()
        if (editMode) exitEdit()
        blockHome(true)
        setup.open(step, single) { if (fromSettings) openSettings() }
        updateHeroRunning()
    }

    fun onSetupClosed() {
        blockHome(false)
        focusDock()
        updateHeroRunning()
    }

    fun openControlCenter() {
        if (cc.isOpen || settings.isOpen || setup.isOpen) return
        leaveMove()
        if (editMode) exitEdit()
        val from = focused()
        blockHome(true)
        cc.open(from = from)
        updateHeroRunning()
    }

    fun onControlCenterClosed(returnTo: View?) {
        blockHome(false)
        if (!settings.isOpen && !editMode) {
            if (returnTo != null && returnTo.isAttachedToWindow && returnTo.parent != null) returnTo.requestFocus() else focusDock()
        }
        updateHeroRunning()
    }

    fun onSettingsClosed() {
        blockHome(false)
        if (!editMode) gear.requestFocus()
        updateHeroRunning()
    }

    fun enterEdit() {
        if (dockTiles.isEmpty()) return
        leaveMove()
        editMode = true
        pickedIndex = -1
        hint.visibility = View.VISIBLE
        dockTiles.forEach { it.setJiggle(true) }
        blockHome(false)
        focusDock()
        updateHeroRunning()
    }

    fun exitEdit() {
        if (pickedIndex in dockTiles.indices) dockTiles[pickedIndex].picked = false
        editMode = false
        pickedIndex = -1
        hint.visibility = View.GONE
        dockTiles.forEach { it.setJiggle(false) }
        saveDock()
        updateHeroRunning()
    }

    private fun togglePick(idx: Int) {
        if (pickedIndex >= 0) {
            dockTiles.getOrNull(pickedIndex)?.picked = false
            pickedIndex = -1
            saveDock()
        } else {
            pickedIndex = idx
            dockTiles[idx].picked = true
        }
    }

    private fun moveTile(dir: Int) {
        val from = pickedIndex
        val to = from + dir
        if (from !in dockTiles.indices || to !in dockTiles.indices) { dockTiles.getOrNull(from)?.nudge(dir, 0); return }
        val pkg = dockPkgs.removeAt(from)
        dockPkgs.add(to, pkg)
        val t = dockTiles.removeAt(from)
        dockTiles.add(to, t)
        val row = t.parent as LinearLayout
        row.removeAllViews()
        dockTiles.forEachIndexed { i, v -> row.addView(v, LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) leftMargin = gap }) }
        // Leaving the window stopped each tile's jiggle: start them again.
        dockTiles.forEach { it.setJiggle(true) }
        rows[0] = ArrayList(dockTiles)
        pickedIndex = to
        lastDockIndex = to
        t.requestFocus()
        sound?.takeIf { prefs.sound }?.tick()
    }

    private fun removeFocusedFromDock() {
        val idx = dockTiles.indexOfFirst { it.isFocused }
        if (idx < 0 || dockPkgs.size <= 1) return
        if (pickedIndex >= 0) { dockTiles.getOrNull(pickedIndex)?.picked = false; pickedIndex = -1 }
        val pkg = dockPkgs.removeAt(idx)
        returnToDrawer(pkg)
        saveDock()
        rebuildGrid(dockPkgs[idx.coerceAtMost(dockPkgs.size - 1)])
    }

    // ---------- State carried between the overlay and MainActivity ----------

    fun state(): HomeState {
        val f = if (cc.isOpen) cc.returnFocus else focused()
        return HomeState((f as? TileView)?.app?.pkg, f === gear, f === hero, lastDockIndex, settings.isOpen, cc.isOpen)
    }

    /** Puts focus, drawer scroll and the shown slide where [s] and [from] had them, with no animation. */
    fun restore(s: HomeState, from: HomeScreen?) {
        if (from != null && from.hero.slides.isNotEmpty()) hero.adopt(from.hero)
        if (setup.isOpen) return
        leaveMove()
        if (editMode) exitEdit()
        lastDockIndex = s.lastDockIndex
        FocusAnim.forceInstant = true
        try {
            if (cc.isOpen) cc.close(instant = true)
            if (s.settingsOpen && !settings.isOpen) { blockHome(true); settings.open(instant = true) }
            else if (!s.settingsOpen && settings.isOpen) settings.close(instant = true)
            if (!s.settingsOpen) {
                val t = s.focusPkg?.let { p -> rows.flatten().firstOrNull { it.app?.pkg == p } }
                when {
                    t != null -> t.requestFocus()
                    s.focusGear -> gear.requestFocus()
                    s.focusHero && hero.isFocusable -> hero.requestFocus()
                    else -> focusDock()
                }
                if (s.ccOpen) { blockHome(true); cc.open(instant = true, from = focused()) }
            }
        } finally {
            FocusAnim.forceInstant = false
        }
        updateHeroRunning()
    }

    /** The tile that opened an app from the overlay stays pressed until the app is in front. */
    fun pressed(v: View?) {
        (v as? TileView)?.animate()?.scaleX(1.1f)?.scaleY(1.1f)?.setDuration(120)?.start()
    }

    // ---------- Keys ----------

    /** Returns true when the key was handled here; false lets the window dispatch it normally. */
    fun onKey(e: KeyEvent): Boolean {
        val code = e.keyCode
        val down = e.action == KeyEvent.ACTION_DOWN
        if (setup.isOpen) return false
        if (code == KeyEvent.KEYCODE_MENU) {
            if (down && e.repeatCount == 0) onMenu()
            return true
        }
        val ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
        if (ok && eatOkUp) {
            if (!down) { eatOkUp = false; return true }
            if (e.repeatCount > 0) return true
            eatOkUp = false
        }
        if (settings.isOpen || cc.isOpen || appMenu.isOpen) return false
        val focusTile = focused() as? TileView
        if (movingPkg != null) {
            // The hold that started the move keeps repeating until OK is let go: only a new press drops.
            when (code) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (down && e.repeatCount == 0) { dropMove(); eatOkUp = true }
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { if (down && e.repeatCount == 0) openAppMenu(); return true }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (down) moveBy(code); return true
                }
            }
            return false
        }
        if (editMode) {
            val idx = dockTiles.indexOf(focusTile)
            if (ok && idx >= 0) {
                if (down && e.repeatCount == 0) togglePick(idx)
                return true
            }
            if (down && pickedIndex >= 0) {
                when (code) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { moveTile(-1); return true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { moveTile(1); return true }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> return true
                }
            }
            if (code == KeyEvent.KEYCODE_DPAD_DOWN && idx >= 0) return true
        } else if (down && focusTile != null) {
            // tvOS behaviour at the edges: no wrapping, no diagonal jumps, just a small tilt.
            val r = (focusTile.tag as? Int) ?: 0
            val row = rows.getOrNull(r)
            val col = row?.indexOf(focusTile) ?: -1
            when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> if (col == 0) { focusTile.nudge(-1, 0); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (row != null && col == row.size - 1) { focusTile.nudge(1, 0); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> if (r >= rows.size - 1) { focusTile.nudge(0, 1); return true }
                    else {
                        val next = rows[r + 1]
                        if (next.isNotEmpty()) { next[col.coerceIn(0, next.size - 1)].requestFocus(View.FOCUS_DOWN); return true }
                    }
                KeyEvent.KEYCODE_DPAD_UP -> if (r >= 1) {
                    val prev = rows[r - 1]
                    if (prev.isNotEmpty()) { prev[col.coerceIn(0, prev.size - 1)].requestFocus(View.FOCUS_UP); return true }
                }
            }
        } else if (down && focused() === gear && code == KeyEvent.KEYCODE_DPAD_DOWN) {
            focusDock(); return true
        }
        return false
    }

    /** Menu opens Control Center anywhere on the home; while an app is moving it opens the app's options. */
    private fun onMenu() {
        when {
            cc.isOpen -> cc.close()
            settings.isOpen -> settings.close()
            appMenu.isOpen -> appMenu.close()
            movingPkg != null -> openAppMenu()
            editMode -> removeFocusedFromDock()
            else -> openControlCenter()
        }
    }

    fun onBack() {
        when {
            setup.isOpen -> setup.back()
            cc.isOpen -> cc.back()
            settings.isOpen -> settings.close()
            appMenu.isOpen -> appMenu.close()
            movingPkg != null -> cancelMove()
            editMode -> exitEdit()
            currentRow > 0 -> focusDock()
            else -> { /* Home stays put. */ }
        }
    }

    /** Home pressed again while this home is shown: like tvOS, close what is open and keep focus. */
    fun onHomeAgain(fromWatcher: Boolean) {
        if (setup.isOpen) return
        if (fromWatcher && !settings.isOpen && !cc.isOpen && !editMode && movingPkg == null && !appMenu.isOpen) {
            if (focused() == null) focusDock()
            return
        }
        if (cc.isOpen) cc.close(instant = true)
        if (settings.isOpen) settings.close()
        leaveMove()
        if (editMode) exitEdit()
        lastDockIndex = 0
        blockHome(false)
        focusDock()
    }

    /** Root view: sends keys to the home screen first, and Back to [onBack]. */
    private inner class KeyRoot(c: Context) : FrameLayout(c) {
        override fun dispatchKeyEvent(e: KeyEvent): Boolean {
            lastKeyAt = SystemClock.uptimeMillis()
            if (onKey(e)) return true
            if (e.keyCode == KeyEvent.KEYCODE_BACK && ctx !is Activity) {
                if (e.action == KeyEvent.ACTION_UP && !e.isCanceled) onBack()
                return true
            }
            return super.dispatchKeyEvent(e)
        }
    }

    companion object {
        const val COLS = 6
        /** A focus change this soon after a key (a press, or a panel closing after Back) counts as caused by it. */
        private const val KEY_FOCUS_MS = 1000L
    }
}
