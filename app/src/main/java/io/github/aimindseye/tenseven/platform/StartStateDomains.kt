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

// Shared key aliases preserve the original on-device preference names byte-for-byte.
private const val KEY_APPEARANCE_ACCENT = StartStateRepository.KEY_APPEARANCE_ACCENT
private const val KEY_APPEARANCE_CUSTOM = StartStateRepository.KEY_APPEARANCE_CUSTOM
private const val KEY_APPEARANCE_MODE = StartStateRepository.KEY_APPEARANCE_MODE
private const val KEY_BB7_DESKTOP = StartStateRepository.KEY_BB7_DESKTOP
private const val KEY_BB7_HOME_ROWS = StartStateRepository.KEY_BB7_HOME_ROWS
private const val KEY_BB7_ICON_SIZE = StartStateRepository.KEY_BB7_ICON_SIZE
private const val KEY_BB7_PANELS = StartStateRepository.KEY_BB7_PANELS
private const val KEY_BB7_SCRIM = StartStateRepository.KEY_BB7_SCRIM
private const val KEY_CUSTOM_THEMES = StartStateRepository.KEY_CUSTOM_THEMES
private const val KEY_DOUBLE_TAP_LOCK = StartStateRepository.KEY_DOUBLE_TAP_LOCK
private const val KEY_FAVORITE_APPS = StartStateRepository.KEY_FAVORITE_APPS
private const val KEY_GLANCE_WIDGETS = StartStateRepository.KEY_GLANCE_WIDGETS
private const val KEY_GRID_COLUMNS = StartStateRepository.KEY_GRID_COLUMNS
private const val KEY_GRID_LABELS = StartStateRepository.KEY_GRID_LABELS
private const val KEY_GRID_ORDER = StartStateRepository.KEY_GRID_ORDER
private const val KEY_HOME_APPS = StartStateRepository.KEY_HOME_APPS
private const val KEY_HOME_DIALS_BB10 = StartStateRepository.KEY_HOME_DIALS_BB10
private const val KEY_HOME_DIALS_BB7 = StartStateRepository.KEY_HOME_DIALS_BB7
private const val KEY_HOME_HIDDEN_SECTIONS = StartStateRepository.KEY_HOME_HIDDEN_SECTIONS
private const val KEY_HOME_MODE = StartStateRepository.KEY_HOME_MODE
private const val KEY_HOME_NOTIFICATION_EXPANDED_BB10 = StartStateRepository.KEY_HOME_NOTIFICATION_EXPANDED_BB10
private const val KEY_HOME_NOTIFICATION_EXPANDED_BB7 = StartStateRepository.KEY_HOME_NOTIFICATION_EXPANDED_BB7
private const val KEY_HOME_NOTIFICATION_STRIP_BB10 = StartStateRepository.KEY_HOME_NOTIFICATION_STRIP_BB10
private const val KEY_HOME_NOTIFICATION_STRIP_BB7 = StartStateRepository.KEY_HOME_NOTIFICATION_STRIP_BB7
private const val KEY_HOME_STYLE = StartStateRepository.KEY_HOME_STYLE
private const val KEY_HOME_WALLPAPER = StartStateRepository.KEY_HOME_WALLPAPER
private const val KEY_HOME_WIDGET_IDS = StartStateRepository.KEY_HOME_WIDGET_IDS
private const val KEY_HUB_CALENDAR_PEEK = StartStateRepository.KEY_HUB_CALENDAR_PEEK
private const val KEY_HUB_ENABLED = StartStateRepository.KEY_HUB_ENABLED
private const val KEY_HUB_EXCLUDED = StartStateRepository.KEY_HUB_EXCLUDED
private const val KEY_HUB_PEEK_TEXT = StartStateRepository.KEY_HUB_PEEK_TEXT
private const val KEY_HUB_SELECTED_CALENDARS = StartStateRepository.KEY_HUB_SELECTED_CALENDARS
private const val KEY_ICON_PACK = StartStateRepository.KEY_ICON_PACK
private const val KEY_ICON_PRESENTATION = StartStateRepository.KEY_ICON_PRESENTATION
private const val KEY_LOCK_LAYER = StartStateRepository.KEY_LOCK_LAYER
private const val KEY_ONBOARDING_DONE = StartStateRepository.KEY_ONBOARDING_DONE
private const val KEY_PINNED_APPS_LEGACY = StartStateRepository.KEY_PINNED_APPS_LEGACY
private const val KEY_QUICK_BAR = StartStateRepository.KEY_QUICK_BAR
private const val KEY_START_TILES = StartStateRepository.KEY_START_TILES
private const val KEY_TYPE_TO_SEARCH = StartStateRepository.KEY_TYPE_TO_SEARCH
private const val KEY_WALLPAPER_SET = StartStateRepository.KEY_WALLPAPER_SET
private const val KEY_WIDGETS_PAGE = StartStateRepository.KEY_WIDGETS_PAGE
private const val KEY_WIDGET_IDS = StartStateRepository.KEY_WIDGET_IDS
private const val QUICK_BAR_SEPARATOR = StartStateRepository.QUICK_BAR_SEPARATOR
private const val HOME_WEATHER_CITY_INPUT_LIMIT = 90
private const val HOME_NOTIFICATION_STRIP_ENABLED = false

