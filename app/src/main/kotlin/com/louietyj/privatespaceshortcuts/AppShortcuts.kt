package com.louietyj.privatespaceshortcuts

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.ShortcutInfo
import android.graphics.BitmapFactory
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.UserHandle
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/** A shortcut published by a private space app, as shown when long-pressing its launcher icon. */
data class AppShortcut(
    val packageName: String,
    val id: String,
    val shortLabel: String,
    val longLabel: String,
    /** Captured so most shortcuts can later be started without Shizuku. */
    val intent: Intent,
    val icon: Drawable?,
)

/**
 * Other apps' shortcuts are only visible to the default launcher, but the shell user holds
 * ACCESS_SHORTCUTS and GET_INTENT_SENDER_INTENT, so we call LauncherApps and ActivityManager as
 * shell through Shizuku.
 */
object AppShortcuts {

    private const val SHELL = "com.android.shell"
    private const val QUERY_FLAGS = ShortcutQuery.FLAG_MATCH_MANIFEST or ShortcutQuery.FLAG_MATCH_DYNAMIC or
        ShortcutQuery.FLAG_MATCH_PINNED or ShortcutQuery.FLAG_MATCH_CACHED

    private val launcherApps by lazy {
        HiddenApiBypass.addHiddenApiExemptions(
            "Landroid/content/pm/ILauncherApps",
            "Landroid/content/pm/ShortcutQueryWrapper",
            "Landroid/app/IActivityManager",
            "Landroid/app/PendingIntent;->getTarget",
            "Landroid/content/pm/ShortcutInfo;->hasAdaptiveBitmap",
        )
        shellService("launcherapps", "android.content.pm.ILauncherApps")
    }
    private val activityManager by lazy { shellService("activity", "android.app.IActivityManager") }

    /** Requires Shizuku. Ordered like the launcher's long-press menu. */
    fun list(context: Context, user: UserHandle, packageName: String): List<AppShortcut> =
        query(user, packageName)
            .filter { it.isEnabled }
            .sortedWith(compareBy({ !it.isDeclaredInManifest }, { it.rank }))
            .mapNotNull { info ->
                val intent = intentOf(info, user) ?: return@mapNotNull null
                AppShortcut(
                    packageName = info.`package`,
                    id = info.id,
                    shortLabel = info.shortLabel.toString(),
                    longLabel = (info.longLabel ?: info.shortLabel).toString(),
                    intent = intent,
                    icon = loadIcon(context, user, info),
                )
            }

    /** Pins the shortcut as shell so [start] still works after the app drops it. Requires Shizuku. */
    fun keepAlive(user: UserHandle, packageName: String, id: String) {
        val pinned = query(user, packageName, ShortcutQuery.FLAG_MATCH_PINNED).map { it.id }
        launcherApps.call("pinShortcuts", SHELL, packageName, (pinned + id).distinct(), user)
    }

    /**
     * Starts the shortcut as its publisher, for targets it doesn't export. Requires Shizuku, and a
     * visible caller: Shizuku's process isn't allowed to start activities from the background.
     */
    fun start(activity: Context, user: UserHandle, packageName: String, id: String) {
        val pi = pendingIntent(user, packageName, id) ?: throw IllegalStateException("Shortcut no longer exists")
        val options = ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS)
        pi.send(activity, 0, null, null, null, null, options.toBundle())
    }

    private fun query(user: UserHandle, packageName: String, flags: Int = QUERY_FLAGS): List<ShortcutInfo> {
        val query = ShortcutQuery().setPackage(packageName).setQueryFlags(flags)
        val wrapper = Class.forName("android.content.pm.ShortcutQueryWrapper")
            .getConstructor(ShortcutQuery::class.java).newInstance(query)
        val slice = launcherApps.call("getShortcuts", SHELL, wrapper, user)!!
        @Suppress("UNCHECKED_CAST")
        return slice.call("getList") as List<ShortcutInfo>
    }

    private fun pendingIntent(user: UserHandle, packageName: String, id: String): PendingIntent? =
        launcherApps.call("getShortcutIntent", SHELL, packageName, id, null as Bundle?, user) as PendingIntent?

    /** LauncherApps strips intents from ShortcutInfo, but its PendingIntent still holds them. */
    private fun intentOf(info: ShortcutInfo, user: UserHandle): Intent? {
        val pi = pendingIntent(user, info.`package`, info.id) ?: return null
        val intent = activityManager.call("getIntentForIntentSender", pi.call("getTarget")) as Intent? ?: return null
        // Started as a shortcut, an implicit intent resolves within the publisher; keep that.
        if (intent.component == null && intent.`package` == null) intent.setPackage(info.`package`)
        return intent
    }

    private fun loadIcon(context: Context, user: UserHandle, info: ShortcutInfo): Drawable? {
        val pm = PrivateSpace.userContext(context, user).packageManager
        val userId = user.hashCode()  // getIdentifier() is hidden; hashCode() returns the same value
        val resId = launcherApps.call("getShortcutIconResId", SHELL, info.`package`, info.id, userId) as Int
        val icon = if (resId != 0) {
            pm.getDrawable(info.`package`, resId, pm.getApplicationInfo(info.`package`, 0))
        } else {
            (launcherApps.call("getShortcutIconFd", SHELL, info.`package`, info.id, userId) as ParcelFileDescriptor?)
                ?.use { BitmapFactory.decodeFileDescriptor(it.fileDescriptor) }
                ?.let { bitmap ->
                    val drawable = BitmapDrawable(context.resources, bitmap)
                    if (info.hasAdaptiveBitmap()) AdaptiveIconDrawable(null, drawable) else drawable
                }
        } ?: return null
        return pm.getUserBadgedIcon(icon, user)
    }

    private fun ShortcutInfo.hasAdaptiveBitmap(): Boolean = call("hasAdaptiveBitmap") as Boolean

    private fun shellService(name: String, iface: String): Any =
        Class.forName("$iface\$Stub").getMethod("asInterface", IBinder::class.java)
            .invoke(null, ShizukuBinderWrapper(SystemServiceHelper.getSystemService(name)))!!

    /** Invokes a hidden method, matched by name and argument count. */
    private fun Any.call(name: String, vararg args: Any?): Any? =
        javaClass.methods.single { it.name == name && it.parameterCount == args.size }.invoke(this, *args)
}
