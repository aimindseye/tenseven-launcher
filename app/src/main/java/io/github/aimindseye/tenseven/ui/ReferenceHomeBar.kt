package io.github.aimindseye.tenseven.ui

import android.os.Build
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.model.HomeBarCompactPolicy
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
    val revision = rememberHubRevision()
    val context = androidx.compose.ui.platform.LocalContext.current
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
    // One destination per icon. Q25 shows four fixed destinations; Titan 2 can show
    // up to two saved extra shortcuts. The saved shortcut list itself is never changed.
    val compact = HomeBarCompactPolicy.compact(Build.MODEL, LocalConfiguration.current.screenWidthDp)
    val shown = HomeBarCompactPolicy.optionalTokens(slots.map { it.token }, compact)
    val homeMenuOpen = remember { mutableStateOf(false) }
    Row(
        Modifier.testTag("home-reference-bar").background(Bb10Background).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HubBarTarget(pages, unread, actions)
        slots.filter { it.token in shown }.filterNot { it.token == QuickBar.COMMAND }.forEach { slot ->
            HomeShortcut(slot, actions.openSlot, actions.options, actions.phoneFocus)
        }
        HomePageControls(
            pages,
            Modifier.weight(1f),
            actions.showPage,
            onCommand = { homeMenuOpen.value = true },
        )
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
    if (homeMenuOpen.value) {
        CompactHomeMenu(
            onDismiss = { homeMenuOpen.value = false },
            actions = listOf(
                "Phone" to { actions.openSlot(QuickSlot(QuickBar.PHONE, "Phone", null)) },
                "Command" to { actions.openSlot(QuickSlot(QuickBar.COMMAND, "Command", null)) },
                "Camera" to { actions.openSlot(QuickSlot(QuickBar.CAMERA, "Camera", null)) },
                "Hub" to { actions.showPage(StartPages.HUB + pages.lead) },
                "Calendar" to {
                    runCatching {
                        context.startActivity(
                            android.content.Intent.makeMainSelectorActivity(
                                android.content.Intent.ACTION_MAIN,
                                android.content.Intent.CATEGORY_APP_CALENDAR,
                            ),
                        )
                    }
                    Unit
                },
            ),
        )
    }
}

@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
private fun HomePageControls(
    pages: HomeBarPages,
    modifier: Modifier,
    onShowPage: (Int) -> Unit,
    onCommand: () -> Unit,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeBarControl(
            if (pages.favorites) "Favorites" else "Active Frames",
            if (pages.favorites) "home" else "layout-grid",
            pages.current == StartPages.HOME + pages.lead,
        ) { onShowPage(StartPages.HOME + pages.lead) }
        val firstApps = StartPages.FIRST_GRID + pages.lead
        HomeBarControl(
            "All Apps", "apps", pages.current >= firstApps,
            onLongClick = onCommand,
        ) { onShowPage(firstApps) }
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
            .onPreviewKeyEvent { event ->
                val menu =
                    io.github.aimindseye.tenseven.platform.DeviceCompatibility
                        .keyAction(event.nativeKeyEvent) ==
                        io.github.aimindseye.tenseven.model.LauncherKeys.Action.Menu
                if (menu && event.type == KeyEventType.KeyDown) onOptions(slot)
                menu
            }.sableFocusRing()
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
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .widthIn(min = 44.dp)
            .heightIn(min = 48.dp)
            .sableFocusRing()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics {
                contentDescription = if (unread > 0) "$label, $unread unread" else label
                selected = isSelected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.heightIn(min = 43.dp).widthIn(min = 44.dp), contentAlignment = Alignment.Center) {
            if (glyph == "circle") {
                // Read theme colors while still inside the composable scope, not Canvas's DrawScope.
                val dotColor = if (isSelected) Bb10Text else Bb10Muted
                androidx.compose.foundation.Canvas(Modifier.size(6.dp)) {
                    drawCircle(dotColor)
                }
            } else {
                LineIcon(homeControlPaths(glyph), Modifier.size(22.dp), Bb10Text)
            }
            if (unread > 0) {
                Text(
                    if (unread > MAX_BAR_COUNT) "99+" else unread.toString(),
                    Modifier.align(Alignment.TopEnd),
                    color = Bb10Blue,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        // One consistent BB10 selection underline across BB10 Home, Active Frames, All Apps and BB7.
        Box(Modifier.width(19.dp).height(2.dp).background(if (isSelected) Bb10Blue else Color.Transparent))
    }
}

@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
internal fun Bb7ReferenceBar(
    apps: List<io.github.aimindseye.tenseven.model.AppEntry>,
    actions: Bb7BarActions,
    appsOpen: Boolean = false,
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
            .mapNotNull { resolveQuickSlot(it, apps, byKey) }
    ReferenceHomeBar(
        slots = slots,
        pages =
            HomeBarPages(
                if (appsOpen) StartPages.FIRST_GRID else StartPages.HOME,
                StartPages.FIRST_GRID + 1,
                0,
                favorites = true,
            ),
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
    val peek: (() -> Unit)? = null,
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

private fun homeControlPaths(glyph: String): List<String> =
    when (glyph) {
        "menu-2" -> listOf("M4 6h16", "M4 12h16", "M4 18h16")
        "home" -> listOf("M3 11l9 -8 9 8", "M5 10v10h14v-10", "M9 20v-7h6v7")
        else -> TablerGlyphPaths.BY_NAME[glyph] ?: LineIconPaths.SEARCH
    }

@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
private fun HubBarTarget(pages: HomeBarPages, unread: Int, actions: HomeBarActions) {
    Box(
        Modifier.onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            val peek = actions.peek
            val space =
                native.keyCode == KeyEvent.KEYCODE_SPACE &&
                    native.metaState and (QuickBar.META_ALT_ON or QuickBar.META_FUNCTION_ON) == 0
            if (space && peek != null) {
                if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) peek()
            }
            space && peek != null
        },
    ) {
        HomeBarControl("Hub", "mail", pages.current == StartPages.HUB + pages.lead, unread) {
            actions.showPage(StartPages.HUB + pages.lead)
        }
    }
}
