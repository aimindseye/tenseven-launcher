package io.github.aimindseye.tenseven.platform

import android.app.Activity
import android.graphics.Color
import android.util.Log
import android.view.View
import android.view.WindowInsets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import io.github.aimindseye.tenseven.model.D5ScreenFitPolicy

/**
 * Explicit safe-viewport boundary for TenSeven's View-based activities.
 *
 * Do NOT apply this blindly to Compose roots (which may already use safeDrawingPadding),
 * wallpaper-backed Home, lock-screen covers or deliberately immersive screens.
 * These need individual inset ownership checks to avoid double padding.
 *
 * Invariant: the root's content begins below system bars and display cutouts,
 * and fixed bottom controls sit above Android gesture/three-button navigation.
 * Root background still paints behind transparent bars for BB10 dark appearance.
 */
internal object TenSevenSafeViewport {
    fun darkSystemBars(activity: Activity) {
        activity.window.decorView.setBackgroundColor(Color.BLACK)
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    fun show(
        activity: Activity,
        root: View,
    ) {
        // Required for targetSdk 35+: edge-to-edge is enforced by Android.
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        darkSystemBars(activity)
        val left = root.paddingLeft
        val top = root.paddingTop
        val right = root.paddingRight
        val bottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                )
            val targetLeft = left + safe.left
            val targetTop = top + safe.top
            val targetRight = right + safe.right
            val targetBottom = bottom + safe.bottom
            if (view.paddingLeft != targetLeft || view.paddingTop != targetTop ||
                view.paddingRight != targetRight || view.paddingBottom != targetBottom
            ) {
                view.setPadding(targetLeft, targetTop, targetRight, targetBottom)
            }
            // Do not consume: child controls and IME handlers may also need insets.
            insets
        }
        activity.setContentView(root)
        Log.i("TenSevenD5R8", "${activity.javaClass.simpleName}: safe viewport enabled")
        ViewCompat.requestApplyInsets(root)
    }

    /**
     * Select Titan 2 / Q25 sizing from current window bounds *minus* system bars,
     * rather than DisplayMetrics height (which includes system bars on Android 15+).
     */
    fun fit(activity: Activity): D5ScreenFitPolicy.Fit {
        val metrics = activity.windowManager.currentWindowMetrics
        val safe =
            metrics.windowInsets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
        val density = activity.resources.displayMetrics.density
        val width = (metrics.bounds.width() - safe.left - safe.right).coerceAtLeast(1)
        val height = (metrics.bounds.height() - safe.top - safe.bottom).coerceAtLeast(1)
        return D5ScreenFitPolicy.forViewport(width / density, height / density)
    }
}
