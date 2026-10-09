package com.louietyj.privatespaceshortcuts

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import androidx.core.graphics.drawable.toBitmap

object Shortcuts {

    fun requestPin(context: Context, component: ComponentName, label: String, icon: Drawable): Boolean =
        requestPin(context, component.flattenToShortString(), label, icon, LaunchActivity.intent(context, component))

    fun requestPin(context: Context, shortcut: AppShortcut, fallbackIcon: Drawable): Boolean =
        requestPin(
            context, "shortcut:${shortcut.packageName}/${shortcut.id}", shortcut.shortLabel,
            shortcut.icon ?: fallbackIcon, LaunchActivity.intent(context, shortcut),
        )

    private fun requestPin(context: Context, id: String, label: String, icon: Drawable, intent: Intent): Boolean {
        val shortcut = ShortcutInfo.Builder(context, id)
            .setShortLabel(label)
            .setIcon(toIcon(icon))
            .setIntent(intent)
            .build()
        return context.getSystemService(ShortcutManager::class.java).requestPinShortcut(shortcut, null)
    }

    /** Keeps adaptive icons adaptive so the launcher can apply its own icon shape. */
    private fun toIcon(drawable: Drawable): Icon {
        if (drawable !is AdaptiveIconDrawable) return Icon.createWithBitmap(drawable.toBitmap())
        val size = drawable.intrinsicWidth.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        listOfNotNull(drawable.background, drawable.foreground).forEach {
            it.setBounds(0, 0, size, size)
            it.draw(canvas)
        }
        return Icon.createWithAdaptiveBitmap(bitmap)
    }
}
