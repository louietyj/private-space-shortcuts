package com.louietyj.privatespaceshortcuts

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku

private const val TAG = "Permissions"
private const val SHIZUKU_REQUEST_CODE = 42

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

    /** True if Shizuku is running and has granted us access. */
    fun isShizukuReady(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: IllegalStateException) {
        false  // Shizuku not running, or binder not received yet
    }

    fun isShizukuRunning(): Boolean = Shizuku.pingBinder()

    fun requestShizukuPermission() {
        try {
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "requestPermission failed: ${e.message}")
        }
    }

    /** Grants every missing permission by running `pm grant` in Shizuku's shell process. */
    fun grantViaShizuku(context: Context): Boolean =
        missing(context).all { runShizukuCommand("pm", "grant", context.packageName, it) }

    /** Shizuku 13.x made newProcess package-private, but it still works reflectively. */
    private fun runShizukuCommand(vararg cmd: String): Boolean = try {
        val stringArrayClass = emptyArray<String>().javaClass
        val newProcess = Shizuku::class.java
            .getDeclaredMethod("newProcess", stringArrayClass, stringArrayClass, String::class.java)
            .also { it.isAccessible = true }
        val process = newProcess.invoke(null, cmd, null, null) as Process
        val exit = process.waitFor()
        Log.d(TAG, "Shizuku cmd=${cmd.toList()} exit=$exit")
        exit == 0
    } catch (e: Exception) {
        Log.e(TAG, "runShizukuCommand ${cmd.toList()} failed", e)
        false
    }
}
