package io.github.abhishekkaisar.glassos

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.service.notification.NotificationListenerService

/** Device admin with force-lock only: lets Glass put the TV to sleep (`DevicePolicyManager.lockNow`). */
class AdminReceiver : DeviceAdminReceiver()

/**
 * Notification access, used for two things only: reading the active media sessions (Now Playing) and switching
 * Do Not Disturb. Glass reads no notifications. Enabled in setup or over ADB (see README.md).
 */
class MediaListener : NotificationListenerService() {
    override fun onListenerConnected() { instance = this }
    override fun onListenerDisconnected() { if (instance === this) instance = null }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }

    companion object {
        @Volatile var instance: MediaListener? = null
        fun component(ctx: Context) = ComponentName(ctx, MediaListener::class.java)
    }
}

/** Fires the sleep timer. */
class SleepReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WLog.init(context)
        WLog.i("sleep timer fired")
        SleepTimer.clear(context)
        Power.sleep(context)
    }
}

/** What Control Center does to the TV itself. */
object Power {
    fun admin(ctx: Context) = ComponentName(ctx, AdminReceiver::class.java)

    fun removeAdmin(ctx: Context) {
        try { (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).removeActiveAdmin(admin(ctx)) } catch (e: Throwable) { }
    }

    /**
     * False on TVs with no device admin at all (the Nvidia Shield declares no android.software.device_admin): there
     * `dpm set-active-admin` prints Success and changes nothing, so the grant and Sleep are not offered.
     */
    fun supported(ctx: Context): Boolean = try {
        ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN)
    } catch (e: Throwable) { true }

    fun adminActive(ctx: Context): Boolean = try {
        (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).isAdminActive(admin(ctx))
    } catch (e: Throwable) { false }

    /** Puts the TV to sleep with the device admin; without it, opens Amazon's quick settings on Fire TV, which has Sleep. */
    fun sleep(ctx: Context): Boolean {
        if (adminActive(ctx)) try {
            (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).lockNow()
            WLog.i("lockNow")
            return true
        } catch (e: Throwable) { WLog.i("lockNow failed: $e") }
        if (Platform.isFireTv(ctx)) try {
            ctx.startActivity(Intent(FireIntents.HUD).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Throwable) { }
        else android.widget.Toast.makeText(ctx, "Sleep needs the device admin permission (Settings > Permissions)", android.widget.Toast.LENGTH_LONG).show()
        return false
    }
}

/** Glass's own sleep timer: an exact alarm that calls [Power.sleep]. The end time is kept in prefs. */
object SleepTimer {
    private fun pi(ctx: Context) = PendingIntent.getBroadcast(ctx, 41, Intent(ctx, SleepReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT)
    private fun sp(ctx: Context) = ctx.getSharedPreferences("glass", Context.MODE_PRIVATE)

    /** Wall clock end time, or 0 when no timer runs. */
    fun endsAt(ctx: Context): Long {
        val t = sp(ctx).getLong("sleepAt", 0L)
        return if (t > System.currentTimeMillis()) t else 0L
    }

    fun start(ctx: Context, ms: Long) {
        val end = System.currentTimeMillis() + ms
        sp(ctx).edit().putLong("sleepAt", end).apply()
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, pi(ctx))
        else am.setExact(AlarmManager.RTC_WAKEUP, end, pi(ctx))
        WLog.i("sleep timer set for ${ms / 1000} s")
    }

