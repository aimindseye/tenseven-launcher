package io.github.aimindseye.tenseven.tasks

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private object TaskLimits {
    const val MAX_TITLE_CHARS = 240
    const val MAX_DETAILS_CHARS = 16000
    const val MAX_TAG_INPUT_CHARS = 1500
    const val MAX_NOTE_ID_CHARS = 240
    const val MAX_TAG_CHARS = 60
    const val MAX_TAGS = 24
}

@Composable
internal fun TaskItem(
    task: TaskRecord,
    onOpen: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onTomorrow: () -> Unit,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Checkbox(checked = task.completed, onCheckedChange = { onComplete() })
            Column(modifier = Modifier.weight(1f).clickable(onClick = onOpen)) {
                Text(task.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    listOfNotNull(
                        task.dueAtEpochMs?.let { "Due " + whenFormatted(it) },
                        task.remindAtEpochMs?.let { "Remind " + whenFormatted(it) },
                        task.originPackage?.let { pkg ->
                            "From " +
                                runCatching {
                                    context.packageManager
                                        .getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0))
                                        .toString()
                                }.getOrDefault("Unavailable app")
                        },
                    ).joinToString(" · ").ifBlank { "No date" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = onOpen) { Text("Edit") }
            TextButton(onClick = onTomorrow, enabled = !task.completed) { Text("Tomorrow") }
            if (task.originPackage != null) {
                TextButton(onClick = {
                    val launch = context.packageManager.getLaunchIntentForPackage(task.originPackage)
                    if (launch != null) {
                        context.startActivity(launch)
                    } else {
                        android.widget.Toast
                            .makeText(context, "Source app unavailable", android.widget.Toast.LENGTH_SHORT)
                            .show()
                    }
                }) { Text("Open source") }
            }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

private fun showTaskDatePicker(
    context: android.content.Context,
    zone: ZoneId,
    current: Long?,
    set: (Long) -> Unit,
) {
    val at = current?.let { Instant.ofEpochMilli(it).atZone(zone) } ?: java.time.ZonedDateTime.now(zone)
    DatePickerDialog(context, { _, year, month, day ->
        TimePickerDialog(context, { _, hour, minute ->
            set(
                LocalDateTime
                    .of(year, month + 1, day, hour, minute)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli(),
            )
        }, at.hour, at.minute, true).show()
    }, at.year, at.monthValue - 1, at.dayOfMonth).show()
}

@Composable
private fun TaskTitleDetailsFields(
    title: String,
    details: String,
    onTitle: (String) -> Unit,
    onDetails: (String) -> Unit,
) {
    OutlinedTextField(
        value = title,
        onValueChange = { onTitle(it.take(TaskLimits.MAX_TITLE_CHARS)) },
        label = { Text("Title") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = details,
        onValueChange = { onDetails(it.take(TaskLimits.MAX_DETAILS_CHARS)) },
        label = { Text("Details (optional)") },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 4,
    )
}

private data class TaskScheduleActions(
    val onDue: () -> Unit,
    val onClearDue: () -> Unit,
    val onReminder: () -> Unit,
    val onClearReminder: () -> Unit,
    val onPreset: (String) -> Unit,
)

@Composable
private fun TaskScheduleControls(
    due: Long?,
    reminder: Long?,
    actions: TaskScheduleActions,
) {
    Text("Due: " + (due?.let(::whenFormatted) ?: "No due date"))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = actions.onDue) { Text("Set due") }
        TextButton(onClick = actions.onClearDue) { Text("Clear") }
    }
    Text("Remind: " + (reminder?.let(::whenFormatted) ?: "Until complete / none"))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = actions.onReminder) { Text("Choose time") }
        TextButton(onClick = actions.onClearReminder) { Text("Clear") }
    }
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (preset in listOf("Later today", "This evening", "Tomorrow morning", "Until complete")) {
            OutlinedButton(onClick = { actions.onPreset(preset) }) { Text(preset) }
        }
    }
}

private class TaskEditDraft(
    task: TaskRecord,
    isNew: Boolean,
) {
    var title by mutableStateOf(if (isNew) "" else task.title)
    var details by mutableStateOf(task.details)
    var priority by mutableStateOf(task.priority)
    var repeat by mutableStateOf(task.repeat)
    var due by mutableStateOf(task.dueAtEpochMs)
    var reminder by mutableStateOf(task.remindAtEpochMs)
    var tags by mutableStateOf(task.tags.joinToString(", "))
    var noteId by mutableStateOf(task.linkedNoteId.orEmpty())

    fun updatedTask(
        task: TaskRecord,
        zone: ZoneId,
    ): TaskRecord {
        val cleaned =
            tags
                .split(",")
                .map { it.trim().take(TaskLimits.MAX_TAG_CHARS) }
                .filter { it.isNotEmpty() }
                .take(TaskLimits.MAX_TAGS)
                .toSet()
        return task.copy(
            title = title.trim(),
            details = details,
            priority = priority,
            repeat = repeat,
            dueAtEpochMs = due,
            remindAtEpochMs = reminder,
            tags = cleaned,
            linkedNoteId = noteId.trim().ifEmpty { null },
            zoneId = zone.id,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
    }
}

@Composable
private fun TaskTagsFields(draft: TaskEditDraft) {
    OutlinedTextField(
        value = draft.tags,
        onValueChange = { draft.tags = it.take(TaskLimits.MAX_TAG_INPUT_CHARS) },
        label = { Text("Tags (comma separated)") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.noteId,
        onValueChange = { draft.noteId = it.take(TaskLimits.MAX_NOTE_ID_CHARS) },
        label = { Text("Linked note ID (optional)") },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun TaskEditor(
    task: TaskRecord,
    onCancel: () -> Unit,
    onSave: (TaskRecord) -> Unit,
    onDelete: (TaskRecord) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val draft = remember(task.id) { TaskEditDraft(task, false) }
    val zone = ZoneId.systemDefault()
    Column(
        modifier = modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Task details", style = MaterialTheme.typography.titleLarge)
        TaskTitleDetailsFields(
            title = draft.title,
            details = draft.details,
            onTitle = { draft.title = it },
            onDetails = { draft.details = it },
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(onClick = {
                draft.priority = TaskPriority.entries[(draft.priority.ordinal + 1) % TaskPriority.entries.size]
            }) { Text("Priority: " + draft.priority.name) }
            OutlinedButton(onClick = {
                draft.repeat = TaskRepeat.entries[(draft.repeat.ordinal + 1) % TaskRepeat.entries.size]
            }) { Text("Repeat: " + draft.repeat.name) }
        }
        TaskScheduleControls(
            due = draft.due,
            reminder = draft.reminder,
            actions =
                TaskScheduleActions(
                    onDue = { showTaskDatePicker(context, zone, draft.due) { draft.due = it } },
                    onClearDue = { draft.due = null },
                    onReminder = { showTaskDatePicker(context, zone, draft.reminder) { draft.reminder = it } },
                    onClearReminder = { draft.reminder = null },
                    onPreset = { preset -> draft.reminder = TaskSchedule.preset(preset, Instant.now(), zone) },
                ),
        )
        TaskTagsFields(draft)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { if (draft.title.isNotBlank()) onSave(draft.updatedTask(task, zone)) },
                enabled = draft.title.isNotBlank(),
            ) { Text("Save") }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            TextButton(onClick = { onDelete(task) }) { Text("Delete") }
        }
    }
}

private fun whenFormatted(epochMs: Long): String =
    DateTimeFormatter
        .ofPattern("MMM d, yyyy h:mm a")
        .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
