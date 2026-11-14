package io.github.aimindseye.tenseven.model

/** Only nonempty Hub sections become top-level tabs. Commands and apps belong in Views. */
internal object HubVisibleTabs {
    fun items(
        messages: Int,
        email: Int,
        unread: Int,
        snoozed: Int,
        reminders: Int,
    ): List<String> =
        buildList {
            add("All")
            if (unread > 0) add("Unread")
            if (messages > 0) add("Messages")
            if (email > 0) add("Email")
            if (snoozed > 0) add("Snoozed")
            if (reminders > 0) add("Remind")
        }
}
