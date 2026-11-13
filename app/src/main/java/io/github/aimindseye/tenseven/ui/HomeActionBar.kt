package io.github.aimindseye.tenseven.ui

import android.content.Intent
import android.provider.CallLog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.model.CoreAppIdentity
import io.github.aimindseye.tenseven.model.HubFilter
import io.github.aimindseye.tenseven.model.QuickBar
import io.github.aimindseye.tenseven.model.StartText.t
import io.github.aimindseye.tenseven.model.inLauncherUser
import io.github.aimindseye.tenseven.model.stableKey
import io.github.aimindseye.tenseven.privacy.AllAppsKeyPolicy
import java.util.Locale
import androidx.compose.foundation.Canvas as ComposeCanvas

// BB10 glyph path ratios are pixel-for-pixel the approved Canvas geometry.
private const val GLYPH_STROKE_RATIO = 0.1f
private const val SEARCH_RING_RADIUS_RATIO = 0.28f
private const val SEARCH_RING_CENTER_RATIO = 0.42f
private const val SEARCH_HANDLE_START_RATIO = 0.62f
private const val SEARCH_HANDLE_END_RATIO = 0.88f
private const val PHONE_ARC_INSET_RATIO = 0.2f
private const val PHONE_ARC_START_DEGREES = 100f
private const val PHONE_ARC_SWEEP_DEGREES = 160f
private const val PHONE_ARC_STROKE_RATIO = 0.15f
private const val PHONE_FIRST_END_DEGREES = 100.0
private const val PHONE_SECOND_END_DEGREES = 260.0
private const val PHONE_END_RADIUS_RATIO = 0.11f
private const val HUB_FIRST_LINE_RATIO = 0.25f
private const val HUB_LINE_SPACING_RATIO = 0.25f
private const val HUB_LINE_LEFT_RATIO = 0.12f
private const val HUB_LINE_RIGHT_RATIO = 0.88f
private const val CAMERA_LEFT_RATIO = 0.1f
private const val CAMERA_TOP_RATIO = 0.3f
private const val CAMERA_WIDTH_RATIO = 0.8f
private const val CAMERA_HEIGHT_RATIO = 0.52f
private const val CAMERA_CORNER_RATIO = 0.08f
private const val CAMERA_BUMP_LEFT_RATIO = 0.34f
private const val CAMERA_BUMP_TOP_RATIO = 0.18f
private const val CAMERA_BUMP_WIDTH_RATIO = 0.32f
private const val CAMERA_BUMP_HEIGHT_RATIO = 0.12f
private const val CAMERA_LENS_RADIUS_RATIO = 0.14f
private const val CAMERA_LENS_X_RATIO = 0.5f
private const val CAMERA_LENS_Y_RATIO = 0.56f
private const val APPS_CELL_RATIO = 0.36f
private const val APPS_GAP_RATIO = 0.14f
private const val APPS_ORIGIN_RATIO = 0.07f
private const val APPS_CORNER_RATIO = 0.06f

@Composable
internal fun SlotOptionsScreen(
    slot: QuickSlot,
    apps: List<AppEntry>,
    onBack: () -> Unit,
    onOpen: () -> Unit,
    onHubFilter: (HubFilter) -> Unit,
    onOpenHubSettings: () -> Unit,
    onStartIntent: (Intent) -> Unit,
    onLaunchApp: (AppEntry) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
    onEditHome: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(slot) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            MetroHeader(title = slot.label.lowercase(Locale.getDefault()), onBack = onBack)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.focusRequester(firstFocus)) {
                ContextAction(mark = "→", label = "open", onClick = onOpen)
            }
            when (slot.token) {
                QuickBar.HUB -> {
                    HubFilter.entries.forEach { filter ->
                        ContextAction(mark = "◇", label = t("hub_page_2", filter.label), onClick = { onHubFilter(filter) })
                    }
                    ContextAction(mark = "⚙", label = t("hub_settings"), onClick = onOpenHubSettings)
                }

                QuickBar.PHONE -> {
                    ContextAction(mark = "☎", label = t("dial_pad"), onClick = { onStartIntent(Intent(Intent.ACTION_DIAL)) })
                    ContextAction(
                        mark = "↺",
                        label = t("recent_calls"),
                        onClick = { onStartIntent(Intent(Intent.ACTION_VIEW, CallLog.Calls.CONTENT_URI)) },
                    )
                    findLiveApp(apps, CONTACTS_PACKAGE)?.let { contacts ->
                        ContextAction(mark = "@", label = "contacts", onClick = { onLaunchApp(contacts) })
                    }
                }

                else -> {
                    Unit
                }
            }
            slot.app?.let { app ->
                ContextAction(mark = "ⓘ", label = t("app_info_2"), onClick = { onAppInfo(app) })
            }
            ContextAction(mark = "▭", label = t("edit_home_and_quick_bar"), onClick = onEditHome)
        }
    }
}

