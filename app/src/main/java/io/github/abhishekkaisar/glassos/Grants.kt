package io.github.abhishekkaisar.glassos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * The permissions GlassOS needs that only ADB can grant on Fire TV, how to check each, and the shell commands that
 * grant it. Every feature checks its own permission and does without it (see the README).
 */
object Grants {
    /** [supported] is false on TVs that cannot hold the grant at all; it is then not offered. */
    class Grant(val title: String, val why: String, val isGranted: (Context) -> Boolean,
                val supported: (Context) -> Boolean = { true }, val commands: (Context) -> List<String>)

    private fun pkg(ctx: Context) = ctx.packageName

    val ALL = listOf(
        Grant("Usage access", "Comes back after the Home button", { HomeWatcher.hasAccess(it) }) { c ->
            listOf("appops set ${pkg(c)} GET_USAGE_STATS allow")
        },
        Grant("Display over other apps", "Shows the home at once after Home", { canDrawOverlays(it) }) { c ->
            listOf("appops set ${pkg(c)} SYSTEM_ALERT_WINDOW allow")
        },
        // API 26+ (Fire OS 7 and 8) keeps the listener list itself and ignores writes to the secure setting, so it
        // needs `cmd notification allow_listener`. Older versions read the setting, which is appended to, never
        // replaced, so other listeners stay enabled.
        Grant("Notification access", "Now Playing and Do Not Disturb", { listenerEnabled(it) }) { listOf("LISTENERS") },
        Grant("Device admin", "Sleep and the sleep timer", { Power.adminActive(it) }, supported = { Power.supported(it) }) { c ->
            listOf("dpm set-active-admin ${Power.admin(c).flattenToString()}")
        },
        Grant("Storage", "Your profile photo and own backgrounds", { storage(it) }) { c ->
            listOf("pm grant ${pkg(c)} ${Manifest.permission.READ_EXTERNAL_STORAGE}")
        }
    )

    fun canDrawOverlays(ctx: Context) = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(ctx)

    fun storage(ctx: Context) = Build.VERSION.SDK_INT < 23 ||
        ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun listenerEnabled(ctx: Context): Boolean = try {
        val comp = MediaListener.component(ctx)
        if (Build.VERSION.SDK_INT >= 27 &&
            ctx.getSystemService(android.app.NotificationManager::class.java).isNotificationListenerAccessGranted(comp)) true
        else {
            val v = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: ""
            v.split(':').any { it == comp.flattenToString() || it == comp.flattenToShortString() }
        }
    } catch (e: Throwable) { false }

    /** The grants this TV can hold: [ALL] less those it does not support (no device admin on the Nvidia Shield). */
    fun available(ctx: Context) = ALL.filter { it.supported(ctx) }

    fun allGranted(ctx: Context) = available(ctx).all { it.isGranted(ctx) }

    /** Result of one grant run: per grant, whether it now holds, and the output of anything that failed. */
    class Result(val ok: Map<Grant, Boolean>, val log: String)

    /**
     * Runs every missing grant with [shell] (the TV's own ADB, or root on a rooted TV) and checks each afterwards.
     * Call off the UI thread.
     */
    fun run(ctx: Context, shell: (String) -> String): Result {
        val log = StringBuilder()
        val grants = available(ctx)
        for (g in grants) {
            if (g.isGranted(ctx)) continue
            for (cmd in g.commands(ctx)) {
                val real = if (cmd == "LISTENERS") listenerCommand(ctx, shell) else cmd
                val out = try { shell(real) } catch (e: Throwable) { "error: ${e.message}" }
                WLog.i("grant: $real -> ${out.trim().take(200)}")
                if (out.isNotBlank() && !out.contains("Success", true)) log.append(g.title).append(": ").append(out.trim().take(160)).append('\n')
            }
        }
        // Grants land asynchronously on some builds; give them a moment.
        Thread.sleep(400)
        HomeWatcher.sync(ctx)
        return Result(grants.associateWith { it.isGranted(ctx) }, log.toString())
    }

    /** Makes GlassOS the default home app (Android TV, API 24+). Call off the UI thread. */
    fun setHome(ctx: Context, shell: (String) -> String) {
        val cmd = "cmd package set-home-activity " + Platform.homeComponent(ctx).flattenToShortString()
        val out = try { shell(cmd) } catch (e: Throwable) { "error: ${e.message}" }
        WLog.i("home: $cmd -> ${out.trim().take(200)}")
        Thread.sleep(300)
    }

    private fun listenerCommand(ctx: Context, shell: (String) -> String): String {
        val comp = MediaListener.component(ctx).flattenToString()
        if (Build.VERSION.SDK_INT >= 26) return "cmd notification allow_listener $comp"
        val cur = try { shell("settings get secure enabled_notification_listeners").trim() } catch (e: Throwable) { "" }
        val list = cur.split(':').filter { it.isNotBlank() && it != "null" && it != comp }
        return "settings put secure enabled_notification_listeners " + (list + comp).joinToString(":")
    }

    /** The same commands for a computer, for the README and the setup screen. */
    fun manualCommands(ctx: Context): List<String> = listOfNotNull(
        "adb shell appops set ${pkg(ctx)} GET_USAGE_STATS allow",
        "adb shell appops set ${pkg(ctx)} SYSTEM_ALERT_WINDOW allow",
        if (Build.VERSION.SDK_INT >= 26) "adb shell cmd notification allow_listener ${MediaListener.component(ctx).flattenToString()}"
        else "adb shell settings put secure enabled_notification_listeners ${MediaListener.component(ctx).flattenToString()}",
        if (Power.supported(ctx)) "adb shell dpm set-active-admin ${Power.admin(ctx).flattenToString()}" else null,
        "adb shell pm grant ${pkg(ctx)} android.permission.READ_EXTERNAL_STORAGE"
    )
}
