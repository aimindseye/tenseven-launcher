package io.github.aimindseye.tenseven.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.aimindseye.tenseven.tasks.TaskEditor
import io.github.aimindseye.tenseven.tasks.TaskItem
import io.github.aimindseye.tenseven.tasks.TaskRecord
import io.github.aimindseye.tenseven.tasks.TaskRepository
import io.github.aimindseye.tenseven.tasks.TaskSchedule
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Follow-ups stay in the Hub; editing overlays it and never starts another Activity. */
@Composable
internal fun HubRemindersPanel(records: List<TaskRecord>) {
    val context = LocalContext.current
    val repository =
        remember(context) {
            io.github.aimindseye.tenseven.platform
                .taskRepository(context)
        }
    var editing by remember { mutableStateOf<TaskRecord?>(null) }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Remind", style = MaterialTheme.typography.titleLarge)
        if (records.none { !it.completed }) Text("No pending reminders", color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (completed in listOf(false, true)) {
            val items = records.filter { it.completed == completed }
            if (completed && items.isNotEmpty()) Text("Completed", style = MaterialTheme.typography.titleMedium)
            items.forEach { task ->
                Surface(color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
                    Column(Modifier.padding(8.dp)) {
                        TaskItem(
                            task,
                            onOpen = { editing = task },
                            onComplete = { repository.complete(task) },
                            onDelete = { repository.delete(task.id) },
                            onTomorrow = {
                                repository.save(
                                    task.copy(
                                        completed = false,
                                        remindAtEpochMs = TaskSchedule.preset("Tomorrow morning", Instant.now(), ZoneId.systemDefault()),
                                        updatedAtEpochMs = System.currentTimeMillis(),
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
    editing?.let { task ->
        Dialog(onDismissRequest = { editing = null }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                TaskEditor(
                    task = task,
                    isNew = false,
                    onCancel = { editing = null },
                    onSave = {
                        repository.save(it)
                        editing = null
                    },
                    onDelete = {
                        repository.delete(it.id)
                        editing = null
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
                )
            }
        }
    }
}

/** A custom time uses dialogs over the current Hub, with no persisted draft if cancelled. */
internal fun chooseHubReminderTime(
    context: Context,
    onChosen: (Long) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val initial = ZonedDateTime.now(zone).plusHours(1)
    DatePickerDialog(context, { _, year, month, day ->
        TimePickerDialog(
            context,
            { _, hour, minute ->
                val at =
                    LocalDateTime
                        .of(year, month + 1, day, hour, minute)
                        .atZone(zone)
                        .toInstant()
                        .toEpochMilli()
                if (at > System.currentTimeMillis()) {
                    onChosen(at)
                } else {
                    android.widget.Toast
                        .makeText(context, "Choose a future reminder time", android.widget.Toast.LENGTH_SHORT)
                        .show()
                }
            },
            initial.hour,
            initial.minute,
            android.text.format.DateFormat
                .is24HourFormat(context),
        ).show()
    }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
}
