package io.github.aimindseye.tenseven.tasks

/** The Hub list and badge use the same records, including undated follow-ups and overdue items. */
object ReminderPolicy {
    fun reminders(records: List<TaskRecord>): List<TaskRecord> =
        records.filter {
            it.originPackage != null ||
                it.remindAtEpochMs != null
        }

    fun pendingCount(records: List<TaskRecord>): Int = reminders(records).count { !it.completed }
}
