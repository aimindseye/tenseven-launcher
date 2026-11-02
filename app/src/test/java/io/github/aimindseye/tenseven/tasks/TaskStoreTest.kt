package io.github.aimindseye.tenseven.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class TaskStoreTest {
    @Test fun taskRoundTripWithoutCalendarOrNotesDependency() {
        val dir = Files.createTempDirectory("tenseven-tasks-").toFile()
        try {
            val store = TaskStore(dir)
            val task = TaskRecord(
                title = "Email the report", details = "Line one\nRésumé and Gujarati ગુજરાતી",
                dueAtEpochMs = 1_900_000_000_000L, remindAtEpochMs = 1_899_999_000_000L,
                priority = TaskPriority.High, tags = setOf("work", "home"),
                zoneId = "America/New_York", updatedAtEpochMs = 16L,
            )
            store.save(task)
            assertEquals(listOf(task), store.list())
            store.save(task.copy(completed = true))
            assertTrue(store.list().single().completed)
            store.delete(task.id)
            assertTrue(store.list().isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun invalidAndCorruptRecordsAreIgnored() {
        val dir = Files.createTempDirectory("tenseven-task-invalid-").toFile()
        try {
            val store = TaskStore(dir)
            FileLikeCorrupt.write(dir, UUID.randomUUID().toString())
            assertTrue(store.list().isEmpty())
            assertTrue(runCatching { store.save(TaskRecord(title = "")) }.isFailure)
        } finally { dir.deleteRecursively() }
    }

    @Test fun failedAtomicReplacementCleansTemporaryFileAndKeepsExistingData() {
        val dir = Files.createTempDirectory("task-atomic-failure-").toFile()
        try {
            val task = TaskRecord(title = "Must not erase data")
            val destination = java.io.File(dir, task.id + ".task").apply { mkdir() }
            val existing = java.io.File(destination, "existing").apply { writeText("retain") }
            assertTrue(runCatching { TaskStore(dir).save(task) }.isFailure)
            assertEquals("retain", existing.readText())
            assertTrue(dir.listFiles()!!.none { it.name.contains(".tmp-") })
        } finally { dir.deleteRecursively() }
    }

    @Test fun reminderTimesUseLocalZoneAcrossDst() {
        val zone = ZoneId.of("America/New_York")
        val now = Instant.parse("2026-03-07T21:00:00Z")
        val tomorrow = TaskReminderTimes.tomorrowAt(9, 0, zone, now)
        assertEquals(Instant.parse("2026-03-08T13:00:00Z"), tomorrow)
        val evening = TaskReminderTimes.eveningAt(now = now, zone = zone)
        assertEquals(Instant.parse("2026-03-07T23:00:00Z"), evening)
        assertFalse(tomorrow == evening)
    }

    private object FileLikeCorrupt {
        fun write(dir: java.io.File, id: String) {
            java.io.File(dir, "$id.task").writeText("incorrect-format")
        }
    }
}
