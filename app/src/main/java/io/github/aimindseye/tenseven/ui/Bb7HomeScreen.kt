package io.github.aimindseye.tenseven.ui

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aimindseye.tenseven.R
import io.github.aimindseye.tenseven.design.SableAppearance
import io.github.aimindseye.tenseven.design.SableTheme
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.model.Bb7Home
import io.github.aimindseye.tenseven.model.Bb7IconSize
import io.github.aimindseye.tenseven.model.Bb7Panel
import io.github.aimindseye.tenseven.model.Bb7Panels
import io.github.aimindseye.tenseven.model.Bb7Scrim
import io.github.aimindseye.tenseven.model.Bb7Swipe
import io.github.aimindseye.tenseven.model.GridColumns
import io.github.aimindseye.tenseven.model.HomeApps
import io.github.aimindseye.tenseven.model.HomeStatusMode
import io.github.aimindseye.tenseven.model.HomeStyle
import io.github.aimindseye.tenseven.model.LauncherKeys
import io.github.aimindseye.tenseven.model.StartText.t
import io.github.aimindseye.tenseven.model.inLauncherUser
import io.github.aimindseye.tenseven.model.stableKey
import io.github.aimindseye.tenseven.platform.AppUsageRepository
import io.github.aimindseye.tenseven.platform.DefaultApps
import io.github.aimindseye.tenseven.platform.DeviceCompatibility
import io.github.aimindseye.tenseven.platform.LiveSurfaceRepository
import io.github.aimindseye.tenseven.platform.StartStateRepository
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/**
 * Sable Start in the BlackBerry 7 style ([io.github.aimindseye.tenseven.model.HomeStyle.Bb7]): the banner
 * (status dials, date, weather and unread count; the time is the status bar's, so it is not shown
 * twice) on the wallpaper, and a tray along the bottom with one row of Favorites that opens into
 * the whole panel. Keys as on BlackBerry 7: arrows move, Enter opens, Up opens the tray and
 * Escape closes it, Space switches panels (Favorites, All, Downloads), typing searches apps, Menu
 * opens the home options. As on the BlackBerry 10 home, Hub is to the left and the apps to the
 * right: a swipe to the right or Alt+Left opens Hub, a swipe to the left or Alt+Right opens All;
 * with the tray open, Alt+Left/Right switch panels. The home screen shows [homeRows] rows of the
 * panel's icons; dragging the tray up or down shows more or fewer, as on BlackBerry 7. Menu on an
 * icon puts it on the home screen (Add to Favorites), takes it off, or moves it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Bb7HomeScreen(
    apps: List<AppEntry>,
    favoriteKeys: List<String>?,
    homeRows: Int,
    liveSurfaceRepository: LiveSurfaceRepository,
    appearance: SableAppearance,
    refreshGeneration: Int,
    homeGeneration: Int,
    onLaunchApp: (AppEntry) -> Boolean,
    onOpenAppInfo: (AppEntry) -> Unit,
    onOpenHub: () -> Unit,
    onFavoritesChanged: (List<String>) -> Unit,
    onHomeRowsChanged: (Int) -> Unit,
    onOpenMenu: (List<Pair<String, () -> Unit>>) -> Unit,
    /** Alt+Space (as on the BlackBerry 10 home): Command. */
    onOpenCommand: () -> Unit = {},
    onDoubleTapEmpty: (() -> Unit)? = null,
    /** The panels the tray cycles through, and how dark the banner and tray are; both are set from the Menu key. */
    panels: List<Bb7Panel> = Bb7Panels.DEFAULT,
    scrimLevel: Int = Bb7Scrim.DEFAULT,
    onPanelsChanged: (List<Bb7Panel>) -> Unit = {},
    onScrimChanged: (Int) -> Unit = {},
    onOpenWallpaper: () -> Unit = {},
    /** Icon size and the widgets shown under the banner (Launcher Settings). */
    iconSize: Bb7IconSize = Bb7IconSize.DEFAULT,
    widgets: List<String> = emptyList(),
    /** Apps placed in the empty space between the banner and the tray (Add to home screen), in order. */
    desktopKeys: List<String> = emptyList(),
    onDesktopChanged: (List<String>) -> Unit = {},
    /** Icon grid (Launcher Settings): columns (0 = as many as fit) and whether icons carry their names. */
    gridColumns: Int = GridColumns.AUTO,
    showLabels: Boolean = true,
) {
    bb7HomePresenter(
        Bb7HomeInputs(
            source =
                Bb7HomeSource(
                    apps,
                    favoriteKeys,
                    homeRows,
                    liveSurfaceRepository,
                    refreshGeneration,
                    homeGeneration,
                ),
            visual = Bb7HomeVisual(appearance, scrimLevel, iconSize, gridColumns, showLabels),
            panel = Bb7HomePanelConfig(panels, desktopKeys),
            launch = Bb7HomeLaunch(onLaunchApp, onOpenAppInfo, onOpenHub, onOpenMenu),
            edits =
                Bb7HomeEdits(
                    onFavoritesChanged,
                    onHomeRowsChanged,
                    onPanelsChanged,
                    onScrimChanged,
                    onDesktopChanged,
                ),
            extra = Bb7HomeExtra(onOpenCommand, onDoubleTapEmpty, onOpenWallpaper),
        ),
    )
}

