package io.github.aimindseye.tenseven.tasks

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant

class TaskRepositoryTest {
    private class Scheduler(
        private val store: TaskStore,
    ) : TaskScheduler {
        val events = mutableListOf<String>()

        override fun cancel(id: String) {
            events += "cancel:$id"
        }

        override fun schedule(task: TaskRecord) {
            assertTrue("Persist before changing alarms", store.list().any { it == task })
            events += "schedule:${task.id}"
        }
    }

    @Test fun savesRestoreAcrossRepositoryInstancesAndDeleteCancelsTheAlarm() {
        val directory = Files.createTempDirectory("task-repository").toFile()
        try {
            val store = TaskStore(directory)
            val scheduler = Scheduler(store)
            val repo = TaskRepository(store, scheduler) { 1000L }
            val task = TaskRecord(title = "Prepare report")
            repo.save(task)
            assertEquals(listOf("cancel:${task.id}", "schedule:${task.id}"), scheduler.events)
            assertEquals(listOf(task), TaskRepository(TaskStore(directory), scheduler).list())
            scheduler.events.clear()
            repo.rescheduleAll()
            assertEquals(listOf("schedule:${task.id}"), scheduler.events)
            repo.delete(task.id)
            assertTrue(repo.list().isEmpty())
            assertEquals("cancel:${task.id}", scheduler.events.last())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun failedPersistenceDoesNotCancelAnExistingAlarm() {
        val directory = Files.createTempDirectory("task-write-failure").toFile()
        try {
            val blocked = File(directory, "not-a-directory").apply { writeText("blocked") }
            val store = TaskStore(blocked)
            val scheduler = Scheduler(store)
            assertTrue(runCatching { TaskRepository(store, scheduler).save(TaskRecord(title = "New")) }.isFailure)
            assertTrue(scheduler.events.isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun completionRepeatsAdvanceDueAndReminderAndCanRestoreCompletedTasks() {
        val directory = Files.createTempDirectory("task-repeat").toFile()
        try {
            val store = TaskStore(directory)
            val now = Instant.parse("2026-11-02T16:00:00Z").toEpochMilli()
            val repo = TaskRepository(store, Scheduler(store)) { now }
            val dated =
                TaskRecord(
                    title = "Daily",
                    zoneId = "UTC",
                    repeat = TaskRepeat.Daily,
                    dueAtEpochMs = now - 1000,
                    remindAtEpochMs = now - 2000,
                )
            repo.complete(dated)
            val next = repo.list().single()
            assertFalse(next.completed)
            assertTrue(next.dueAtEpochMs!! > now && next.remindAtEpochMs!! > now)
            assertEquals(now, next.updatedAtEpochMs)
            val reminderOnly =
                dated.copy(
                    id =
                        java.util.UUID
                            .randomUUID()
                            .toString(),
                    dueAtEpochMs = null,
                )
            repo.complete(reminderOnly)
            assertNull(repo.list().single { it.id == reminderOnly.id }.dueAtEpochMs)
            for (task in listOf(
                TaskRecord(title = "Once"),
                TaskRecord(title = "Undated repeat", repeat = TaskRepeat.Weekly),
                TaskRecord(title = "Already done", completed = true),
            )) {
                repo.complete(task)
                assertEquals(!task.completed, repo.list().single { it.id == task.id }.completed)
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun followUpsDedupeRescheduleAndKeepOnlyHashedNotificationIdentity() {
        val directory = Files.createTempDirectory("follow-up").toFile()
        try {
            val store = TaskStore(directory)
            val repo = TaskRepository(store, Scheduler(store)) { 1000L }
            val first = repo.followUp("example.mail", "private-original-key", "Mail", 2000L)
            val updated = repo.followUp("example.mail", "private-original-key", "Mail", null)
            assertEquals(first.id, updated.id)
            assertNull(updated.remindAtEpochMs)
            assertEquals(1, repo.list().size)
            assertEquals(64, updated.originToken!!.length)
            assertTrue(directory.listFiles()!!.none { it.readText().contains("private-original-key") })
            repo.followUp("other.mail", "private-original-key", "Other", null)
            assertEquals(2, repo.list().size)
            repo.complete(updated)
            val fresh = repo.followUp("example.mail", "private-original-key", "Mail", 3000L)
            assertNotEquals(updated.id, fresh.id)
            assertEquals(3, repo.list().size)
        } finally {
            directory.deleteRecursively()
        }
    }
}