    fun cancel(ctx: Context) {
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi(ctx))
        clear(ctx)
    }

    fun clear(ctx: Context) { sp(ctx).edit().remove("sleepAt").apply() }

    /** "24:59" style countdown, or null when no timer runs. */
    fun remaining(ctx: Context): String? {
        val end = endsAt(ctx)
        if (end == 0L) return null
        val s = ((end - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }
}

/** Fire OS 7 screens Control Center opens (all checked with `cmd package resolve-activity` on the TV). */
object FireIntents {
    const val SETTINGS_PKG = "com.amazon.tv.settings.v2"
    const val HUD = "com.amazon.device.settings.action.SHOW_HUD"

    /** The stand-alone Settings grid. android.settings.SETTINGS instead opens the Settings tab inside Amazon's home. */
    fun settings() = Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.LEANBACK_SETTINGS")
        .setClassName(HomeWatcher.AMAZON, "com.amazon.tv.launcher.ui.MainSettingsActivity")

    fun wifi() = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
    fun audio() = Intent("com.amazon.device.settings.action.AUDIO")
    fun controllers() = Intent("android.settings.CONTROLLERS_SETTINGS")
    fun accessibility() = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
    fun parental() = Intent("com.amazon.settings.PARENTAL_SETTINGS")
    fun profiles() = Intent("com.amazon.tv.action.LAUNCH_PROFILE_PICKER").setPackage("com.amazon.ftv.profilepicker")
}

/** Network state for the Wi-Fi button and the VPN tab. */
object Net {
    private fun cm(ctx: Context) = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private fun anyTransport(ctx: Context, t: Int): Boolean = try {
        cm(ctx).allNetworks.any { cm(ctx).getNetworkCapabilities(it)?.hasTransport(t) == true }
    } catch (e: Throwable) { false }

    fun vpnActive(ctx: Context) = anyTransport(ctx, NetworkCapabilities.TRANSPORT_VPN)

    /** The Wi-Fi network's name, "Ethernet", or "Not connected". */
    fun networkName(ctx: Context): String {
        try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ssid = wm.connectionInfo?.ssid?.trim('"')
            if (wm.isWifiEnabled && !ssid.isNullOrBlank() && ssid != "<unknown ssid>" && ssid != "0x") return ssid
        } catch (e: Throwable) { }
        if (anyTransport(ctx, NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet"
        return if (anyTransport(ctx, NetworkCapabilities.TRANSPORT_WIFI)) "Wi-Fi" else "Not connected"
    }
}

/** Do Not Disturb through notification access (or notification policy access, if that was granted instead). */
object Dnd {
    private fun nm(ctx: Context) = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun isOn(ctx: Context): Boolean = Build.VERSION.SDK_INT >= 23 &&
        nm(ctx).currentInterruptionFilter.let { it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN }

    fun canSet(ctx: Context): Boolean = MediaListener.instance != null ||
        (Build.VERSION.SDK_INT >= 23 && nm(ctx).isNotificationPolicyAccessGranted)

    /** Turns DND on (priority only) or off. Returns false when Glass may not change it. */
    fun set(ctx: Context, on: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < 23) return false
        val l = MediaListener.instance
        if (l != null) {
            l.requestInterruptionFilter(if (on) NotificationListenerService.INTERRUPTION_FILTER_PRIORITY else NotificationListenerService.INTERRUPTION_FILTER_ALL)
            return true
        }
        if (nm(ctx).isNotificationPolicyAccessGranted) {
            nm(ctx).setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
            return true
        }
        return false
    }
}

/** The media session Control Center shows: playing, buffering or paused with a title. */
class NowPlaying(val controller: MediaController, val title: String, val subtitle: String, val art: Bitmap?, val playing: Boolean) {
    val pkg: String get() = controller.packageName

    companion object {
        fun sessions(ctx: Context): List<MediaController> = try {
            (ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager).getActiveSessions(MediaListener.component(ctx))
        } catch (e: Throwable) { emptyList() }

        fun current(ctx: Context): NowPlaying? {
            for (c in sessions(ctx)) {
                val st = c.playbackState?.state ?: continue
                val md = c.metadata ?: continue
                val title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: continue
                val playing = st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING
                if (!playing && st != PlaybackState.STATE_PAUSED) continue
                val sub = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                    ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
                val art = md.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                return NowPlaying(c, title, sub, art, playing)
            }
            return null
        }
    }
}
