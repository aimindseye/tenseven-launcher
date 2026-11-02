package io.github.aimindseye.tenseven.tasks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** One repository for Hub follow-ups, stand-alone Tasks and Notes links. Never dependent on UI activity state. */
class TaskRepository(private val context: Context) {
    companion object {
        var revision by androidx.compose.runtime.mutableIntStateOf(0)
            private set
    }

    private val store = TaskStore(context.filesDir.resolve("tasks-v1"))
    fun list(): List<TaskRecord> = store.list()
    fun save(task: TaskRecord) {
        // Commit to disk first; a failed write must not cancel a previously scheduled reminder.
        store.save(task)
        revision++
        TaskAlarms.cancel(context, task.id)
        TaskAlarms.schedule(context, task)
    }
    fun delete(id: String) {
        TaskAlarms.cancel(context, id)
        store.delete(id)
        revision++
    }
    fun complete(task: TaskRecord) {
        val next = if (!task.completed && task.repeat != TaskRepeat.Never &&
            (task.dueAtEpochMs != null || task.remindAtEpochMs != null)) {
            val nextDue = TaskSchedule.advanceAfter(task.dueAtEpochMs, task.repeat, task.zoneId, System.currentTimeMillis())
            val nextRemind = TaskSchedule.advanceAfter(task.remindAtEpochMs, task.repeat, task.zoneId, System.currentTimeMillis())
            task.copy(dueAtEpochMs = nextDue, remindAtEpochMs = nextRemind, updatedAtEpochMs = System.currentTimeMillis())
        } else task.copy(completed = !task.completed, updatedAtEpochMs = System.currentTimeMillis())
        save(next)
    }
    /** Explicit opt-in; stores only generic source label, source package and a one-way key digest. */
    fun followUp(packageName: String, notificationKey: String, appName: String, remindAt: Long?): TaskRecord {
        val token = MessageDigest.getInstance("SHA-256")
            .digest((packageName + "\n" + notificationKey).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val existing = list().firstOrNull { !it.completed && it.originToken == token }
        val task = existing?.copy(remindAtEpochMs = remindAt, updatedAtEpochMs = System.currentTimeMillis())
            ?: TaskRecord(title = "Follow up in " + appName.take(100), remindAtEpochMs = remindAt,
                originPackage = packageName, originToken = token)
        save(task)
        return task
    }
    fun rescheduleAll() = list().forEach { TaskAlarms.schedule(context, it) }
}

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
        val today = LocalDate.ofInstant(now, zone)
        return when {
            !date.isAfter(today) -> "Today"
            date == today.plusDays(1) -> "Tomorrow"
            !date.isAfter(today.plusDays(7)) -> "Later this week"
            else -> "Upcoming"
        }
    }
}

/** Android inexact, deferrable wake-ups. In-app overdue reminders never require notification access. */
object TaskAlarms {
    private fun intent(context: Context, id: String): PendingIntent {
        val i = Intent(context, TaskAlarmReceiver::class.java).apply {
            action = "io.github.aimindseye.tenseven.TASK_DUE"
            data = Uri.parse("tenseven-task://remind/" + id)
            putExtra("id", id)
        }
        return PendingIntent.getBroadcast(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    fun cancel(context: Context, id: String) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm?.cancel(intent(context, id))
    }
    fun schedule(context: Context, task: TaskRecord) {
        if (task.completed || task.remindAtEpochMs == null || task.remindAtEpochMs <= System.currentTimeMillis()) return
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        // setAndAllowWhileIdle is not an exact-alarm API and needs no SCHEDULE_EXACT_ALARM grant.
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.remindAtEpochMs, intent(context, task.id))
    }
}

class TaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                TaskRepository(context).rescheduleAll()
            }
            "io.github.aimindseye.tenseven.TASK_DUE" -> {
                val id = intent.getStringExtra("id") ?: return
                val task = TaskRepository(context).list().firstOrNull { it.id == id } ?: return
                if (task.completed || task.remindAtEpochMs == null) return
                if (task.remindAtEpochMs > System.currentTimeMillis()) {
                    TaskAlarms.schedule(context, task)
                    return
                }
                val manager = context.getSystemService(NotificationManager::class.java) ?: return
                if (Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
                manager.createNotificationChannel(NotificationChannel("tenseven_tasks", "TenSeven Remind",
                    NotificationManager.IMPORTANCE_DEFAULT))
                val open = Intent(context, TasksActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(TasksActivity.EXTRA_MODE, "remind")
                }
                val pending = PendingIntent.getActivity(context, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val notification = Notification.Builder(context, "tenseven_tasks")
                    .setSmallIcon(io.github.aimindseye.tenseven.R.mipmap.ic_tenseven)
                    .setContentTitle("TenSeven Remind")
                    // No user-entered or source-app text on a lock screen.
                    .setContentText("You have a follow-up to review")
                    .setVisibility(Notification.VISIBILITY_PRIVATE)
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .build()
                manager.notify(task.id.hashCode(), notification)
            }
        }
    }
}
