package io.github.aimindseye.tenseven.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId

class TaskScheduleTest {
    private val ny = ZoneId.of("America/New_York")

    @Test fun springForwardTomorrowMorningPreservesLocalTime() {
        val now = Instant.parse("2026-03-07T21:00:00Z")
        assertEquals(
            Instant.parse("2026-03-08T13:00:00Z").toEpochMilli(),
            TaskSchedule.preset("Tomorrow morning", now, ny),
        )
    }

    @Test fun nextDailyOccurrenceAcrossDst() {
        val before = Instant.parse("2026-03-07T14:00:00Z").toEpochMilli()
        val expected = Instant.parse("2026-03-08T13:00:00Z").toEpochMilli()
        assertEquals(expected, TaskSchedule.nextOccurrence(before, TaskRepeat.Daily, ny.id))
    }

    @Test fun skippedOccurrencesAdvancePastNow() {
        val old = Instant.parse("2026-03-07T14:00:00Z").toEpochMilli()
        val now = Instant.parse("2026-03-10T02:00:00Z").toEpochMilli()
        val next = TaskSchedule.advanceAfter(old, TaskRepeat.Daily, ny.id, now)
        assertEquals(Instant.parse("2026-03-10T13:00:00Z").toEpochMilli(), next)
    }

    @Test fun earlyCompletionStillMovesRepeatForward() {
        val original = Instant.parse("2026-03-09T13:00:00Z").toEpochMilli()
        val completed = Instant.parse("2026-03-08T19:00:00Z").toEpochMilli()
        assertEquals(
            Instant.parse("2026-03-10T13:00:00Z").toEpochMilli(),
            TaskSchedule.advanceAfter(original, TaskRepeat.Daily, ny.id, completed),
        )
    }

    @Test fun untilCompleteHasNoAndroidAlarm() {
        assertNull(TaskSchedule.preset("Until complete", Instant.now(), ny))
        assertEquals("No due date", TaskSchedule.group(TaskRecord(title = "Offline task")))
    }

    @Test fun metadataRoundTripWithoutNotificationText() {
        val dir = Files.createTempDirectory("task-source-").toFile()
        try {
            val token = "a".repeat(64)
            val item =
                TaskRecord(
                    title = "Follow up in Mail",
                    originPackage = "example.mail",
                    originToken = token,
                    linkedNoteId = "note-42",
                )
            val store = TaskStore(dir)
            store.save(item)
            assertEquals(item, store.list().single())
            assertTrue(
                store
                    .list()
                    .single()
                    .details
                    .isEmpty(),
            )
            store.delete(item.id)
            assertTrue(store.list().isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun groupingUsesLocalCalendarDatesForEveryListSection() {
        val now = Instant.parse("2026-11-02T16:00:00Z")

        fun task(days: Long) = TaskRecord(title = "Dated", dueAtEpochMs = now.plusSeconds(days * 86400).toEpochMilli())
        assertEquals("Today", TaskSchedule.group(task(-1), now, ny))
        assertEquals("Today", TaskSchedule.group(task(0), now, ny))
        assertEquals("Tomorrow", TaskSchedule.group(task(1), now, ny))
        assertEquals("Later this week", TaskSchedule.group(task(5), now, ny))
        assertEquals("Upcoming", TaskSchedule.group(task(9), now, ny))
        assertEquals("Completed", TaskSchedule.group(task(0).copy(completed = true), now, ny))
    }

    @Test fun presetAndRepeatBoundariesHandleMidnightEveningAndMonthEnd() {
        val utc = ZoneId.of("UTC")
        val late = Instant.parse("2026-11-02T23:00:00Z")
        assertEquals(Instant.parse("2026-11-03T09:00:00Z").toEpochMilli(), TaskSchedule.preset("Later today", late, utc))
        val morning = Instant.parse("2026-11-02T10:00:00Z")
        assertEquals(Instant.parse("2026-11-02T12:00:00Z").toEpochMilli(), TaskSchedule.preset("Later today", morning, utc))
        assertEquals(Instant.parse("2026-11-02T18:00:00Z").toEpochMilli(), TaskSchedule.preset("This evening", morning, utc))
        assertEquals(Instant.parse("2026-11-03T18:00:00Z").toEpochMilli(), TaskSchedule.preset("This evening", late, utc))
        val jan = Instant.parse("2026-01-31T09:00:00Z").toEpochMilli()
        assertEquals(Instant.parse("2026-02-28T09:00:00Z").toEpochMilli(), TaskSchedule.nextOccurrence(jan, TaskRepeat.Monthly, "UTC"))
        assertEquals(Instant.parse("2026-02-07T09:00:00Z").toEpochMilli(), TaskSchedule.nextOccurrence(jan, TaskRepeat.Weekly, "UTC"))
        assertEquals(jan, TaskSchedule.advanceAfter(jan, TaskRepeat.Never, "UTC", late.toEpochMilli()))
        assertNull(TaskSchedule.advanceAfter(null, TaskRepeat.Daily, "UTC", late.toEpochMilli()))
        assertTrue(runCatching { TaskSchedule.preset("Unknown", morning, utc) }.isFailure)
    }

    @Test fun manualTaskCanRepeatWithoutCalendar() {
        val item = TaskRecord(title = "Water plants", repeat = TaskRepeat.Weekly)
        assertNull(TaskSchedule.nextOccurrence(item.dueAtEpochMs, item.repeat, item.zoneId))
    }
}
