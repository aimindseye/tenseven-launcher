package io.github.aimindseye.tenseven.tasks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.security.MessageDigest

/** Named presentation/protocol values; numeric values unchanged from the reviewed source. */
private const val TASK_REPOSITORY_FOLLOW_UP_MAX_100 = 100

/** One repository for Hub follow-ups, stand-alone Tasks and Notes links. Never dependent on UI activity state. */
interface TaskScheduler {
    fun cancel(id: String)

    fun schedule(task: TaskRecord)
}

class TaskRepository(
    private val store: TaskStore,
    private val scheduler: TaskScheduler,
    private val now: () -> Long = System::currentTimeMillis,
) {
    companion object {
        var revision by androidx.compose.runtime.mutableIntStateOf(0)
            private set
    }

    fun list(): List<TaskRecord> = store.list()

    fun save(task: TaskRecord) {
        // Commit to disk first; a failed write must not cancel a previously scheduled reminder.
        store.save(task)
        revision++
        scheduler.cancel(task.id)
        scheduler.schedule(task)
    }

    fun delete(id: String) {
        scheduler.cancel(id)
        store.delete(id)
        revision++
    }

    fun complete(task: TaskRecord) {
        val recurringWithSchedule =
            !task.completed && task.repeat != TaskRepeat.Never &&
                (task.dueAtEpochMs != null || task.remindAtEpochMs != null)
        val next =
            if (recurringWithSchedule) {
                val nextDue = TaskSchedule.advanceAfter(task.dueAtEpochMs, task.repeat, task.zoneId, now())
                val nextRemind = TaskSchedule.advanceAfter(task.remindAtEpochMs, task.repeat, task.zoneId, now())
                task.copy(dueAtEpochMs = nextDue, remindAtEpochMs = nextRemind, updatedAtEpochMs = now())
            } else {
                task.copy(completed = !task.completed, updatedAtEpochMs = now())
            }
        save(next)
    }

    /** Explicit opt-in; stores only generic source label, source package and a one-way key digest. */
    fun followUp(
        packageName: String,
        notificationKey: String,
        appName: String,
        remindAt: Long?,
    ): TaskRecord {
        val token =
            MessageDigest
                .getInstance("SHA-256")
                .digest((packageName + "\n" + notificationKey).toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 255) }
        val existing = list().firstOrNull { !it.completed && it.originToken == token }
        val task =
            existing?.copy(remindAtEpochMs = remindAt, updatedAtEpochMs = now())
                ?: TaskRecord(
                    title = "Follow up in " + appName.take(TASK_REPOSITORY_FOLLOW_UP_MAX_100),
                    remindAtEpochMs = remindAt,
                    originPackage = packageName,
                    originToken = token,
                )
        save(task)
        return task
    }

    fun rescheduleAll() = list().forEach { scheduler.schedule(it) }
}
