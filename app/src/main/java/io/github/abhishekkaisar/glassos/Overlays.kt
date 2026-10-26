package io.github.abhishekkaisar.glassos

import android.graphics.Canvas
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Right-hand glass settings panel. */
class SettingsView(private val act: HomeScreen) : FrameLayout(act.ctx) {
    var isOpen = false
        private set
    private val scrim = View(context).apply { setBackgroundColor(0x73000000) }
    private val panelW = dpi(410f)
    private val margin = dpi(26f)
    val panel = GlassBox(context, dp(36f)).apply {
        glass.tint = 0.10f
        glass.dark = 0.30f
        glass.sheen = 0.10f
        shadowAlpha = 0.5f
    }
    private val list = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = true
        clipToPadding = false
    }
    private val nameRow = RowView(context, "Name")
    private val photoRow = RowView(context, "Profile photo")
    private val countryRow = RowView(context, "Country")
    private val shelfRow = RowView(context, "Top Shelf")
    private val keyRow = RowView(context, "TMDB API key")
    private val appsRow = RowView(context, "Top row apps")
    private val hiddenRow = RowView(context, "Hidden apps")
    private val permsRow = RowView(context, "Permissions")
    private val setupRow = RowView(context, "Run setup again")
    private val aboutRow = RowView(context, "About")
    private val uninstallRow = RowView(context, "Prepare to uninstall")
    private val soundRow = RowView(context, "Focus sounds")
    private val editRow = RowView(context, "Edit top row")
    private val bgRow = RowView(context, "Background").apply { isFocusable = false; isClickable = false }
    private val allRow = RowView(context, "Show all apps")
    private val replaceRow = RowView(context, "Replace ${Platform.name(context)} home")
    private val fireSettingsRow = RowView(context, "${Platform.name(context)} settings")
    private val fireHomeRow = RowView(context, "${Platform.name(context)} home")
    /** Android TV and Google TV: make GlassOS the default home app. */
    private val homeAppRow = RowView(context, "Home screen")
    private val glassTvRow = RowView(context, "Glass TV")
    private val scroll = ScrollView(context).apply {
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        isSmoothScrollingEnabled = true
    }
    private val thumbsRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
    private val thumbsScroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        isHorizontalFadingEdgeEnabled = false
        setFadingEdgeLength(0)
        clipChildren = true
        clipToPadding = false
        isFillViewport = false
        overScrollMode = View.OVER_SCROLL_NEVER
    }
    private val thumbs = ArrayList<ThumbView>()

    init {
        visibility = View.GONE
        clipChildren = false
        addView(scrim, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(panel, LayoutParams(panelW, LayoutParams.MATCH_PARENT, Gravity.END).apply { setMargins(margin, margin, margin, margin) })
        val pad = dpi(22f)
        list.setPadding(pad, dpi(8f), pad, dpi(8f))
        scroll.addView(list, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        // On API 25 a parent's clipChildren sets its children's clip, so the panel clips the scroll to its bounds,
        // inset from the rounded corners.
        panel.clipChildren = true
        panel.addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            topMargin = dpi(12f); bottomMargin = dpi(14f)
        })
        if (android.os.Build.VERSION.SDK_INT >= 23) scroll.setOnScrollChangeListener { _, _, _, _, _ -> invalidateDeep(list) }

        val title = TextView(context).apply {
            text = "Settings"
            textSize = 26f
            typeface = Fonts.medium
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dpi(6f), 0, 0, dpi(8f))
        }
        list.addView(title)
        val rowLp = { LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpi(48f)).apply { bottomMargin = dpi(5f) } }
        list.addView(nameRow, rowLp())
        list.addView(photoRow, rowLp())
        list.addView(countryRow, rowLp())
        list.addView(shelfRow, rowLp())
        list.addView(keyRow, rowLp())
        list.addView(soundRow, rowLp())
        list.addView(bgRow, rowLp())
        val bgLabel = TextView(context).apply {
            text = "When Top Shelf art is unavailable"
            textSize = 15f
            typeface = Fonts.medium
            setTextColor(0x99FFFFFF.toInt())
            setPadding(dpi(20f), dpi(8f), 0, dpi(2f))
        }
        list.addView(bgLabel)
        thumbsScroll.setPadding(0, dpi(12f), 0, dpi(14f))
        thumbsRow.setPadding(dpi(18f), 0, dpi(18f), 0)
        thumbsScroll.addView(thumbsRow, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        list.addView(thumbsScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = -dpi(14f); rightMargin = -dpi(14f); bottomMargin = dpi(6f)
        })
        list.addView(editRow, rowLp())
        list.addView(appsRow, rowLp())
        list.addView(hiddenRow, rowLp())
        list.addView(allRow, rowLp())
        list.addView(replaceRow, rowLp())
        list.addView(permsRow, rowLp())
        if (!Platform.isFireTv(context)) list.addView(homeAppRow, rowLp())
        list.addView(glassTvRow, rowLp())
        list.addView(fireSettingsRow, rowLp())
        if (Platform.stockHome(context) != null) list.addView(fireHomeRow, rowLp())
        list.addView(setupRow, rowLp())
        list.addView(uninstallRow, rowLp())
        list.addView(aboutRow, rowLp())

        fun step(row: View, s: SetupView.Step) { resumeRow = row; act.openSetup(s, single = true) }
        nameRow.onClick = { step(nameRow, SetupView.Step.NAME) }
        photoRow.onClick = { step(photoRow, SetupView.Step.PHOTO) }
        countryRow.onClick = { step(countryRow, SetupView.Step.COUNTRY) }
        keyRow.onClick = { step(keyRow, SetupView.Step.TMDB_KEY) }
        appsRow.onClick = { step(appsRow, SetupView.Step.APPS) }
        hiddenRow.onClick = { step(hiddenRow, SetupView.Step.HIDDEN) }
        permsRow.onClick = { step(permsRow, SetupView.Step.PERMISSIONS) }
        homeAppRow.onClick = { step(homeAppRow, SetupView.Step.HOME) }
        glassTvRow.onClick = {
            val pm = context.packageManager
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_LEANBACK_LAUNCHER)
                setPackage("com.gathin.tvhome")
            }
            val launch = pm.resolveActivity(intent, 0)?.let {
                android.content.Intent(intent).setClassName(
                    "com.gathin.tvhome",
                    it.activityInfo.name
                )
            }
            if (launch != null) {
                close { context.startActivity(launch) }
            } else {
                android.widget.Toast.makeText(
                    context,
                    "Aktivitas Glass TV tidak ditemukan",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
        setupRow.onClick = { resumeRow = setupRow; act.openSetup() }
        aboutRow.onClick = { step(aboutRow, SetupView.Step.ABOUT) }
        // Device admin blocks uninstalling; an app may always remove its own admin.
        uninstallRow.onClick = {
            Power.removeAdmin(context)
            act.setReplaceHome(false)
            updateValues()
            // Removing the admin lands a moment later; refresh the permissions count once it has.
            postDelayed({ if (isOpen) updateValues() }, 800)
            val msg = if (Platform.isFireTv(context) || !Platform.isDefaultHome(context)) "Device admin removed and ${Platform.name(context)} home restored. You can uninstall GlassOS now."
                else "Device admin removed. Choose your old home in Settings > Apps > Default apps (or on Home), then uninstall GlassOS."
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
        }
        shelfRow.onClick = {
            val ids = HeroRepo.SOURCES.map { it.first }
            act.setTopShelf(ids[(ids.indexOf(act.prefs.topShelf) + 1) % ids.size])
            updateValues()
        }
        soundRow.onClick = {
            act.prefs.sound = !act.prefs.sound
            updateValues()
        }
        editRow.onClick = { close { act.enterEdit() } }
        allRow.onClick = { close { act.focusAllApps() } }
        replaceRow.onClick = {
            act.setReplaceHome(!act.prefs.replaceHome)
            updateValues()
        }
        fireSettingsRow.onClick = { close { act.openFireSettings() } }
        fireHomeRow.onClick = { close { act.openAmazonHome() } }
        for (r in listOf(fireSettingsRow, fireHomeRow, photoRow, appsRow, setupRow, aboutRow)) { r.valueView.text = "›"; r.valueView.textSize = 24f }
        allRow.valueView.text = "›"
        editRow.valueView.text = "›"
        allRow.valueView.textSize = 24f
        editRow.valueView.textSize = 24f
    }

    private fun updateValues() {
        soundRow.valueView.text = if (act.prefs.sound) "On" else "Off"
        val hiddenCount = act.prefs.hidden.count { p -> HomeShare.apps?.any { it.pkg == p } ?: true }
        hiddenRow.valueView.text = if (hiddenCount == 0) "None" else "$hiddenCount"
        nameRow.valueView.text = act.prefs.name.ifEmpty { "Not set" }
        countryRow.valueView.text = act.prefs.country.ifEmpty { "Not set" }.let { c -> if (c.length == 2) java.util.Locale("", c).displayCountry else c }
        shelfRow.valueView.text = HeroRepo.sourceLabel(act.prefs.topShelf)
        keyRow.valueView.text = when {
            act.prefs.tmdbKey.isNotEmpty() -> "Your key"
            BuildConfig.TMDB_API_KEY.isNotEmpty() -> "Built in"
            else -> "Not set"
        }
        permsRow.valueView.text = "${Grants.ALL.count { it.isGranted(context) }} of ${Grants.ALL.size}"
        bgRow.valueView.text = WallpaperStore.label(act.prefs.scene)
        replaceRow.valueView.text = if (act.prefs.replaceHome) "On" else "Off"
        homeAppRow.valueView.text = if (Platform.isDefaultHome(context)) "GlassOS" else "Not GlassOS"
    }

    fun refreshThumbs() {
        thumbsRow.removeAllViews()
        thumbs.clear()
        val ids = WallpaperStore.scenes.map { it.first } + listOf(HeroRepo.PHOTOS) + act.userImageIds()
        val tw = dpi(136f)
        val th = dpi(76f)
        val current = act.prefs.scene
        for ((i, id) in ids.withIndex()) {
            val t = ThumbView(context, tw, th, id, WallpaperStore.label(id))
            t.id = View.generateViewId()
            t.chosen = id == current
            t.onClick = {
                act.setWallpaper(id)
                thumbs.forEach { it.chosen = it.wallId == id }
                updateValues()
            }
            thumbsRow.addView(t, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                if (i > 0) leftMargin = dpi(16f)
            })
            thumbs.add(t)
        }
        act.loadThumbs(thumbs)
        val chosen = thumbs.firstOrNull { it.chosen } ?: thumbs.firstOrNull()
        soundRow.nextFocusDownId = chosen?.id ?: View.NO_ID
        editRow.nextFocusUpId = chosen?.id ?: View.NO_ID
        if (chosen != null) thumbsScroll.post { thumbsScroll.scrollTo((chosen.left - dpi(18f)).coerceAtLeast(0), 0) }
    }

    /** The row that opened a setup step; Settings comes back with it focused rather than at the top. */
    private var resumeRow: View? = null

    /** Focuses the row a setup step was opened from, or the first row. */
    private fun focusStart() {
        val r = resumeRow
        resumeRow = null
        if (r != null) { r.requestFocus(); return }
        scroll.scrollTo(0, 0)
        nameRow.requestFocus()
    }

    fun open(instant: Boolean = false) {
        if (isOpen) return
        isOpen = true
        updateValues()
        refreshThumbs()
        visibility = View.VISIBLE
        if (instant) {
            scrim.alpha = 1f
            panel.translationX = 0f
            focusStart()
            return
        }
        scrim.alpha = 0f
        scrim.animate().alpha(1f).setDuration(220).start()
        val off = (panelW + margin).toFloat()
        panel.translationX = off
        panel.animate().translationX(0f).setDuration(320)
            .setInterpolator(android.view.animation.DecelerateInterpolator(2f))
            .setUpdateListener { invalidateDeep(panel) }
            .withEndAction { invalidateDeep(panel) }
            .start()
        focusStart()
    }

    fun close(instant: Boolean = false, after: (() -> Unit)? = null) {
        if (!isOpen) return
        isOpen = false
        if (instant) {
            visibility = View.GONE
            act.onSettingsClosed()
            after?.invoke()
            return
        }
        val off = (panelW + margin).toFloat()
        scrim.animate().alpha(0f).setDuration(200).start()
        panel.animate().translationX(off).setDuration(220)
            .setInterpolator(android.view.animation.AccelerateInterpolator(1.5f))
            .setUpdateListener { invalidateDeep(panel) }
            .withEndAction {
                visibility = View.GONE
                act.onSettingsClosed()
                after?.invoke()
            }.start()
    }
}