private data class Bb7HomeSource(
    val apps: List<AppEntry>,
    val favoriteKeys: List<String>?,
    val homeRows: Int,
    val liveSurfaceRepository: LiveSurfaceRepository,
    val refreshGeneration: Int,
    val homeGeneration: Int,
)

private data class Bb7HomeVisual(
    val appearance: SableAppearance,
    val scrimLevel: Int,
    val iconSize: Bb7IconSize,
    val gridColumns: Int,
    val showLabels: Boolean,
)

private data class Bb7HomePanelConfig(
    val panels: List<Bb7Panel>,
    val desktopKeys: List<String>,
)

private data class Bb7HomeLaunch(
    val onLaunchApp: (AppEntry) -> Boolean,
    val onOpenAppInfo: (AppEntry) -> Unit,
    val onOpenHub: () -> Unit,
    val onOpenMenu: (List<Pair<String, () -> Unit>>) -> Unit,
)

private data class Bb7HomeEdits(
    val onFavoritesChanged: (List<String>) -> Unit,
    val onHomeRowsChanged: (Int) -> Unit,
    val onPanelsChanged: (List<Bb7Panel>) -> Unit,
    val onScrimChanged: (Int) -> Unit,
    val onDesktopChanged: (List<String>) -> Unit,
)

private data class Bb7HomeExtra(
    val onOpenCommand: () -> Unit,
    val onDoubleTapEmpty: (() -> Unit)?,
    val onOpenWallpaper: () -> Unit,
)

private data class Bb7HomeInputs(
    val source: Bb7HomeSource,
    val visual: Bb7HomeVisual,
    val panel: Bb7HomePanelConfig,
    val launch: Bb7HomeLaunch,
    val edits: Bb7HomeEdits,
    val extra: Bb7HomeExtra,
)

/** All mutable BB7 navigation state has the same remember lifetime as before extraction. */
private class Bb7HomeSession {
    var panel by mutableStateOf(Bb7Panel.Favorites)
    var open by mutableStateOf(false)
    var query by mutableStateOf("")
    var selected by mutableIntStateOf(-1)
    var moving by mutableStateOf(false)
    var pickingDesktop by mutableStateOf(false)
    var inDesktopZone by mutableStateOf(false)
    var deskSelected by mutableIntStateOf(0)
    var keyColumns by mutableIntStateOf(Bb7Home.MIN_COLUMNS)
    val focus = FocusRequester()

    fun resetForHome() {
        open = false
        query = ""
        selected = -1
        moving = false
        pickingDesktop = false
        inDesktopZone = false
    }
}

private class Bb7HomeComputed(
    private val session: Bb7HomeSession,
    val lists: Bb7AppLists,
) {
    val searching = session.query.isNotEmpty()
    val inDesktop = if (session.open || searching) false else session.inDesktopZone && lists.desktop.isNotEmpty()
    val deskIndex = session.deskSelected.coerceIn(0, (lists.desktop.size - 1).coerceAtLeast(0))
    val trayTitle =
        when {
            session.moving -> {
                t("move") +
                    lists.shown
                        .getOrNull(session.selected)
                        ?.label
                        .orEmpty() + t("enter_to_drop")
            }

            session.pickingDesktop -> {
                "Tap an app to add to Home"
            }

            searching -> {
                t("search", session.query)
            }

            else -> {
                session.panel.title
            }
        }
}

private data class Bb7HomeDisplayFeed(
    val enabledDials: Set<String>,
    val wallpaperEnabled: Boolean,
    val statusMode: HomeStatusMode,
    val stripExpanded: Boolean,
    val hubSources: Set<String>,
)

private data class Bb7HomeLiveFeed(
    val live: io.github.aimindseye.tenseven.live.LiveSurfaceSnapshot,
    val today: Long,
)

private data class Bb7HomeFeeds(
    val display: Bb7HomeDisplayFeed,
    val live: Bb7HomeLiveFeed,
    val context: Context,
)

private val bb7HomePresenter: @Composable (Bb7HomeInputs) -> Unit = { input ->
    val context = LocalContext.current
    val resources = LocalResources.current
    val stateRepo = remember(context) { StartStateRepository(context) }
    val generation = input.source.refreshGeneration
    val display =
        Bb7HomeDisplayFeed(
            remember(generation) { stateRepo.loadHomeDials() },
            remember(generation) { stateRepo.loadHomeWallpaper() },
            remember(generation) { stateRepo.loadHomeStatusMode() },
            remember(generation) { stateRepo.loadHomeNotificationExpanded(HomeStyle.Bb7) },
            remember(generation) { stateRepo.loadHubEnabledApps() },
        )
    val usage =
        remember(input.source.apps, generation, input.source.homeGeneration) {
            AppUsageRepository(context).load()
        }
    val state = remember { Bb7HomeSession() }
    LaunchedEffect(input.panel.panels) {
        if (state.panel !in input.panel.panels) state.panel = input.panel.panels.first()
    }
    LaunchedEffect(input.source.homeGeneration) { state.resetForHome() }
    val feeds =
        Bb7HomeFeeds(
            display,
            Bb7HomeLiveFeed(
                rememberBb7Live(input.source.liveSurfaceRepository, generation),
                rememberBb7Date(generation),
            ),
            context,
        )
    val lists =
        rememberBb7Apps(
            input.source.apps,
            usage,
            Bb7AppSelection(state.panel, state.query, input.source.favoriteKeys, input.panel.desktopKeys),
        )
    val model = Bb7HomeComputed(state, lists)
    val actions = Bb7HomeActions(input, state, model, context, resources)
    val keyboard = Bb7HomeKeyboard(input, state, model, actions)
    Bb7HomeCanvas(input, state, model, actions, keyboard, feeds).render()
    LaunchedEffect(Unit) { runCatching { state.focus.requestFocus() } }
}

