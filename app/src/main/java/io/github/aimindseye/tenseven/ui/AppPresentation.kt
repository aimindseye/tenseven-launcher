package io.github.aimindseye.tenseven.ui

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.iconpacks.IconPacks
import io.github.aimindseye.tenseven.iconpacks.IconPresentationState
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.model.AppGlyphs
import io.github.aimindseye.tenseven.model.AppProfiles
import io.github.aimindseye.tenseven.model.ExtraApps
import io.github.aimindseye.tenseven.model.IconPresentationPolicy
import io.github.aimindseye.tenseven.model.StartText.t
import io.github.aimindseye.tenseven.model.stableKey
import io.github.aimindseye.tenseven.privacy.PrivacySummaryPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AppRow(
    app: AppEntry,
    onClick: () -> Unit,
    onContext: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier =
                    modifier
                        .weight(1f)
                        .heightIn(min = 58.dp)
                        .sableFocusRing()
                        .combinedClickable(
                            onClick = onClick,
                            onLongClick = onContext,
                        ).padding(vertical = 6.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppMark(app = app, size = 42)
                Spacer(Modifier.width(14.dp))
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = app.label,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        app.profileLabel?.let { profile ->
                            Spacer(Modifier.width(6.dp))
                            ProfileBadge(profile)
                        }
                    }
                    PrivacySummaryLine(app)
                }
            }

            // Actions are also reachable with Menu / Fn+Enter; never touch-only.
            TextButton(
                onClick = onContext,
                modifier =
                    Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = t("actions_for", app.label) },
            ) {
                Text(
                    text = "›",
                    fontSize = 26.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (expanded) {
            PrivacyDetail(
                app = app,
                modifier = Modifier.padding(start = 56.dp, bottom = 8.dp),
            )
        }
    }
}

