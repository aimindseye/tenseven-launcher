package io.github.aimindseye.tenseven.platform

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.github.aimindseye.tenseven.LockLayerActivity

/** Starts the lock layer when the screen wakes onto a locked phone, if the user turned it on and allowed it. */
object LockLayerLauncher {
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Called on screen on. Does nothing unless the setting is on, the overlay permission is granted and the keyguard is up. */
    fun showIfLocked(context: Context) {
        if (!StartStateRepository(context).loadLockLayer() || !canDrawOverlays(context)) return
        val keyguard = context.getSystemService(KeyguardManager::class.java) ?: return
        if (!keyguard.isKeyguardLocked) return
        runCatching {
            context.startActivity(
                Intent(context, LockLayerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            )
        }
    }
}
