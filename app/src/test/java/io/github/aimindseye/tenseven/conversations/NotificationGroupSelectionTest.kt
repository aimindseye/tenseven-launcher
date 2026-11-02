package io.github.aimindseye.tenseven.conversations

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationGroupSelectionTest {
    private val summary = NotificationGroupEntry("summary", "yahoo", "account-a", true)
    private val child = NotificationGroupEntry("child", "yahoo", "account-a", false)

    @Test fun summaryOnlyMailAppearsThenChildrenReplaceItWithoutDuplication() {
        assertEquals(setOf("summary"), NotificationGroupSelection.visibleKeys(listOf(summary)))
        assertEquals(setOf("child"), NotificationGroupSelection.visibleKeys(listOf(summary, child)))
        assertEquals(setOf("child", "child-2"), NotificationGroupSelection.visibleKeys(
            listOf(summary, child, child.copy(key = "child-2"))))
        // Removal of the last usable child restores the still-active summary.
        assertEquals(setOf("summary"), NotificationGroupSelection.visibleKeys(listOf(summary)))
    }

    @Test fun anotherProviderOrAccountDoesNotSuppressAnUnrelatedSummary() {
        assertEquals(setOf("summary", "gmail"), NotificationGroupSelection.visibleKeys(
            listOf(summary, child.copy(key = "gmail", packageName = "gmail"))))
        assertEquals(setOf("summary", "other-account"), NotificationGroupSelection.visibleKeys(
            listOf(summary, child.copy(key = "other-account", groupKey = "account-b"))))
        assertEquals(setOf("summary", "child"), NotificationGroupSelection.visibleKeys(
            listOf(summary.copy(groupKey = null), child.copy(groupKey = null))))
    }
}