/** One SharedPreferences instance shared by all preference domains. */
internal class StartStateBacking(
    context: Context,
) {
    val hubSources = HubSourceRepository(context)
    val preferences =
        context.applicationContext.getSharedPreferences(
            StartStateRepository.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    fun loadOrdered(key: String): List<String>? =
        preferences
            .getString(key, null)
            ?.split(QUICK_BAR_SEPARATOR)
            ?.filter { it.isNotEmpty() }

    fun saveOrdered(
        key: String,
        values: List<String>?,
    ) {
        preferences
            .edit()
            .apply {
                if (values == null) {
                    remove(key)
                } else {
                    putString(key, values.joinToString(QUICK_BAR_SEPARATOR))
                }
            }.apply()
    }
}

/** Launcher preference API: frames domain. */
interface StartFramesState {
    fun loadFrameSettings(): io.github.aimindseye.tenseven.frames.FrameSettings

    fun saveFrameSettings(value: io.github.aimindseye.tenseven.frames.FrameSettings)

    fun loadFrameDismissals(): Map<String, Long>

    fun saveFrameDismissals(value: Map<String, Long>)
}

internal class StartFramesStateImpl(
    private val shared: StartStateBacking,
) : StartFramesState {
    private val preferences: SharedPreferences get() = shared.preferences

    override fun loadFrameSettings(): io.github.aimindseye.tenseven.frames.FrameSettings =
        io.github.aimindseye.tenseven.frames
            .FrameSettings(
                liveSummaries = preferences.getBoolean("frames_live", true),
                newMessages = preferences.getBoolean("frames_messages", true),
                snapshots = preferences.getBoolean("frames_snapshots", true),
                openApps = preferences.getBoolean("frames_open", true),
                rememberReturnedApps = preferences.getBoolean("frames_recents", true),
                maximum = preferences.getInt("frames_maximum", 8),
            ).normalized()

    override fun saveFrameSettings(value: io.github.aimindseye.tenseven.frames.FrameSettings) {
        val settings = value.normalized()
        preferences.edit {
            putBoolean("frames_live", settings.liveSummaries)
            putBoolean("frames_messages", settings.newMessages)
            putBoolean("frames_snapshots", settings.snapshots)
            putBoolean("frames_open", settings.openApps)
            putBoolean("frames_recents", settings.rememberReturnedApps)
            putInt("frames_maximum", settings.maximum)
        }
    }

    override fun loadFrameDismissals(): Map<String, Long> =
        io.github.aimindseye.tenseven.frames.FrameDismissals.decode(
            preferences.getString("frames_dismissed", null),
        )

    override fun saveFrameDismissals(value: Map<String, Long>) {
        preferences.edit {
            putString(
                "frames_dismissed",
                io.github.aimindseye.tenseven.frames.FrameDismissals
                    .encode(value),
            )
        }
    }
}

/** Launcher preference API: favorites domain. */
interface StartFavoritesState {
    fun loadFavoriteKeys(): Set<String>

    fun saveFavoriteKeys(keys: Set<String>)

    fun loadStartTileKeys(): Set<String>?

    fun saveStartTileKeys(keys: Set<String>)

    fun loadQuickBar(): List<String>?

    fun saveQuickBar(tokens: List<String>?)
}

internal class StartFavoritesStateImpl(
    private val shared: StartStateBacking,
) : StartFavoritesState {
    private val preferences: SharedPreferences get() = shared.preferences

    override fun loadFavoriteKeys(): Set<String> {
        val explicit =
            preferences.getStringSet(
                KEY_FAVORITE_APPS,
                null,
            )
        if (explicit != null) {
            return explicit.toSet()
        }

        return preferences
            .getStringSet(
                KEY_PINNED_APPS_LEGACY,
                emptySet(),
            ).orEmpty()
            .toSet()
    }

    override fun saveFavoriteKeys(keys: Set<String>) {
        preferences
            .edit {
                putStringSet(
                    KEY_FAVORITE_APPS,
                    keys.toSet(),
                ).remove(KEY_PINNED_APPS_LEGACY)
            }
    }

    /**
     * Null means the user has never customized Start tiles and the production
     * UI should resolve the current preferred/default set from installed apps.
     * An empty set is an explicit user choice to keep Start tile-free.
     */

    override fun loadStartTileKeys(): Set<String>? {
        if (!preferences.contains(KEY_START_TILES)) {
            return null
        }

        return preferences
            .getStringSet(
                KEY_START_TILES,
                emptySet(),
            ).orEmpty()
            .toSet()
    }

    override fun saveStartTileKeys(keys: Set<String>) {
        preferences
            .edit {
                putStringSet(
                    KEY_START_TILES,
                    keys.toSet(),
                )
            }
    }

    /**
     * Ordered quick bar slot tokens (see QuickBar). Null means never
     * customised, so the UI shows the default bar. Stored as one string
     * because a string set would lose the order.
     */

    override fun loadQuickBar(): List<String>? =
        preferences
            .getString(KEY_QUICK_BAR, null)
            ?.split(QUICK_BAR_SEPARATOR)
            ?.filter { it.isNotEmpty() }

    override fun saveQuickBar(tokens: List<String>?) {
        preferences
            .edit {
                if (tokens == null) {
                    remove(KEY_QUICK_BAR)
                } else {
                    putString(KEY_QUICK_BAR, tokens.joinToString(QUICK_BAR_SEPARATOR))
                }
            }
    }
}

/** Launcher preference API: bb7 domain. */
interface StartBb7State {
    fun loadBb7HomeRows(): Int

    fun saveBb7HomeRows(rows: Int)

    fun loadBb7Panels(): List<Bb7Panel>

    fun saveBb7Panels(panels: List<Bb7Panel>)

    fun loadBb7Scrim(): Int

    fun saveBb7Scrim(level: Int)

    fun loadBb7IconSize(): Bb7IconSize

    fun saveBb7IconSize(size: Bb7IconSize)

    fun loadBb7Desktop(): List<String>

    fun saveBb7Desktop(keys: List<String>)
}

internal class StartBb7StateImpl(
    private val shared: StartStateBacking,
) : StartBb7State {
    private val preferences: SharedPreferences get() = shared.preferences

    private fun loadOrdered(key: String): List<String>? = shared.loadOrdered(key)

    private fun saveOrdered(
        key: String,
        values: List<String>?,
    ) = shared.saveOrdered(key, values)

    /** Rows of icons on the BlackBerry 7 home screen (see Bb7Home.MAX_HOME_ROWS). */
    override fun loadBb7HomeRows(): Int =
        preferences.getInt(KEY_BB7_HOME_ROWS, Bb7Home.MIN_HOME_ROWS).coerceIn(Bb7Home.MIN_HOME_ROWS, Bb7Home.MAX_HOME_ROWS)

    override fun saveBb7HomeRows(rows: Int) {
        preferences.edit().putInt(KEY_BB7_HOME_ROWS, rows).apply()
    }

    /** The BlackBerry 7 tray's panels, in order (see Bb7Panels). */
    override fun loadBb7Panels(): List<Bb7Panel> = Bb7Panels.decode(preferences.getString(KEY_BB7_PANELS, null))

    override fun saveBb7Panels(panels: List<Bb7Panel>) {
        preferences.edit().putString(KEY_BB7_PANELS, Bb7Panels.encode(panels)).apply()
    }

    /** How dark the BlackBerry 7 banner and tray are over the wallpaper (see Bb7Scrim). */
    override fun loadBb7Scrim(): Int = Bb7Scrim.clamp(preferences.getInt(KEY_BB7_SCRIM, Bb7Scrim.DEFAULT))

    override fun saveBb7Scrim(level: Int) {
        preferences.edit().putInt(KEY_BB7_SCRIM, Bb7Scrim.clamp(level)).apply()
    }

    override fun loadBb7IconSize(): Bb7IconSize =
        Bb7IconSize.fromStable(
            preferences.getInt(
                KEY_BB7_ICON_SIZE,
                Bb7IconSize
                    .DEFAULT.stable,
            ),
        )

    override fun saveBb7IconSize(size: Bb7IconSize) {
        preferences.edit().putInt(KEY_BB7_ICON_SIZE, size.stable).apply()
    }

    /** Apps placed in the empty space of the BlackBerry 7 home screen, in order (stable keys). */
    override fun loadBb7Desktop(): List<String> = Bb7Home.desktopShortcuts(loadOrdered(KEY_BB7_DESKTOP).orEmpty())

    override fun saveBb7Desktop(keys: List<String>) = saveOrdered(KEY_BB7_DESKTOP, Bb7Home.desktopShortcuts(keys))
}

/** Launcher preference API: homelayout domain. */
interface StartHomeLayoutState {
    fun loadHomeApps(): List<String>?

    fun saveHomeApps(keys: List<String>?)

    fun loadGridOrder(): List<String>?

    fun saveGridOrder(keys: List<String>?)

    fun loadGlanceWidgets(): List<String>?

    fun saveGlanceWidgets(widgets: List<String>?)

    fun loadHiddenHomeSections(): Set<String>?

    fun saveHiddenHomeSections(sections: Set<String>?)

    fun loadHomeMode(): String?

    fun saveHomeMode(mode: String)
}

internal class StartHomeLayoutStateImpl(
    private val shared: StartStateBacking,
) : StartHomeLayoutState {
    private val preferences: SharedPreferences get() = shared.preferences

    private fun loadOrdered(key: String): List<String>? = shared.loadOrdered(key)

    private fun saveOrdered(
        key: String,
        values: List<String>?,
    ) = shared.saveOrdered(key, values)

    /** Ordered Home apps (see HomeApps); null means never customised. */
    override fun loadHomeApps(): List<String>? = loadOrdered(KEY_HOME_APPS)

    override fun saveHomeApps(keys: List<String>?) = saveOrdered(KEY_HOME_APPS, keys)

    /** The app grid order (AppGrid, the BB10 layout); null means never customised. */
    override fun loadGridOrder(): List<String>? = loadOrdered(KEY_GRID_ORDER)

    override fun saveGridOrder(keys: List<String>?) = saveOrdered(KEY_GRID_ORDER, keys)

    /** Ordered Glance widgets, shown as Active Frames while no app is running (see GlanceWidgets); null means never customised. */
    override fun loadGlanceWidgets(): List<String>? = loadOrdered(KEY_GLANCE_WIDGETS)

    override fun saveGlanceWidgets(widgets: List<String>?) = saveOrdered(KEY_GLANCE_WIDGETS, widgets)

    /** Home modules the user hid in Edit Home (see HomeLayout); null means never customised. */
    override fun loadHiddenHomeSections(): Set<String>? =
        if (preferences.contains(KEY_HOME_HIDDEN_SECTIONS)) {
            preferences.getStringSet(KEY_HOME_HIDDEN_SECTIONS, emptySet()).orEmpty().toSet()
        } else {
            null
        }

    override fun saveHiddenHomeSections(sections: Set<String>?) {
        preferences
            .edit {
                if (sections == null) {
                    remove(KEY_HOME_HIDDEN_SECTIONS)
                } else {
                    putStringSet(KEY_HOME_HIDDEN_SECTIONS, sections.toSet())
                }
            }
    }

    /** "list" or "tiles" (see HomeLayout); null means never customised. */
    override fun loadHomeMode(): String? = preferences.getString(KEY_HOME_MODE, null)

    override fun saveHomeMode(mode: String) {
        preferences.edit().putString(KEY_HOME_MODE, mode).apply()
    }
}

/** Launcher preference API: general domain. */
interface StartGeneralState {
    fun loadHomeStyle(): HomeStyle

    fun saveHomeStyle(style: HomeStyle)

    fun loadLauncherTextSize(): io.github.aimindseye.tenseven.model.LauncherTextSize

    fun saveLauncherTextSize(value: io.github.aimindseye.tenseven.model.LauncherTextSize)

    fun loadOnboardingDone(): Boolean

    fun saveOnboardingDone()

    fun loadDoubleTapLock(): Boolean

    fun saveDoubleTapLock(enabled: Boolean)

    fun loadLockLayer(): Boolean

    fun saveLockLayer(enabled: Boolean)
}

internal class StartGeneralStateImpl(
    private val shared: StartStateBacking,
) : StartGeneralState {
    private val preferences: SharedPreferences get() = shared.preferences

    /** BlackBerry 10 or BlackBerry 7 home (HomeStyle); the BB10 layout until the user picks. */
    override fun loadHomeStyle(): HomeStyle = HomeStyle.fromStableValue(preferences.getString(KEY_HOME_STYLE, null))

    override fun saveHomeStyle(style: HomeStyle) {
        preferences.edit().putString(KEY_HOME_STYLE, style.stableValue).apply()
    }

    /** Safe launcher-owned font preset: do not alter Android system font scale. */
    override fun loadLauncherTextSize(): io.github.aimindseye.tenseven.model.LauncherTextSize =
        io.github.aimindseye.tenseven.model.LauncherTextSize.decode(
            preferences.getString("launcher_text_size", null),
        )

    override fun saveLauncherTextSize(value: io.github.aimindseye.tenseven.model.LauncherTextSize) {
        preferences.edit { putString("launcher_text_size", value.name) }
    }

    /** Whether the setup flow has been shown (finished, skipped or backed out of); it opens once on first run. */
    override fun loadOnboardingDone(): Boolean = preferences.getBoolean(KEY_ONBOARDING_DONE, false)

    override fun saveOnboardingDone() {
        preferences.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
    }

    /** Double-tap on empty Home space locks the screen (needs the accessibility service); off until chosen. */
    override fun loadDoubleTapLock(): Boolean = preferences.getBoolean(KEY_DOUBLE_TAP_LOCK, false)

    override fun saveDoubleTapLock(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_DOUBLE_TAP_LOCK, enabled).apply()
    }

    /** The Tenseven lock layer shown over the system lock screen when the screen wakes; off until chosen. */
    override fun loadLockLayer(): Boolean = preferences.getInt(KEY_LOCK_LAYER, 0) != 0

    override fun saveLockLayer(enabled: Boolean) {
        preferences.edit().putInt(KEY_LOCK_LAYER, if (enabled) 1 else 0).apply()
    }
}

