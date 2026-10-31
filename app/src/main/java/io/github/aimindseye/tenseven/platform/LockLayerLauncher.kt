package io.github.aimindseye.tenseven.platform

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import io.github.aimindseye.tenseven.LockLayerActivity

/** Starts the lock layer when the screen wakes onto a locked phone, if the user turned it on and allowed it. */
object LockLayerLauncher {
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Called on screen on. Does nothing unless the setting is on, the overlay permission is granted and the keyguard is up. */
    fun showIfLocked(context: Context) {
        if (!StartStateRepository(context).loadLockLayer() || !canDrawOverlays(context)) return
        val keyguard = context.getSystemService(KeyguardManager::class.java) ?: return
        if (!keyguard.isKeyguardLocked) return
        // Android 14 and later only let an app start a screen from the background while it has a visible window, so a
        // one-pixel see-through overlay is shown for a moment around the start.
        val overlay = showAnchorWindow(context)
        runCatching {
            context.startActivity(
                Intent(context, LockLayerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            )
        }
        overlay?.let { (manager, view) ->
            Handler(Looper.getMainLooper()).postDelayed({ runCatching { manager.removeView(view) } }, ANCHOR_MS)
        }
    }

    private fun showAnchorWindow(context: Context): Pair<WindowManager, View>? =
        runCatching {
            val manager = context.getSystemService(WindowManager::class.java)
            val view = View(context)
            val params =
                WindowManager.LayoutParams(
                    1,
                    1,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT,
                )
            manager.addView(view, params)
            manager to view
        }.getOrNull()

    private const val ANCHOR_MS = 3000L
}