/**
 * The BB10 bar under Active Frames and the app grid: Phone · Search · Camera
 * by default, icon only, white on the dark bar, the focused slot BB10 blue.
 * Enter or a tap opens a slot; long press, Menu or Alt+Enter (Fn+Enter) opens
 * the slot's options; Space on a Hub slot peeks at the Hub page; digits typed
 * while Phone is focused go to the dialer (handled by [StartStrip]).
 */
@Composable
internal fun Bb10ActionBar(
    slots: List<QuickSlot>,
    onSlot: (QuickSlot) -> Unit,
    onOptions: (QuickSlot) -> Unit,
    onHubPeek: () -> Unit,
    onPhoneFocusChanged: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT_DP.dp)
                .background(Brush.verticalGradient(listOf(Bb10BarTop, Bb10BarBottom)))
                .border(1.dp, Bb10Line)
                .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        slots.forEachIndexed { index, slot ->
            var focused by remember { mutableStateOf(false) }
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (focused) Bb10Blue else Color.Transparent)
                        .onFocusChanged { state ->
                            focused = state.isFocused
                            if (slot.token == QuickBar.PHONE) onPhoneFocusChanged(state.isFocused)
                        }.onPreviewKeyEvent { event ->
                            val native = event.nativeKeyEvent
                            val alt = native.metaState and (QuickBar.META_FUNCTION_ON or QuickBar.META_ALT_ON) != 0
                            val action: (() -> Unit)? =
                                when {
                                    native.keyCode == AllAppsKeyPolicy.KEYCODE_MENU ||
                                        (native.keyCode == AllAppsKeyPolicy.KEYCODE_ENTER && alt) -> {
                                        { onOptions(slot) }
                                    }

                                    native.keyCode == AllAppsKeyPolicy.KEYCODE_SPACE && !alt && slot
                                        .token == QuickBar.HUB -> {
                                        onHubPeek
                                    }

                                    else -> {
                                        null
                                    }
                                }
                            if (action != null && event.type == KeyEventType.KeyDown && native.repeatCount == 0) action()
                            action != null
                        }.combinedClickable(
                            onClick = { onSlot(slot) },
                            onLongClick = { onOptions(slot) },
                        ).semantics {
                            contentDescription = "${slot.label}, ${QuickBar.shortcutLabel(index)}"
                        },
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalContentColor provides if (focused) Bb10OnBlue else Bb10Text) {
                    QuickSlotMark(slot = slot, size = BAR_GLYPH_DP, monochrome = true)
                }
            }
        }
    }
}

/**
 * A slot's mark. Phone, Search, Camera and All Apps are drawn as BB10-style
 * glyphs ([monochrome] in the bar's content colour); app slots show the app's icon.
 */
@Composable
internal fun QuickSlotMark(
    slot: QuickSlot,
    size: Int,
    monochrome: Boolean = false,
) {
    val app = slot.app
    val glyph =
        slot.token in setOf(QuickBar.COMMAND, QuickBar.ALL_APPS, QuickBar.HUB) ||
            (monochrome && slot.token in setOf(QuickBar.PHONE, QuickBar.CAMERA))
    if (app != null && !glyph) {
        AppMark(app = app, size = size)
        return
    }
    val color = if (monochrome) LocalContentColor.current else MaterialTheme.colorScheme.primary
    ComposeCanvas(Modifier.size(size.dp)) {
        val unit = this.size.minDimension
        val stroke = unit * GLYPH_STROKE_RATIO
        when (slot.token) {
            QuickBar.COMMAND -> drawQuickSlotSearchGlyph(color, unit, stroke)
            QuickBar.PHONE -> drawQuickSlotPhoneGlyph(color, unit)
            QuickBar.HUB -> drawQuickSlotHubGlyph(color, unit)
            QuickBar.CAMERA -> drawQuickSlotCameraGlyph(color, unit, stroke)
            else -> drawQuickSlotAppsGlyph(color, unit)
        }
    }
}