/** Launcher preference API: weather domain. */
interface StartWeatherState {
    fun loadHomeWeatherEnabled(): Boolean

    fun saveHomeWeatherEnabled(enabled: Boolean)

    fun loadHomeWeatherCity(): String

    fun saveHomeWeatherCity(value: String)

    fun onHomeReadabilityChanged(listener: () -> Unit): () -> Unit

    fun loadHomeWeatherUseDeviceLocation(): Boolean

    fun saveHomeWeatherUseDeviceLocation(enabled: Boolean)

    fun loadHomeStatusSize(): io.github.aimindseye.tenseven.model.HomeStatusSize

    fun saveHomeStatusSize(size: io.github.aimindseye.tenseven.model.HomeStatusSize)
}

internal class StartWeatherStateImpl(
    private val shared: StartStateBacking,
) : StartWeatherState {
    private val preferences: SharedPreferences get() = shared.preferences

    /** Opt-in city, never inferred from location or IP address. */
    override fun loadHomeWeatherEnabled(): Boolean = preferences.getBoolean("home_weather_enabled", false)

    override fun saveHomeWeatherEnabled(enabled: Boolean) {
        preferences.edit { putBoolean("home_weather_enabled", enabled) }
    }

    override fun loadHomeWeatherCity(): String = preferences.getString("home_weather_city", "").orEmpty()

    override fun saveHomeWeatherCity(value: String) {
        preferences.edit { putString("home_weather_city", value.trim().take(HOME_WEATHER_CITY_INPUT_LIMIT)) }
    }

    /** Observes Home weather and font changes without requiring a restart of Settings or Home. */
    override fun onHomeReadabilityChanged(listener: () -> Unit): () -> Unit {
        val callback =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                when (key) {
                    "launcher_text_size", "home_weather_enabled", "home_weather_city",
                    "home_weather_use_device_location", "home_status_size",
                    -> listener()
                }
            }
        preferences.registerOnSharedPreferenceChangeListener(callback)
        return { preferences.unregisterOnSharedPreferenceChangeListener(callback) }
    }

    /** Explicit opt-in; manual city remains available when permission is denied or unavailable. */
    override fun loadHomeWeatherUseDeviceLocation(): Boolean {
        val useDeviceLocation = preferences.getBoolean("home_weather_use_device_location", false)
        return useDeviceLocation
    }

    override fun saveHomeWeatherUseDeviceLocation(enabled: Boolean) {
        preferences.edit { putBoolean("home_weather_use_device_location", enabled) }
    }

    /** One status readout size for BB7/BB10; no migration of dial selections needed. */
    override fun loadHomeStatusSize(): io.github.aimindseye.tenseven.model.HomeStatusSize =
        io.github.aimindseye.tenseven.model.HomeStatusSize
            .decode(preferences.getString("home_status_size", null))

    override fun saveHomeStatusSize(size: io.github.aimindseye.tenseven.model.HomeStatusSize) {
        preferences.edit { putString("home_status_size", size.stableValue) }
    }
}

