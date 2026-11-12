package io.github.aimindseye.tenseven.platform

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.aimindseye.tenseven.model.BackupValue
import io.github.aimindseye.tenseven.model.Bb7Home
import io.github.aimindseye.tenseven.model.Bb7IconSize
import io.github.aimindseye.tenseven.model.Bb7Panel
import io.github.aimindseye.tenseven.model.Bb7Panels
import io.github.aimindseye.tenseven.model.Bb7Scrim
import io.github.aimindseye.tenseven.model.CustomTheme
import io.github.aimindseye.tenseven.model.CustomThemes
import io.github.aimindseye.tenseven.model.GridColumns
import io.github.aimindseye.tenseven.model.HomeDisplayPolicy
import io.github.aimindseye.tenseven.model.HomeStyle
import io.github.aimindseye.tenseven.model.LauncherBackup
import io.github.aimindseye.tenseven.model.ThemePresets
import io.github.aimindseye.tenseven.model.WallpaperSets
import io.github.aimindseye.tenseven.model.WidgetList

/**
 * Stores launcher-only presentation state.
 *
 * Global Sable appearance is intentionally NOT stored here. Appearance is
 * owned by the privileged Settings-hosted org.sableos.appearance provider so
 * Launcher3 cannot become the operating system's theme authority.
 *
 * Recent activity intentionally stays in-memory so Sable Start does not build
 * a persistent app-usage history.
 */
class StartStateRepository private constructor(
    private val shared: StartStateBacking,
) : StartFramesState by StartFramesStateImpl(shared),
    StartFavoritesState by StartFavoritesStateImpl(shared),
    StartBb7State by StartBb7StateImpl(shared),
    StartHomeLayoutState by StartHomeLayoutStateImpl(shared),
    StartGeneralState by StartGeneralStateImpl(shared),
    StartWeatherState by StartWeatherStateImpl(shared),
    StartIconsState by StartIconsStateImpl(shared),
    StartStatusState by StartStatusStateImpl(shared),
    StartWallpaperState by StartWallpaperStateImpl(shared),
    StartHubState by StartHubStateImpl(shared),
    StartAppearanceState by StartAppearanceStateImpl(shared),
    StartWidgetsState by StartWidgetsStateImpl(shared),
    StartBackupState by StartBackupStateImpl(shared) {
    constructor(context: Context) : this(StartStateBacking(context))

    private val preferences: SharedPreferences get() = shared.preferences

    /** Per-user Hub Compose preference. Android remains the authority for the chosen default app. */
    internal fun loadHubComposeMode(): io.github.aimindseye.tenseven.model.HubComposeMode =
        io.github.aimindseye.tenseven.model.HubComposeMode.decode(
            preferences.getString("hub_compose_mode_2", null),
        )

    internal fun saveHubComposeMode(mode: io.github.aimindseye.tenseven.model.HubComposeMode) {
        preferences.edit { putString("hub_compose_mode_2", mode.key) }
    }

    /** D4r3: the Home duplicate notification shelf is retired. Hub remains the entry point.
     * The old stored preference is deliberately not erased so upgrades and backups remain compatible.
     */

    companion object {
        const val PREFERENCES_NAME = "sable_start_state"
        const val KEY_FAVORITE_APPS = "favorite_apps"
        const val KEY_START_TILES = "start_tiles"
        const val KEY_PINNED_APPS_LEGACY = "pinned_apps"
        const val KEY_QUICK_BAR = "quick_bar"
        const val QUICK_BAR_SEPARATOR = "\n"
        const val KEY_HOME_APPS = "home_apps"
        const val KEY_BB7_HOME_ROWS = "bb7_home_rows"
        const val KEY_BB7_PANELS = "bb7_panels"
        const val KEY_BB7_SCRIM = "bb7_scrim"
        const val KEY_HOME_DIALS_BB7 = "home_dials_bb7"
        const val KEY_HOME_DIALS_BB10 = "home_dials_bb10"
        const val KEY_HOME_NOTIFICATION_STRIP_BB7 = "home_notification_strip_bb7"
        const val KEY_HOME_NOTIFICATION_STRIP_BB10 = "home_notification_strip_bb10"
        const val KEY_HOME_NOTIFICATION_EXPANDED_BB7 = "home_notification_expanded_bb7"
        const val KEY_HOME_NOTIFICATION_EXPANDED_BB10 = "home_notification_expanded_bb10"
        const val KEY_TYPE_TO_SEARCH = "type_to_search"
        const val KEY_BB7_ICON_SIZE = "bb7_icon_size"
        const val KEY_WIDGETS_PAGE = "widgets_page"
        const val KEY_BB7_DESKTOP = "bb7_desktop"
        const val KEY_GRID_COLUMNS = "grid_columns"
        const val KEY_GRID_LABELS = "grid_labels"
        const val KEY_LOCK_LAYER = "lock_layer"
        const val KEY_HOME_WALLPAPER = "home_wallpaper"
        const val KEY_WALLPAPER_SET = "wallpaper_set"
        const val KEY_HUB_EXCLUDED = "hub_excluded_apps" // legacy backup compatibility; never used as consent
        const val KEY_HUB_ENABLED = "hub_enabled_apps"
        const val KEY_HUB_PEEK_TEXT = "hub_peek_text"
        const val KEY_HUB_CALENDAR_PEEK = "hub_calendar_peek"
        const val KEY_HUB_SELECTED_CALENDARS = "hub_selected_calendar_ids_device"
        const val KEY_HOME_WIDGET_IDS = "home_widget_ids"
        const val KEY_APPEARANCE_MODE = "appearance_mode"
        const val KEY_APPEARANCE_ACCENT = "appearance_accent"
        const val KEY_APPEARANCE_CUSTOM = "appearance_custom"
        const val KEY_CUSTOM_THEMES = "custom_themes"
        const val KEY_WIDGET_IDS = "widget_ids"
        const val KEY_GLANCE_WIDGETS = "glance_widgets"

        // v2: the BB10 Home shows the status dials by default; sets saved by the
        // three-page Home hid them without the user choosing, so they are not read.
        const val KEY_HOME_HIDDEN_SECTIONS = "home_hidden_sections_v2"
        const val KEY_HOME_MODE = "home_mode"
        const val KEY_HOME_STYLE = "home_style"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_ICON_PACK = "icon_pack"
        const val KEY_ICON_PRESENTATION = "icon_presentation"
        const val KEY_DOUBLE_TAP_LOCK = "double_tap_lock"
        const val KEY_GRID_ORDER = "grid_order"
    }
}