// Preserve search glyph geometry with an isolated DrawScope operation.
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawQuickSlotSearchGlyph(
    color: Color,
    unit: Float,
    stroke: Float,
) {
    // Magnifier: Search / Command.
    drawCircle(
        color = color,
        radius = unit * SEARCH_RING_RADIUS_RATIO,
        center =
            Offset(
                unit * SEARCH_RING_CENTER_RATIO,
                unit * SEARCH_RING_CENTER_RATIO,
            ),
        style = Stroke(stroke),
    )
    drawLine(
        color = color,
        start = Offset(unit * SEARCH_HANDLE_START_RATIO, unit * SEARCH_HANDLE_START_RATIO),
        end = Offset(unit * SEARCH_HANDLE_END_RATIO, unit * SEARCH_HANDLE_END_RATIO),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
}

// Preserve phone glyph geometry with an isolated DrawScope operation.
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawQuickSlotPhoneGlyph(
    color: Color,
    unit: Float,
) {
    // Handset: a "C" arc with round ear and mouth pieces.
    val inset = unit * PHONE_ARC_INSET_RATIO
    drawArc(
        color = color,
        startAngle = PHONE_ARC_START_DEGREES,
        sweepAngle = PHONE_ARC_SWEEP_DEGREES,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = Size(unit - inset * 2, unit - inset * 2),
        style = Stroke(unit * PHONE_ARC_STROKE_RATIO, cap = StrokeCap.Round),
    )
    val radius = (unit - inset * 2) / 2f
    val center = Offset(unit / 2f, unit / 2f)
    listOf(PHONE_FIRST_END_DEGREES, PHONE_SECOND_END_DEGREES).forEach { angle ->
        val rad = Math.toRadians(angle)
        drawCircle(
            color = color,
            radius = unit * PHONE_END_RADIUS_RATIO,
            center =
                Offset(
                    center.x + radius * kotlin.math.cos(rad).toFloat(),
                    center.y + radius * kotlin.math.sin(rad).toFloat(),
                ),
        )
    }
}

// Preserve hub glyph geometry with an isolated DrawScope operation.
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawQuickSlotHubGlyph(
    color: Color,
    unit: Float,
) {
    // Hub: three short lines, as on the page indicator.
    for (line in 0..2) {
        val y = unit * (HUB_FIRST_LINE_RATIO + line * HUB_LINE_SPACING_RATIO)
        drawLine(
            color,
            Offset(unit * HUB_LINE_LEFT_RATIO, y),
            Offset(unit * HUB_LINE_RIGHT_RATIO, y),
            strokeWidth = unit * GLYPH_STROKE_RATIO,
            cap = StrokeCap.Round,
        )
    }
}

// Preserve camera glyph geometry with an isolated DrawScope operation.
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawQuickSlotCameraGlyph(
    color: Color,
    unit: Float,
    stroke: Float,
) {
    // Camera body, viewfinder bump and lens.
    drawRoundRect(
        color = color,
        topLeft = Offset(unit * CAMERA_LEFT_RATIO, unit * CAMERA_TOP_RATIO),
        size = Size(unit * CAMERA_WIDTH_RATIO, unit * CAMERA_HEIGHT_RATIO),
        cornerRadius = CornerRadius(unit * CAMERA_CORNER_RATIO),
        style = Stroke(stroke),
    )
    drawRect(
        color = color,
        topLeft = Offset(unit * CAMERA_BUMP_LEFT_RATIO, unit * CAMERA_BUMP_TOP_RATIO),
        size =
            Size(unit * CAMERA_BUMP_WIDTH_RATIO, unit * CAMERA_BUMP_HEIGHT_RATIO),
    )
    drawCircle(
        color = color,
        radius = unit * CAMERA_LENS_RADIUS_RATIO,
        center =
            Offset(
                unit * CAMERA_LENS_X_RATIO,
                unit * CAMERA_LENS_Y_RATIO,
            ),
        style = Stroke(stroke),
    )
}

// Preserve apps glyph geometry with an isolated DrawScope operation.
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawQuickSlotAppsGlyph(
    color: Color,
    unit: Float,
) {
    // Four squares: All Apps.
    val cell = APPS_CELL_RATIO * unit
    val gap = APPS_GAP_RATIO * unit
    for (row in 0..1) {
        for (col in 0..1) {
            drawRoundRect(
                color = color,
                topLeft =
                    Offset(
                        unit * APPS_ORIGIN_RATIO + col * (cell + gap),
                        unit * APPS_ORIGIN_RATIO +
                            row * (cell + gap),
                    ),
                size = Size(cell, cell),
                cornerRadius = CornerRadius(unit * APPS_CORNER_RATIO),
            )
        }
    }
}

/**
 * All Apps "Choose base bar slot" (sable-all-apps-titan2 panel 5): the bar is
 * full, so the app replaces the slot the user picks. Each slot says Keep, the
 * focused one "Replace with <app>", and Search "Required path" (it cannot be
 * replaced). Enter replaces; Back returns to the app's actions.
 */
@Composable
internal fun SlotPickerScreen(
    app: AppEntry,
    tokens: List<String>,
    apps: List<AppEntry>,
    appByKey: Map<String, AppEntry>,
    onBack: () -> Unit,
    onReplace: (Int) -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(app) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            MetroHeader(title = t("choose_bar_slot"), onBack = onBack)
            Text(
                text = t("add_to_the_bar", app.label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
        }
        itemsIndexed(tokens, key = { _, token -> token }) { index, token ->
            val slot = resolveQuickSlot(token, apps, appByKey)
            var focused by remember { mutableStateOf(false) }
            val required = QuickBar.isRequired(token)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .heightIn(min = 56.dp)
                        .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                        .onFocusChanged { focused = it.isFocused }
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (focused && !required) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .sableFocusRing()
                        .clickable { if (!required) onReplace(index) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (slot != null) QuickSlotMark(slot = slot, size = 30)
                Spacer(Modifier.width(14.dp))
                val content = if (focused && !required) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                Column(modifier = Modifier.weight(1f)) {
                    Text(slot?.label ?: quickSlotFallbackLabel(token), style = MaterialTheme.typography.titleMedium, color = content)
                    Text(
                        text = QuickBar.pickerLine(token, focused, app.label),
                        style = MaterialTheme.typography.bodySmall,
                        color = content.copy(alpha = 0.8f),
                    )
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                text = t("every_app_stays_on_the"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The lists Edit Home reorders. */
internal fun quickBarChoices(
    apps: List<AppEntry>,
    appByKey: Map<String, AppEntry>,
    current: List<String>,
): List<QuickSlot> {
    val roles =
        listOf(QuickBar.PHONE, QuickBar.CAMERA, QuickBar.HUB, QuickBar.ALL_APPS)
            .filterNot { it in current }
            .mapNotNull { resolveQuickSlot(it, apps, appByKey) }
    val taken =
        current.mapNotNull { resolveQuickSlot(it, apps, appByKey)?.app?.stableKey() }.toSet() +
            roles.mapNotNull { it.app?.stableKey() }
    val others =
        apps
            .filter { it.inLauncherUser && it.stableKey() !in taken }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
            .map { QuickSlot(QuickBar.appToken(it.stableKey()), it.label, it) }
    return roles + others
}

/** Null when the slot's app is not installed; the bar then skips it. */
internal fun resolveQuickSlot(
    token: String,
    apps: List<AppEntry>,
    appByKey: Map<String, AppEntry>,
): QuickSlot? {
    fun role(
        packages: List<String>,
        labels: List<String>,
    ): AppEntry? {
        val personal = apps.filter { it.inLauncherUser }
        return packages.firstNotNullOfOrNull { pkg ->
            personal.firstOrNull { CoreAppIdentity.canonicalPackage(it.component.packageName) == pkg }
        } ?: personal.firstOrNull { app -> labels.any { app.label.equals(it, ignoreCase = true) } }
    }
    return when (token) {
        QuickBar.COMMAND -> {
            QuickSlot(token, t("search_2"), null)
        }

        QuickBar.ALL_APPS -> {
            QuickSlot(token, t("all_apps"), null)
        }

        QuickBar.PHONE -> {
            role(listOf(DIALER_PACKAGE), listOf(t("phone")))?.let { QuickSlot(token, t("phone"), it) }
        }

        // Start's own Hub page: always available, whatever messaging apps are installed.
        QuickBar.HUB -> {
            QuickSlot(token, t("hub"), null)
        }

        QuickBar.CAMERA -> {
            role(CoreAppIdentity.startTileCandidates(CoreAppIdentity.CAMERA), listOf(t("camera")))
                ?.let { QuickSlot(token, t("camera"), it) }
        }

        else -> {
            QuickBar.appKey(token)?.let(appByKey::get)?.let { QuickSlot(token, it.label, it) }
        }
    }
}

internal fun quickSlotFallbackLabel(token: String): String =
    when (token) {
        QuickBar.PHONE -> t("phone_not_installed")
        QuickBar.HUB -> t("hub_not_installed")
        QuickBar.CAMERA -> t("camera_not_installed")
        else -> t("app_not_installed")
    }

/** Role routing shared by the bar; presentation never invents an app record for Camera or Hub. */
internal class QuickSlotActions(
    val command: () -> Unit,
    val apps: () -> Unit,
    val hub: () -> Unit,
    val camera: () -> Unit,
    val launch: (AppEntry) -> Unit,
)

internal fun activateQuickSlot(
    slot: QuickSlot,
    actions: QuickSlotActions,
) {
    when (slot.token) {
        QuickBar.COMMAND -> actions.command()
        QuickBar.ALL_APPS -> actions.apps()
        QuickBar.HUB -> actions.hub()
        QuickBar.CAMERA -> actions.camera()
        else -> slot.app?.let(actions.launch)
    }
}
