package org.sableos.start.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.addSvg
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.sableos.start.model.AppGlyph
import org.sableos.start.model.TablerGlyphPaths

/**
 * Start's one icon style (Q4 device review, Q7): a tile-free glyph, lit like
 * the BlackBerry OS 7 / Ripe glyphs: white at the top shading to a silver
 * tinted with the app's accent, over a soft drop shadow, straight on the BB10
 * background. No coloured tile, no border, no rail. On a light surface (All
 * Apps or Search in the light appearance) the same glyph is graphite shading
 * to the accent, so it keeps its contrast.
 */
@Composable
internal fun SableGlyphIcon(
    glyph: AppGlyph,
    modifier: Modifier = Modifier,
) {
    val glyphPaths =
        remember(glyph) {
            sableGlyphPaths(glyph).map { pathData -> Path().apply { addSvg(pathData) } }
        }
    val accent = Color(glyph.accent)
    val onLight = MaterialTheme.colorScheme.surface.luminance() > LIGHT_SURFACE_LUMINANCE
    Canvas(modifier = modifier) {
        val scale = size.minDimension / GLYPH_VIEWPORT
        val dx = (size.width - size.minDimension) / 2f
        val dy = (size.height - size.minDimension) / 2f
        val stroke = Stroke(width = TablerGlyphPaths.STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
        translate(left = dx, top = dy) {
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero) {
                // The shadow, then the lit glyph; the gradient spans the 24-unit glyph box.
                translate(top = GLYPH_VIEWPORT * SHADOW_DROP) {
                    glyphPaths.forEach { path ->
                        drawPath(path = path, color = if (onLight) GlyphShadowOnLight else GlyphShadow, style = stroke)
                    }
                }
                val brush = sableGlyphBrush(accent, 0f, GLYPH_VIEWPORT, onLight)
                glyphPaths.forEach { path -> drawPath(path = path, brush = brush, style = stroke) }
            }
        }
    }
}

/**
 * An app's own monochrome (themed icon) layer, drawn in the glyph style: the
 * layer is used as a mask for the same shading and shadow as [SableGlyphIcon].
 */
@Composable
internal fun SableMonochromeIcon(
    mask: ImageBitmap,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val onLight = MaterialTheme.colorScheme.surface.luminance() > LIGHT_SURFACE_LUMINANCE
    Canvas(modifier = modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val box = IntSize(size.width.toInt(), size.height.toInt())
        // Shade only the glyph (the gradient is kept where the mask is opaque), then put the shadow behind it.
        drawImage(image = mask, dstSize = box)
        drawRect(
            brush = sableGlyphBrush(accent, 0f, size.height, onLight),
            blendMode = BlendMode.SrcAtop,
        )
        drawImage(
            image = mask,
            dstOffset = IntOffset(0, (size.minDimension * SHADOW_DROP).toInt()),
            dstSize = box,
            colorFilter = ColorFilter.tint(if (onLight) GlyphShadowOnLight else GlyphShadow),
            blendMode = BlendMode.DstOver,
        )
    }
}

/**
 * White at the top to an accent-tinted silver at the bottom, between [top]
 * and [bottom]; [onLight] gives graphite to an accent-tinted slate instead.
 */
internal fun sableGlyphBrush(
    accent: Color,
    top: Float,
    bottom: Float,
    onLight: Boolean = false,
): Brush =
    if (onLight) {
        Brush.verticalGradient(
            0f to GlyphGraphite,
            0.45f to GlyphGraphite,
            1f to lerp(GlyphGraphite, accent, ACCENT_TINT),
            startY = top,
            endY = bottom,
        )
    } else {
        Brush.verticalGradient(
            0f to Color.White,
            0.45f to GlyphSilverLight,
            1f to lerp(GlyphSilver, accent, ACCENT_TINT),
            startY = top,
            endY = bottom,
        )
    }

private const val GLYPH_VIEWPORT = TablerGlyphPaths.VIEWPORT
private const val SHADOW_DROP = 0.035f
private const val ACCENT_TINT = 0.6f
private const val LIGHT_SURFACE_LUMINANCE = 0.5f
private val GlyphShadow = Color(0x99000000)
private val GlyphShadowOnLight = Color(0x26000000)
private val GlyphGraphite = Color(0xFF2B3138)
private val GlyphSilverLight = Color(0xFFE6E9EE)
private val GlyphSilver = Color(0xFFB4BAC4)

/** Tabler outline geometry ([AppGlyph.tabler]) at the version pinned in third_party/tabler-icons: one path string per SVG element. */
internal fun sableGlyphPaths(glyph: AppGlyph): List<String> =
    checkNotNull(TablerGlyphPaths.BY_NAME[glyph.tabler]) { "No Tabler geometry for ${glyph.tabler}; run tools/tabler_icons.py" }
