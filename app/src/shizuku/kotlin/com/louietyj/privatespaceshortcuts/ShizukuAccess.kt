package com.louietyj.privatespaceshortcuts

import android.content.Context
import android.content.pm.PackageManager
import android.os.UserHandle
import android.util.Log
import rikka.shizuku.Shizuku

private const val TAG = "ShizukuAccess"
private const val SHIZUKU_REQUEST_CODE = 42

/** Everything that needs Shizuku. The plain flavor has a stub with the same API. */
object ShizukuAccess {

    const val SUPPORTED = true

    /** True if Shizuku is running and has granted us access. */
    fun isReady(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: IllegalStateException) {
        false  // Shizuku not running, or binder not received yet
    }

    fun isRunning(): Boolean = Shizuku.pingBinder()

    fun requestPermission() {
        try {
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "requestPermission failed: ${e.message}")
        }
    }

    /** Calls back when Shizuku grants us access or connects. Returns a function that stops observing. */
    fun observe(onPermissionGranted: () -> Unit, onBinderReceived: () -> Unit): () -> Unit {
        val permissionListener = Shizuku.OnRequestPermissionResultListener { _, result ->
            if (result == PackageManager.PERMISSION_GRANTED) onPermissionGranted()
        }
        val binderListener = Shizuku.OnBinderReceivedListener { onBinderReceived() }
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        return {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
            Shizuku.removeBinderReceivedListener(binderListener)
        }
    }

    /** Grants [permissions] by running `pm grant` in Shizuku's shell process. */
    fun grant(context: Context, permissions: List<String>): Boolean =
        permissions.all { runCommand("pm", "grant", context.packageName, it) }

    fun listShortcuts(context: Context, user: UserHandle, packageName: String): List<AppShortcut> =
        AppShortcuts.list(context, user, packageName)

    fun keepShortcutAlive(user: UserHandle, packageName: String, id: String) =
        AppShortcuts.keepAlive(user, packageName, id)

    fun startShortcut(activity: Context, user: UserHandle, packageName: String, id: String) =
        AppShortcuts.start(activity, user, packageName, id)

    /** Shizuku 13.x made newProcess package-private, but it still works reflectively. */
    private fun runCommand(vararg cmd: String): Boolean = try {
        val stringArrayClass = emptyArray<String>().javaClass
        val newProcess = Shizuku::class.java
            .getDeclaredMethod("newProcess", stringArrayClass, stringArrayClass, String::class.java)
            .also { it.isAccessible = true }
        val process = newProcess.invoke(null, cmd, null, null) as Process
        val exit = process.waitFor()
        Log.d(TAG, "Shizuku cmd=${cmd.toList()} exit=$exit")
        exit == 0
    } catch (e: Exception) {
        Log.e(TAG, "runCommand ${cmd.toList()} failed", e)
        false
    }
}
