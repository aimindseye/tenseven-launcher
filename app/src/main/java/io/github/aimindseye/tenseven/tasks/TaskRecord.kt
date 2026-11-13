package io.github.aimindseye.tenseven.tasks

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

enum class TaskPriority { Low, Normal, High }

enum class TaskRepeat { Never, Daily, Weekly, Monthly }

/**
 * One actionable task, independent of Android Calendar, Google services or an optional Notes UI.
 * Notification-derived content is intentionally NOT stored until the consent/encryption path lands.
 */
data class TaskRecord(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val details: String = "",
    val completed: Boolean = false,
    val dueAtEpochMs: Long? = null,
    val remindAtEpochMs: Long? = null,
    val zoneId: String = ZoneId.systemDefault().id,
    val priority: TaskPriority = TaskPriority.Normal,
    val repeat: TaskRepeat = TaskRepeat.Never,
    val tags: Set<String> = emptySet(),
    val linkedNoteId: String? = null,
    /** Source identity, never notification text or an Android PendingIntent. */
    val originPackage: String? = null,
    val originToken: String? = null,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
) {
    init {
        require(UUID_PATTERN.matches(id)) { "Invalid task ID" }
        require(title.isNotBlank() && title.length <= 240) { "Task title is required" }
        require(details.length <= 16_000) { "Task details too long" }
        require(dueAtEpochMs == null || dueAtEpochMs >= 0) { "Invalid due date" }
        require(remindAtEpochMs == null || remindAtEpochMs >= 0) { "Invalid reminder date" }
        require(updatedAtEpochMs >= 0) { "Invalid update time" }
        require(tags.size <= 24 && tags.all { it.isNotBlank() && it.length <= 60 }) { "Invalid tags" }
        require(originPackage == null || (originPackage.isNotBlank() && originPackage.length <= 240))
        require(originToken == null || originToken.matches(Regex("[0-9a-f]{64}")))
        ZoneId.of(zoneId)
    }

    companion object {
        private val UUID_PATTERN = Regex("[0-9a-f-]{36}")
    }
}

/** Pure calendar-free, timezone-aware scheduling policy, not an OS exact-alarm guarantee. */
object TaskReminderTimes {
    fun tomorrowAt(
        hour: Int,
        minute: Int,
        zone: ZoneId,
        now: Instant,
    ): Instant =
        now
            .atZone(zone)
            .toLocalDate()
            .plusDays(1)
            .atTime(hour, minute)
            .atZone(zone)
            .toInstant()

    fun eveningAt(
        hour: Int = 18,
        zone: ZoneId,
        now: Instant,
    ): Instant {
        require(hour in 16..22)
        val local = ZonedDateTime.ofInstant(now, zone)
        val today =
            local
                .toLocalDate()
                .atTime(hour, 0)
                .atZone(zone)
                .toInstant()
        return if (today > now) {
            today
        } else {
            local
                .toLocalDate()
                .plusDays(1)
                .atTime(hour, 0)
                .atZone(zone)
                .toInstant()
        }
    }
}
