package io.github.aimindseye.tenseven.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import io.github.aimindseye.tenseven.design.SableAppearance
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.model.Bb7IconSize
import io.github.aimindseye.tenseven.model.GridColumns
import io.github.aimindseye.tenseven.model.HubFilter
import io.github.aimindseye.tenseven.platform.LetterShortcutRepository
import io.github.aimindseye.tenseven.platform.LiveSurfaceRepository
import io.github.aimindseye.tenseven.platform.SpeedDialRepository
import io.github.aimindseye.tenseven.platform.WidgetHostController

@Composable
fun SableStartScreen(
    apps: List<AppEntry>,
    liveSurfaceRepository: LiveSurfaceRepository,
    appearance: SableAppearance = SableAppearance(),
    refreshGeneration: Int = 0,
    homeGeneration: Int = 0,
    backGeneration: Int = 0,
    initialScreen: String = "start",
    onCanNavigateBackChanged: (Boolean) -> Unit = {},
    /** Back from the Hub or an app page is about to slide to the home page. */
    onBackToHomePage: () -> Unit = {},
    /** A visit from the BlackBerry 7 home: its Home page stays empty so the BlackBerry 10 cards never flash. */
    blankHomePage: Boolean = false,
    /** Start tiles chosen before Home became an ordered list; seeds the grid order with [initialHomeApps]. */
    initialStartTileKeys: Set<String>? = null,
    /** The three-page Home apps; they lead the app grid until the user orders it. */
    initialHomeApps: List<String>? = null,
    initialGridOrder: List<String>? = null,
    onGridOrderChanged: (List<String>?) -> Unit = {},
    initialQuickBar: List<String>? = null,
    onQuickBarChanged: (List<String>?) -> Unit = {},
    initialGlanceWidgets: List<String>? = null,
    /** Launcher Settings: whether typing on Home starts a search. */
    typeToSearch: Boolean = true,
    widgetsPage: Boolean = false,
    widgetsController: WidgetHostController? = null,
    widgetIds: List<Int> = emptyList(),
    homeWidgetIds: List<Int> = emptyList(),
    wallpaperBehind: Boolean = false,
    widgetsGeneration: Int = 0,
    showIconLabels: Boolean = true,
    gridColumnsSetting: Int = GridColumns.AUTO,
    gridIconSize: Bb7IconSize = Bb7IconSize.DEFAULT,
    onGlanceWidgetsChanged: (List<String>?) -> Unit = {},
    initialHiddenHomeSections: Set<String>? = null,
    onHiddenHomeSectionsChanged: (Set<String>?) -> Unit = {},
    framesState: io.github.aimindseye.tenseven.frames.FramesState =
        io.github.aimindseye.tenseven.frames
            .FramesState(),
    frameSnapshot: (io.github.aimindseye.tenseven.frames.FrameItem, Long) -> Bitmap? = { _, _ -> null },
    onResumeFrame: (io.github.aimindseye.tenseven.frames.FrameItem) -> Unit = {},
    onCloseFrame: (io.github.aimindseye.tenseven.frames.FrameItem) -> Unit = {},
    onAllowUsageAccess: () -> Unit = {},
    onAppearanceChanged: (SableAppearance) -> Unit = {},
    onResetAppearance: () -> Unit = {},
    onLaunchApp: (AppEntry) -> Boolean,
    onOpenSystemSettings: () -> Unit,
    onOpenAppInfo: (AppEntry) -> Unit,
    onRequestLivePermissions: () -> Unit,
    onRefreshLive: () -> Unit,
    onOpenNotificationSettings: (AppEntry) -> Unit = onOpenAppInfo,
    onUninstallApp: (AppEntry) -> Unit = {},
    onStartIntent: (Intent) -> Unit = {},
    /** null: no private space on this device; true: locked; false: unlocked. */
    privateSpaceLocked: Boolean? = null,
    onTogglePrivateSpace: () -> Unit = {},
    workProfilePaused: Boolean? = null,
    onToggleWorkProfile: () -> Unit = {},
    onDoubleTapEmpty: (() -> Unit)? = null,
    /** Counts up for each "show the Hub page" request (org.sableos.start.action.SHOW_HUB). */
    hubGeneration: Int = 0,
    /** The tab that request asked for ([HubFilter] by name), or null for All. */
    hubRequestFilter: String? = null,
    /** The Contacts tab asks for READ_CONTACTS; the activity bumps [refreshGeneration] after. */
    onRequestContactsPermission: () -> Unit = {},
    /** Phone's speed dial: holding an assigned letter on Home calls it (null: no speed dial). */
    speedDial: SpeedDialRepository? = null,
    /** The apps held on a letter on Home; Peek ("More actions") sets and clears them. */
    letterShortcuts: LetterShortcutRepository? = null,
) {
    StartScreenInputs(
        apps = apps,
        liveSurfaceRepository = liveSurfaceRepository,
        appearance = appearance,
        refreshGeneration = refreshGeneration,
        homeGeneration = homeGeneration,
        backGeneration = backGeneration,
        initialScreen = initialScreen,
        onCanNavigateBackChanged = onCanNavigateBackChanged,
        onBackToHomePage = onBackToHomePage,
        blankHomePage = blankHomePage,
        initialStartTileKeys = initialStartTileKeys,
        initialHomeApps = initialHomeApps,
        initialGridOrder = initialGridOrder,
        onGridOrderChanged = onGridOrderChanged,
        initialQuickBar = initialQuickBar,
        onQuickBarChanged = onQuickBarChanged,
        initialGlanceWidgets = initialGlanceWidgets,
        typeToSearch = typeToSearch,
        widgetsPage = widgetsPage,
        widgetsController = widgetsController,
        widgetIds = widgetIds,
        homeWidgetIds = homeWidgetIds,
        wallpaperBehind = wallpaperBehind,
        widgetsGeneration = widgetsGeneration,
        showIconLabels = showIconLabels,
        gridColumnsSetting = gridColumnsSetting,
        gridIconSize = gridIconSize,
        onGlanceWidgetsChanged = onGlanceWidgetsChanged,
        initialHiddenHomeSections = initialHiddenHomeSections,
        onHiddenHomeSectionsChanged = onHiddenHomeSectionsChanged,
        framesState = framesState,
        frameSnapshot = frameSnapshot,
        onResumeFrame = onResumeFrame,
        onCloseFrame = onCloseFrame,
        onAllowUsageAccess = onAllowUsageAccess,
        onAppearanceChanged = onAppearanceChanged,
        onResetAppearance = onResetAppearance,
        onLaunchApp = onLaunchApp,
        onOpenSystemSettings = onOpenSystemSettings,
        onOpenAppInfo = onOpenAppInfo,
        onRequestLivePermissions = onRequestLivePermissions,
        onRefreshLive = onRefreshLive,
        onOpenNotificationSettings = onOpenNotificationSettings,
        onUninstallApp = onUninstallApp,
        onStartIntent = onStartIntent,
        privateSpaceLocked = privateSpaceLocked,
        onTogglePrivateSpace = onTogglePrivateSpace,
        workProfilePaused = workProfilePaused,
        onToggleWorkProfile = onToggleWorkProfile,
        onDoubleTapEmpty = onDoubleTapEmpty,
        hubGeneration = hubGeneration,
        hubRequestFilter = hubRequestFilter,
        onRequestContactsPermission = onRequestContactsPermission,
        speedDial = speedDial,
        letterShortcuts = letterShortcuts,
    ).RenderStartScreen()
}
