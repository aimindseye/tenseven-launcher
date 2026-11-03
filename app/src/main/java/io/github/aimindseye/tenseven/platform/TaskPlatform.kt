package io.github.aimindseye.tenseven.platform

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
import io.github.aimindseye.tenseven.tasks.TaskRecord
import io.github.aimindseye.tenseven.tasks.TaskStore
import io.github.aimindseye.tenseven.tasks.TaskRepository
import io.github.aimindseye.tenseven.tasks.TaskScheduler
import io.github.aimindseye.tenseven.tasks.TaskAlarmReceiver
import io.github.aimindseye.tenseven.tasks.TasksActivity

/** Android wiring only; task persistence, deduplication and completion stay in the tested domain. */
fun taskRepository(context: Context): TaskRepository {
    val app = context.applicationContext
    return TaskRepository(TaskStore(app.filesDir.resolve("tasks-v1")), object : TaskScheduler {
        override fun cancel(id: String) = TaskAlarms.cancel(app, id)
        override fun schedule(task: TaskRecord) = TaskAlarms.schedule(app, task)
    })
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

open class TaskAlarmReceiverBase : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                taskRepository(context).rescheduleAll()
            }
            "io.github.aimindseye.tenseven.TASK_DUE" -> {
                val id = intent.getStringExtra("id") ?: return
                val task = taskRepository(context).list().firstOrNull { it.id == id } ?: return
                if (task.completed || task.remindAtEpochMs == null) return
                if (task.remindAtEpochMs > System.currentTimeMillis()) {
                    TaskAlarms.schedule(context, task)
                    return
                }
                val manager = context.getSystemService(NotificationManager::class.java) ?: return
                if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
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
