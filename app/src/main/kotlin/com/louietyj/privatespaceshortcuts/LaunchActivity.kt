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

/**
 * Invisible activity behind every pinned shortcut. Opens the app in private space, asking the
 * system to unlock private space first if needed.
 */
class LaunchActivity : Activity() {

    private lateinit var user: UserHandle
    private lateinit var component: ComponentName
    private val handler = Handler(Looper.getMainLooper())

    /** Set once the system's credential prompt has covered us. */
    private var promptShown = false
    private var awaitingUnlock = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        component = intent.getStringExtra(EXTRA_COMPONENT)?.let(ComponentName::unflattenFromString)
            ?: return finish()
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
            PrivateSpace.launch(this, user, component)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $component", e)
            Toast.makeText(this, "Couldn't open app: ${e.cause?.message ?: e.message}", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun fail(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        private const val TAG = "LaunchActivity"
        private const val EXTRA_COMPONENT = "component"
        private const val UNLOCK_TIMEOUT_MS = 3000L
        private const val POLL_INTERVAL_MS = 100L

        fun intent(context: Context, component: ComponentName): Intent =
            Intent(context, LaunchActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(EXTRA_COMPONENT, component.flattenToString())
    }
}
