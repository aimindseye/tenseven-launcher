package io.github.aimindseye.tenseven.platform

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import io.github.aimindseye.tenseven.model.WallpaperSet
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Where a wallpaper goes. */
enum class WallpaperTarget { HOME, LOCK, BOTH }

/**
 * Draws Tenseven's own wallpapers and sets them. Nothing is downloaded and no image ships in the app: each wallpaper is
 * painted from the few colours of its [WallpaperSet], so there is no third-party or BlackBerry artwork in it.
 */
private const val ART_RADIUS_BASE = 0.22f
private const val ART_RADIUS_VARIATION = 0.1f
private const val ART_CENTER_X_BASE = 0.3f
private const val ART_CENTER_X_VARIATION = 0.4f
private const val ART_CENTER_Y_BASE = 0.22f
private const val ART_CENTER_Y_VARIATION = 0.12f
private const val ART_HALO_SCALE = 2.2f
private const val ART_OUTER_ALPHA = 230
private const val ART_INNER_ALPHA = 235
private const val ART_CORE_SCALE = 0.55f
private const val ART_RIDGE_LAYERS = 3
private const val ART_RIDGE_BASE = 0.52f
private const val ART_RIDGE_STEP = 0.14f
private const val ART_WAVE_AMPLITUDE = 0.05f
private const val ART_WAVE_DECAY = 0.008f
private const val ART_WAVE_CYCLES = 1.2f
private const val ART_CYCLE_VARIATION = 1.4f
private const val ART_CYCLE_STEP = 0.5f
private const val ART_RIDGE_SAMPLE_STEP = 4f
private const val ART_RIDGE_ALPHA_BASE = 90
private const val ART_RIDGE_ALPHA_STEP = 55

object WallpaperArt {
    fun render(
        set: WallpaperSet,
        width: Int,
        height: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = width.toFloat()
        val h = height.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, h, set.top, set.bottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)

        val random = Random(set.seed)
        val radius = w * (ART_RADIUS_BASE + random.nextFloat() * ART_RADIUS_VARIATION)
        val cx = w * (ART_CENTER_X_BASE + random.nextFloat() * ART_CENTER_X_VARIATION)
        val cy = h * (ART_CENTER_Y_BASE + random.nextFloat() * ART_CENTER_Y_VARIATION)
        paint.shader =
            RadialGradient(
                cx,
                cy,
                radius * ART_HALO_SCALE,
                intArrayOf(
                    ColorUtils.setAlphaComponent(set.glow, ART_OUTER_ALPHA),
                    ColorUtils
                        .setAlphaComponent(set.glow, 0),
                ),
                null,
                Shader.TileMode.CLAMP,
            )
        canvas.drawCircle(cx, cy, radius * ART_HALO_SCALE, paint)
        paint.shader = null
        paint.color = ColorUtils.setAlphaComponent(set.glow, ART_INNER_ALPHA)
        canvas.drawCircle(cx, cy, radius * ART_CORE_SCALE, paint)

        // Ridges: three layers of slow waves, each closer and darker than the one behind it.
        val layers = ART_RIDGE_LAYERS
        for (layer in 0 until layers) {
            val base = h * (ART_RIDGE_BASE + layer * ART_RIDGE_STEP)
            val amplitude = h * (ART_WAVE_AMPLITUDE - layer * ART_WAVE_DECAY)
            val cycles =
                ART_WAVE_CYCLES +
                    random.nextFloat() * ART_CYCLE_VARIATION +
                    layer * ART_CYCLE_STEP
            val phase = random.nextFloat() * (2 * PI).toFloat()
            val path = Path().apply { moveTo(0f, h) }
            var x = 0f
            while (x <= w + ART_RIDGE_SAMPLE_STEP) {
                val y = base + amplitude * sin(cycles * 2 * PI * (x / w) + phase).toFloat()
                path.lineTo(x, y)
                x += ART_RIDGE_SAMPLE_STEP
            }
            path.lineTo(w, h)
            path.close()
            paint.color =
                ColorUtils.setAlphaComponent(
                    set.ridge,
                    ART_RIDGE_ALPHA_BASE + layer *
                        ART_RIDGE_ALPHA_STEP,
                )
            canvas.drawPath(path, paint)
        }
        return bitmap
    }

    /** Sets the wallpaper on the home screen, the lock screen or both. Run off the main thread; false if Android refuses. */
    fun apply(
        context: Context,
        set: WallpaperSet,
        target: WallpaperTarget,
    ): Boolean =
        runCatching {
            val manager = WallpaperManager.getInstance(context)
            val width = manager.desiredMinimumWidth.coerceAtLeast(context.resources.displayMetrics.widthPixels)
            val height = manager.desiredMinimumHeight.coerceAtLeast(context.resources.displayMetrics.heightPixels)
            val bitmap = render(set, width, height)
            val flags =
                when (target) {
                    WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
                    WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
                    WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                }
            manager.setBitmap(bitmap, null, true, flags)
            bitmap.recycle()
            true
        }.getOrDefault(false)
}
