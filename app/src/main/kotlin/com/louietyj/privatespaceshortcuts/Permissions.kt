package com.louietyj.privatespaceshortcuts

import android.content.Context
import android.content.pm.PackageManager

/** The development permissions we need, granted once via adb or Shizuku. */
object Permissions {

    val REQUIRED = listOf(
        "android.permission.INTERACT_ACROSS_USERS",
        "android.permission.MODIFY_QUIET_MODE",
    )

    fun missing(context: Context): List<String> =
        REQUIRED.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

    fun adbCommands(context: Context): List<String> =
        REQUIRED.map { "adb shell pm grant ${context.packageName} $it" }
}
