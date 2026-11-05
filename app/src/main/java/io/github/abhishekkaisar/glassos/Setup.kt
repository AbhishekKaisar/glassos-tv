package io.github.abhishekkaisar.glassos

import android.Manifest
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.Locale

private const val REQ_HOME = 8

/**
 * First-run setup in a centred glass panel over the Top Shelf, driven with the D-pad: name (on-screen keyboard),
 * profile photo, country, the apps on the top row, and one-tap permissions over the TV's own ADB. Single steps
 * also open from Settings ([open] with single = true).
 */
class SetupView(private val home: HomeScreen) : FrameLayout(home.ctx) {
    enum class Step { WELCOME, NAME, PHOTO, COUNTRY, APPS, PERMISSIONS, HOME, DONE, TMDB_KEY, ABOUT, HIDDEN }

    /** Fire TV cannot change its home app, so the home step is for Android TV and Google TV only. */
    private val fireTv = Platform.isFireTv(context)
    private val flow = listOfNotNull(Step.WELCOME, Step.NAME, Step.PHOTO, Step.COUNTRY, Step.APPS, Step.PERMISSIONS,
        if (fireTv) null else Step.HOME, Step.DONE)
    var isOpen = false
        private set
    private var step = Step.WELCOME
    private var single = false
    private var onClosed: (() -> Unit)? = null
    private val prefs get() = home.prefs
    private val ui = Handler(Looper.getMainLooper())

