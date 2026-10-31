package io.github.aimindseye.tenseven.platform

import android.content.Context
import android.content.SharedPreferences
import io.github.aimindseye.tenseven.model.CustomTheme
import io.github.aimindseye.tenseven.model.CustomThemes
import io.github.aimindseye.tenseven.model.Bb7Home
import io.github.aimindseye.tenseven.model.Bb7IconSize
import io.github.aimindseye.tenseven.model.Bb7Panel
import io.github.aimindseye.tenseven.model.Bb7Panels
import io.github.aimindseye.tenseven.model.Bb7Scrim
import io.github.aimindseye.tenseven.model.BackupValue
import io.github.aimindseye.tenseven.model.GridColumns
import io.github.aimindseye.tenseven.model.HomeStyle
import io.github.aimindseye.tenseven.model.LauncherBackup
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
class StartStateRepository(
    context: Context,
) {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    fun loadFavoriteKeys(): Set<String> {
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
            )
            .orEmpty()
            .toSet()
    }

    fun saveFavoriteKeys(keys: Set<String>) {
        preferences
            .edit()
            .putStringSet(
                KEY_FAVORITE_APPS,
                keys.toSet(),
            )
            .remove(KEY_PINNED_APPS_LEGACY)
            .apply()
    }

    /**
     * Null means the user has never customized Start tiles and the production
     * UI should resolve the current preferred/default set from installed apps.
     * An empty set is an explicit user choice to keep Start tile-free.
     */
    fun loadStartTileKeys(): Set<String>? {
        if (!preferences.contains(KEY_START_TILES)) {
            return null
        }

        return preferences
            .getStringSet(
                KEY_START_TILES,
                emptySet(),
            )
            .orEmpty()
            .toSet()
    }

    fun saveStartTileKeys(keys: Set<String>) {
        preferences
            .edit()
            .putStringSet(
                KEY_START_TILES,
                keys.toSet(),
            )
            .apply()
    }

    /**
     * Ordered quick bar slot tokens (see QuickBar). Null means never
     * customised, so the UI shows the default bar. Stored as one string
     * because a string set would lose the order.
     */
    fun loadQuickBar(): List<String>? =
        preferences
            .getString(KEY_QUICK_BAR, null)
            ?.split(QUICK_BAR_SEPARATOR)
            ?.filter { it.isNotEmpty() }

    fun saveQuickBar(tokens: List<String>?) {
        preferences
            .edit()
            .apply {
                if (tokens == null) {
                    remove(KEY_QUICK_BAR)
                } else {
                    putString(KEY_QUICK_BAR, tokens.joinToString(QUICK_BAR_SEPARATOR))
                }
            }.apply()
    }

    /** Ordered Home apps (see HomeApps); null means never customised. */
    fun loadHomeApps(): List<String>? = loadOrdered(KEY_HOME_APPS)

    fun saveHomeApps(keys: List<String>?) = saveOrdered(KEY_HOME_APPS, keys)

    /** Rows of icons on the BlackBerry 7 home screen (see Bb7Home.MAX_HOME_ROWS). */
    fun loadBb7HomeRows(): Int =
        preferences.getInt(KEY_BB7_HOME_ROWS, Bb7Home.MIN_HOME_ROWS).coerceIn(Bb7Home.MIN_HOME_ROWS, Bb7Home.MAX_HOME_ROWS)

    fun saveBb7HomeRows(rows: Int) {
        preferences.edit().putInt(KEY_BB7_HOME_ROWS, rows).apply()
    }

    /** The BlackBerry 7 tray's panels, in order (see Bb7Panels). */
    fun loadBb7Panels(): List<Bb7Panel> = Bb7Panels.decode(preferences.getString(KEY_BB7_PANELS, null))

    fun saveBb7Panels(panels: List<Bb7Panel>) {
        preferences.edit().putString(KEY_BB7_PANELS, Bb7Panels.encode(panels)).apply()
    }

    /** How dark the BlackBerry 7 banner and tray are over the wallpaper (see Bb7Scrim). */
    fun loadBb7Scrim(): Int = Bb7Scrim.clamp(preferences.getInt(KEY_BB7_SCRIM, Bb7Scrim.DEFAULT))

    fun saveBb7Scrim(level: Int) {
        preferences.edit().putInt(KEY_BB7_SCRIM, Bb7Scrim.clamp(level)).apply()
    }

    /** The app grid order (AppGrid, the BB10 layout); null means never customised. */
    fun loadGridOrder(): List<String>? = loadOrdered(KEY_GRID_ORDER)

    fun saveGridOrder(keys: List<String>?) = saveOrdered(KEY_GRID_ORDER, keys)

    /** Ordered Glance widgets, shown as Active Frames while no app is running (see GlanceWidgets); null means never customised. */
    fun loadGlanceWidgets(): List<String>? = loadOrdered(KEY_GLANCE_WIDGETS)

    fun saveGlanceWidgets(widgets: List<String>?) = saveOrdered(KEY_GLANCE_WIDGETS, widgets)

    /** Home modules the user hid in Edit Home (see HomeLayout); null means never customised. */
    fun loadHiddenHomeSections(): Set<String>? =
        if (preferences.contains(KEY_HOME_HIDDEN_SECTIONS)) {
            preferences.getStringSet(KEY_HOME_HIDDEN_SECTIONS, emptySet()).orEmpty().toSet()
        } else {
            null
        }

    fun saveHiddenHomeSections(sections: Set<String>?) {
        preferences
            .edit()
            .apply {
                if (sections == null) {
                    remove(KEY_HOME_HIDDEN_SECTIONS)
                } else {
                    putStringSet(KEY_HOME_HIDDEN_SECTIONS, sections.toSet())
                }
            }.apply()
    }

    /** "list" or "tiles" (see HomeLayout); null means never customised. */
    fun loadHomeMode(): String? = preferences.getString(KEY_HOME_MODE, null)

    fun saveHomeMode(mode: String) {
        preferences.edit().putString(KEY_HOME_MODE, mode).apply()
    }

    /** BlackBerry 10 or BlackBerry 7 home (HomeStyle); the BB10 layout until the user picks. */
    fun loadHomeStyle(): HomeStyle = HomeStyle.fromStableValue(preferences.getString(KEY_HOME_STYLE, null))

    fun saveHomeStyle(style: HomeStyle) {
        preferences.edit().putString(KEY_HOME_STYLE, style.stableValue).apply()
    }

    /** Whether the setup flow has been shown (finished, skipped or backed out of); it opens once on first run. */
    fun loadOnboardingDone(): Boolean = preferences.getBoolean(KEY_ONBOARDING_DONE, false)

    fun saveOnboardingDone() {
        preferences.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
    }

    /** Double-tap on empty Home space locks the screen (needs the accessibility service); off until chosen. */
    fun loadDoubleTapLock(): Boolean = preferences.getBoolean(KEY_DOUBLE_TAP_LOCK, false)

    fun saveDoubleTapLock(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_DOUBLE_TAP_LOCK, enabled).apply()
    }

    /** Package name of the chosen icon pack, or null for each app's own icon. */
    fun loadIconPack(): String? = preferences.getString(KEY_ICON_PACK, null)?.takeIf { it.isNotBlank() }

    fun saveIconPack(packageName: String?) {
        preferences.edit().apply {
            if (packageName.isNullOrBlank()) remove(KEY_ICON_PACK) else putString(KEY_ICON_PACK, packageName)
        }.apply()
    }

    /** Whether typing on a home screen starts a search (default on). */
    fun loadTypeToSearch(): Boolean = preferences.getInt(KEY_TYPE_TO_SEARCH, 1) != 0

    fun saveTypeToSearch(enabled: Boolean) {
        preferences.edit().putInt(KEY_TYPE_TO_SEARCH, if (enabled) 1 else 0).apply()
    }

    fun loadBb7IconSize(): Bb7IconSize =
        Bb7IconSize.fromStable(preferences.getInt(KEY_BB7_ICON_SIZE, Bb7IconSize.DEFAULT.stable))

    fun saveBb7IconSize(size: Bb7IconSize) {
        preferences.edit().putInt(KEY_BB7_ICON_SIZE, size.stable).apply()
    }

    /** Apps placed in the empty space of the BlackBerry 7 home screen, in order (stable keys). */
    fun loadBb7Desktop(): List<String> = loadOrdered(KEY_BB7_DESKTOP).orEmpty()

    fun saveBb7Desktop(keys: List<String>) = saveOrdered(KEY_BB7_DESKTOP, keys)

    /** Icon grid: columns (0 = as many as fit) and whether icons carry their names. */
    fun loadGridColumns(): Int = GridColumns.normalize(preferences.getInt(KEY_GRID_COLUMNS, GridColumns.AUTO))

    fun saveGridColumns(columns: Int) {
        preferences.edit().putInt(KEY_GRID_COLUMNS, GridColumns.normalize(columns)).apply()
    }

    /** The Tenseven lock layer shown over the system lock screen when the screen wakes; off until chosen. */
    fun loadLockLayer(): Boolean = preferences.getInt(KEY_LOCK_LAYER, 0) != 0

    fun saveLockLayer(enabled: Boolean) {
        preferences.edit().putInt(KEY_LOCK_LAYER, if (enabled) 1 else 0).apply()
    }

    /** Whether the BlackBerry 10 home lets the wallpaper show through, dimmed. On by default. */
    fun loadHomeWallpaper(): Boolean = preferences.getInt(KEY_HOME_WALLPAPER, 1) != 0

    fun saveHomeWallpaper(shown: Boolean) {
        preferences.edit().putInt(KEY_HOME_WALLPAPER, if (shown) 1 else 0).apply()
    }

    fun loadGridLabels(): Boolean = preferences.getInt(KEY_GRID_LABELS, 1) != 0

    fun saveGridLabels(shown: Boolean) {
        preferences.edit().putInt(KEY_GRID_LABELS, if (shown) 1 else 0).apply()
    }

    /** Packages whose notifications the Hub ignores ("Apps in the Hub": the ones switched off). */
    fun loadHubExcludedApps(): Set<String> = preferences.getStringSet(KEY_HUB_EXCLUDED, null).orEmpty().toSet()

    fun saveHubExcludedApps(packages: Set<String>) {
        preferences.edit().putStringSet(KEY_HUB_EXCLUDED, packages.toSet()).apply()
    }

    /** Whether the Hub peek on the home screen shows message text (off: names and counts only). */
    fun loadHubPeekText(): Boolean = preferences.getInt(KEY_HUB_PEEK_TEXT, 0) != 0

    fun saveHubPeekText(shown: Boolean) {
        preferences.edit().putInt(KEY_HUB_PEEK_TEXT, if (shown) 1 else 0).apply()
    }

    /** Android widget ids shown as tiles on the BlackBerry 10 home. Device-specific, so never part of a backup. */
    fun loadHomeWidgetIds(): List<Int> = WidgetList.parse(preferences.getString(KEY_HOME_WIDGET_IDS, null))

    fun saveHomeWidgetIds(ids: List<Int>) {
        preferences.edit().putString(KEY_HOME_WIDGET_IDS, WidgetList.format(ids)).apply()
    }

    /** The theme chosen in Tenseven (mode and accent, stable values); null means follow the system's Sable appearance. */
    fun loadAppearanceMode(): String? = preferences.getString(KEY_APPEARANCE_MODE, null)

    fun loadAppearanceAccent(): String? = preferences.getString(KEY_APPEARANCE_ACCENT, null)

    /** Choosing a built-in theme also clears any imported theme that was chosen. */
    fun saveAppearance(
        mode: String?,
        accent: String?,
    ) {
        preferences.edit().apply {
            if (mode == null) remove(KEY_APPEARANCE_MODE) else putString(KEY_APPEARANCE_MODE, mode)
            if (accent == null) remove(KEY_APPEARANCE_ACCENT) else putString(KEY_APPEARANCE_ACCENT, accent)
            remove(KEY_APPEARANCE_CUSTOM)
        }.apply()
    }

    /** The imported themes (a JSON array, see [CustomThemes.formatStored]); included in a backup. */
    fun loadCustomThemes(): List<CustomTheme> = CustomThemes.parseStored(preferences.getString(KEY_CUSTOM_THEMES, null))

    fun saveCustomThemes(themes: List<CustomTheme>) {
        preferences.edit().putString(KEY_CUSTOM_THEMES, CustomThemes.formatStored(themes)).apply()
    }

    /** The id of the imported theme in use, or null when a built-in theme is. */
    fun loadActiveCustomThemeId(): String? = preferences.getString(KEY_APPEARANCE_CUSTOM, null)

    /** Uses the imported [theme]: its mode, the "custom" accent, and its id. */
    fun saveActiveCustomTheme(theme: CustomTheme) {
        preferences.edit()
            .putString(KEY_APPEARANCE_MODE, theme.mode)
            .putString(KEY_APPEARANCE_ACCENT, CustomThemes.ACCENT_VALUE)
            .putString(KEY_APPEARANCE_CUSTOM, theme.id)
            .apply()
    }

    /** Calls [listener] when the theme choice changes; the returned function stops listening. */
    fun onAppearanceChanged(listener: () -> Unit): () -> Unit {
        val callback =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == KEY_APPEARANCE_MODE || key == KEY_APPEARANCE_ACCENT || key == KEY_APPEARANCE_CUSTOM || key == KEY_CUSTOM_THEMES) listener()
            }
        preferences.registerOnSharedPreferenceChangeListener(callback)
        return { preferences.unregisterOnSharedPreferenceChangeListener(callback) }
    }

    /** Whether the Widgets page is the page left of Hub (default on). */
    fun loadWidgetsPage(): Boolean = preferences.getInt(KEY_WIDGETS_PAGE, 1) != 0

    fun saveWidgetsPage(enabled: Boolean) {
        preferences.edit().putInt(KEY_WIDGETS_PAGE, if (enabled) 1 else 0).apply()
    }

    /** Android widget ids this launcher allocated, in display order. Device-specific, so never part of a backup. */
    fun loadWidgetIds(): List<Int> = WidgetList.parse(preferences.getString(KEY_WIDGET_IDS, null))

    fun saveWidgetIds(ids: List<Int>) {
        preferences.edit().putString(KEY_WIDGET_IDS, WidgetList.format(ids)).apply()
    }

    /** The settings a backup carries (see LauncherBackup); nothing else in preferences is read. */
    fun exportSettings(): Map<String, BackupValue> = LauncherBackup.select(preferences.all)

    /** Replaces every backed-up setting with [settings]: keys the file lacks go back to their defaults. */
    fun restoreSettings(settings: Map<String, BackupValue>) {
        preferences.edit().apply {
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

    private fun loadOrdered(key: String): List<String>? =
        preferences
            .getString(key, null)
            ?.split(QUICK_BAR_SEPARATOR)
            ?.filter { it.isNotEmpty() }

    private fun saveOrdered(
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
        const val KEY_TYPE_TO_SEARCH = "type_to_search"
        const val KEY_BB7_ICON_SIZE = "bb7_icon_size"
        const val KEY_WIDGETS_PAGE = "widgets_page"
        const val KEY_BB7_DESKTOP = "bb7_desktop"
        const val KEY_GRID_COLUMNS = "grid_columns"
        const val KEY_GRID_LABELS = "grid_labels"
        const val KEY_LOCK_LAYER = "lock_layer"
        const val KEY_HOME_WALLPAPER = "home_wallpaper"
        const val KEY_HUB_EXCLUDED = "hub_excluded_apps"
        const val KEY_HUB_PEEK_TEXT = "hub_peek_text"
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
        const val KEY_DOUBLE_TAP_LOCK = "double_tap_lock"
        const val KEY_GRID_ORDER = "grid_order"
    }
}
