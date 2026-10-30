package io.github.aimindseye.tenseven.model

import io.github.aimindseye.tenseven.model.StartText.t

/**
 * Sable Start's two home styles (owner request, 2026-10-28): the BlackBerry 10 layout (Hub,
 * Active Frames and app pages) and the BlackBerry 7 one (clock and status on the wallpaper over a
 * tray of apps). The choice is made in Sable Setup or from Start's own menu. Pure policy, unit
 * tested in apps/r8/android/sablestart-presentation-check/src/test.
 */
enum class HomeStyle(
    val stableValue: String,
    private val titleKey: String,
    private val summaryKey: String,
) {
    Bb10(
        "bb10",
        "blackberry_10_style",
        "hub_active_frames_and_app",
    ),
    Bb7(
        "bb7",
        "blackberry_7_style",
        "clock_and_status_on_the",
    ),
    ;

    val title: String get() = t(titleKey)
    val summary: String get() = t(summaryKey)

    companion object {
        val DEFAULT = Bb10

        fun fromStableValue(value: String?): HomeStyle = entries.firstOrNull { it.stableValue == value } ?: DEFAULT
    }
}

/** The BlackBerry 7 tray's panels, switched with Alt+Left/Right or Space, as on BlackBerry 7. */
enum class Bb7Panel(
    private val titleKey: String,
    private val fixedTitle: String? = null,
) {
    Favorites("favorites"),
    All("all"),
    Downloads("downloads"),

    /** The apps opened most from Tenseven (English title: it is Tenseven's own, not an imported Sable panel). */
    Frequent("frequent", fixedTitle = "Frequent"),
    ;

    val title: String get() = fixedTitle ?: t(titleKey)
}

/** Which panels the tray cycles through, in order; kept as their names. Frequent is off until the user adds it. */
object Bb7Panels {
    val DEFAULT: List<Bb7Panel> = listOf(Bb7Panel.Favorites, Bb7Panel.All, Bb7Panel.Downloads)

    /** Reads the saved names; unknown names drop, the order is the panels' own, and none left means the default. */
    fun normalize(names: Collection<String>?): List<Bb7Panel> {
        if (names == null) return DEFAULT
        val chosen = Bb7Panel.entries.filter { panel -> names.any { it == panel.name } }
        return chosen.ifEmpty { DEFAULT }
    }

    /** [panel] shown or hidden; the last panel cannot be hidden. */
    fun toggle(
        current: List<Bb7Panel>,
        panel: Bb7Panel,
    ): List<Bb7Panel> {
        val next = if (panel in current) current - panel else (current + panel)
        return if (next.isEmpty()) current else Bb7Panel.entries.filter { it in next }
    }

    fun encode(panels: List<Bb7Panel>): String = panels.joinToString(",") { it.name }

    fun decode(text: String?): List<Bb7Panel> = normalize(text?.split(',')?.map(String::trim))
}

/** How see-through the BlackBerry 7 banner and tray are over the wallpaper: 0 (clear) to 3 (dark). */
object Bb7Scrim {
    const val MIN = 0
    const val MAX = 3
    const val DEFAULT = 2

    private val BANNER = floatArrayOf(0f, 0.35f, 0.6f, 0.8f)
    private val TRAY = floatArrayOf(0.25f, 0.6f, 0.9f, 0.97f)

    fun clamp(level: Int): Int = level.coerceIn(MIN, MAX)

    fun bannerAlpha(level: Int): Float = BANNER[clamp(level)]

    fun trayAlpha(level: Int): Float = TRAY[clamp(level)]

    /** One step darker ([delta] +1) or clearer (-1), stopping at the ends. */
    fun step(
        level: Int,
        delta: Int,
    ): Int = clamp(level + delta)
}

/** Where a horizontal swipe on the BlackBerry 7 home goes: Hub is to the left, the apps to the right. */
enum class Bb7Swipe { Hub, Apps }

object Bb7Home {
    /** Icon cell width the tray is laid out from: 6 columns on the Q25's 411 dp. */
    const val CELL_DP = 64
    const val MIN_COLUMNS = 4
    const val MAX_COLUMNS = 8

    /** Rows of icons the closed tray shows on the home screen; one more opens the whole panel. */
    const val MIN_HOME_ROWS = 1
    const val MAX_HOME_ROWS = 3

    /** The system Settings app, always part of the starting home next to the phone's default apps. */
    const val SETTINGS_PACKAGE = "com.android.settings"

    fun columns(widthDp: Int): Int = (widthDp / CELL_DP).coerceIn(MIN_COLUMNS, MAX_COLUMNS)

