package io.github.abhishekkaisar.glassos

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import java.io.File

class AppEntry(val pkg: String, val label: String, val art: Bitmap?, val isBanner: Boolean, val color: Int)

object AppRepo {
    /**
     * Popular streaming apps in the order the default top row uses them. Each entry lists the package names the
     * same service uses on Fire TV and on Android TV.
     */
    val POPULAR: List<List<String>> = listOf(
        listOf("com.amazon.firetv.youtube", "com.google.android.youtube.tv"),
        listOf("com.netflix.ninja"),
        listOf("com.amazon.firebat", "com.amazon.avod", "com.amazon.amazonvideo.livingroom"),
        listOf("com.disney.disneyplus"),
        listOf("com.apple.atve.amazon.appletv", "com.apple.atve.androidtv.appletv"),
        listOf("com.spotify.tv.android"),
        listOf("com.wbd.stream", "com.hbo.hbonow"),
        listOf("com.hulu.livingroomplus", "com.hulu.plus"),
        listOf("com.cbs.ott", "com.cbs.app"),
        listOf("com.peacocktv.peacockandroid"),
        listOf("com.plexapp.android"),
        listOf("tv.twitch.android.app", "tv.twitch.android.viewer"),
        listOf("com.crunchyroll.crunchyroid")
    )

    /** The first six popular apps that are installed, in [POPULAR] order, then other apps alphabetically. */
    fun defaultDock(apps: List<AppEntry>, max: Int = HomeScreen.COLS): List<String> {
        val installed = apps.map { it.pkg }.toSet()
        val out = ArrayList<String>()
        for (alts in POPULAR) alts.firstOrNull { it in installed }?.let { out.add(it) }
        for (a in apps) if (a.pkg !in out) out.add(a.pkg)
        return out.take(max)
    }

    /** [apps] in the [saved] order; apps missing from it (new installs) follow in their own order. */
    fun arrange(apps: List<AppEntry>, saved: List<String>?): List<AppEntry> {
        if (saved.isNullOrEmpty()) return apps
        val byPkg = apps.associateBy { it.pkg }
        val out = ArrayList<AppEntry>(apps.size)
        val seen = HashSet<String>()
        for (p in saved) byPkg[p]?.let { if (seen.add(p)) out.add(it) }
        for (a in apps) if (a.pkg !in seen) out.add(a)
        return out
    }

    private val EXTS = setOf("png", "jpg", "jpeg", "webp")

    /** Custom tile art: an image per app in Pictures/Glass/icons, named after the app ("Netflix.png") or its package. */
    fun iconsDir(): File = File(WallpaperStore.userDir(), "icons")

    /** Letters and digits in lower case, so "Prime Video.png" and "primevideo.png" both match Prime Video. */
    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** Images in [iconsDir] by normalised name. Empty without storage access. */
    private fun customArt(ctx: Context): Map<String, File> = if (!Grants.storage(ctx)) emptyMap() else try {
        iconsDir().listFiles()?.filter { it.isFile && it.extension.lowercase() in EXTS && norm(it.nameWithoutExtension).isNotEmpty() }
            ?.associateBy { norm(it.nameWithoutExtension) } ?: emptyMap()
    } catch (e: Throwable) { emptyMap() }

    private fun customStamp(ctx: Context): String = if (!Grants.storage(ctx)) "" else try {
        iconsDir().listFiles()?.sortedBy { it.name }?.joinToString("|") { "${it.name}:${it.length()}:${it.lastModified()}" } ?: ""
    } catch (e: Throwable) { "" }

    @Volatile private var loadedStamp: String? = null

    /** True when Pictures/Glass/icons changed since the app list was loaded: Glass reloads it on coming back. */
    fun customArtChanged(ctx: Context): Boolean = loadedStamp.let { it != null && it != customStamp(ctx) }

