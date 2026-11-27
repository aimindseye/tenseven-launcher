package io.github.aimindseye.tenseven.platform

import android.content.Context
import android.provider.Settings

/**
 * The system choice to show the media player on the lock screen (Settings.Secure
 * media_controls_lock_screen). It is on unless the owner turned it off; Sable Media's playing
 * notification is what the lock screen shows. Writing needs WRITE_SECURE_SETTINGS, which Start holds.
 */
internal object LockScreenMedia {
    private const val KEY = "media_controls_lock_screen"

    /** Apps that are not system apps may not read this key (SecurityException); the system default is on. */
    fun isOn(context: Context): Boolean {
        val resolver = context.contentResolver
        return runCatching { Settings.Secure.getInt(resolver, KEY, 1) != 0 }.getOrDefault(true)
    }

    /** True when the choice was stored; false when the system refused it. */
    fun set(
        context: Context,
        on: Boolean,
    ): Boolean {
        val value = if (on) 1 else 0
        return runCatching { Settings.Secure.putInt(context.contentResolver, KEY, value) }.getOrDefault(false)
    }
}