    /**
     * The apps of one panel, in order. Favorites: the user's Home apps ([favoriteKeys], stable
     * keys) or, without any, the phone's [defaultPackages] (and Settings) that are installed; All: every app by
     * name; Downloads: apps the user installed (removable ones).
     */
    fun panelApps(
        panel: Bb7Panel,
        apps: List<AppEntry>,
        favoriteKeys: List<String>?,
        usage: AppUsage = AppUsage.EMPTY,
        defaultPackages: List<String> = emptyList(),
    ): List<AppEntry> {
        val byName = apps.sortedBy { it.label.lowercase() }
        return when (panel) {
            Bb7Panel.All -> byName
            Bb7Panel.Frequent ->
                byName
                    .filter { usage.score(it.stableKey()) > 0 }
                    .sortedByDescending { usage.score(it.stableKey()) }
                    .take(FREQUENT_APPS)
            Bb7Panel.Downloads -> byName.filter { it.canUninstall }
            Bb7Panel.Favorites -> {
                val byKey = apps.associateBy { it.stableKey() }
                val chosen = favoriteKeys.orEmpty().mapNotNull { byKey[it] }
                chosen.ifEmpty {
                    (defaultPackages + SETTINGS_PACKAGE).mapNotNull { pkg -> byName.firstOrNull { it.component.packageName == pkg } }
                }
            }
        }
    }

    /** Most-used apps the Frequent panel lists. */
    const val FREQUENT_APPS = 24

    /** The next panel left ([step] -1) or right (+1) among [panels], wrapping. */
    fun step(
        panel: Bb7Panel,
        step: Int,
        panels: List<Bb7Panel> = Bb7Panels.DEFAULT,
    ): Bb7Panel {
        val index = panels.indexOf(panel).takeIf { it >= 0 } ?: return panels.first()
        return panels[(index + step).mod(panels.size)]
    }

    /**
     * Typing on the home screen searches apps, as on BlackBerry 7: names starting with the text
     * first, then names with a word starting with it, then any match.
     */
    fun search(
        apps: List<AppEntry>,
        query: String,
    ): List<AppEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val byName = apps.sortedBy { it.label.lowercase() }
        val starts = byName.filter { it.label.lowercase().startsWith(q) }
        val word = byName.filter { it !in starts && it.label.lowercase().split(' ').any { w -> w.startsWith(q) } }
        val any = byName.filter { it !in starts && it !in word && q in it.label.lowercase() }
        return starts + word + any
    }

    /**
     * Moving the selection in a grid of [count] items and [columns] columns: Left/Right by one,
     * Up/Down by a row, clamped to the items.
     */
    fun move(
        index: Int,
        count: Int,
        columns: Int,
        dx: Int,
        dy: Int,
    ): Int {
        if (count <= 0) return 0
        return (index + dx + dy * columns).coerceIn(0, count - 1)
    }

    /**
     * A finished horizontal drag of [dragged] px (positive = finger moved right): far enough
     * ([threshold] px) to the right reveals Hub, to the left the apps, as on the BlackBerry 10 home.
     */
    fun swipe(
        dragged: Float,
        threshold: Float,
    ): Bb7Swipe? =
        when {
            dragged >= threshold -> Bb7Swipe.Hub
            dragged <= -threshold -> Bb7Swipe.Apps
            else -> null
        }

    /**
     * The closed tray's rows after a vertical drag of [dragged] px (negative = finger moved up),
     * one row per [threshold] px, as the BlackBerry 7 home let users drag the rows of apps up or
     * down. Above [MAX_HOME_ROWS] means open the whole panel; the result is never under
     * [MIN_HOME_ROWS].
     */
    fun resizeRows(
        rows: Int,
        dragged: Float,
        threshold: Float,
    ): Int {
        if (threshold <= 0f) return rows
        val steps = (-dragged / threshold).toInt()
        return (rows + steps).coerceIn(MIN_HOME_ROWS, MAX_HOME_ROWS + 1)
    }

    /** Favourites as stable keys after adding or removing [key]; [current] is what the panel shows now. */
    fun toggleFavorite(
        current: List<String>,
        key: String,
    ): List<String> = HomeApps.toggle(current, key)
}

/**
 * A visit from the BlackBerry 7 home to the BlackBerry 10 screen for Hub or Edit Home. It ends,
 * back on the BlackBerry 7 home, once the BlackBerry 10 screen has left its home page and come
 * back to it (a swipe or Back from Hub, Done in Edit Home). A Recents visit (Active Frames, the
 * BlackBerry 10 home page itself) does not [returnOnHome]; Home ends it.
 */
data class Bb7Detour(
    val returnOnHome: Boolean = false,
    val leftHome: Boolean = false,
) {
    /** The BlackBerry 10 screen reported whether Back has somewhere to go (it is off its home page). */
    fun onCanNavigateBack(canNavigateBack: Boolean): Bb7Detour = if (canNavigateBack) copy(leftHome = true) else this

    /** True when the BlackBerry 10 screen is back on its home page after leaving it. */
    fun endsAt(canNavigateBack: Boolean): Boolean = returnOnHome && leftHome && !canNavigateBack
}
