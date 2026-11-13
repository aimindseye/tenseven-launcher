package io.github.aimindseye.tenseven.tasks

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.LauncherTheme
import io.github.aimindseye.tenseven.TenSeven2AppTheme
import kotlinx.coroutines.delay
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private object TaskLimits {
    const val BYTE_MASK = 255
    const val SOURCE_TITLE_LENGTH = 100
    const val REFRESH_INTERVAL_MS = 60_000L
    const val NOTIFICATION_PERMISSION_REQUEST = 7302
    const val MAX_TITLE_CHARS = 240
    const val MAX_DETAILS_CHARS = 16000
    const val MAX_TAG_INPUT_CHARS = 1500
    const val MAX_NOTE_ID_CHARS = 240
    const val MAX_TAG_CHARS = 60
    const val MAX_TAGS = 24
}

/** Always-active internal screen. The optional launcher icon is managed independently. */
class TasksActivity : ComponentActivity() {
    private var resumeRevision by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        io.github.aimindseye.tenseven.platform.FrameWindowRepository
            .local(this, javaClass.name)
        resumeRevision++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        io.github.aimindseye.tenseven.design.DesignText.t
            .bindTo(this)
        setContent {
            LauncherTheme(window = window) {
                TenSeven2AppTheme { TasksWorkspace(intent, resumeRevision) }
            }
        }
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_SOURCE_PACKAGE = "source_package"
        const val EXTRA_SOURCE_KEY = "source_key"
        const val EXTRA_SOURCE_APP = "source_app"
        const val EXTRA_NOTE_ID = "note_id"
    }
}

private class TasksWorkspaceActions(
    val onNew: () -> Unit,
    val onEdit: (TaskRecord) -> Unit,
    val onComplete: (TaskRecord) -> Unit,
    val onDelete: (TaskRecord) -> Unit,
    val onTomorrow: (TaskRecord) -> Unit,
)

private fun tasksInitialDraft(
    sourcePackage: String?,
    sourceKey: String?,
    sourceApp: String?,
    linkedNoteId: String?,
): TaskRecord? =
    if (sourcePackage != null && sourceKey != null && sourceApp != null) {
        val digest =
            MessageDigest
                .getInstance("SHA-256")
                .digest((sourcePackage + "\n" + sourceKey).toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and TaskLimits.BYTE_MASK) }
        TaskRecord(
            title = "Follow up in " + sourceApp.take(TaskLimits.SOURCE_TITLE_LENGTH),
            originPackage = sourcePackage,
            originToken = digest,
        )
    } else if (linkedNoteId != null) {
        TaskRecord(title = "New task", linkedNoteId = linkedNoteId)
    } else {
        null
    }

