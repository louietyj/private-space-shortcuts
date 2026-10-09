package com.louietyj.privatespaceshortcuts

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import java.lang.reflect.InvocationTargetException

/**
 * LauncherApps and CrossProfileApps hide private space from everything but the default launcher,
 * so we use the hidden multi-user APIs, which only need INTERACT_ACROSS_USERS.
 */
object PrivateSpace {

    fun profile(context: Context): UserHandle? {
        val um = context.getSystemService(UserManager::class.java)
        return um.userProfiles.firstOrNull { it != Process.myUserHandle() && isPrivateProfile(context, it) }
    }

    private fun isPrivateProfile(context: Context, user: UserHandle): Boolean {
        val um = userContext(context, user).getSystemService(UserManager::class.java)
        return UserManager::class.java.getMethod("isPrivateProfile").invoke(um) as Boolean
    }

    fun isReady(context: Context, user: UserHandle): Boolean {
        val um = context.getSystemService(UserManager::class.java)
        return !um.isQuietModeEnabled(user) && um.isUserUnlocked(user)
    }

    /** False means the system is showing its credential prompt and unlocks asynchronously. */
    fun requestUnlock(context: Context, user: UserHandle): Boolean =
        context.getSystemService(UserManager::class.java).requestQuietModeEnabled(false, user)

    /** Launcher activities installed in [user]. Empty while private space is locked. */
    fun launcherActivities(context: Context, user: UserHandle): List<ResolveInfo> {
        val pm = userContext(context, user).packageManager
        return pm.queryIntentActivities(launcherIntent(), 0)
            .filter { it.activityInfo.packageName != "com.android.privatespace" }
    }

    fun loadLabel(context: Context, user: UserHandle, info: ResolveInfo): String =
        info.loadLabel(userContext(context, user).packageManager).toString()

    fun loadIcon(context: Context, user: UserHandle, info: ResolveInfo) =
        info.loadIcon(userContext(context, user).packageManager)

    fun launch(context: Context, user: UserHandle, component: ComponentName) =
        startActivity(context, user, launcherIntent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))

    /** Throws SecurityException if the target activity isn't exported. */
    fun startActivity(context: Context, user: UserHandle, intent: Intent) {
        try {
            Context::class.java
                .getMethod("startActivityAsUser", Intent::class.java, UserHandle::class.java)
                .invoke(context, Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), user)
        } catch (e: InvocationTargetException) {
            throw e.cause ?: e
        }
    }

    fun userContext(context: Context, user: UserHandle): Context =
        Context::class.java
            .getMethod("createContextAsUser", UserHandle::class.java, Int::class.javaPrimitiveType)
            .invoke(context, user, 0) as Context

    private fun launcherIntent() =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
}
