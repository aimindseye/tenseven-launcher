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
        val radius = w * (0.22f + random.nextFloat() * 0.1f)
        val cx = w * (0.3f + random.nextFloat() * 0.4f)
        val cy = h * (0.22f + random.nextFloat() * 0.12f)
        paint.shader =
            RadialGradient(
                cx,
                cy,
                radius * 2.2f,
                intArrayOf(ColorUtils.setAlphaComponent(set.glow, 230), ColorUtils.setAlphaComponent(set.glow, 0)),
                null,
                Shader.TileMode.CLAMP,
            )
        canvas.drawCircle(cx, cy, radius * 2.2f, paint)
        paint.shader = null
        paint.color = ColorUtils.setAlphaComponent(set.glow, 235)
        canvas.drawCircle(cx, cy, radius * 0.55f, paint)

        // Ridges: three layers of slow waves, each closer and darker than the one behind it.
        val layers = 3
        for (layer in 0 until layers) {
            val base = h * (0.52f + layer * 0.14f)
            val amplitude = h * (0.05f - layer * 0.008f)
            val cycles = 1.2f + random.nextFloat() * 1.4f + layer * 0.5f
            val phase = random.nextFloat() * (2 * PI).toFloat()
            val path = Path().apply { moveTo(0f, h) }
            var x = 0f
            while (x <= w + 4f) {
                val y = base + amplitude * sin(cycles * 2 * PI * (x / w) + phase).toFloat()
                path.lineTo(x, y)
                x += 4f
            }
            path.lineTo(w, h)
            path.close()
            paint.color = ColorUtils.setAlphaComponent(set.ridge, 90 + layer * 55)
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