/** Menu and shortcut actions are deliberately outside the composable's rendering path. */
private class Bb7HomeActions(
    private val input: Bb7HomeInputs,
    private val state: Bb7HomeSession,
    private val model: Bb7HomeComputed,
    private val context: Context,
    private val resources: android.content.res.Resources,
) {
    private val favorites get() = model.lists.favorites
    private val desktopKeys get() = input.panel.desktopKeys

    fun openApps() =
        with(state) {
            query = ""
            panel = if (Bb7Panel.All in input.panel.panels) Bb7Panel.All else input.panel.panels.first()
            open = true
            selected = 0
        }

    fun moveBy(delta: Int) =
        with(state) {
            if (model.inDesktop) {
                val next = HomeApps.move(desktopKeys, model.deskIndex, delta)
                if (next != desktopKeys) {
                    input.edits.onDesktopChanged(next)
                    deskSelected = model.deskIndex + delta
                }
            } else {
                val next = HomeApps.move(favorites, selected, delta)
                if (next != favorites) {
                    input.edits.onFavoritesChanged(next)
                    selected += delta
                }
            }
        }

    fun addToFavorites(key: String) {
        val next = Bb7Home.addVisible(favorites, key, state.keyColumns)
        input.edits.onFavoritesChanged(next)
        val rows = Bb7Home.rowsFor(next.size, state.keyColumns)
        if (rows > input.source.homeRows) input.edits.onHomeRowsChanged(rows)
    }

    fun addToDesktop(app: AppEntry) =
        with(state) {
            addBb7Desktop(context, desktopKeys, app.stableKey()) { next ->
                input.edits.onDesktopChanged(next)
                open = false
                query = ""
                pickingDesktop = false
                selected = -1
                showHomeAdded(context, resources.getString(R.string.bb7_added_home, app.label))
            }
        }

    fun menuFor(
        app: AppEntry?,
        fromDesktop: Boolean,
    ): List<Pair<String, () -> Unit>> = if (app != null) appMenu(app, fromDesktop) else homeMenu()

    private fun appMenu(
        app: AppEntry,
        fromDesktop: Boolean,
    ): List<Pair<String, () -> Unit>> =
        buildList {
            val key = app.stableKey()
            add(t("open") to { input.launch.onLaunchApp(app) })
            if (key in desktopKeys) {
                if (fromDesktop) add(t("move_2") to { state.moving = true })
                add(
                    resources.getString(R.string.bb7_remove_home) to {
                        input.edits.onDesktopChanged(desktopKeys.filterNot { it == key })
                    },
                )
            } else {
                add(resources.getString(R.string.bb7_add_home) to { addToDesktop(app) })
            }
            if (key in favorites) {
                add(
                    resources.getString(R.string.bb7_remove_favorites) to {
                        input.edits.onFavoritesChanged(Bb7Home.toggleFavorite(favorites, key))
                    },
                )
            } else {
                add(resources.getString(R.string.bb7_add_favorites) to { addToFavorites(key) })
            }
            add(t("app_info") to { input.launch.onOpenAppInfo(app) })
        }

    private fun homeMenu(): List<Pair<String, () -> Unit>> =
        buildList {
            add(
                "Add shortcut" to {
                    openApps()
                    state.pickingDesktop = true
                },
            )
            add(
                "Customize Home" to {
                    context.startActivity(
                        Intent(context, io.github.aimindseye.tenseven.LauncherSettingsActivity::class.java)
                            .putExtra("tenseven.settings.category", "home"),
                    )
                },
            )
            add("Command" to { input.extra.onOpenCommand() })
            add(
                "Launcher Settings" to {
                    context.startActivity(Intent(context, io.github.aimindseye.tenseven.LauncherSettingsActivity::class.java))
                },
            )
            add("Wallpaper" to { input.extra.onOpenWallpaper() })
            val frequent = Bb7Panel.Frequent
            add(
                (if (frequent in input.panel.panels) "Hide Frequent panel" else "Show Frequent panel") to {
                    input.edits.onPanelsChanged(Bb7Panels.toggle(input.panel.panels, frequent))
                },
            )
            if (input.visual.scrimLevel > Bb7Scrim.MIN) {
                add("Lighter panels" to { input.edits.onScrimChanged(Bb7Scrim.step(input.visual.scrimLevel, -1)) })
            }
            if (input.visual.scrimLevel < Bb7Scrim.MAX) {
                add("Darker panels" to { input.edits.onScrimChanged(Bb7Scrim.step(input.visual.scrimLevel, 1)) })
            }
        }
}

