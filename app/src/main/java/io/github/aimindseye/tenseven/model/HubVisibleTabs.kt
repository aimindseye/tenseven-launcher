package io.github.aimindseye.tenseven.model

/**
 * The Hub's top-level tabs, as on the approved board: All, Messages, Calls and Contacts (home UI proposal, Hub boards). Email, Unread,
 * Snoozed, Remind and Calendar live in Views.
 */
internal object HubVisibleTabs {
    fun items(): List<String> = listOf("All", "Messages", "Calls", "Contacts")
}