    private val scrim = View(context).apply { setBackgroundColor(0x8C000000.toInt()) }
    private val panelW = Backdrop.screenW * 64 / 100
    private val panelH = Backdrop.screenH * 88 / 100
    val panel = GlassBox(context, dp(40f)).apply {
        glass.tint = 0.10f; glass.dark = 0.34f; glass.sheen = 0.08f; shadowAlpha = 0.5f
    }
    private val title = TextView(context).apply {
        textSize = 30f; typeface = Fonts.medium; setTextColor(Color.WHITE); gravity = Gravity.CENTER
    }
    private val subtitle = TextView(context).apply {
        textSize = 15f; typeface = Fonts.regular; setTextColor(0xB3FFFFFF.toInt()); gravity = Gravity.CENTER
        setLineSpacing(0f, 1.15f)
    }
    private val body = FrameLayout(context).apply { clipChildren = false; clipToPadding = false }
    private val nav = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; clipChildren = false; clipToPadding = false }
    private val dots = Dots(context)

    // Values edited across steps, saved when setup finishes (or when a single step is confirmed).
    private var name = ""
    private var photo: File? = null
    private var photoCleared = false
    private var country = ""
    private val picked = ArrayList<String>()
    private val hidden = HashSet<String>()
    private var keyText = ""

    init {
        visibility = View.GONE
        clipChildren = false
        addView(scrim, LayoutParams(-1, -1))
        addView(panel, LayoutParams(panelW, panelH, Gravity.CENTER))
        panel.clipChildren = true
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        val pad = dpi(34f)
        col.setPadding(pad, dpi(26f), pad, dpi(22f))
        panel.addView(col, LayoutParams(-1, -1))
        col.addView(dots, LinearLayout.LayoutParams(-1, dpi(10f)))
        col.addView(title, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(10f) })
        col.addView(subtitle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(6f) })
        col.addView(body, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dpi(14f); bottomMargin = dpi(10f) })
        col.addView(nav, LinearLayout.LayoutParams(-1, dpi(56f)))
    }

    // ---------- Opening, moving and closing ----------

    fun open(start: Step = Step.WELCOME, single: Boolean = false, onClosed: (() -> Unit)? = null) {
        this.single = single
        this.onClosed = onClosed
        name = prefs.name
        photo = null; photoCleared = false
        country = prefs.country.ifEmpty { suggestedCountry() }
        picked.clear()
        picked.addAll(prefs.dock ?: AppRepo.defaultDock(HomeShare.apps ?: emptyList()))
        keyText = prefs.tmdbKey
        hidden.clear()
        hidden.addAll(prefs.hidden)
        isOpen = true
        visibility = View.VISIBLE
        alpha = 0f
        animate().alpha(1f).setDuration(220).start()
        show(start)
    }

    private fun close() {
        if (!isOpen) return
        isOpen = false
        animate().alpha(0f).setDuration(180).withEndAction {
            visibility = View.GONE
            body.removeAllViews(); nav.removeAllViews()
        }.start()
        home.onSetupClosed()
        onClosed?.invoke()
        onClosed = null
    }

    /** Back: the previous step, or closes a single step without saving. The first step stays. */
    fun back() {
        if (single) { close(); return }
        val i = flow.indexOf(step)
        if (i > 0) show(flow[i - 1])
    }

    private fun next() {
        if (single) { save(step); close(); return }
        save(step)
        val i = flow.indexOf(step)
        if (i < flow.size - 1) show(flow[i + 1]) else finish()
    }

    private fun finish() {
        prefs.setupDone = true
        close()
        home.reloadProfile()
        home.refetchTopShelf()
    }

    private fun save(s: Step) {
        when (s) {
            Step.NAME -> { prefs.name = name.trim().take(24); home.reloadProfile() }
            Step.PHOTO -> {
                val f = photo
                if (f != null) Profile.savePhoto(context, f) else if (photoCleared) Profile.clearPhoto(context)
                home.reloadProfile()
            }
            Step.COUNTRY -> if (country != prefs.country) { prefs.country = country; if (single) home.refetchTopShelf() }
            Step.APPS -> if (picked.isNotEmpty()) { prefs.dock = picked.toList(); home.reloadProfile(); if (single) home.refetchTopShelf() }
            Step.TMDB_KEY -> { prefs.tmdbKey = keyText; home.refetchTopShelf() }
            Step.HIDDEN -> if (hidden != prefs.hidden) { prefs.hidden = hidden.toSet(); home.reloadDock() }
            else -> {}
        }
    }

    private fun show(s: Step) {
        step = s
        // Steps with a scrolling list clip to the body, or the list draws over the buttons; the others leave room
        // for focus scaling.
        body.clipChildren = s == Step.COUNTRY || s == Step.APPS || s == Step.HIDDEN
        body.removeAllViews()
        nav.removeAllViews()
        val i = flow.indexOf(s)
        dots.count = if (single || i < 0) 0 else flow.size
        dots.index = i
        dots.invalidate()
        when (s) {
            Step.WELCOME -> welcome()
            Step.NAME -> nameStep()
            Step.PHOTO -> photoStep()
            Step.COUNTRY -> countryStep()
            Step.APPS -> appsStep()
            Step.PERMISSIONS -> permissionsStep()
            Step.HOME -> homeStep()
            Step.DONE -> doneStep()
            Step.TMDB_KEY -> keyStep()
            Step.ABOUT -> aboutStep()
            Step.HIDDEN -> hiddenStep()
        }
        invalidateDeep(panel)
    }

    private fun navButton(label: String, primary: Boolean = false, click: () -> Unit): RowView {
        val b = RowView(context, label).apply {
            id = View.generateViewId()
            titleView.gravity = Gravity.CENTER
            titleView.textSize = 18f
            if (primary) titleView.typeface = Fonts.medium
            onClick = click
        }
        nav.addView(b, LinearLayout.LayoutParams(dpi(164f), dpi(46f)).apply { leftMargin = dpi(8f); rightMargin = dpi(8f) })
        return b
    }

    private fun continueLabel() = if (single) "Done" else "Continue"

    private fun text(t: String, size: Float = 16f, color: Int = 0xE6FFFFFF.toInt()) = TextView(context).apply {
        text = t; textSize = size; typeface = Fonts.regular; setTextColor(color); setLineSpacing(0f, 1.2f)
    }

    // ---------- Steps ----------

    private fun welcome() {
        title.text = "Welcome to GlassOS"
        subtitle.text = "A calm, glassy home screen for your TV, inspired by tvOS.\nA few quick steps and you are ready."
        // The launcher icon drawn large (docs/logo.svg): the 96 px icon itself is soft at this size.
        val logo = ImageView(context).apply { setImageResource(R.drawable.logo); scaleType = ImageView.ScaleType.FIT_CENTER }
        body.addView(logo, LayoutParams(dpi(120f), dpi(120f), Gravity.CENTER))
        navButton("Get Started", primary = true) { next() }.requestFocus()
    }

    private fun nameStep() {
        title.text = "What is your name?"
        subtitle.text = "Shown on your profile in Control Center."
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; clipChildren = false; clipToPadding = false }
        val field = Field(context, "Your name")
        field.value = name
        box.addView(field, LinearLayout.LayoutParams(dpi(420f), dpi(46f)))
        val kb = Keyboard(context, digits = false) { k ->
            name = edit(name, k, 24)
            field.value = name
        }
        box.addView(kb, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(12f) })
        body.addView(box, LayoutParams(-1, -2, Gravity.CENTER))
        if (!single) navButton("Back") { back() }
        val go = navButton(continueLabel(), primary = true) { next() }
        kb.firstKey()?.requestFocus()
        kb.downTo(go)
    }

    private fun keyStep() {
        title.text = "TMDB API key"
        subtitle.text = "Your TMDB API key (v3) or read access token. Leave it empty for the built-in key."
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; clipChildren = false; clipToPadding = false }
        val field = Field(context, "API key")
        field.value = keyText
        field.small = true
        box.addView(field, LinearLayout.LayoutParams(dpi(520f), dpi(46f)))
        val kb = Keyboard(context, digits = true, capitalise = false) { k ->
            keyText = edit(keyText, k, 400, allowSpace = false)
            field.value = keyText
        }
        box.addView(kb, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(12f) })
        body.addView(box, LayoutParams(-1, -2, Gravity.CENTER))
        navButton("Cancel") { back() }
        val go = navButton("Save", primary = true) { next() }
        kb.firstKey()?.requestFocus()
        kb.downTo(go)
    }

    private fun edit(cur: String, k: String, max: Int, allowSpace: Boolean = true): String = when (k) {
        Keyboard.DEL -> cur.dropLast(1)
        Keyboard.CLEAR -> ""
        " " -> if (allowSpace && cur.isNotEmpty() && !cur.endsWith(" ")) (cur + " ").take(max) else cur
        else -> (cur + k).take(max)
    }

    private fun photoStep() {
        title.text = "Profile photo"
        subtitle.text = "Pick a photo from the Pictures folder, or keep the glass avatar."
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; clipChildren = false; clipToPadding = false
        }
        row.setPadding(dpi(16f), dpi(10f), dpi(16f), dpi(10f))
        scroll.addView(row, ViewGroup.LayoutParams(-2, -2))
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; clipChildren = false; clipToPadding = false }
        val preview = AvatarPreview(context, dpi(80f))
        box.addView(preview, LinearLayout.LayoutParams(dpi(80f), dpi(80f)))
        box.addView(scroll, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(8f) })
        val note = text("", 13f, 0x99FFFFFF.toInt()).apply { gravity = Gravity.CENTER }
        box.addView(note, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(4f) })
        body.addView(box, LayoutParams(-1, -2, Gravity.CENTER))

        val size = dpi(96f)
        val thumbs = ArrayList<ThumbView>()
        val current = Profile.photoFile(context).takeIf { it.exists() && !photoCleared }
        fun choose(t: ThumbView, f: File?, bmp: Bitmap?) {
            thumbs.forEach { it.chosen = it === t }
            if (f == null) { photo = null; photoCleared = true } else { photo = f; photoCleared = false }
            preview.bitmap = bmp
        }
        val def = ThumbView(context, size, size, "default", "Glass avatar").apply { id = View.generateViewId(); bitmap = Profile.silhouette(size) }
        def.onClick = { choose(def, null, null) }
        def.chosen = photo == null && current == null
        row.addView(def); thumbs.add(def)
        if (current != null && photo == null) {
            val bmp = android.graphics.BitmapFactory.decodeFile(current.path)
            preview.bitmap = bmp
            val cur = ThumbView(context, size, size, "current", "Current").apply { id = View.generateViewId(); bitmap = bmp; chosen = true }
            cur.onClick = { thumbs.forEach { it.chosen = it === cur }; photo = null; photoCleared = false; preview.bitmap = bmp }
            row.addView(cur, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dpi(16f) }); thumbs.add(cur)
        }
        if (!Grants.storage(context)) {
            note.text = "GlassOS needs storage access to see your photos. Allow it when asked, or in the permissions step."
            askStorage()
        } else {
            val files = pictures()
            if (files.isEmpty()) note.text = "No photos found in Pictures. Copy one to the TV's Pictures folder to use it."
            Thread {
                for (f in files) {
                    val b = WallpaperStore.decodeCrop(f, size, size) ?: continue
                    ui.post {
                        if (!isOpen || step != Step.PHOTO) return@post
                        val t = ThumbView(context, size, size, f.path, f.nameWithoutExtension).apply { id = View.generateViewId(); bitmap = b }
                        t.chosen = photo?.path == f.path
                        t.onClick = { choose(t, f, b) }
                        row.addView(t, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dpi(16f) }); thumbs.add(t)
                    }
                }
            }.start()
        }
        if (!single) navButton("Back") { back() }
        navButton(continueLabel(), primary = true) { next() }
        (thumbs.firstOrNull { it.chosen } ?: def).requestFocus()
    }

    /** Images in Pictures and its direct sub-folders, newest first, at most 40. */
    private fun pictures(): List<File> {
        val root = File(android.os.Environment.getExternalStorageDirectory(), "Pictures")
        val exts = setOf("jpg", "jpeg", "png", "webp")
        val out = ArrayList<File>()
        try {
            root.listFiles()?.forEach { f ->
                if (f.isFile && f.extension.lowercase() in exts) out.add(f)
                else if (f.isDirectory) f.listFiles()?.forEach { g -> if (g.isFile && g.extension.lowercase() in exts) out.add(g) }
            }
        } catch (e: Throwable) { }
        return out.sortedByDescending { it.lastModified() }.take(40)
    }

    private fun askStorage() {
        val a = context as? Activity ?: return
        if (Build.VERSION.SDK_INT >= 23) a.requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 7)
    }

    /** Storage was granted or refused while setup was open. */
    fun onStoragePermission() { if (isOpen && step == Step.PHOTO && Grants.storage(context)) show(Step.PHOTO) }

    private fun countryStep() {
        title.text = "Where are you?"
        subtitle.text = "Used to show what is streaming in your country on the Top Shelf."
        val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; clipChildren = true }
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        list.setPadding(dpi(6f), dpi(4f), dpi(6f), dpi(4f))
        scroll.addView(list, ViewGroup.LayoutParams(-1, -2))
        body.addView(scroll, LayoutParams(dpi(520f), -1, Gravity.CENTER_HORIZONTAL))
        if (Build.VERSION.SDK_INT >= 23) scroll.setOnScrollChangeListener { _, _, _, _, _ -> invalidateDeep(list) }
        val rows = ArrayList<Pair<String, RowView>>()
        val suggested = suggestedCountry()
        val codes = Locale.getISOCountries().map { it to Locale("", it).displayCountry }.sortedBy { it.second }
        val ordered = listOfNotNull(codes.firstOrNull { it.first == suggested }) + codes.filter { it.first != suggested }
        for ((code, label) in ordered) {
            val r = RowView(context, label).apply { id = View.generateViewId() }
            r.valueView.text = if (code == country) "✓" else ""
            r.onClick = {
                country = code
                rows.forEach { (c, v) -> v.valueView.text = if (c == code) "✓" else "" }
                next()
            }
            list.addView(r, LinearLayout.LayoutParams(-1, dpi(46f)).apply { bottomMargin = dpi(4f) })
            rows.add(code to r)
        }
        if (!single) navButton("Back") { back() }
        navButton(continueLabel(), primary = true) { next() }
        val target = rows.firstOrNull { it.first == country }?.second ?: rows.firstOrNull()?.second
        target?.requestFocus()
    }

    private fun suggestedCountry(): String = Locale.getDefault().country.ifEmpty { "US" }

    private fun appsStep() {
        title.text = "Your top row"
        subtitle.text = "Choose up to ${HomeScreen.COLS} apps for the shelf. You can change them later."
        val apps = allApps().filter { it.pkg !in hidden }
        if (picked.isEmpty()) picked.addAll(AppRepo.defaultDock(apps))
        picked.retainAll(apps.map { it.pkg }.toSet())
        fun refreshCount() { subtitle.text = "Choose up to ${HomeScreen.COLS} apps for the shelf (${picked.size} chosen). You can change them later." }
        refreshCount()
        val tiles = appGrid(apps, { it in picked }) { a, t ->
            if (a.pkg in picked) { picked.remove(a.pkg); t.chosen = false }
            else if (picked.size >= HomeScreen.COLS) Toast.makeText(context, "The top row holds ${HomeScreen.COLS} apps. Remove one first.", Toast.LENGTH_SHORT).show()
            else { picked.add(a.pkg); t.chosen = true }
            refreshCount()
        }
        if (!single) navButton("Back") { back() }
        navButton(continueLabel(), primary = true) { next() }
        tiles.firstOrNull()?.requestFocus()
    }

    /** Settings > Hidden apps: ticked apps stay off the drawer, the top row and the top row picker. */
    private fun hiddenStep() {
        title.text = "Hidden apps"
        val apps = allApps()
        fun refreshCount() {
            subtitle.text = if (hidden.isEmpty()) "Choose apps to hide from Home. They still open from the Top Shelf."
                else "${hidden.size} hidden. Choose an app again to show it on Home."
        }
        refreshCount()
        // Hidden apps first, so the ones to bring back are at hand.
        val sorted = apps.filter { it.pkg in hidden } + apps.filter { it.pkg !in hidden }
        val tiles = appGrid(sorted, { it in hidden }) { a, t ->
            if (a.pkg !in hidden && apps.count { it.pkg !in hidden } <= 1) {
                Toast.makeText(context, "Keep at least one app on Home", Toast.LENGTH_SHORT).show()
                return@appGrid
            }
            if (!hidden.remove(a.pkg)) hidden.add(a.pkg)
            t.chosen = a.pkg in hidden
            refreshCount()
        }
        navButton("Done", primary = true) { next() }
        tiles.firstOrNull()?.requestFocus()
    }

    private fun allApps(): List<AppEntry> = HomeShare.apps ?: AppRepo.load(context).also { HomeShare.apps = it }

    /** A scrolling grid of app thumbnails, five a row; [chosen] marks a tile, [click] runs on OK. */
    private fun appGrid(apps: List<AppEntry>, chosen: (String) -> Boolean, click: (AppEntry, ThumbView) -> Unit): List<ThumbView> {
        val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; clipChildren = true; clipToPadding = false }
        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        grid.setPadding(dpi(14f), dpi(14f), dpi(14f), dpi(14f))
        scroll.addView(grid, ViewGroup.LayoutParams(-1, -2))
        body.addView(scroll, LayoutParams(-1, -1))
        if (Build.VERSION.SDK_INT >= 23) scroll.setOnScrollChangeListener { _, _, _, _, _ -> invalidateDeep(grid) }
        val cols = 5
        val tw = (panelW - dpi(68f) - dpi(28f) - (cols - 1) * dpi(18f)) / cols
        val th = tw * 9 / 16
        val tiles = ArrayList<ThumbView>()
        var row: LinearLayout? = null
        apps.forEachIndexed { i, a ->
            if (i % cols == 0) {
                row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
                grid.addView(row, LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = dpi(10f) })
            }
            val t = ThumbView(context, tw, th, a.pkg, a.label).apply {
                id = View.generateViewId()
                bitmap = tileArt(a, tw, th)
                this.chosen = chosen(a.pkg)
            }
            t.onClick = { click(a, t) }
            row!!.addView(t, LinearLayout.LayoutParams(-2, -2).apply { if (i % cols > 0) leftMargin = dpi(18f) })
            tiles.add(t)
        }
        return tiles
    }

    /** The app's banner, or its icon centred on its own colour, at thumbnail size. */
    private fun tileArt(a: AppEntry, w: Int, h: Int): Bitmap? {
        val art = a.art ?: return null
        if (a.isBanner) return art
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(a.color)
        val s = h * 0.62f
        c.drawBitmap(art, null, RectF((w - s) / 2, (h - s) / 2, (w + s) / 2, (h + s) / 2), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return b
    }

    private var grantRows = ArrayList<Pair<Grants.Grant, RowView>>()
    private var granting = false
    /** Set while an attempt waits on the TV's prompt: Set Up again starts a new attempt (the prompt may have been cancelled). */
    private var waitingForPrompt = false
    private var attempt = 0

    private fun permissionsStep() {
        title.text = "Permissions"
        subtitle.text = "GlassOS gives itself a few permissions once, over the TV's own ADB."
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        // The how-to, replaced by progress and results once Set Up is chosen.
        val status = text(howTo() + " Everything works without these, with less.",
            14f, 0xD9FFFFFF.toInt()).apply { gravity = Gravity.CENTER_HORIZONTAL }
        box.addView(status, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dpi(10f) })
        grantRows = ArrayList()
        for (g in Grants.ALL) {
            val r = RowView(context, g.title).apply { isFocusable = false; isClickable = false }
            r.titleView.textSize = 16f
            r.valueView.textSize = 15f
            box.addView(r, LinearLayout.LayoutParams(-1, dpi(34f)).apply { bottomMargin = dpi(3f) })
            grantRows.add(g to r)
        }
        body.addView(box, LayoutParams(-1, -2, Gravity.CENTER_VERTICAL))
        refreshGrants()
        if (!single) navButton("Back") { back() }
        val setUp = navButton("Set Up", primary = true) { runGrants(status) }
        navButton(if (single) "Close" else "Skip") { next() }
        if (Grants.allGranted(context)) status.text = "All set: every permission is granted."
        setUp.requestFocus()
    }

    private fun refreshGrants() {
        for ((g, r) in grantRows) {
            val ok = g.isGranted(context)
            r.valueView.text = if (ok) "✓  " + g.why else g.why
            r.valueView.setTextColor(if (ok) 0xFF7CE38B.toInt() else 0x99FFFFFF.toInt())
        }
    }

    private fun runGrants(status: TextView) {
        if (granting && !waitingForPrompt) return
        if (Grants.allGranted(context)) { status.text = "All set: every permission is granted."; return }
        granting = true
        waitingForPrompt = false
        val id = ++attempt
        status.text = "Connecting to the TV's ADB…"
        Thread {
            var msg: String
            try {
                AdbClient.connect(context, onPrompt = {
                    ui.post { if (id == attempt) { waitingForPrompt = true; status.text = "Choose Allow on the TV's prompt (tick Always allow)." } }
                }).use { adb ->
                    prefs.adbBlockedBuild = ""
                    if (id != attempt) return@Thread
                    ui.post { if (id == attempt) { waitingForPrompt = false; status.text = "Connected. Granting…" } }
                    val r = Grants.run(context, adb)
                    val missing = r.ok.filterValues { !it }.keys.map { it.title }
                    msg = if (missing.isEmpty()) "All set: every permission is granted. You can turn ADB debugging off again."
                    else "Could not grant: ${missing.joinToString(", ")}. ${r.log.lineSequence().firstOrNull() ?: ""}"
                }
            } catch (e: Throwable) {
                WLog.i("adb: grants failed: $e")
                if (blockedOnFire(e)) prefs.adbBlockedBuild = Build.FINGERPRINT
                msg = adbError(e)
            }
            ui.post {
                if (id != attempt) return@post
                granting = false
                waitingForPrompt = false
                if (!isOpen) return@post
                status.text = msg
                refreshGrants()
                invalidateDeep(panel)
            }
        }.start()
    }

    /**
     * How to turn on ADB for the one-tap grants, for this platform. A Fire TV whose ADB kept GlassOS out before (and
     * has not been updated since) gets the computer steps first.
     */
    private fun howTo(): String = when {
        fireTv && prefs.adbBlockedBuild == Build.FINGERPRINT ->
            "This Fire TV kept GlassOS out of its ADB last time. ${computerGrants()} Or choose Set Up to try again."
        fireTv ->
            "Turn on ADB debugging in Settings > My Fire TV > Developer options, choose Set Up, then choose Allow " +
                "when the TV asks \"Allow USB debugging?\" (tick Always allow)."
        else ->
            "Turn on USB debugging (or Network debugging) in Settings > Device Preferences > Developer options, choose Set Up, " +
                "then choose Allow when the TV asks \"Allow USB debugging?\" (tick Always allow)."
    }

    /**
     * True on Fire TV for any failure but nothing listening on the port or no answer to the prompt. Fire OS builds that
     * block apps from the TV's own ADB (late Fire OS 6, 7.6.6.9 and later, 8.1.0.3 and later) accept the connection,
     * then close or refuse it in ways that differ between builds; turning debugging off and on does not help there.
     */
    private fun blockedOnFire(e: Throwable) =
        fireTv && e !is java.net.ConnectException && e !is java.net.SocketTimeoutException

    /** The way round a Fire TV that blocks its ADB. Fire OS 5 and 6 need their own notification access command. */
    private fun computerGrants() = "Run the grants from a computer instead (README: Manual grants" +
        (if (Build.VERSION.SDK_INT < 26) ", with the Fire OS 5 and 6 notification command)." else ").")

    /** A clear message for a failed connection to the TV's own ADB. */
    private fun adbError(e: Throwable): String = when (e) {
        is java.net.ConnectException -> if (fireTv)
            "Could not reach ADB. Turn on ADB debugging in Developer options, then try again. No Developer options? " +
                "Open Settings > My Fire TV > About and press OK on your device name 7 times."
        else
            "The TV refused the connection on 127.0.0.1:5555. Turn on USB or Network debugging in Device Preferences > " +
                "Developer options (no Developer options? press OK on Build in Device Preferences > About 7 times). " +
                "On Android 11 and later, Wireless debugging alone does not open this port: run the adb commands from a computer instead (see the README)."
        is java.net.SocketTimeoutException -> "No answer from the TV's prompt. Try again and choose Allow."
        is AdbClient.Closed -> if (fireTv)
            "This Fire TV closed its ADB to GlassOS (${e.stage}). Recent Fire OS updates block apps from using the " +
                "TV's own ADB. ${computerGrants()}"
        else
            "The TV closed the ADB connection (${e.stage}). Turn debugging off and on and try again, or run the grants " +
                "from a computer (README: Manual grants)."
        // On Fire TV the same block, refused another way; the class name tells reports which way this build refuses.
        else -> if (fireTv)
            "This Fire TV refused GlassOS on its ADB (${e.javaClass.simpleName}: ${e.message}). Recent Fire OS updates " +
                "block apps from using the TV's own ADB. ${computerGrants()}"
        else
            "ADB refused the connection (${e.javaClass.simpleName}: ${e.message}). Turn debugging off and on and " +
                "try again, or run the grants from a computer (README: Manual grants)."
    }

    // ---------- Home app (Android TV and Google TV) ----------

    /** How the next "Make Home" press tries: the system's own ways first, then ADB. */
    private var homeTries = 0

    private fun homeStep() {
        title.text = "Make GlassOS your home screen"
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; clipChildren = false; clipToPadding = false }
        val status = text("", 15f, 0xD9FFFFFF.toInt()).apply { gravity = Gravity.CENTER_HORIZONTAL }
        homeStatus = status
        homeTries = 0
        box.addView(status, LinearLayout.LayoutParams(-1, -2))
        body.addView(box, LayoutParams(-1, -2, Gravity.CENTER_VERTICAL))
        if (!single) navButton("Back") { back() }
        val make = navButton("Make Home", primary = true) { makeHome(status) }
        navButton(if (single) "Close" else continueLabel()) { next() }
        refreshHome()
        make.requestFocus()
    }

    private var homeStatus: TextView? = null

    private fun refreshHome() {
        val status = homeStatus ?: return
        if (Platform.isDefaultHome(context)) {
            subtitle.text = "Done: Home opens GlassOS."
            status.text = "✓  GlassOS is the default home app. The Home button opens it straight away, with no watcher running."
            status.setTextColor(0xFF7CE38B.toInt())
        } else if (homeTries > 0) {
            subtitle.text = "Home still opens the TV's own home screen."
            status.text = "Choose Make Home again to try another way. If none works, this TV keeps its own home app, and " +
                "GlassOS comes back after Home on its own (needs Usage access and Display over other apps)."
            status.setTextColor(0xD9FFFFFF.toInt())
        } else {
            subtitle.text = "So the Home button opens GlassOS straight away."
            status.text = "Choose Make Home, then pick GlassOS (choose Always if asked). If your TV does not let you change " +
                "its home app, GlassOS comes back after Home on its own (needs Usage access and Display over other apps)."
            status.setTextColor(0xD9FFFFFF.toInt())
        }
    }

    /** Back from the system's home picker: show whether it worked. */
    fun onReturned() { if (isOpen && step == Step.HOME) { refreshHome(); invalidateDeep(panel) } }

    private fun makeHome(status: TextView) {
        if (Platform.isDefaultHome(context)) { refreshHome(); return }
        val act = context as? Activity
        val n = homeTries++
        // 1. The system's role request (Android 10+), 2. the default-apps page, 3. the Home chooser, 4. ADB.
        if (n == 0 && act != null && Build.VERSION.SDK_INT >= 29) {
            val rm = context.getSystemService(android.app.role.RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME) && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                try { act.startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), REQ_HOME); return } catch (e: Throwable) { }
            }
        }
        if (n <= 1) {
            val i = Platform.homeSettingsIntent(context)
            if (i != null) try {
                if (act == null) i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
                Toast.makeText(context, "Choose GlassOS as the home app", Toast.LENGTH_LONG).show()
                return
            } catch (e: Throwable) { }
            if (Platform.homeChooserAvailable(context)) try {
                context.startActivity(Platform.homeChooserIntent().addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                Toast.makeText(context, "Choose GlassOS, then Always", Toast.LENGTH_LONG).show()
                return
            } catch (e: Throwable) { }
        }
        setHomeOverAdb(status)
    }

    /** `cmd package set-home-activity` over the TV's own ADB, for builds whose settings will not change the home app. */
    private fun setHomeOverAdb(status: TextView) {
        if (Build.VERSION.SDK_INT < 24) {
            status.text = "This Android version cannot change the home app this way. GlassOS comes back after Home on its own."
            return
        }
        if (granting) return
        granting = true
        val id = ++attempt
        status.text = "Setting the home app over the TV's ADB…"
        Thread {
            val msg: String = try {
                AdbClient.connect(context, onPrompt = {
                    ui.post { if (id == attempt) status.text = "Choose Allow on the TV's prompt (tick Always allow)." }
                }).use { adb -> Grants.setHome(context, adb) }
                if (Platform.isDefaultHome(context)) "" else
                    "This TV keeps its own home app. GlassOS comes back after Home on its own instead (needs Usage access and Display over other apps)."
            } catch (e: Throwable) { adbError(e) }
            ui.post {
                granting = false
                if (!isOpen || id != attempt) return@post
                HomeWatcher.sync(context)
                if (msg.isEmpty()) refreshHome() else status.text = msg
                invalidateDeep(panel)
            }
        }.start()
    }

    private fun doneStep() {
        title.text = "You are all set"
        subtitle.text = "Press Menu any time for Control Center. Long press OK on an app to add it to the top row or remove it."
        val av = AvatarPreview(context, dpi(110f))
        val f = Profile.photoFile(context)
        if (f.exists()) av.bitmap = android.graphics.BitmapFactory.decodeFile(f.path)
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        box.addView(av, LinearLayout.LayoutParams(dpi(110f), dpi(110f)))
        box.addView(TextView(context).apply {
            text = prefs.name.ifEmpty { "Welcome" }; textSize = 24f; typeface = Fonts.medium; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(12f) })
        body.addView(box, LayoutParams(-1, -2, Gravity.CENTER))
        navButton("Back") { back() }
        navButton("Start", primary = true) { finish() }.requestFocus()
    }

    private fun aboutStep() {
        title.text = "About GlassOS"
        subtitle.text = "Version ${BuildConfig.VERSION_NAME}. Free software under the GNU GPL v3. Inspired by tvOS."
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        box.addView(ImageView(context).apply { setImageResource(R.drawable.tmdb_logo); scaleType = ImageView.ScaleType.FIT_CENTER },
            LinearLayout.LayoutParams(dpi(150f), dpi(44f)))
        box.addView(text("This product uses the TMDB API but is not endorsed or certified by TMDB.", 15f).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(12f) })
        box.addView(text("Top Shelf: ${HeroRepo.sourceLabel(prefs.topShelf)}. Titles and images come from TMDB; streaming availability from TMDB's watch provider data (JustWatch).", 14f, 0xB3FFFFFF.toInt()).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(12f) })
        box.addView(text("Not affiliated with or endorsed by Google, Amazon or Apple. Android TV and Google TV are trademarks of Google LLC; " +
            "Fire TV is a trademark of Amazon.com, Inc.", 13f, 0x99FFFFFF.toInt()).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(12f) })
        body.addView(box, LayoutParams(-1, -2, Gravity.CENTER))
        navButton("Close", primary = true) { close() }.requestFocus()
    }

    // ---------- Small views ----------

    /** Step dots at the top of the panel. */
    private class Dots(ctx: Context) : View(ctx) {
        var count = 0
        var index = 0
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            if (count <= 0) return
            val r = dp(3.5f)
            val gap = dp(14f)
            val start = width / 2f - (count - 1) * gap / 2
            for (i in 0 until count) {
                p.color = if (i == index) Color.WHITE else 0x4DFFFFFF
                c.drawCircle(start + i * gap, height / 2f, r, p)
            }
        }
    }

    /** A glass text field showing [value] with a caret, or [hint] when empty. */
    private class Field(ctx: Context, private val hint: String) : View(ctx) {
        var value = ""
            set(v) { field = v; invalidate() }
        var small = false
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private val t = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.regular; color = Color.WHITE }
        override fun onDraw(c: Canvas) {
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            fill.color = 0x26FFFFFF
            c.drawRoundRect(rect, height / 2f, height / 2f, fill)
            t.textSize = dp(if (small) 15f else 22f)
            val pad = dp(22f)
            val empty = value.isEmpty()
            t.color = if (empty) 0x80FFFFFF.toInt() else Color.WHITE
            var s = if (empty) hint else value
            // Long values (API keys) show their end, where typing happens.
            while (!empty && t.measureText("$s|") > width - 2 * pad && s.length > 1) s = s.substring(1)
            val y = height / 2f - (t.ascent() + t.descent()) / 2
            c.drawText(s, pad, y, t)
            if (!empty) { t.color = 0xCCFFFFFF.toInt(); c.drawText("|", pad + t.measureText(s) + dp(1f), y, t) }
        }
    }

    /** The round avatar preview: the photo, or the glass silhouette. */
    private class AvatarPreview(ctx: Context, private val size: Int) : View(ctx) {
        var bitmap: Bitmap? = null
            set(v) { field = v; invalidate() }
        private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val m = android.graphics.Matrix()
        override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(size, size)
        override fun onDraw(c: Canvas) {
            val r = size / 2f
            val b = bitmap
            if (b == null) { Profile.drawSilhouette(c, r, r, r); return }
            val sh = android.graphics.BitmapShader(b, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
            val sc = maxOf(size / b.width.toFloat(), size / b.height.toFloat())
            m.setScale(sc, sc); m.postTranslate((size - b.width * sc) / 2, (size - b.height * sc) / 2)
            sh.setLocalMatrix(m)
            p.shader = sh
            c.drawCircle(r, r, r, p)
        }
    }
}

