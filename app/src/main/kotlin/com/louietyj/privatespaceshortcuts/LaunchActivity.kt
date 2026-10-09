package com.louietyj.privatespaceshortcuts

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.UserHandle
import android.util.Log
import android.widget.Toast

private sealed interface Target {
    data class App(val component: ComponentName) : Target
    data class Shortcut(val packageName: String, val id: String, val intent: Intent) : Target
}

/**
 * Invisible activity behind every pinned shortcut. Opens the app or app shortcut in private space,
 * asking the system to unlock private space first if needed.
 */
class LaunchActivity : Activity() {

    private lateinit var user: UserHandle
    private lateinit var target: Target
    private val handler = Handler(Looper.getMainLooper())

    /** Set once the system's credential prompt has covered us. */
    private var promptShown = false
    private var awaitingUnlock = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        target = parseTarget(intent) ?: return finish()
        if (Permissions.missing(this).isNotEmpty()) {
            return fail("Open ${getString(R.string.app_name)} to finish setup")
        }
        user = PrivateSpace.profile(this) ?: return fail("Private space not found")

        when {
            PrivateSpace.isReady(this, user) -> launch()
            PrivateSpace.requestUnlock(this, user) -> waitForUnlock()
            else -> awaitingUnlock = true
        }
    }

    override fun onPause() {
        super.onPause()
        if (awaitingUnlock) promptShown = true
    }

    override fun onResume() {
        super.onResume()
        // Back from the credential prompt, either authenticated or cancelled.
        if (awaitingUnlock && promptShown) {
            awaitingUnlock = false
            waitForUnlock()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /** Unlocking is asynchronous, so poll until the profile is running or give up. */
    private fun waitForUnlock() {
        val deadline = SystemClock.uptimeMillis() + UNLOCK_TIMEOUT_MS
        val poll = object : Runnable {
            override fun run() {
                when {
                    PrivateSpace.isReady(this@LaunchActivity, user) -> launch()
                    SystemClock.uptimeMillis() > deadline -> fail("Private space is locked")
                    else -> handler.postDelayed(this, POLL_INTERVAL_MS)
                }
            }
        }
        poll.run()
    }

    private fun launch() {
        try {
            when (val t = target) {
                is Target.App -> PrivateSpace.launch(this, user, t.component)
                is Target.Shortcut -> launchShortcut(t)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $target", e)
            Toast.makeText(this, "Couldn't open app: ${e.message}", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    /** Most shortcuts target exported activities we can start ourselves; the rest need Shizuku. */
    private fun launchShortcut(shortcut: Target.Shortcut) {
        try {
            PrivateSpace.startActivity(this, user, shortcut.intent)
        } catch (e: SecurityException) {
            if (!Permissions.isShizukuReady()) throw IllegalStateException("this shortcut needs Shizuku running", e)
            AppShortcuts.start(this, user, shortcut.packageName, shortcut.id)
        }
    }

    private fun fail(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        private const val TAG = "LaunchActivity"
        private const val EXTRA_COMPONENT = "component"
        private const val EXTRA_SHORTCUT_PACKAGE = "shortcut_package"
        private const val EXTRA_SHORTCUT_ID = "shortcut_id"
        private const val EXTRA_SHORTCUT_INTENT = "shortcut_intent"
        private const val UNLOCK_TIMEOUT_MS = 3000L
        private const val POLL_INTERVAL_MS = 100L

        fun intent(context: Context, component: ComponentName): Intent =
            Intent(context, LaunchActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(EXTRA_COMPONENT, component.flattenToString())

        fun intent(context: Context, shortcut: AppShortcut): Intent =
            Intent(context, LaunchActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(EXTRA_SHORTCUT_PACKAGE, shortcut.packageName)
                .putExtra(EXTRA_SHORTCUT_ID, shortcut.id)
                .putExtra(EXTRA_SHORTCUT_INTENT, shortcut.intent.toUri(Intent.URI_INTENT_SCHEME))

        private fun parseTarget(intent: Intent): Target? {
            intent.getStringExtra(EXTRA_COMPONENT)?.let { flat ->
                return ComponentName.unflattenFromString(flat)?.let(Target::App)
            }
            return Target.Shortcut(
                packageName = intent.getStringExtra(EXTRA_SHORTCUT_PACKAGE) ?: return null,
                id = intent.getStringExtra(EXTRA_SHORTCUT_ID) ?: return null,
                intent = Intent.parseUri(intent.getStringExtra(EXTRA_SHORTCUT_INTENT) ?: return null, Intent.URI_INTENT_SCHEME),
            )
        }
    }
}
