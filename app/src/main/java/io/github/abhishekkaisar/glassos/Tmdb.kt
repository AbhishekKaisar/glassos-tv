package io.github.abhishekkaisar.glassos

import android.content.Context
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Top Shelf titles from TMDB: trending films and series, with titles from the streaming services installed on
 * this TV mixed in first (TMDB watch providers for the TV's country, or the one chosen in Settings). Each title
 * gets a wide, text-free backdrop, its English title logo when TMDB has one, and the installed app OK should open.
 *
 * This product uses the TMDB API but is not endorsed or certified by TMDB.
 */
object Tmdb {
    private const val API = "https://api.themoviedb.org/3"
    private const val IMG = "https://image.tmdb.org/t/p/"
    private const val MAX_TITLES = 8

    /** A streaming service: its TMDB watch provider ids and the app packages that play it. */
    class Provider(val name: String, val ids: List<Int>, val pkgs: List<String>)

    val PROVIDERS = listOf(
        Provider("Netflix", listOf(8), listOf("com.netflix.ninja")),
        Provider("Prime Video", listOf(9, 119), listOf("com.amazon.firebat", "com.amazon.avod", "com.amazon.amazonvideo.livingroom")),
        Provider("Disney+", listOf(337), listOf("com.disney.disneyplus")),
        Provider("Apple TV+", listOf(350), listOf("com.apple.atve.amazon.appletv", "com.apple.atve.androidtv.appletv")),
        Provider("Max", listOf(1899, 384), listOf("com.wbd.stream", "com.hbo.hbonow")),
        Provider("Hulu", listOf(15), listOf("com.hulu.livingroomplus", "com.hulu.plus")),
        Provider("Paramount+", listOf(531), listOf("com.cbs.ott", "com.cbs.app")),
        Provider("Peacock", listOf(386), listOf("com.peacocktv.peacockandroid")),
        Provider("Crunchyroll", listOf(283), listOf("com.crunchyroll.crunchyroid")),
        Provider("hoichoi", listOf(315), listOf("com.viewlift.hoichoiprod")),
        Provider("MUBI", listOf(11), listOf("com.mubi")),
        Provider("ZEE5", listOf(232), listOf("com.graymatrix.did")),
        Provider("Hotstar", listOf(122), listOf("in.startv.hotstar"))
    )

    /** Installed services, each with the first of its packages that is installed. */
    fun installed(ctx: Context): List<Pair<Provider, String>> {
        val pm = ctx.packageManager
        return PROVIDERS.mapNotNull { p ->
            p.pkgs.firstOrNull { pkg -> try { pm.getApplicationInfo(pkg, 0); true } catch (e: Throwable) { false } }?.let { p to it }
        }
    }

    private class Ref(val type: String, val id: Long, val title: String, val backdrop: String, val providerPkg: String?)

    /**
     * Fetches the current titles. Empty without a key or a network. Call off the UI thread. [trending] ("day" or
     * "week") takes TMDB's trending list of that window alone; without it the installed services' titles come first.
     * Either way OK opens a title in an installed service that streams it here.
     */
    fun fetch(ctx: Context, trending: String? = null): List<HeroRepo.Item> {
        val prefs = Prefs(ctx)
        val key = prefs.effectiveTmdbKey
        if (key.isEmpty()) return emptyList()
        val region = prefs.effectiveCountry
        val services = installed(ctx)
        val byId = HashMap<Int, Pair<Provider, String>>()
        services.forEach { s -> s.first.ids.forEach { byId[it] = s } }

        // Lists: titles on the installed services (series and films), then trending, mixed in turn.
        val lists = ArrayList<List<Ref>>()
        if (trending == null && services.isNotEmpty()) {
            val ids = services.flatMap { it.first.ids }.joinToString("|")
            for (type in listOf("tv", "movie")) {
                lists.add(refs(get(key, "/discover/$type?watch_region=$region&with_watch_providers=${enc(ids)}&with_watch_monetization_types=flatrate&sort_by=popularity.desc&include_adult=false"), type))
            }
        }
        lists.add(refs(get(key, "/trending/all/${trending ?: "week"}"), null))
        val picked = ArrayList<Ref>()
        val seen = HashSet<String>()
        var i = 0
        while (picked.size < MAX_TITLES && lists.any { i < it.size }) {
            for (l in lists) {
                val r = l.getOrNull(i) ?: continue
                if (seen.add(r.type + r.id) && picked.size < MAX_TITLES) picked.add(r)
            }
            i++
        }
        return picked.mapNotNull { r -> item(key, r, region, byId) }
    }

    private fun item(key: String, r: Ref, region: String, byId: Map<Int, Pair<Provider, String>>): HeroRepo.Item? {
        // The service that streams it here, if it is installed.
        var service: Pair<Provider, String>? = null
        get(key, "/${r.type}/${r.id}/watch/providers")?.optJSONObject("results")?.optJSONObject(region)?.optJSONArray("flatrate")?.let { arr ->
            for (k in 0 until arr.length()) {
                byId[arr.getJSONObject(k).optInt("provider_id")]?.let { service = it }
                if (service != null) break
            }
        }
        // A text-free backdrop and the English title logo.
        var backdrop = r.backdrop
        var logo: String? = null
        get(key, "/${r.type}/${r.id}/images?include_image_language=en,null")?.let { o ->
            o.optJSONArray("backdrops")?.let { arr ->
                for (k in 0 until arr.length()) {
                    val b = arr.getJSONObject(k)
                    if (b.isNull("iso_639_1") && b.optInt("width") >= 1920) { backdrop = b.optString("file_path"); break }
                }
            }
            o.optJSONArray("logos")?.let { arr ->
                for (k in 0 until arr.length()) {
                    val l = arr.getJSONObject(k)
                    val path = l.optString("file_path")
                    if (l.optString("iso_639_1") == "en" && path.endsWith(".png")) { logo = IMG + "w500" + path; break }
                }
            }
        }
        if (backdrop.isEmpty()) return null
        val kind = if (r.type == "tv") "TV Show" else "Film"
        val sub = service?.let { "$kind · ${it.first.name}" } ?: kind
        return HeroRepo.Item("tmdb_${r.type}_${r.id}", r.title, sub, "", IMG + "original" + backdrop, logo, service?.second)
    }

    private fun refs(o: JSONObject?, forcedType: String?): List<Ref> {
        val arr = o?.optJSONArray("results") ?: return emptyList()
        val out = ArrayList<Ref>()
        for (k in 0 until arr.length()) {
            val x = arr.getJSONObject(k)
            val type = forcedType ?: x.optString("media_type")
            if (type != "tv" && type != "movie") continue
            if (x.optBoolean("adult")) continue
            val bd = x.optString("backdrop_path").takeIf { it.startsWith("/") } ?: continue
            val title = x.optString(if (type == "tv") "name" else "title")
            out.add(Ref(type, x.optLong("id"), title, bd, null))
        }
        return out
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** v3 keys go in the query; a v4 read access token (a JWT) goes in the Authorization header. */
    private fun get(key: String, path: String): JSONObject? {
        val bearer = key.startsWith("eyJ")
        val sep = if (path.contains('?')) '&' else '?'
        val url = API + path + if (bearer) "" else "${sep}api_key=${enc(key)}"
        val headers = if (bearer) mapOf("Authorization" to "Bearer $key", "Accept" to "application/json") else mapOf("Accept" to "application/json")
        val (code, body) = HeroRepo.get(url, headers)
        if (code != 200 || body == null) { WLog.i("tmdb $path -> $code"); return null }
        return try { JSONObject(body) } catch (e: Throwable) { null }
    }
}
