package io.github.abhishekkaisar.glassos

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("glass", Context.MODE_PRIVATE)

    var sound: Boolean
        get() = sp.getBoolean("sound", true)
        set(v) { sp.edit().putBoolean("sound", v).apply() }

    /**
     * Fallback background, shown when the Top Shelf has no title art: a built-in scene (scene:..., default),
     * the user's own photos in Pictures/Glass (hero:photos) or one image (file:...).
     */
    var scene: String
        get() = sp.getString("fallback", DEFAULT_SCENE) ?: DEFAULT_SCENE
        set(v) { sp.edit().putString("fallback", v).apply() }

    /** Top Shelf source: [HeroRepo.SRC_TMDB] (default), [HeroRepo.SRC_APPLE] or [HeroRepo.SRC_SCENES]. */
    var topShelf: String
        get() = sp.getString("topShelf", HeroRepo.SRC_TMDB) ?: HeroRepo.SRC_TMDB
        set(v) { sp.edit().putString("topShelf", v).apply() }

    /** TMDB key entered in Settings; the build's own key (BuildConfig.TMDB_API_KEY) is used when this is empty. */
    var tmdbKey: String
        get() = sp.getString("tmdbKey", "") ?: ""
        set(v) { sp.edit().putString("tmdbKey", v.trim()).apply() }

    val effectiveTmdbKey: String get() = tmdbKey.ifEmpty { BuildConfig.TMDB_API_KEY }

    /** ISO 3166 country code for the Top Shelf region (streaming availability). */
    var country: String
        get() = sp.getString("country", "") ?: ""
        set(v) { sp.edit().putString("country", v).apply() }

    var replaceHome: Boolean
        get() = sp.getBoolean("replaceHome", true)
        set(v) { sp.edit().putBoolean("replaceHome", v).apply() }

    var dock: List<String>?
        get() = sp.getString("dock", null)?.split(',')?.filter { it.isNotBlank() }
        set(v) { sp.edit().putString("dock", v?.joinToString(",")).apply() }

    /** The drawer's order once the user has moved an app (null: alphabetical). Apps not in it go at the end. */
    var order: List<String>?
        get() = sp.getString("order", null)?.split(',')?.filter { it.isNotBlank() }
        set(v) { sp.edit().putString("order", v?.joinToString(",")).apply() }

    /** Apps left out of the drawer, the top row and the top row picker (Settings > Hidden apps). */
    var hidden: Set<String>
        get() = sp.getString("hidden", null)?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        set(v) { sp.edit().putString("hidden", v.joinToString(",")).apply() }

    var name: String
        get() = sp.getString("name", "") ?: ""
        set(v) { sp.edit().putString("name", v).apply() }

    var setupDone: Boolean
        get() = sp.getBoolean("setupDone", false)
        set(v) { sp.edit().putBoolean("setupDone", v).apply() }

    var askedStorage: Boolean
        get() = sp.getBoolean("askedStorage", false)
        set(v) { sp.edit().putBoolean("askedStorage", v).apply() }

    /** Build.FINGERPRINT of a Fire OS build that kept GlassOS out of its own ADB; setup then leads with a computer. */
    var adbBlockedBuild: String
        get() = sp.getString("adbBlockedBuild", "") ?: ""
        set(v) { sp.edit().putString("adbBlockedBuild", v).apply() }

    companion object {
        const val DEFAULT_SCENE = "scene:desert"
    }
}