/** Keyboard state machine: menu, desktop and tray keys retain their original precedence. */
private class Bb7HomeKeyboard(
    private val input: Bb7HomeInputs,
    private val state: Bb7HomeSession,
    private val model: Bb7HomeComputed,
    private val actions: Bb7HomeActions,
) {
    private val shown get() = model.lists.shown
    private val desktopApps get() = model.lists.desktop
    private val columns get() = state.keyColumns
    private val visible get() =
        if (state.open || model.searching) shown.size else minOf(shown.size, columns * input.source.homeRows)

    fun onKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val key = event.nativeKeyEvent
        return when {
            state.moving -> {
                when (key.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> actions.moveBy(-1)
                    KeyEvent.KEYCODE_DPAD_RIGHT -> actions.moveBy(1)
                    KeyEvent.KEYCODE_DPAD_UP -> actions.moveBy(-columns)
                    KeyEvent.KEYCODE_DPAD_DOWN -> actions.moveBy(columns)
                    else -> state.moving = false
                }
                true
            }

            model.inDesktop && desktopKey(key) -> {
                true
            }

            else -> {
                trayKey(key)
            }
        }
    }

    private fun desktopKey(key: KeyEvent): Boolean {
        if (key.isAltPressed) {
            return if (key.keyCode == KeyEvent.KEYCODE_ENTER) {
                input.launch.onOpenMenu(actions.menuFor(desktopApps[model.deskIndex], true))
                true
            } else {
                false
            }
        }
        return if (desktopMovement(key.keyCode)) {
            true
        } else {
            when (key.keyCode) {
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                    input.launch.onLaunchApp(desktopApps[model.deskIndex])
                    true
                }

                KeyEvent.KEYCODE_MENU -> {
                    input.launch.onOpenMenu(actions.menuFor(desktopApps[model.deskIndex], true))
                    true
                }

                KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> {
                    state.inDesktopZone = false
                    true
                }

                else -> {
                    false
                }
            }
        }
    }

    private fun desktopMovement(code: Int): Boolean =
        with(state) {
            when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val dx = if (code == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                    deskSelected = Bb7Home.move(model.deskIndex, desktopApps.size, columns, dx, 0)
                    true
                }

                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (model.deskIndex >= columns) {
                        deskSelected = Bb7Home.move(model.deskIndex, desktopApps.size, columns, 0, -1)
                    } else {
                        inDesktopZone = false
                        open = true
                        selected = 0
                    }
                    true
                }

                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (model.deskIndex + columns < desktopApps.size) {
                        deskSelected = Bb7Home.move(model.deskIndex, desktopApps.size, columns, 0, 1)
                    } else {
                        inDesktopZone = false
                        selected = (model.deskIndex % columns).coerceAtMost((visible - 1).coerceAtLeast(0))
                    }
                    true
                }

                else -> {
                    false
                }
            }
        }

    private fun trayKey(key: KeyEvent): Boolean =
        when {
            altNavigation(key) -> true
            trayMovement(key.keyCode) -> true
            else -> trayAction(key)
        }

    private fun altNavigation(key: KeyEvent): Boolean =
        with(state) {
            if (!key.isAltPressed) return false
            when (key.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (open || model.searching) {
                        panel = Bb7Home.step(panel, -1, input.panel.panels)
                        selected = 0
                    } else {
                        input.launch.onOpenHub()
                    }
                    true
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (open || model.searching) {
                        panel = Bb7Home.step(panel, 1, input.panel.panels)
                        selected = 0
                    } else {
                        actions.openApps()
                    }
                    true
                }

                else -> {
                    false
                }
            }
        }

    private fun trayMovement(code: Int): Boolean =
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val dx = if (code == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                state.selected =
                    if (state.selected < 0) {
                        0
                    } else {
                        Bb7Home.move(state.selected, visible, columns, dx, 0)
                    }
                true
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                moveTrayUp()
                true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (state.open && state.selected + columns >= visible) {
                    state.open = false
                    state.selected = -1
                } else {
                    state.selected =
                        if (state.selected < 0) {
                            0
                        } else {
                            Bb7Home.move(state.selected, visible, columns, 0, 1)
                        }
                }
                true
            }

            else -> {
                false
            }
        }

    private fun moveTrayUp() {
        if (state.open || model.searching || state.selected >= columns) {
            state.selected = Bb7Home.move(state.selected, visible, columns, 0, -1)
        } else if (desktopApps.isNotEmpty()) {
            state.inDesktopZone = true
            state.deskSelected =
                (desktopApps.lastIndex / columns * columns + state.selected % columns)
                    .coerceAtMost(desktopApps.lastIndex)
        } else {
            state.open = true
        }
    }

    private fun trayAction(key: KeyEvent): Boolean =
        with(state) {
            when {
                DeviceCompatibility.keyAction(key) == LauncherKeys.Action.Menu -> {
                    input.launch.onOpenMenu(actions.menuFor(shown.getOrNull(selected), false))
                    true
                }

                key.keyCode == KeyEvent.KEYCODE_ENTER || key.keyCode == KeyEvent.KEYCODE_DPAD_CENTER -> {
                    shown.getOrNull(selected)?.let { input.launch.onLaunchApp(it) }
                    true
                }

                DeviceCompatibility.keyAction(key) == LauncherKeys.Action.Command -> {
                    input.extra.onOpenCommand()
                    true
                }

                else -> {
                    traySecondaryAction(key)
                }
            }
        }

    private fun traySecondaryAction(key: KeyEvent): Boolean =
        with(state) {
            when {
                key.keyCode == KeyEvent.KEYCODE_SPACE && !model.searching -> {
                    panel = Bb7Home.step(panel, 1, input.panel.panels)
                    selected = 0
                    true
                }

                key.keyCode == KeyEvent.KEYCODE_DEL && model.searching -> {
                    query = query.dropLast(1)
                    selected = 0
                    true
                }

                key.keyCode == KeyEvent.KEYCODE_ESCAPE || key.keyCode == KeyEvent.KEYCODE_BACK -> {
                    if (model.searching || open) {
                        query = ""
                        open = false
                        selected = 0
                        true
                    } else {
                        false
                    }
                }

                key.keyCode == KeyEvent.KEYCODE_MENU -> {
                    input.launch.onOpenMenu(actions.menuFor(shown.getOrNull(selected), false))
                    true
                }

                else -> {
                    appendQueryCharacter(key)
                }
            }
        }

    private fun appendQueryCharacter(key: KeyEvent): Boolean {
        val ch = key.unicodeChar.takeIf { it > 0 }?.toChar()
        val isLetterOrDigit = ch?.isLetterOrDigit() == true
        val isSearchSpace = ch == ' ' && model.searching
        return if (isLetterOrDigit || isSearchSpace) {
            state.query += ch
            state.selected = 0
            true
        } else {
            false
        }
    }
}

