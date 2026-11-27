package io.github.aimindseye.tenseven.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class TaskScheduleBoundariesTest {
    @Test
    fun corruptAncientRepeatStopsAtTheExistingBoundRatherThanLoopingForever() {
        val previous = Instant.parse("2000-01-01T09:00:00Z")
        val now = Instant.parse("2100-01-01T09:00:00Z")
        assertEquals(
            previous
                .atZone(java.time.ZoneId.of("UTC"))
                .plusDays(4001)
                .toInstant()
                .toEpochMilli(),
            TaskSchedule.advanceAfter(previous.toEpochMilli(), TaskRepeat.Daily, "UTC", now.toEpochMilli()),
        )
    }

    @Test
    fun noDateAndNonRepeatingTasksDoNotResolveInvalidTimeZones() {
        assertNull(TaskSchedule.advanceAfter(null, TaskRepeat.Daily, "invalid-zone", 0L))
        assertEquals(1L, TaskSchedule.advanceAfter(1L, TaskRepeat.Never, "invalid-zone", 0L))
        assertEquals("Completed", TaskSchedule.group(TaskRecord(title = "Done", completed = true)))
    }
}