/** Launcher preference API: icons domain. */
interface StartIconsState {
    fun loadIconPack(): String?

    fun saveIconPack(packageName: String?)

    fun loadIconPresentation(): io.github.aimindseye.tenseven.model.IconPresentation

    fun saveIconPresentation(style: io.github.aimindseye.tenseven.model.IconPresentation)

    fun loadTypeToSearch(): Boolean

    fun saveTypeToSearch(enabled: Boolean)

    fun loadGridColumns(): Int

    fun saveGridColumns(columns: Int)

    fun loadGridLabels(): Boolean

    fun saveGridLabels(shown: Boolean)
}

internal class StartIconsStateImpl(
    private val shared: StartStateBacking,
) : StartIconsState {
    private val preferences: SharedPreferences get() = shared.preferences

    /** Package name of the chosen icon pack, or null for each app's own icon. */
    override fun loadIconPack(): String? = preferences.getString(KEY_ICON_PACK, null)?.takeIf { it.isNotBlank() }

    override fun saveIconPack(packageName: String?) {
        preferences
            .edit {
                if (packageName.isNullOrBlank()) remove(KEY_ICON_PACK) else putString(KEY_ICON_PACK, packageName)
            }
    }

    /** Release 2 defaults to TenSeven/Tabler glyphs; persisted Original remains an explicit opt-out. */
    override fun loadIconPresentation(): io.github.aimindseye.tenseven.model.IconPresentation =
        io.github.aimindseye.tenseven.model.IconPresentation.decode(
            preferences.getString(KEY_ICON_PRESENTATION, null),
        )

    override fun saveIconPresentation(style: io.github.aimindseye.tenseven.model.IconPresentation) {
        preferences.edit().putString(KEY_ICON_PRESENTATION, style.key).apply()
    }

    /** Whether typing on a home screen starts a search (default on). */
    override fun loadTypeToSearch(): Boolean = preferences.getInt(KEY_TYPE_TO_SEARCH, 1) != 0

    override fun saveTypeToSearch(enabled: Boolean) {
        preferences.edit().putInt(KEY_TYPE_TO_SEARCH, if (enabled) 1 else 0).apply()
    }

    /** Icon grid: columns (0 = as many as fit) and whether icons carry their names. */
    override fun loadGridColumns(): Int = GridColumns.normalize(preferences.getInt(KEY_GRID_COLUMNS, GridColumns.AUTO))

    override fun saveGridColumns(columns: Int) {
        preferences.edit().putInt(KEY_GRID_COLUMNS, GridColumns.normalize(columns)).apply()
    }

    override fun loadGridLabels(): Boolean = preferences.getInt(KEY_GRID_LABELS, 1) != 0

    override fun saveGridLabels(shown: Boolean) {
        preferences.edit().putInt(KEY_GRID_LABELS, if (shown) 1 else 0).apply()
    }
}

