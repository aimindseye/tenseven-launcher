package io.github.aimindseye.tenseven.ui

import android.content.Intent
import android.graphics.Bitmap
import io.github.aimindseye.tenseven.design.SableAppearance
import io.github.aimindseye.tenseven.frames.FrameItem
import io.github.aimindseye.tenseven.frames.FramesState
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.model.Bb7IconSize
import io.github.aimindseye.tenseven.model.GridColumns
import io.github.aimindseye.tenseven.model.HubFilter
import io.github.aimindseye.tenseven.platform.LetterShortcutRepository
import io.github.aimindseye.tenseven.platform.LiveSurfaceRepository
import io.github.aimindseye.tenseven.platform.SpeedDialRepository
import io.github.aimindseye.tenseven.platform.WidgetHostController

/**
 * Immutable host contract for the existing SableStartScreen API.
 * Defaults match that API; production passes every value explicitly.
 * This object is never a remember/effect key or a persistence record.
 */
internal data class StartScreenInputs(
    val apps: List<AppEntry>,
    val liveSurfaceRepository: LiveSurfaceRepository,
    val appearance: SableAppearance = SableAppearance(),
    val refreshGeneration: Int = 0,
    val homeGeneration: Int = 0,
    val backGeneration: Int = 0,
    val initialScreen: String = "start",
    val onCanNavigateBackChanged: (Boolean) -> Unit = {},
    /** Back from the Hub or an app page is about to slide to the home page. */
    val onBackToHomePage: () -> Unit = {},
    /**
     * A visit from the BlackBerry 7 home: its Home page stays empty so the BlackBerry 10 cards
     * never flash.
     */
    val blankHomePage: Boolean = false,
    /** Start tiles chosen before Home became an ordered list; seeds the grid order with [initialHomeApps]. */
    val initialStartTileKeys: Set<String>? = null,
    /** The three-page Home apps; they lead the app grid until the user orders it. */
    val initialHomeApps: List<String>? = null,
    val initialGridOrder: List<String>? = null,
    val onGridOrderChanged: (List<String>?) -> Unit = {},
    val initialQuickBar: List<String>? = null,
    val onQuickBarChanged: (List<String>?) -> Unit = {},
    val initialGlanceWidgets: List<String>? = null,
    /** Launcher Settings: whether typing on Home starts a search. */
    val typeToSearch: Boolean = true,
    val widgetsPage: Boolean = false,
    val widgetsController: WidgetHostController? = null,
    val widgetIds: List<Int> = emptyList(),
    val homeWidgetIds: List<Int> = emptyList(),
    val wallpaperBehind: Boolean = false,
    val widgetsGeneration: Int = 0,
    val showIconLabels: Boolean = true,
    val gridColumnsSetting: Int = GridColumns.AUTO,
    val gridIconSize: Bb7IconSize = Bb7IconSize.DEFAULT,
    val onGlanceWidgetsChanged: (List<String>?) -> Unit = {},
    val initialHiddenHomeSections: Set<String>? = null,
    val onHiddenHomeSectionsChanged: (Set<String>?) -> Unit = {},
    val framesState: FramesState =
        FramesState(),
    val frameSnapshot: (FrameItem, Long) -> Bitmap? = { _, _ -> null },
    val onResumeFrame: (FrameItem) -> Unit = {},
    val onCloseFrame: (FrameItem) -> Unit = {},
    val onAllowUsageAccess: () -> Unit = {},
    val onAppearanceChanged: (SableAppearance) -> Unit = {},
    val onResetAppearance: () -> Unit = {},
    val onLaunchApp: (AppEntry) -> Boolean,
    val onOpenSystemSettings: () -> Unit,
    val onOpenAppInfo: (AppEntry) -> Unit,
    val onRequestLivePermissions: () -> Unit,
    val onRefreshLive: () -> Unit,
    val onOpenNotificationSettings: (AppEntry) -> Unit = onOpenAppInfo,
    val onUninstallApp: (AppEntry) -> Unit = {},
    val onStartIntent: (Intent) -> Unit = {},
    /** null: no private space on this device; true: locked; false: unlocked. */
    val privateSpaceLocked: Boolean? = null,
    val onTogglePrivateSpace: () -> Unit = {},
    val workProfilePaused: Boolean? = null,
    val onToggleWorkProfile: () -> Unit = {},
    val onDoubleTapEmpty: (() -> Unit)? = null,
    /** Counts up for each "show the Hub page" request (org.sableos.start.action.SHOW_HUB). */
    val hubGeneration: Int = 0,
    /** The tab that request asked for ([HubFilter] by name), or null for All. */
    val hubRequestFilter: String? = null,
    /** The Contacts tab asks for READ_CONTACTS; the activity bumps [refreshGeneration] after. */
    val onRequestContactsPermission: () -> Unit = {},
    /** Phone's speed dial: holding an assigned letter on Home calls it (null: no speed dial). */
    val speedDial: SpeedDialRepository? = null,
    /** The apps held on a letter on Home; Peek ("More actions") sets and clears them. */
    val letterShortcuts: LetterShortcutRepository? = null,
)