@Composable
private fun TasksWorkspace(
    intent: Intent,
    resumeRevision: Int,
) {
    val context = LocalContext.current
    val repo =
        remember(context) {
            io.github.aimindseye.tenseven.platform
                .taskRepository(context)
        }
    var revision by remember { mutableIntStateOf(0) }
    var tab by remember { mutableStateOf(intent.getStringExtra(TasksActivity.EXTRA_MODE) ?: "tasks") }
    var editing by remember { mutableStateOf<TaskRecord?>(null) }
    var newDraft by remember { mutableStateOf(false) }
    val sourcePackage = intent.getStringExtra(TasksActivity.EXTRA_SOURCE_PACKAGE)
    val sourceKey = intent.getStringExtra(TasksActivity.EXTRA_SOURCE_KEY)
    val sourceApp = intent.getStringExtra(TasksActivity.EXTRA_SOURCE_APP)
    val linkedNoteId = intent.getStringExtra(TasksActivity.EXTRA_NOTE_ID)
    val draft =
        remember(sourcePackage, sourceKey, linkedNoteId) {
            tasksInitialDraft(sourcePackage, sourceKey, sourceApp, linkedNoteId)
        }
    var consumedDraft by remember { mutableStateOf(false) }
    if (!consumedDraft && draft != null && editing == null) {
        editing = draft
        newDraft = draft.originPackage == null
        consumedDraft = true
    }
    val records = remember(revision, resumeRevision, TaskRepository.revision) { repo.list() }

    fun refresh() {
        revision++
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TaskLimits.REFRESH_INTERVAL_MS)
            refresh()
        }
    }
    BackHandler(enabled = editing != null) { editing = null }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        if (editing != null) {
            TaskEditor(task = editing!!, isNew = newDraft, onCancel = { editing = null }, onSave = { next ->
                repo.save(next)
                editing = null
                refresh()
            }, onDelete = { next ->
                repo.delete(next.id)
                editing = null
                refresh()
            })
        } else {
            TasksOverview(
                tab = tab,
                records = records,
                onTabChange = { tab = it },
                actions =
                    TasksWorkspaceActions(
                        onNew = {
                            editing = TaskRecord(title = "New task")
                            newDraft = true
                        },
                        onEdit = { task ->
                            editing = task
                            newDraft = false
                        },
                        onComplete = { task ->
                            repo.complete(task)
                            refresh()
                        },
                        onDelete = { task ->
                            repo.delete(task.id)
                            refresh()
                        },
                        onTomorrow = { task ->
                            repo.save(
                                task.copy(
                                    completed = false,
                                    remindAtEpochMs =
                                        TaskSchedule.preset(
                                            "Tomorrow morning",
                                            Instant.now(),
                                            ZoneId.systemDefault(),
                                        ),
                                    updatedAtEpochMs = System.currentTimeMillis(),
                                ),
                            )
                            refresh()
                        },
                    ),
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun TasksOverview(
    tab: String,
    records: List<TaskRecord>,
    onTabChange: (String) -> Unit,
    actions: TasksWorkspaceActions,
) {
    val context = LocalContext.current
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("TenSeven Tasks", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onTabChange("tasks") }) { Text("Tasks") }
            OutlinedButton(onClick = { onTabChange("remind") }) { Text("Remind") }
            Button(onClick = actions.onNew) { Text("New") }
        }
        if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            OutlinedButton(onClick = {
                (context as? android.app.Activity)?.requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    TaskLimits.NOTIFICATION_PERMISSION_REQUEST,
                )
            }) { Text("Enable Android reminder alerts (optional)") }
        }
        Text(
            "Local on-device tasks. Reminders stay available here even without Android notification permission.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TasksGroupedList(tab, records, actions)
    }
}

@Suppress("FunctionNaming")
@Composable
private fun TasksGroupedList(
    tab: String,
    records: List<TaskRecord>,
    actions: TasksWorkspaceActions,
) {
    val shown = records.filter { tab != "remind" || (it.remindAtEpochMs != null || it.originPackage != null) }
    if (shown.isEmpty()) Text(if (tab == "remind") "No follow-ups yet" else "No tasks yet")
    val groups =
        if (tab == "remind") {
            listOf("Overdue", "Upcoming", "Until complete", "Completed")
        } else {
            listOf("Today", "Tomorrow", "Later this week", "Upcoming", "No due date", "Completed")
        }
    for (group in groups) {
        val items = shown.filter { task -> taskBelongsToGroup(task, tab, group) }
        if (items.isNotEmpty()) TasksGroup(group, items, actions)
    }
}

private fun taskBelongsToGroup(
    task: TaskRecord,
    tab: String,
    group: String,
): Boolean {
    val category =
        if (tab != "remind") {
            TaskSchedule.group(task)
        } else {
            when {
                task.completed -> "Completed"
                task.remindAtEpochMs == null -> "Until complete"
                task.remindAtEpochMs <= System.currentTimeMillis() -> "Overdue"
                else -> "Upcoming"
            }
        }
    return category == group
}

@Suppress("FunctionNaming")
@Composable
private fun TasksGroup(
    group: String,
    tasks: List<TaskRecord>,
    actions: TasksWorkspaceActions,
) {
    Text(group, style = MaterialTheme.typography.titleMedium)
    tasks.forEach { task ->
        TaskItem(
            task,
            onOpen = { actions.onEdit(task) },
            onComplete = { actions.onComplete(task) },
            onDelete = { actions.onDelete(task) },
            onTomorrow = { actions.onTomorrow(task) },
        )
    }
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
                                    context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
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

@Suppress("FunctionNaming")
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

@Suppress("FunctionNaming")
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

@Suppress("FunctionNaming")
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
    isNew: Boolean,
    onCancel: () -> Unit,
    onSave: (TaskRecord) -> Unit,
    onDelete: (TaskRecord) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val context = LocalContext.current
    val draft = remember(task.id) { TaskEditDraft(task, isNew) }
    val zone = ZoneId.systemDefault()
    Column(
        modifier = modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (isNew) "New task" else "Task details", style = MaterialTheme.typography.titleLarge)
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
            if (!isNew) TextButton(onClick = { onDelete(task) }) { Text("Delete") }
        }
    }
}

private fun whenFormatted(epochMs: Long): String =
    DateTimeFormatter
        .ofPattern("MMM d, yyyy h:mm a")
        .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