/** Launcher preference API: status domain. */
interface StartStatusState {
    fun loadHomeDials(): Set<String>

    fun loadHomeDials(style: HomeStyle): Set<String>

    fun saveHomeDials(selected: Set<String>)

    fun loadHomeStatusMode(): io.github.aimindseye.tenseven.model.HomeStatusMode

    fun saveHomeStatusMode(mode: io.github.aimindseye.tenseven.model.HomeStatusMode)

    fun loadHomeNotificationStrip(style: HomeStyle): Boolean

    fun saveHomeNotificationStrip(
        style: HomeStyle,
        enabled: Boolean,
    )

    fun loadHomeNotificationExpanded(style: HomeStyle): Boolean

    fun saveHomeNotificationExpanded(
        style: HomeStyle,
        expanded: Boolean,
    )
}

internal class StartStatusStateImpl(
    private val shared: StartStateBacking,
) : StartStatusState {
    private val preferences: SharedPreferences get() = shared.preferences

    private fun loadHomeStyle(): HomeStyle = HomeStyle.fromStableValue(preferences.getString(KEY_HOME_STYLE, null))

    /** Shared Home metrics, migrated from the currently selected style on first read. */
    override fun loadHomeDials(): Set<String> =
        HomeDisplayPolicy.normalizedDials(
            preferences.getStringSet("home_status_metrics_shared", null)
                ?: preferences.getStringSet(HomeDisplayPolicy.dialPreference(loadHomeStyle()), null)
                ?: HomeDisplayPolicy.DEFAULT_DIALS,
        )

    /** Candidate-only legacy read for protected Active Frames. Shared dials ignore style. */
    @Suppress("UnusedParameter", "UNUSED_PARAMETER")
    override fun loadHomeDials(style: HomeStyle): Set<String> = loadHomeDials()

    override fun saveHomeDials(selected: Set<String>) {
        preferences
            .edit {
                putStringSet(
                    "home_status_metrics_shared",
                    HomeDisplayPolicy.normalizedDials(selected),
                )
            }
    }

    /** One Home status mode, shared across BB7/BB10. The old selected-style choice is retained. */
    override fun loadHomeStatusMode(): io.github.aimindseye.tenseven.model.HomeStatusMode =
        io.github.aimindseye.tenseven.model.HomeStatusMode.decode(
            preferences.getString("home_status_mode_shared", null)
                ?: preferences.getString(
                    io.github.aimindseye.tenseven.model.HomeStatusMode
                        .preference(loadHomeStyle()),
                    null,
                ),
            loadHomeDials().size,
        )

    override fun saveHomeStatusMode(mode: io.github.aimindseye.tenseven.model.HomeStatusMode) {
        preferences.edit { putString("home_status_mode_shared", mode.key) }
    }

    @Suppress("UnusedParameter", "UNUSED_PARAMETER")
    override fun loadHomeNotificationStrip(style: HomeStyle): Boolean = HOME_NOTIFICATION_STRIP_ENABLED

    override fun saveHomeNotificationStrip(
        style: HomeStyle,
        enabled: Boolean,
    ) {
        preferences.edit().putInt(HomeDisplayPolicy.stripPreference(style), if (enabled) 1 else 0).apply()
    }

    override fun loadHomeNotificationExpanded(style: HomeStyle): Boolean =
        preferences.getInt(HomeDisplayPolicy.stripExpandedPreference(style), 0) != 0

    override fun saveHomeNotificationExpanded(
        style: HomeStyle,
        expanded: Boolean,
    ) {
        preferences.edit().putInt(HomeDisplayPolicy.stripExpandedPreference(style), if (expanded) 1 else 0).apply()
    }
}

