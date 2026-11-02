package io.github.aimindseye.tenseven.model

/** Keep the selected secondary category visible, even after its button requested a collapsed menu. */
internal object HubNavigationPolicy {
    fun showSecondaryControls(
        expanded: Boolean,
        currentTab: HubFilter,
        snoozed: Boolean,
        reminders: Boolean,
        sourcePackage: String?,
    ): Boolean = expanded || snoozed || reminders || sourcePackage != null ||
        currentTab == HubFilter.Calls || currentTab == HubFilter.Contacts
}
