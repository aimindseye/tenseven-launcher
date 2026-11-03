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

                                    native.keyCode == AllAppsKeyPolicy.KEYCODE_SPACE && !alt && slot.token == QuickBar.HUB -> {
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
        val stroke = unit * 0.1f
        when (slot.token) {
            QuickBar.COMMAND -> {
                // Magnifier: Search / Command.
                drawCircle(color = color, radius = unit * 0.28f, center = Offset(unit * 0.42f, unit * 0.42f), style = Stroke(stroke))
                drawLine(
                    color = color,
                    start = Offset(unit * 0.62f, unit * 0.62f),
                    end = Offset(unit * 0.88f, unit * 0.88f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }

            QuickBar.PHONE -> {
                // Handset: a "C" arc with round ear and mouth pieces.
                val inset = unit * 0.2f
                drawArc(
                    color = color,
                    startAngle = 100f,
                    sweepAngle = 160f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(unit - inset * 2, unit - inset * 2),
                    style = Stroke(unit * 0.15f, cap = StrokeCap.Round),
                )
                val radius = (unit - inset * 2) / 2f
                val center = Offset(unit / 2f, unit / 2f)
                listOf(100.0, 260.0).forEach { angle ->
                    val rad = Math.toRadians(angle)
                    drawCircle(
                        color = color,
                        radius = unit * 0.11f,
                        center =
                            Offset(
                                center.x + radius * kotlin.math.cos(rad).toFloat(),
                                center.y + radius * kotlin.math.sin(rad).toFloat(),
                            ),
                    )
                }
            }

            QuickBar.HUB -> {
                // Hub: three short lines, as on the page indicator.
                for (line in 0..2) {
                    val y = unit * (0.25f + line * 0.25f)
                    drawLine(color, Offset(unit * 0.12f, y), Offset(unit * 0.88f, y), strokeWidth = stroke, cap = StrokeCap.Round)
                }
            }

            QuickBar.CAMERA -> {
                // Camera body, viewfinder bump and lens.
                drawRoundRect(
                    color = color,
                    topLeft = Offset(unit * 0.1f, unit * 0.3f),
                    size = Size(unit * 0.8f, unit * 0.52f),
                    cornerRadius = CornerRadius(unit * 0.08f),
                    style = Stroke(stroke),
                )
                drawRect(color = color, topLeft = Offset(unit * 0.34f, unit * 0.18f), size = Size(unit * 0.32f, unit * 0.12f))
                drawCircle(color = color, radius = unit * 0.14f, center = Offset(unit * 0.5f, unit * 0.56f), style = Stroke(stroke))
            }

            else -> {
                // Four squares: All Apps.
                val cell = unit * 0.36f
                val gap = unit * 0.14f
                for (row in 0..1) {
                    for (col in 0..1) {
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(unit * 0.07f + col * (cell + gap), unit * 0.07f + row * (cell + gap)),
                            size = Size(cell, cell),
                            cornerRadius = CornerRadius(unit * 0.06f),
                        )
                    }
                }
            }
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