/** Launcher preference API: wallpaper domain. */
interface StartWallpaperState {
    fun loadWallpaperSetId(): String

    fun saveWallpaperSetId(id: String)

    fun loadHomeWallpaper(): Boolean

    fun saveHomeWallpaper(shown: Boolean)
}

internal class StartWallpaperStateImpl(
    private val shared: StartStateBacking,
) : StartWallpaperState {
    private val preferences: SharedPreferences get() = shared.preferences

    private fun loadAppearanceMode(): String? = preferences.getString(KEY_APPEARANCE_MODE, null)

    private fun loadAppearanceAccent(): String? = preferences.getString(KEY_APPEARANCE_ACCENT, null)

    /** Explicit selection is separate from theme and Android WallpaperManager. */
    override fun loadWallpaperSetId(): String =
        WallpaperSets.selectedId(
            preferences.getString(KEY_WALLPAPER_SET, null),
            ThemePresets.matching(loadAppearanceMode(), loadAppearanceAccent()).wallpaperId,
        )

    override fun saveWallpaperSetId(id: String) {
        require(WallpaperSets.ALL.any { it.id == id }) { "Unknown wallpaper: $id" }
        preferences.edit().putString(KEY_WALLPAPER_SET, id).apply()
    }

    /** Whether the BlackBerry 10 home lets the wallpaper show through, dimmed. On by default. */
    override fun loadHomeWallpaper(): Boolean = preferences.getInt(KEY_HOME_WALLPAPER, 0) != 0

    override fun saveHomeWallpaper(shown: Boolean) {
        preferences.edit().putInt(KEY_HOME_WALLPAPER, if (shown) 1 else 0).apply()
    }
}

