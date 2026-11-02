package io.github.aimindseye.tenseven.tasks

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderPolicyTest {
    @Test fun badgeCountsPendingFollowUpsAndScheduledTasksButNotCompletedOrPlainTasks() {
        val records = listOf(
            TaskRecord(title = "Overdue", remindAtEpochMs = 1L),
            TaskRecord(title = "Upcoming", remindAtEpochMs = Long.MAX_VALUE),
            TaskRecord(title = "Until complete", originPackage = "mail"),
            TaskRecord(title = "Done", originPackage = "mail", completed = true),
            TaskRecord(title = "Plain to-do"),
        )
        assertEquals(3, ReminderPolicy.pendingCount(records))
        assertEquals(listOf("Overdue", "Upcoming", "Until complete", "Done"),
            ReminderPolicy.reminders(records).map { it.title })
    }

    @Test fun editsCompletionAndDeletionChangeTheSameListUsedByTheBadge() {
        val initial = TaskRecord(title = "Follow-up", originPackage = "chat")
        assertEquals(1, ReminderPolicy.pendingCount(listOf(initial)))
        assertEquals(0, ReminderPolicy.pendingCount(listOf(initial.copy(completed = true))))
        assertEquals(0, ReminderPolicy.pendingCount(emptyList()))
    }
}