/** Presentation is split into independently testable Compose regions. */
private class Bb7HomeCanvas(
    private val input: Bb7HomeInputs,
    private val state: Bb7HomeSession,
    private val model: Bb7HomeComputed,
    private val actions: Bb7HomeActions,
    private val keyboard: Bb7HomeKeyboard,
    private val feeds: Bb7HomeFeeds,
) {
    private val apps get() = input.source.apps
    private val desktopApps get() = model.lists.desktop
    private val shown get() = model.lists.shown
    private val context get() = feeds.context

    val render: @Composable () -> Unit = {
        SableTheme(appearance = input.visual.appearance) {
            Bb10Theme(input.visual.appearance) {
                ScaledLauncherTextTheme { frame() }
            }
        }
    }

    private val frame: @Composable () -> Unit = {
        with(state) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(if (feeds.display.wallpaperEnabled) Color.Transparent else Color.Black)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .focusRequester(focus)
                        .focusable()
                        .onPreviewKeyEvent { keyboard.onKey(it) },
            ) {
                val columns = bb7Columns(input.visual.gridColumns, maxWidth.value.toInt(), input.visual.iconSize.cellDp)
                SideEffect { keyColumns = columns }
                val swipe = with(LocalDensity.current) { SWIPE_DP.dp.toPx() }
                val rowPx = with(LocalDensity.current) { ROW_DP.dp.toPx() }
                content(columns, swipe, rowPx)
            }
        }
    }

    private val content: @Composable (Int, Float, Float) -> Unit = { columns, swipe, rowPx ->
        with(state) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(swipe) {
                            var dragged = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { dragged = 0f },
                                onDragEnd = {
                                    when (Bb7Home.swipe(dragged, swipe)) {
                                        Bb7Swipe.Hub -> input.launch.onOpenHub()
                                        Bb7Swipe.Apps -> actions.openApps()
                                        null -> Unit
                                    }
                                },
                            ) { _, amount -> dragged += amount }
                        }.pointerInput(input.extra.onDoubleTapEmpty, open) {
                            if (!open) {
                                detectTapGestures(
                                    onDoubleTap = { input.extra.onDoubleTapEmpty?.invoke() },
                                    onLongPress = { actions.openApps() },
                                )
                            }
                        },
            ) {
                banner()
                if (!open && !model.searching) desktop(columns)
                tray(columns, rowPx)
                referenceBar()
            }
        }
    }

    private val banner: @Composable () -> Unit = {
        bb7Banner(
            Bb7BannerDisplay(
                dateText = DateFormat.getDateInstance(DateFormat.FULL).format(Date(feeds.live.today)),
                enabledDials = feeds.display.enabledDials,
                statusMode = feeds.display.statusMode,
                compact = state.open || model.searching,
                scrim = Color.Black.copy(alpha = Bb7Scrim.bannerAlpha(input.visual.scrimLevel)),
            ),
            Bb7BannerNotifications(
                counts = feeds.live.live.hubApps,
                enabledPackages = feeds.display.hubSources,
                showStrip = false,
                expanded = feeds.display.stripExpanded,
            ),
            input.launch.onOpenHub,
            { pkg ->
                apps
                    .firstOrNull { it.inLauncherUser && it.component.packageName == pkg }
                    ?.let(input.launch.onLaunchApp)
            },
        )
    }

    private val desktop: @Composable ColumnScope.(Int) -> Unit = { columns ->
        with(state) {
            bb7Desktop(
                Bb7DesktopView(
                    desktopApps,
                    Bb7GridAppearance(columns, input.visual.iconSize.iconDp, input.visual.showLabels),
                    Bb7GridSelection(if (model.inDesktop) model.deskIndex else -1, moving),
                    Modifier.weight(1f),
                ),
                Bb7DesktopEvents(
                    onLaunch = { input.launch.onLaunchApp(it) },
                    onEmptyTap = {
                        selected = -1
                        inDesktopZone = false
                        actions.openApps()
                        pickingDesktop = true
                        Toast.makeText(context, "Tap an app to add to Home", Toast.LENGTH_SHORT).show()
                    },
                    onLongPress = { app ->
                        inDesktopZone = true
                        deskSelected = desktopApps.indexOf(app).coerceAtLeast(0)
                        input.launch.onOpenMenu(actions.menuFor(app, true))
                    },
                ),
            )
        }
    }

    private val tray: @Composable ColumnScope.(Int, Float) -> Unit = { columns, rowPx ->
        with(state) {
            bb7Tray(
                Bb7TrayView(
                    title = model.trayTitle,
                    apps = if (open || model.searching) shown else shown.take(columns * input.source.homeRows),
                    grid = Bb7GridAppearance(columns, input.visual.iconSize.iconDp, input.visual.showLabels),
                    selection = Bb7GridSelection(if (model.inDesktop) -1 else selected, moving),
                    style =
                        Bb7TrayStyle(
                            open || model.searching,
                            Color.Black.copy(alpha = Bb7Scrim.trayAlpha(input.visual.scrimLevel)),
                            if (open || model.searching) Modifier.weight(1f) else Modifier,
                        ),
                ),
                Bb7TrayEvents(
                    onResize = { dragged ->
                        val rows = Bb7Home.resizeRows(input.source.homeRows, dragged, rowPx)
                        if (rows > Bb7Home.MAX_HOME_ROWS) {
                            open = true
                        } else if (rows != input.source.homeRows) {
                            input.edits.onHomeRowsChanged(rows)
                            selected = selected.coerceAtMost(columns * rows - 1)
                        }
                    },
                    onToggle = {
                        open = !open
                        query = ""
                        selected = if (open) 0 else -1
                        if (!open) pickingDesktop = false
                    },
                    onStep = { step ->
                        query = ""
                        panel = Bb7Home.step(panel, step, input.panel.panels)
                        selected = 0
                    },
                    onLaunch = { if (pickingDesktop) actions.addToDesktop(it) else input.launch.onLaunchApp(it) },
                    onMenu = { input.launch.onOpenMenu(actions.menuFor(shown.getOrNull(selected), false)) },
                    onLongPress = { app ->
                        selected = shown.indexOf(app).coerceAtLeast(0)
                        input.launch.onOpenMenu(actions.menuFor(app, false))
                    },
                ),
            )
        }
    }

    private val referenceBar: @Composable () -> Unit = {
        with(state) {
            Bb7ReferenceBar(
                apps = apps,
                appsOpen = open || model.searching,
                actions =
                    Bb7BarActions(
                        hub = input.launch.onOpenHub,
                        command = input.extra.onOpenCommand,
                        apps = actions::openApps,
                        favorites = {
                            panel = Bb7Panel.Favorites
                            open = false
                            query = ""
                            pickingDesktop = false
                            moving = false
                            selected = -1
                        },
                        launch = { input.launch.onLaunchApp(it) },
                    ),
            )
        }
    }
}