/** WORK_PROFILE_BADGE=REQUIRED: visible text plus a spoken label, beside the app name. */
@Composable
internal fun ProfileBadge(profile: String) {
    Text(
        text = AppProfiles.badge(profile),
        modifier =
            Modifier
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp)
                .semantics { contentDescription = t("profile", AppProfiles.badge(profile)) },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

/**
 * The one-line privacy/security summary. Whole labels fold into "+N" until the
 * line fits the row width at the current font scale (no mid-word clipping);
 * special access shows a warning mark that always carries a spoken label.
 * Reads only the precomputed snapshot on [AppEntry]; no platform query here.
 */
@Composable
internal fun PrivacySummaryLine(app: AppEntry) {
    val style = MaterialTheme.typography.bodySmall
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxWidthPx = constraints.maxWidth
        val badgePx = with(density) { PrivacyBadgeWidth.roundToPx() }
        val row =
            remember(app.privacy, maxWidthPx, style, density.fontScale) {
                val probe = PrivacySummaryPolicy.compact(app.privacy)
                val available = if (probe.badgeLabels.isNotEmpty()) maxWidthPx - badgePx else maxWidthPx
                PrivacySummaryPolicy.compact(app.privacy) { text ->
                    measurer.measure(text, style, maxLines = 1).size.width <= available
                }
            }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (row.badgeLabels.isNotEmpty()) {
                Text(
                    text = "⚠",
                    modifier =
                        Modifier
                            .width(PrivacyBadgeWidth)
                            .semantics {
                                contentDescription = t("special_access") + row.badgeLabels.joinToString(", ")
                            },
                    style = style,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = row.text,
                modifier = Modifier.semantics { contentDescription = row.accessibleText(app.privacy) },
                style = style,
                color =
                    if (row.warning && row.badgeLabels.isEmpty()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Expanded privacy detail (Space in All Apps, and in Peek). */
@Composable
internal fun PrivacyDetail(
    app: AppEntry,
    modifier: Modifier = Modifier,
) {
    val lines = PrivacySummaryPolicy.detail(app.privacy)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (lines.isEmpty()) {
            Text(
                text = PrivacySummaryPolicy.compact(app.privacy).text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        lines.forEach { line ->
            Text(
                text = if (line.special) t("special_access_2", line.label) else line.label,
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (line.special) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        }
        Text(
            text = PrivacySummaryPolicy.DETAIL_FOOTER,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun CompactAppRow(
    app: AppEntry,
    onClick: () -> Unit,
    onContext: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
                    .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppMark(app = app, size = 36)
            Spacer(Modifier.width(12.dp))
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
        }
        TextButton(onClick = onContext) {
            Text("⋯")
        }
    }
}

/**
 * An app's icon in Start's one style (Q4 device review, Q7): the app's Sable
 * glyph when it has one ([AppGlyphs], by package only), else its own themed
 * monochrome layer shaded like the glyphs, else its own icon at the same size.
 * Never a tile behind it.
 */
@Composable
internal fun AppMark(
    app: AppEntry,
    size: Int,
) {
    val context = LocalContext.current
    IconPacks.init(context)
    IconPresentationState.init(context)
    val iconPack = IconPacks.selected
    val iconStyle = IconPresentationState.selected
    val isOwnExtra =
        app.component.packageName == context.packageName &&
            ExtraApps.byClassName(app.component.className) != null
    val glyph =
        if (IconPresentationPolicy.useGlyph(iconStyle, isOwnExtra, iconPack != null)) {
            AppGlyphs.forComponent(app.component.packageName, app.component.className)
        } else {
            null
        }
    if (glyph != null) {
        SableGlyphIcon(glyph = glyph, modifier = Modifier.size(size.dp))
        return
    }

    val themedMonochrome = IconPresentationPolicy.useMonochrome(iconStyle, iconPack != null)
    val densityDpi = context.resources.displayMetrics.densityDpi
    val iconKey = "${app.stableKey()}@$size@$densityDpi@${iconPack.orEmpty()}@${iconStyle.key}"
    // Loaded off the main thread and cached for the process: binding a row never
    // waits on LauncherApps (ROW_BIND_BLOCKING_QUERY=NO).
    val icon by
        produceState<LauncherIcon?>(initialValue = LauncherIconCache.get(iconKey), iconKey) {
            if (value == null) {
                value =
                    withContext(Dispatchers.IO) {
                        loadLauncherIcon(context = context, app = app, sizeDp = size, themedMonochrome = themedMonochrome)
                    }?.also { LauncherIconCache.put(iconKey, it) }
            }
        }

    val loadedIcon = icon
    when {
        loadedIcon == null -> {
            // Until the icon loads: the first letter in the glyph colours, no tile.
            Box(modifier = Modifier.size(size.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = appMark(app),
                    fontWeight = FontWeight.Bold,
                    fontSize = (size * 0.5f).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        loadedIcon.monochrome -> {
            SableMonochromeIcon(
                mask = loadedIcon.bitmap.asImageBitmap(),
                accent = appColor(app),
                modifier = Modifier.size(size.dp),
            )
        }

        else -> {
            Image(
                bitmap = loadedIcon.bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(size.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/** A loaded launcher icon: the app's themed monochrome layer (a mask) or its full icon. */
internal class LauncherIcon(
    val bitmap: Bitmap,
    val monochrome: Boolean,
)

private object LauncherIconCache : LruCache<String, LauncherIcon>(LAUNCHER_ICON_CACHE_ENTRIES)

/**
 * The app's icon at [sizeDp]. An adaptive icon with a monochrome layer
 * (Android 13 themed icons) gives that layer, its 66dp safe zone scaled to
 * the glyph box so it sits at the glyphs' size; any other icon is drawn whole,
 * inset like the glyphs' own margin. Work and clone badges come from the
 * row's profile label, not from the icon.
 */
internal fun loadLauncherIcon(
    context: Context,
    app: AppEntry,
    sizeDp: Int,
    themedMonochrome: Boolean,
): LauncherIcon? =
    runCatching {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val activityInfo =
            launcherApps
                .getActivityList(app.component.packageName, app.user)
                .firstOrNull { candidate ->
                    candidate.componentName == app.component
                } ?: return@runCatching null

        val densityDpi = context.resources.displayMetrics.densityDpi
        val density = context.resources.displayMetrics.density
        val packDrawable = IconPacks.drawableFor(context, app.component)
        val iconSizePx =
            (sizeDp * density)
                .toInt()
                .coerceAtLeast(1)
        val bitmap =
            Bitmap.createBitmap(
                iconSizePx,
                iconSizePx,
                Bitmap.Config.ARGB_8888,
            )
        val canvas = Canvas(bitmap)
        val drawable = packDrawable ?: activityInfo.getIcon(densityDpi)
        val monochrome =
            if (themedMonochrome && packDrawable == null &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && drawable is AdaptiveIconDrawable
            ) {
                drawable.monochrome
            } else {
                null
            }
        if (monochrome != null) {
            // The layer spans the 108dp adaptive canvas; its 66dp safe zone fills the icon box.
            val canvasPx = (iconSizePx * ADAPTIVE_CANVAS_DP / ADAPTIVE_SAFE_ZONE_DP).toInt()
            val offset = (iconSizePx - canvasPx) / 2
            monochrome.setBounds(offset, offset, offset + canvasPx, offset + canvasPx)
            monochrome.draw(canvas)
            return@runCatching LauncherIcon(bitmap, monochrome = true)
        }
        val insetPx = (iconSizePx * OWN_ICON_INSET).toInt()
        drawable.setBounds(
            insetPx,
            insetPx,
            iconSizePx - insetPx,
            iconSizePx - insetPx,
        )
        drawable.draw(canvas)
        LauncherIcon(bitmap, monochrome = false)
    }.getOrNull()

internal fun appMark(app: AppEntry): String =
    app.label
        .trim()
        .take(1)
        .uppercase()
        .ifEmpty { "•" }

/** The app's accent: its glyph's ([AppGlyphs], by package), else a stable pick from the Sable palette. */
internal fun appColor(app: AppEntry): Color {
    AppGlyphs.forComponent(app.component.packageName, app.component.className)?.let { return Color(it.accent) }
    val index =
        Math.floorMod(
            app.stableKey().hashCode(),
            SableTilePalette.size,
        )
    return SableTilePalette[index]
}
