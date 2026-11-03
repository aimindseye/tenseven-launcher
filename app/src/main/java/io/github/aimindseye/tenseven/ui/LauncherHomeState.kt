package io.github.aimindseye.tenseven.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.model.Bb7Detour
import io.github.aimindseye.tenseven.model.Bb7IconSize
import io.github.aimindseye.tenseven.model.Bb7Panels
import io.github.aimindseye.tenseven.model.Bb7Scrim
import io.github.aimindseye.tenseven.model.GlanceWidgets
import io.github.aimindseye.tenseven.model.GridColumns
import io.github.aimindseye.tenseven.model.HomeStyle
import io.github.aimindseye.tenseven.platform.StartStateRepository
import io.github.aimindseye.tenseven.platform.WidgetHostController
import io.github.aimindseye.tenseven.platform.WidgetTarget
import io.github.aimindseye.tenseven.runtime.LauncherRequest

/** Compose presentation state; Android registrations and inventory work live in LauncherRuntime. */
internal class LauncherHomeState {
    val appsState = mutableStateOf<List<AppEntry>>(emptyList())

    /** null: no private space; true: locked; false: unlocked. */
    val privateSpaceLockedState = mutableStateOf<Boolean?>(null)
    val workPausedState = mutableStateOf<Boolean?>(null)
    val doubleTapLockState = mutableStateOf(false)
    val liveRefreshGeneration = mutableIntStateOf(0)
    val homeGeneration = mutableIntStateOf(0)
    val backGeneration = mutableIntStateOf(0)

    /** "Show the Hub page" requests (ACTION_SHOW_HUB) and the tab each asked for. */
    val hubGeneration = mutableIntStateOf(0)
    val hubRequestFilter = mutableStateOf<String?>(null)

    /** Screen asked for by Settings (EDIT_HOME, EDIT_QUICK_BAR), and a serial so a repeat opens it again. */
    val startRequest = mutableStateOf(LauncherRequest())

    /**
     * The home style chosen in Sable Setup (BlackBerry 10 or 7). The BlackBerry 7 home has no Hub,
     * Active Frames or Edit Home of its own, so those requests show the BlackBerry 10 screen
     * ([bb10Detour]) until Home is pressed again.
     */
    val homeStyle = mutableStateOf(HomeStyle.DEFAULT)
    val bb10Detour = mutableStateOf(false)
    var bb7Detour = Bb7Detour()

    /** The BlackBerry 7 home's favourites (the Home apps) and its rows of icons, read in onCreate. */
    val bb7Favorites = mutableStateOf<List<String>?>(null)
    val bb7HomeRows = mutableIntStateOf(1)
    val bb7Desktop = mutableStateOf<List<String>>(emptyList())
    val bb7Panels = mutableStateOf(Bb7Panels.DEFAULT)
    val bb7Scrim = mutableIntStateOf(Bb7Scrim.DEFAULT)
    val bb7IconSize = mutableStateOf(Bb7IconSize.DEFAULT)
    val typeToSearch = mutableStateOf(true)
    val gridColumns = mutableIntStateOf(GridColumns.AUTO)
    val gridLabels = mutableStateOf(true)
    val widgetsPageEnabled = mutableStateOf(true)
    val widgetIds = mutableStateOf<List<Int>>(emptyList())
    val widgetsGeneration = mutableIntStateOf(0)
    val homeWidgetIds = mutableStateOf<List<Int>>(emptyList())
    val homeWallpaper = mutableStateOf(true)
    val homeWidgets = mutableStateOf(GlanceWidgets.DEFAULT)
    var canNavigateBack = false

    fun reload(
        store: StartStateRepository,
        widgets: WidgetHostController,
    ) {
        homeStyle.value = store.loadHomeStyle()
        doubleTapLockState.value = store.loadDoubleTapLock()
        bb7Favorites.value = store.loadHomeApps()
        bb7HomeRows.intValue = store.loadBb7HomeRows()
        bb7Panels.value = store.loadBb7Panels()
        bb7Desktop.value = store.loadBb7Desktop()
        bb7Scrim.intValue = store.loadBb7Scrim()
        bb7IconSize.value = store.loadBb7IconSize()
        typeToSearch.value = store.loadTypeToSearch()
        gridColumns.intValue = store.loadGridColumns()
        gridLabels.value = store.loadGridLabels()
        widgetsPageEnabled.value = store.loadWidgetsPage()
        widgetIds.value = widgets.load()
        homeWidgetIds.value = widgets.load(WidgetTarget.HOME)
        homeWallpaper.value = store.loadHomeWallpaper()
        homeWidgets.value = GlanceWidgets.normalize(store.loadGlanceWidgets())
    }
}