/** Launcher preference API: hub domain. */
interface StartHubState {
    fun loadHubEnabledApps(): Set<String>

    fun saveHubEnabledApps(packages: Set<String>)

    fun loadHubSelectedCalendarIds(): Set<Long>?

    fun saveHubSelectedCalendarIds(ids: Set<Long>?)

    fun loadHubCalendarPeek(): Boolean

    fun saveHubCalendarPeek(enabled: Boolean)

    fun loadHubPeekText(): Boolean

    fun saveHubPeekText(shown: Boolean)
}

internal class StartHubStateImpl(
    private val shared: StartStateBacking,
) : StartHubState {
    private val preferences: SharedPreferences get() = shared.preferences
    private val hubSources: HubSourceRepository get() = shared.hubSources

    /** Compatibility view of automatic observed sources or the optional saved manual selection. */
    override fun loadHubEnabledApps(): Set<String> = hubSources.enabledPackages()

    override fun saveHubEnabledApps(packages: Set<String>) {
        hubSources.saveManual(packages)
    }

    /** null selects all visible calendars; empty selects none. IDs stay on-device (never backed up). */
    override fun loadHubSelectedCalendarIds(): Set<Long>? =
        if (preferences.contains(KEY_HUB_SELECTED_CALENDARS)) {
            preferences
                .getStringSet(KEY_HUB_SELECTED_CALENDARS, emptySet())
                .orEmpty()
                .mapNotNull { it.toLongOrNull() }
                .toSet()
        } else {
            null
        }

    override fun saveHubSelectedCalendarIds(ids: Set<Long>?) {
        preferences
            .edit()
            .apply {
                if (ids == null) {
                    remove(KEY_HUB_SELECTED_CALENDARS)
                } else {
                    putStringSet(KEY_HUB_SELECTED_CALENDARS, ids.map { it.toString() }.toSet())
                }
            }.apply()
    }

    /** Calendar Peek is optional: never read CalendarProvider until the user enables this. */
    override fun loadHubCalendarPeek(): Boolean = preferences.getInt(KEY_HUB_CALENDAR_PEEK, 0) != 0

    override fun saveHubCalendarPeek(enabled: Boolean) {
        preferences.edit().putInt(KEY_HUB_CALENDAR_PEEK, if (enabled) 1 else 0).apply()
    }

    /** Whether the Hub peek on the home screen shows message text (off: names and counts only). */
    override fun loadHubPeekText(): Boolean = preferences.getInt(KEY_HUB_PEEK_TEXT, 0) != 0

    override fun saveHubPeekText(shown: Boolean) {
        preferences.edit().putInt(KEY_HUB_PEEK_TEXT, if (shown) 1 else 0).apply()
    }
}

/** Launcher preference API: appearance domain. */
interface StartAppearanceState {
    fun loadAppearanceMode(): String?

    fun loadAppearanceAccent(): String?

    fun saveAppearance(
        mode: String?,
        accent: String?,
    )

    fun loadCustomThemes(): List<CustomTheme>

    fun saveCustomThemes(themes: List<CustomTheme>)

    fun loadActiveCustomThemeId(): String?

    fun saveActiveCustomTheme(theme: CustomTheme)

    fun onAppearanceChanged(listener: () -> Unit): () -> Unit
}