/**
 * The BlackBerry 7 banner: the status dials (as on the BlackBerry 10 home), the date, weather and
 * the Hub unread count, on a scrim so they read over any wallpaper. The status bar shows the time.
 */
private data class Bb7BannerDisplay(
    val dateText: String,
    val enabledDials: Set<String>,
    val statusMode: HomeStatusMode,
    val compact: Boolean,
    val scrim: Color,
)

private data class Bb7BannerNotifications(
    val counts: List<io.github.aimindseye.tenseven.live.AppUnreadCount>,
    val enabledPackages: Set<String>,
    val showStrip: Boolean,
    val expanded: Boolean,
)

private val bb7Banner: @Composable (
    Bb7BannerDisplay,
    Bb7BannerNotifications,
    () -> Unit,
    (String) -> Unit,
) -> Unit = { display, notifications, onOpenHub, onOpenNotificationApp ->
    val dateText = display.dateText
    val enabledDials = display.enabledDials
    val statusMode = display.statusMode
    val compact = display.compact
    val scrim = display.scrim
    val notificationCounts = notifications.counts
    val hubSources = notifications.enabledPackages
    val showStrip = notifications.showStrip
    val stripExpanded = notifications.expanded
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(scrim)
                .padding(horizontal = 12.dp, vertical = if (compact) 4.dp else 8.dp),
    ) {
        val header = rememberHeaderOptions()
        if (!compact && statusMode != HomeStatusMode.Hidden) {
            BbHomeStatus(statusMode, enabledDials, Modifier.fillMaxWidth().padding(vertical = 6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(top = if (compact) 0.dp else 6.dp)) {
                Text(
                    text = dateText,
                    color = Color.White,
                    fontSize = ((if (compact) 16f else 22f) * header.size.multiplier).sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                if (!compact) HomeWeatherLine(header, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.width(6.dp))
        }
        if (showStrip && !compact) {
            HomeNotificationStrip(
                counts = notificationCounts,
                enabledPackages = hubSources,
                expanded = stripExpanded,
                onOpenHub = onOpenHub,
                onOpenApp = onOpenNotificationApp,
            )
        }
    }
}

/** Group the tray, desktop and icon display inputs without adding parameter-heavy helpers. */
private data class Bb7GridAppearance(
    val columns: Int,
    val iconDp: Int,
    val showLabels: Boolean,
)

private data class Bb7GridSelection(
    val selected: Int,
    val moving: Boolean,
)

private data class Bb7IconAppearance(
    val iconDp: Int,
    val showLabel: Boolean,
    val selected: Boolean,
    val moving: Boolean,
)

private data class Bb7TrayStyle(
    val open: Boolean,
    val scrim: Color,
    val modifier: Modifier,
)

private data class Bb7TrayView(
    val title: String,
    val apps: List<AppEntry>,
    val grid: Bb7GridAppearance,
    val selection: Bb7GridSelection,
    val style: Bb7TrayStyle,
)

private data class Bb7TrayEvents(
    val onResize: (Float) -> Unit,
    val onToggle: () -> Unit,
    val onMenu: () -> Unit,
    val onStep: (Int) -> Unit,
    val onLaunch: (AppEntry) -> Unit,
    val onLongPress: (AppEntry) -> Unit,
)

private data class Bb7DesktopView(
    val apps: List<AppEntry>,
    val grid: Bb7GridAppearance,
    val selection: Bb7GridSelection,
    val modifier: Modifier,
)

private data class Bb7DesktopEvents(
    val onLaunch: (AppEntry) -> Unit,
    val onLongPress: (AppEntry) -> Unit,
    val onEmptyTap: () -> Unit,
)

/**
 * The tray: its panel name between arrows, then the home screen's rows of apps (closed) or the
 * whole panel (open). Tapping the name opens or closes it; the arrows switch panels; dragging the
 * closed tray up or down ([onResize], px) shows more or fewer rows. The icon being moved is outlined.
 */
@OptIn(ExperimentalFoundationApi::class)
private val bb7Tray: @Composable (Bb7TrayView, Bb7TrayEvents) -> Unit = { view, events ->
    val grid = rememberLazyGridState()
    LaunchedEffect(view.selection.selected, view.style.open) {
        if (view.style.open && view.selection.selected in view.apps.indices) {
            grid.animateScrollToItem(view.selection.selected)
        }
    }
    Column(
        modifier =
            view.style.modifier
                .fillMaxWidth()
                .background(view.style.scrim)
                .bb7TrayResize(view.style.open, events.onResize)
                .padding(bottom = 6.dp),
    ) {
        bb7TrayHeader(view.title, events)
        if (view.apps.isEmpty()) {
            Text(
                text = t("no_apps_here"),
                color = BANNER_MUTED,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                textAlign = TextAlign.Center,
            )
        } else {
            bb7TrayGrid(view, events, grid)
        }
    }
}

private fun Modifier.bb7TrayResize(
    open: Boolean,
    onResize: (Float) -> Unit,
): Modifier =
    if (open) {
        this
    } else {
        pointerInput(Unit) {
            var dragged = 0f
            detectVerticalDragGestures(
                onDragStart = { dragged = 0f },
                onDragEnd = { onResize(dragged) },
            ) { _, amount -> dragged += amount }
        }
    }

private val bb7TrayHeader: @Composable (String, Bb7TrayEvents) -> Unit = { title, events ->
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).clickable(onClick = events.onToggle).padding(start = 12.dp),
        )
        trayArrow("‹") { events.onStep(-1) }
        trayArrow("›") { events.onStep(1) }
        trayArrow("☰") { events.onMenu() }
    }
}

