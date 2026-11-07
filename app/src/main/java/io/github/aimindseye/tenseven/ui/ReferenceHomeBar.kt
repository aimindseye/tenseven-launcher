package io.github.aimindseye.tenseven.ui

import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.model.HomeNavigationPolicy
import io.github.aimindseye.tenseven.model.QuickBar
import io.github.aimindseye.tenseven.model.StartPages
import io.github.aimindseye.tenseven.model.TablerGlyphPaths
import io.github.aimindseye.tenseven.model.stableKey

/** One reference-style row; central page targets scroll at large font sizes without shrinking touch targets. */
@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
internal fun ReferenceHomeBar(
    slots: List<QuickSlot>,
    pages: HomeBarPages,
    actions: HomeBarActions,
) {
    val capabilities = rememberPlatformCapabilities()
    val keyboard = capabilities.hardwareKeyboard
    val revision = rememberHubRevision()
    val context = androidx.compose.ui.platform.LocalContext.pages.current
    val enabled =
        remember(revision) {
            io.github.aimindseye.tenseven.platform
                .StartStateRepository(context)
                .loadHubEnabledApps()
        }
    val unread =
        if (capabilities.hubNotifications == io.github.aimindseye.tenseven.platform.CapabilityStatus.Ready) {
            io.github.aimindseye.tenseven.platform.LockLayerState.counts
                .filterKeys { it in enabled }
                .values
                .sum()
        } else {
            0
        }
    val compact =
        remember(keyboard) {
            HomeNavigationPolicy.compact(
                keyboard,
                KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_CALL),
                KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_ENDCALL),
            )
        }
    val shown = HomeNavigationPolicy.shortcuts(slots.map { it.token }, compact)
    Row(
        Modifier.testTag("home-reference-bar").background(Bb10Background).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeBarControl("Hub", "mail", pages.current == StartPages.HUB + pages.lead, unread) {
            actions.showPage(StartPages.HUB + pages.lead)
        }
        slots.filter { it.token in shown }.filterNot { it.token == QuickBar.COMMAND }.forEach { slot ->
            HomeShortcut(slot, actions.openSlot, actions.options, actions.phoneFocus)
        }
        HomePageControls(pages, Modifier.weight(1f), actions.showPage)
        slots.filter { it.token in shown && it.token == QuickBar.COMMAND }.forEach { slot ->
            HomeShortcut(slot, actions.openSlot, actions.options, actions.phoneFocus)
        }
        HomeBarControl(
            "Camera",
            "camera",
        ) {
            val camera = slots.firstOrNull { it.token == QuickBar.CAMERA }
            actions.openSlot(camera ?: QuickSlot(QuickBar.CAMERA, "Camera", null))
        }
    }
}

@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
private fun HomePageControls(
    pages: HomeBarPages,
    modifier: Modifier,
    onShowPage: (Int) -> Unit,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pages.lead > 0) {
            HomeBarControl("Widgets", "layout-grid", pages.current == 0) { onShowPage(0) }
        }
        HomeBarControl(
            if (pages.favorites) "Favorites" else "Active Frames",
            "layout-grid",
            pages.current == StartPages.HOME + pages.lead,
        ) {
            onShowPage(StartPages.HOME + pages.lead)
        }
        for (page in StartPages.FIRST_GRID + pages.lead until pages.count) {
            HomeBarControl(
                "Apps page ${page - pages.lead - StartPages.FIRST_GRID + 1}",
                if (page ==
                    StartPages.FIRST_GRID + pages.lead
                ) {
                    "apps"
                } else {
                    "circle"
                },
                pages.current == page,
            ) {
                onShowPage(page)
            }
        }
    }
}

@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
private fun HomeShortcut(
    slot: QuickSlot,
    onSlot: (QuickSlot) -> Unit,
    onOptions: (QuickSlot) -> Unit,
    onPhoneFocus: (Boolean) -> Unit,
) {
    Box(
        Modifier
            .size(48.dp)
            .onFocusChanged { if (slot.token == QuickBar.PHONE) onPhoneFocus(it.isFocused) }
            .sableFocusRing()
            .combinedClickable(onClick = { onSlot(slot) }, onLongClick = { onOptions(slot) })
            .semantics { contentDescription = slot.label },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides Bb10Text,
        ) {
            QuickSlotMark(slot, BAR_GLYPH_SIZE, monochrome = true)
        }
    }
}

@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
internal fun HomeBarControl(
    label: String,
    glyph: String,
    isSelected: Boolean = false,
    unread: Int = 0,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .widthIn(min = 48.dp)
            .heightIn(min = 48.dp)
            .border(
                if (isSelected) 1.dp else 0.dp,
                if (isSelected) Bb10Blue else androidx.compose.ui.graphics.Color.Transparent,
            ).sableFocusRing()
            .combinedClickable(onClick = onClick)
            .semantics {
                contentDescription = if (unread > 0) "$label, $unread unread" else label
                selected = isSelected
            },
        contentAlignment = Alignment.Center,
    ) {
        LineIcon(TablerGlyphPaths.BY_NAME[glyph] ?: LineIconPaths.SEARCH, Modifier.size(22.dp), Bb10Text)
        if (unread > 0) {
            Text(
                if (unread >
                    MAX_BAR_COUNT
                ) {
                    "99+"
                } else {
                    unread.toString()
                },
                Modifier.align(Alignment.TopEnd),
                color = Bb10Blue,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
internal fun Bb7ReferenceBar(
    apps: List<io.github.aimindseye.tenseven.model.AppEntry>,
    actions: Bb7BarActions,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state =
        remember(context) {
            io.github.aimindseye.tenseven.platform
                .StartStateRepository(context)
        }
    val byKey = remember(apps) { apps.associateBy { it.stableKey() } }
    val slots =
        io.github.aimindseye.tenseven.model.QuickBar
            .normalize(state.loadQuickBar())
            .map { resolveQuickSlot(it, apps, byKey) }
    ReferenceHomeBar(
        slots = slots,
        pages = HomeBarPages(StartPages.HOME, StartPages.FIRST_GRID + 1, 0, favorites = true),
        actions =
            HomeBarActions(
                showPage = { page ->
                    when (page) {
                        StartPages.HUB -> actions.hub()
                        StartPages.HOME -> actions.favorites()
                        else -> actions.apps()
                    }
                },
                openSlot = { slot ->
                    when (slot.token) {
                        QuickBar.COMMAND -> {
                            actions.command()
                        }

                        QuickBar.CAMERA -> {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA,
                                    ),
                                )
                            }
                            Unit
                        }

                        else -> {
                            slot.app?.let(actions.launch)
                        }
                    }
                },
                options = { actions.apps() },
            ),
    )
}

internal data class HomeBarPages(
    val current: Int,
    val count: Int,
    val lead: Int,
    val favorites: Boolean = false,
)

internal class HomeBarActions(
    val showPage: (Int) -> Unit,
    val openSlot: (QuickSlot) -> Unit,
    val options: (QuickSlot) -> Unit,
    val phoneFocus: (Boolean) -> Unit = {},
)

internal class Bb7BarActions(
    val hub: () -> Unit,
    val command: () -> Unit,
    val apps: () -> Unit,
    val favorites: () -> Unit,
    val launch: (io.github.aimindseye.tenseven.model.AppEntry) -> Unit,
)

private const val BAR_GLYPH_SIZE = 22
private const val MAX_BAR_COUNT = 99