internal class StartAppearanceStateImpl(
    private val shared: StartStateBacking,
) : StartAppearanceState {
    private val preferences: SharedPreferences get() = shared.preferences

    /** The theme chosen in Tenseven (mode and accent, stable values); null means follow the system's Sable appearance. */
    override fun loadAppearanceMode(): String? = preferences.getString(KEY_APPEARANCE_MODE, null)

    override fun loadAppearanceAccent(): String? = preferences.getString(KEY_APPEARANCE_ACCENT, null)

    /** Choosing a built-in theme also clears any imported theme that was chosen. */
    override fun saveAppearance(
        mode: String?,
        accent: String?,
    ) {
        preferences
            .edit()
            .apply {
                if (mode == null) remove(KEY_APPEARANCE_MODE) else putString(KEY_APPEARANCE_MODE, mode)
                if (accent == null) remove(KEY_APPEARANCE_ACCENT) else putString(KEY_APPEARANCE_ACCENT, accent)
                remove(KEY_APPEARANCE_CUSTOM)
            }.apply()
    }

    /** The imported themes (a JSON array, see [CustomThemes.formatStored]); included in a backup. */
    override fun loadCustomThemes(): List<CustomTheme> {
        val storedThemes = preferences.getString(KEY_CUSTOM_THEMES, null)
        return CustomThemes.parseStored(storedThemes)
    }

    override fun saveCustomThemes(themes: List<CustomTheme>) {
        preferences.edit().putString(KEY_CUSTOM_THEMES, CustomThemes.formatStored(themes)).apply()
    }

    /** The id of the imported theme in use, or null when a built-in theme is. */
    override fun loadActiveCustomThemeId(): String? = preferences.getString(KEY_APPEARANCE_CUSTOM, null)

    /** Uses the imported [theme]: its mode, the "custom" accent, and its id. */
    override fun saveActiveCustomTheme(theme: CustomTheme) {
        preferences
            .edit()
            .putString(KEY_APPEARANCE_MODE, theme.mode)
            .putString(KEY_APPEARANCE_ACCENT, CustomThemes.ACCENT_VALUE)
            .putString(KEY_APPEARANCE_CUSTOM, theme.id)
            .apply()
    }

    /** Calls [listener] when the theme choice changes; the returned function stops listening. */
    override fun onAppearanceChanged(listener: () -> Unit): () -> Unit {
        val callback =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                when (key) {
                    KEY_APPEARANCE_MODE, KEY_APPEARANCE_ACCENT,
                    KEY_APPEARANCE_CUSTOM, KEY_CUSTOM_THEMES,
                    -> listener()
                }
            }
        preferences.registerOnSharedPreferenceChangeListener(callback)
        return { preferences.unregisterOnSharedPreferenceChangeListener(callback) }
    }
}

/** Launcher preference API: widgets domain. */
interface StartWidgetsState {
    fun loadHomeWidgetIds(): List<Int>

    fun saveHomeWidgetIds(ids: List<Int>)

    fun loadWidgetsPage(): Boolean

    fun saveWidgetsPage(enabled: Boolean)

    fun loadWidgetIds(): List<Int>

    fun saveWidgetIds(ids: List<Int>)
}

internal class StartWidgetsStateImpl(
    private val shared: StartStateBacking,
) : StartWidgetsState {
    private val preferences: SharedPreferences get() = shared.preferences

    /** Android widget ids shown as tiles on the BlackBerry 10 home. Device-specific, so never part of a backup. */
    override fun loadHomeWidgetIds(): List<Int> = WidgetList.parse(preferences.getString(KEY_HOME_WIDGET_IDS, null))

    override fun saveHomeWidgetIds(ids: List<Int>) {
        preferences.edit().putString(KEY_HOME_WIDGET_IDS, WidgetList.format(ids)).apply()
    }

    /** Whether the Widgets page is the page left of Hub (default on). */
    override fun loadWidgetsPage(): Boolean = preferences.getInt(KEY_WIDGETS_PAGE, 1) != 0

    override fun saveWidgetsPage(enabled: Boolean) {
        preferences.edit().putInt(KEY_WIDGETS_PAGE, if (enabled) 1 else 0).apply()
    }

    /** Android widget ids this launcher allocated, in display order. Device-specific, so never part of a backup. */
    override fun loadWidgetIds(): List<Int> = WidgetList.parse(preferences.getString(KEY_WIDGET_IDS, null))

    override fun saveWidgetIds(ids: List<Int>) {
        preferences.edit().putString(KEY_WIDGET_IDS, WidgetList.format(ids)).apply()
    }
}

/** Launcher preference API: backup domain. */
interface StartBackupState {
    fun exportSettings(): Map<String, BackupValue>

    fun restoreSettings(settings: Map<String, BackupValue>)
}

internal class StartBackupStateImpl(
    private val shared: StartStateBacking,
) : StartBackupState {
    private val preferences: SharedPreferences get() = shared.preferences

    /** The settings a backup carries (see LauncherBackup); nothing else in preferences is read. */
    override fun exportSettings(): Map<String, BackupValue> = LauncherBackup.select(preferences.all)

    /** Replaces every backed-up setting with [settings]: keys the file lacks go back to their defaults. */
    override fun restoreSettings(settings: Map<String, BackupValue>) {
        preferences
            .edit()
            .apply {
                LauncherBackup.keys.forEach { remove(it) }
                settings.forEach { (key, value) ->
                    if (key in LauncherBackup.keys) {
                        when (value) {
                            is BackupValue.Text -> putString(key, value.value)
                            is BackupValue.Number -> putInt(key, value.value)
                            is BackupValue.Items -> putStringSet(key, value.values)
                        }
                    }
                }
            }.apply()
    }
}