    fun load(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        loadedStamp = customStamp(ctx)
        val custom = customArt(ctx)
        val map = LinkedHashMap<String, ResolveInfo>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val list = try { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0) } catch (e: Exception) { emptyList<ResolveInfo>() }
            for (ri in list) {
                val pkg = ri.activityInfo.packageName
                if (pkg == ctx.packageName || map.containsKey(pkg)) continue
                map[pkg] = ri
            }
        }
        return map.values.map { entry(ctx, it, custom) }.sortedBy { it.label.lowercase() }
    }

    private fun entry(ctx: Context, ri: ResolveInfo, custom: Map<String, File>): AppEntry {
        val pm = ctx.packageManager
        val ai = ri.activityInfo
        val label = (ri.loadLabel(pm)?.toString() ?: ai.packageName).trim()
        // The user's own art for this app comes first.
        (custom[norm(label)] ?: custom[norm(ai.packageName)])?.let { f ->
            customTile(ctx, f)?.let { (art, banner) ->
                return AppEntry(ai.packageName, label, art, banner, if (banner) 0xFF2A3350.toInt() else dominant(art))
            }
        }
        var d: Drawable? = null
        var banner = false
        try {
            val res = pm.getResourcesForApplication(ai.applicationInfo)
            val bannerId = if (ai.banner != 0) ai.banner else ai.applicationInfo.banner
            if (bannerId != 0) {
                d = try { res.getDrawableForDensity(bannerId, DisplayMetrics.DENSITY_XHIGH, null) } catch (e: Exception) { null }
                banner = d != null
            }
            if (d == null) {
                val iconId = ri.iconResource
                if (iconId != 0) d = try { res.getDrawableForDensity(iconId, DisplayMetrics.DENSITY_XXXHIGH, null) } catch (e: Exception) { null }
            }
        } catch (e: Exception) { }
        if (d == null) d = try { ri.loadIcon(pm) } catch (e: Exception) { null }
        val art = d?.let { if (banner) render(it, 400, 225) else iconArt(it) }
        val color = if (art != null && !banner) dominant(art) else 0xFF2A3350.toInt()
        return AppEntry(ai.packageName, label, art, banner, color)
    }

    /** Custom art from [f]: a wide image fills the tile like a TV banner, any other sits centred like an icon. */
    private fun customTile(ctx: Context, f: File): Pair<Bitmap, Boolean>? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        when {
            o.outWidth <= 0 || o.outHeight <= 0 -> null
            o.outWidth >= o.outHeight * 1.4f -> WallpaperStore.decodeCrop(f, 400, 225)?.let { it to true }
            else -> {
                var sample = 1
                while (o.outWidth / (sample * 2) >= 192 && o.outHeight / (sample * 2) >= 192) sample *= 2
                BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })?.let { src ->
                    val art = render(BitmapDrawable(ctx.resources, src), 192, 192)
                    src.recycle()
                    art to false
                }
            }
        }
    } catch (e: Throwable) { null }

    /** Average of the opaque, reasonably saturated pixels, used as the backdrop of icon-only tiles. */
    private fun dominant(b: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(b, 24, 24, true)
        var r = 0L; var g = 0L; var bl = 0L; var n = 0L
        var r2 = 0L; var g2 = 0L; var b2 = 0L; var n2 = 0L
        val hsv = FloatArray(3)
        for (y in 0 until 24) for (x in 0 until 24) {
            val c = small.getPixel(x, y)
            if (android.graphics.Color.alpha(c) < 200) continue
            android.graphics.Color.colorToHSV(c, hsv)
            r2 += android.graphics.Color.red(c); g2 += android.graphics.Color.green(c); b2 += android.graphics.Color.blue(c); n2++
            if (hsv[1] < 0.25f || hsv[2] < 0.15f) continue
            r += android.graphics.Color.red(c); g += android.graphics.Color.green(c); bl += android.graphics.Color.blue(c); n++
        }
        if (small !== b) small.recycle()
        if (n < 8) { r = r2; g = g2; bl = b2; n = n2 }
        if (n == 0L) return 0xFF2A3350.toInt()
        val c = android.graphics.Color.rgb((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
        android.graphics.Color.colorToHSV(c, hsv)
        hsv[2] = hsv[2].coerceIn(0.28f, 0.62f)
        return android.graphics.Color.HSVToColor(hsv)
    }

    private fun iconArt(d: Drawable): Bitmap {
        val s = 192
        if (d is BitmapDrawable && d.bitmap != null && d.bitmap.width == s && d.bitmap.height == s) return d.bitmap
        return render(d, s, s)
    }

    private fun render(d: Drawable, w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val iw = d.intrinsicWidth
        val ih = d.intrinsicHeight
        if (iw > 0 && ih > 0 && (iw * h != ih * w)) {
            // Fit inside, keeping the aspect ratio.
            val scale = minOf(w / iw.toFloat(), h / ih.toFloat())
            val dw = (iw * scale).toInt()
            val dh = (ih * scale).toInt()
            d.setBounds((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2)
        } else d.setBounds(0, 0, w, h)
        d.draw(c)
        return b
    }
}