/**
 * Options for the app being moved (Menu while it wiggles): a small glass card in the middle of the screen, in the
 * style of the settings rows. Each action closes the card first.
 */
class AppMenu(private val act: HomeScreen) : FrameLayout(act.ctx) {
    var isOpen = false
        private set
    private val scrim = View(context).apply { setBackgroundColor(0x59000000) }
    val panel = GlassBox(context, dp(30f)).apply {
        glass.tint = 0.10f
        glass.dark = 0.30f
        glass.sheen = 0.10f
        shadowAlpha = 0.5f
    }
    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
    private val title = TextView(context).apply {
        textSize = 22f
        typeface = Fonts.medium
        setTextColor(0xFFFFFFFF.toInt())
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    private var onClosed: (() -> Unit)? = null

    init {
        visibility = View.GONE
        clipChildren = false
        addView(scrim, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(panel, LayoutParams(dpi(380f), LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        val pad = dpi(18f)
        list.setPadding(pad, dpi(16f), pad, dpi(12f))
        panel.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /** Shows [items] (label to action) under [heading]; [closed] runs whenever the card closes, before any action. */
    fun open(heading: String, items: List<Pair<String, () -> Unit>>, closed: () -> Unit) {
        if (isOpen) return
        isOpen = true
        onClosed = closed
        list.removeAllViews()
        title.text = heading
        list.addView(title, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dpi(10f) })
        var first: View? = null
        for ((label, action) in items) {
            val row = RowView(context, label)
            row.titleView.gravity = Gravity.CENTER
            row.onClick = { close(); action() }
            list.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpi(48f)).apply { bottomMargin = dpi(5f) })
            if (first == null) first = row
        }
        visibility = View.VISIBLE
        alpha = 0f
        animate().alpha(1f).setDuration(160).withEndAction { invalidateDeep(panel) }.start()
        first?.requestFocus()
        invalidateDeep(panel)
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        animate().cancel()
        visibility = View.GONE
        val c = onClosed
        onClosed = null
        c?.invoke()
    }
}
