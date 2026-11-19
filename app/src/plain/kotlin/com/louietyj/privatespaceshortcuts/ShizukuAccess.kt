package com.louietyj.privatespaceshortcuts

import android.content.Context
import android.os.UserHandle

/** The plain flavor ships without Shizuku: grant via adb, and no app shortcuts. */
object ShizukuAccess {

    const val SUPPORTED = false

    fun isReady(): Boolean = false

    fun isRunning(): Boolean = false

    fun requestPermission() = Unit

    fun observe(onPermissionGranted: () -> Unit, onBinderReceived: () -> Unit): () -> Unit = {}

    fun grant(context: Context, permissions: List<String>): Boolean = false

    fun listShortcuts(context: Context, user: UserHandle, packageName: String): List<AppShortcut> = emptyList()

    fun keepShortcutAlive(user: UserHandle, packageName: String, id: String) = Unit

    fun startShortcut(activity: Context, user: UserHandle, packageName: String, id: String): Unit =
        throw UnsupportedOperationException("this shortcut needs the Shizuku build")
}
