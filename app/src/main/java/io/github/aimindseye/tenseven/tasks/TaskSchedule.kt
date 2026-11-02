package io.github.aimindseye.tenseven.tasks

import java.time.Instant
import java.time.ZoneId

/** Calendar-aware calculations; no exact timing promise. */
object TaskSchedule {
    fun nextOccurrence(epochMs: Long?, repeat: TaskRepeat, zoneName: String): Long? {
        if (epochMs == null || repeat == TaskRepeat.Never) return epochMs
        val at = Instant.ofEpochMilli(epochMs).atZone(ZoneId.of(zoneName))
        val shifted = when (repeat) {
            TaskRepeat.Daily -> at.plusDays(1)
            TaskRepeat.Weekly -> at.plusWeeks(1)
            TaskRepeat.Monthly -> at.plusMonths(1)
            TaskRepeat.Never -> at
        }
        return shifted.toInstant().toEpochMilli()
    }
    fun advanceAfter(epochMs: Long?, repeat: TaskRepeat, zoneName: String, nowMs: Long): Long? {
        val previous = epochMs ?: return null
        if (repeat == TaskRepeat.Never) return previous
        // Completing a future occurrence still advances to the NEXT one.
        var next = nextOccurrence(previous, repeat, zoneName) ?: return null
        // Bounded to resist corrupt/imported years-old records and avoid infinite loops.
        kotlin.repeat(4000) {
            if (next > nowMs) return next
            next = nextOccurrence(next, repeat, zoneName) ?: return null
        }
        return next
    }
    fun preset(name: String, now: Instant, zone: ZoneId): Long? {
        val local = now.atZone(zone)
        val whenAt = when (name) {
            "Later today" -> {
                val later = local.plusHours(2)
                if (later.toLocalDate() != local.toLocalDate()) local.toLocalDate().plusDays(1).atTime(9,0).atZone(zone) else later
            }
            "This evening" -> TaskReminderTimes.eveningAt(zone = zone, now = now).atZone(zone)
            "Tomorrow morning" -> TaskReminderTimes.tomorrowAt(9, 0, zone, now).atZone(zone)
            "Until complete" -> return null
            else -> error("Unknown reminder preset")
        }
        return whenAt.toInstant().toEpochMilli()
    }
    fun group(task: TaskRecord, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        if (task.completed) return "Completed"
        val due = task.dueAtEpochMs ?: return "No due date"
        val date = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        return when {
            !date.isAfter(today) -> "Today"
            date == today.plusDays(1) -> "Tomorrow"
            !date.isAfter(today.plusDays(7)) -> "Later this week"
            else -> "Upcoming"
        }
    }
}