/**
 * On-screen keyboard for the D-pad: letters (with Shift), optionally digits, space, delete and clear. Each key
 * turns white with dark text when focused, like the other glass buttons.
 */
class Keyboard(ctx: Context, digits: Boolean, private val capitalise: Boolean = true, private val onKey: (String) -> Unit) : LinearLayout(ctx) {
    private var shift = capitalise
    private val letterKeys = ArrayList<Key>()
    private val rows = ArrayList<List<Key>>()

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
        val lines = ArrayList<List<String>>()
        lines.add("abcdefghijklm".map { it.toString() })
        lines.add("nopqrstuvwxyz".map { it.toString() })
        lines.add(if (digits) "1234567890-_.".map { it.toString() } else "1234567890-.'".map { it.toString() })
        for (l in lines) addRow(l.map { k -> Key(context, k, 1).also { if (k[0].isLetter()) letterKeys.add(it) } })
        addRow(listOf(Key(context, SHIFT, 2), Key(context, " ", 5, "space"), Key(context, DEL, 3, "delete"), Key(context, CLEAR, 3, "clear")))
        updateCase()
    }

    private fun addRow(keys: List<Key>) {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER; clipChildren = false; clipToPadding = false }
        keys.forEachIndexed { i, k ->
            k.id = View.generateViewId()
            k.onClick = { press(k) }
            row.addView(k, LayoutParams(k.w, dpi(KEY), 0f).apply { if (i > 0) leftMargin = dpi(GAP) })
        }
        addView(row, LayoutParams(-2, -2).apply { if (rows.isNotEmpty()) topMargin = dpi(GAP) })
        rows.add(keys)
    }

    private fun press(k: Key) {
        when (k.value) {
            SHIFT -> { shift = !shift; updateCase(); return }
            else -> onKey(if (k.value.length == 1 && k.value[0].isLetter() && shift) k.value.uppercase() else k.value)
        }
        if (shift && k.value.length == 1 && k.value[0].isLetter()) { shift = false; updateCase() }
        if (k.value == " " && capitalise) { shift = true; updateCase() }
    }

    private fun updateCase() { letterKeys.forEach { it.label = if (shift) it.value.uppercase() else it.value } }

    fun firstKey(): View? = rows.firstOrNull()?.firstOrNull()

    /** Down from the last row moves to [v] (the Continue button). */
    fun downTo(v: View) { rows.lastOrNull()?.forEach { it.nextFocusDownId = v.id }; v.nextFocusUpId = rows.last()[1].id }

    class Key(ctx: Context, val value: String, units: Int, display: String? = null) : View(ctx) {
        val w = ctx.dpi(KEY * units + GAP * (units - 1))
        var label = display ?: if (value == SHIFT) "shift" else value
            set(v) { field = v; invalidate() }
        var onClick: (() -> Unit)? = null
        private val focus = FocusAnim(this, 1.12f) { invalidate() }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private val t = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.medium; textAlign = Paint.Align.CENTER }
        init { isFocusable = true; isClickable = true; setOnClickListener { onClick?.invoke() } }
        override fun onFocusChanged(gained: Boolean, direction: Int, previous: Rect?) {
            super.onFocusChanged(gained, direction, previous); focus.set(gained)
        }
        override fun onDraw(c: Canvas) {
            val f = focus.amount
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            fill.color = alphaOf(Color.WHITE, 0.12f + 0.85f * f)
            c.drawRoundRect(rect, dp(10f), dp(10f), fill)
            t.textSize = dp(if (label.length > 1) 13f else 19f)
            t.color = lerpColor(Color.WHITE, 0xFF1C1C1E.toInt(), f)
            c.drawText(label, width / 2f, height / 2f - (t.ascent() + t.descent()) / 2, t)
        }
    }

    companion object {
        const val SHIFT = "\u0001shift"
        const val DEL = "\u0001del"
        const val CLEAR = "\u0001clear"
        private const val KEY = 36f
        private const val GAP = 5f
    }
}