private val bb7TrayGrid: @Composable ColumnScope.(
    Bb7TrayView,
    Bb7TrayEvents,
    androidx.compose.foundation.lazy.grid.LazyGridState,
) -> Unit = { view, events, grid ->
    LazyVerticalGrid(
        columns = GridCells.Fixed(view.grid.columns),
        state = grid,
        userScrollEnabled = view.style.open,
        modifier = if (view.style.open) Modifier.weight(1f) else Modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(
            view.apps,
            key = { _, app -> app.component.flattenToString() + app.user.hashCode() },
        ) { index, app ->
            bb7IconCell(
                app,
                Bb7IconAppearance(
                    view.grid.iconDp,
                    view.grid.showLabels,
                    index == view.selection.selected,
                    view.selection.moving,
                ),
                { events.onLaunch(app) },
                { events.onLongPress(app) },
            )
        }
    }
}

/** One icon with its name, the highlight when selected (white outline while it is being moved). */
@OptIn(ExperimentalFoundationApi::class)
private val bb7IconCell: @Composable (AppEntry, Bb7IconAppearance, () -> Unit, () -> Unit) -> Unit = {
    app,
    display,
    onLaunch,
    onLongPress,
    ->
    val outline = if (display.moving && display.selected) Color.White else MaterialTheme.colorScheme.primary
    Column(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (display.selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    } else {
                        Color.Transparent
                    },
                ).border(
                    if (display.selected) 2.dp else 0.dp,
                    if (display.selected) outline else Color.Transparent,
                    RoundedCornerShape(8.dp),
                ).combinedClickable(onClick = onLaunch, onLongClick = onLongPress)
                .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppMark(app = app, size = display.iconDp)
        if (display.showLabel) Spacer(Modifier.height(2.dp))
        if (display.showLabel) {
            Text(
                text = app.label,
                color = Color.White,
                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                // The wallpaper shows behind these icons, so the name gets a shadow to stay readable.
                style = TextStyle(shadow = Shadow(Color.Black, blurRadius = 6f)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

/** The icons the user put in the empty space between the banner and the tray (Add to home screen). */
private val bb7Desktop: @Composable (Bb7DesktopView, Bb7DesktopEvents) -> Unit = bb7DesktopLambda@{ view, events ->
    if (view.apps.isEmpty()) {
        Box(
            view.modifier.fillMaxWidth().clickable(onClick = events.onEmptyTap),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "+ Add shortcuts",
                color = Color.White.copy(alpha = 0.65f),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        return@bb7DesktopLambda
    }
    val grid = rememberLazyGridState()
    LaunchedEffect(view.selection.selected) {
        if (view.selection.selected in view.apps.indices) grid.animateScrollToItem(view.selection.selected)
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(view.grid.columns),
        state = grid,
        modifier =
            view.modifier
                .fillMaxWidth()
                .clickable(onClick = events.onEmptyTap)
                .padding(horizontal = 4.dp, vertical = 6.dp),
        userScrollEnabled = false,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(
            view.apps,
            key = { _, app -> app.component.flattenToString() + app.user.hashCode() },
        ) { index, app ->
            bb7IconCell(
                app,
                Bb7IconAppearance(
                    view.grid.iconDp,
                    view.grid.showLabels,
                    index == view.selection.selected,
                    view.selection.moving,
                ),
                { events.onLaunch(app) },
                { events.onLongPress(app) },
            )
        }
    }
}

private val trayArrow: @Composable (String, () -> Unit) -> Unit = { mark, onClick ->
    Box(
        modifier = Modifier.width(48.dp).height(48.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = mark, color = Color.White, fontSize = 22.sp)
    }
}

private const val DATE_TICK_MS = 60_000L
private const val SWIPE_DP = 72

/** A tray row's height, the drag that shows one more or one fewer row. */
private const val ROW_DP = 64
private val BANNER_MUTED = Color(0xFFC9D1DA)

@Composable
private fun bb7Columns(
    chosen: Int,
    widthDp: Int,
    cellDp: Int,
): Int {
    val normal = GridColumns.resolve(chosen, widthDp, Bb7Home.columns(widthDp, cellDp))
    return normal.coerceAtMost(
        io.github.aimindseye.tenseven.model.HomeViewport
            .columns(widthDp, chosen, LocalDensity.current.fontScale),
    )
}

private fun showHomeAdded(
    context: Context,
    message: String,
) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

@Composable
private fun rememberBb7Live(
    repository: LiveSurfaceRepository,
    generation: Int,
): io.github.aimindseye.tenseven.live.LiveSurfaceSnapshot {
    val snapshot by produceState(repository.initialSnapshot(), generation) { value = repository.snapshot() }
    return snapshot
}

@Composable
private fun rememberBb7Date(generation: Int): Long {
    val today by produceState(System.currentTimeMillis(), generation) {
        while (true) {
            value = System.currentTimeMillis()
            delay(DATE_TICK_MS - value % DATE_TICK_MS)
        }
    }
    return today
}

private data class Bb7AppSelection(
    val panel: Bb7Panel,
    val query: String,
    val favorites: List<String>?,
    val desktop: List<String>,
)

private data class Bb7AppLists(
    val shown: List<AppEntry>,
    val favorites: List<String>,
    val desktop: List<AppEntry>,
)

@Composable
private fun rememberBb7Apps(
    apps: List<AppEntry>,
    usage: io.github.aimindseye.tenseven.model.AppUsage,
    selection: Bb7AppSelection,
): Bb7AppLists {
    val context = LocalContext.current
    val defaults = remember(context, apps.size) { DefaultApps.homePackages(context) }
    val shown =
        if (selection.query.isNotEmpty()) {
            Bb7Home.search(apps, selection.query)
        } else {
            Bb7Home.panelApps(selection.panel, apps, selection.favorites, usage, defaults)
        }
    val favorites = Bb7Home.panelApps(Bb7Panel.Favorites, apps, selection.favorites, defaultPackages = defaults)
    val byKey = remember(apps) { apps.associateBy { it.stableKey() } }
    val desktop = Bb7Home.desktopShortcuts(selection.desktop).mapNotNull { byKey[it] }
    return Bb7AppLists(shown, favorites.map { it.stableKey() }, desktop)
}

/** Validate a desktop addition before the screen changes its browsing state. */
private fun addBb7Desktop(
    context: android.content.Context,
    current: List<String>,
    key: String,
    onAdded: (List<String>) -> Unit,
) {
    val next = Bb7Home.addDesktopShortcut(current, key)
    when {
        key in current -> {
            Toast.makeText(context, "Already on Home", Toast.LENGTH_SHORT).show()
        }

        key !in next -> {
            Toast
                .makeText(
                    context,
                    "Home holds at most ${Bb7Home.MAX_DESKTOP_SHORTCUTS} shortcuts",
                    Toast.LENGTH_LONG,
                ).show()
        }

        else -> {
            onAdded(next)
        }
    }
}
