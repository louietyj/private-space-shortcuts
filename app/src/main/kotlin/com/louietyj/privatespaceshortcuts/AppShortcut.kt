package com.louietyj.privatespaceshortcuts

import android.content.Intent
import android.graphics.drawable.Drawable

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
