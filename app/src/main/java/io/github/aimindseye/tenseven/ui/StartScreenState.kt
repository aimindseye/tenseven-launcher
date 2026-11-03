package io.github.aimindseye.tenseven.ui

import android.provider.Settings
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.model.StartPages
import java.util.Locale

internal enum class SableStartDestination {
    /** The Start pages: Hub, Active Frames, app grid ([StartPages]). */
    Start,
    Apps,
    Search,
    Appearance,
    Peek,
    EditHome,
    SlotOptions,

    /** All Apps "Choose base bar slot" for an app when the bar is full. */
    SlotPicker,
}

/** One quick bar slot resolved against the installed apps. */
internal data class QuickSlot(
    val token: String,
    val label: String,
    /** The app a role or app slot opens; null for Command and All Apps. */
    val app: AppEntry?,
)

internal data class AppRailRow(
    val app: AppEntry,
    val section: String,
    val startsSection: Boolean,
)

internal data class SearchCommand(
    val title: String,
    val subtitle: String,
    val mark: String,
    /** Where the result comes from, shown on the row ("Settings", "Contacts"). */
    val source: String,
    val onClick: () -> Unit,
    /** Per-result actions (Menu or Alt+Enter on the row); empty means "open" only. */
    val actions: List<Pair<String, () -> Unit>> = emptyList(),
)

/** What Edit Home saves with Done. */
internal data class HomeEdit(
    val gridOrder: List<String>,
    val quickBar: List<String>,
    val glanceWidgets: List<String>,
    val hiddenSections: Set<String>,
)

internal fun destinationFor(screen: String): SableStartDestination =
    when (screen.lowercase(Locale.ROOT)) {
        "apps" -> SableStartDestination.Apps
        "search" -> SableStartDestination.Search
        "appearance" -> SableStartDestination.Appearance
        "quickbar", "home", "edit" -> SableStartDestination.EditHome
        else -> SableStartDestination.Start
    }

internal fun backDestination(
    current: SableStartDestination,
    returnTo: SableStartDestination,
): SableStartDestination =
    when (current) {
        SableStartDestination.Search, SableStartDestination.Peek -> returnTo
        SableStartDestination.SlotPicker -> SableStartDestination.Peek
        else -> SableStartDestination.Start
    }

// A Glance or Home module card; the focused card is filled with the accent.
