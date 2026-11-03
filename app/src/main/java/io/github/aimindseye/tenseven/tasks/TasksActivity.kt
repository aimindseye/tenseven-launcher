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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.security.MessageDigest
import kotlinx.coroutines.delay

/** Always-active internal screen. The optional launcher icon is managed independently. */
class TasksActivity : ComponentActivity() {
    private var resumeRevision by mutableIntStateOf(0)
    override fun onResume() {
        super.onResume()
        io.github.aimindseye.tenseven.platform.FrameWindowRepository.local(this, javaClass.name)
        resumeRevision++
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        io.github.aimindseye.tenseven.design.DesignText.t.bindTo(this)
        setContent {
            LauncherTheme(window = window) {
                TasksWorkspace(intent, resumeRevision)
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

@Composable
private fun TasksWorkspace(intent: Intent, resumeRevision: Int) {
    val context = LocalContext.current
    val repo = remember(context) { io.github.aimindseye.tenseven.platform.taskRepository(context) }
    var revision by remember { mutableIntStateOf(0) }
    var tab by remember { mutableStateOf(intent.getStringExtra(TasksActivity.EXTRA_MODE) ?: "tasks") }
    var editing by remember { mutableStateOf<TaskRecord?>(null) }
    var newDraft by remember { mutableStateOf(false) }
    val sourcePackage = intent.getStringExtra(TasksActivity.EXTRA_SOURCE_PACKAGE)
    val sourceKey = intent.getStringExtra(TasksActivity.EXTRA_SOURCE_KEY)
    val sourceApp = intent.getStringExtra(TasksActivity.EXTRA_SOURCE_APP)
    val linkedNoteId = intent.getStringExtra(TasksActivity.EXTRA_NOTE_ID)
    val draft = remember(sourcePackage, sourceKey, linkedNoteId) {
        if (sourcePackage != null && sourceKey != null && sourceApp != null) {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest((sourcePackage + "\n" + sourceKey).toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            TaskRecord(title = "Follow up in " + sourceApp.take(100), originPackage = sourcePackage, originToken = digest)
        } else if (linkedNoteId != null) {
            TaskRecord(title = "New task", linkedNoteId = linkedNoteId)
        } else null
    }
    var consumedDraft by remember { mutableStateOf(false) }
    if (!consumedDraft && draft != null && editing == null) {
        // Draft is NOT saved to disk until the user presses Save in the editor.
        editing = draft
        newDraft = draft.originPackage == null
        consumedDraft = true
    }
    val records = remember(revision, resumeRevision, TaskRepository.revision) { repo.list() }
    fun refresh() { revision++ }
    // Keep the in-app overdue list live even when the optional Android notification grant is denied.
    LaunchedEffect(Unit) { while (true) { delay(60_000L); refresh() } }
    BackHandler(enabled = editing != null) { editing = null }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        if (editing != null) {
            TaskEditor(
                task = editing!!,
                isNew = newDraft,
                onCancel = { editing = null },
                onSave = { next ->
                    repo.save(next)
                    editing = null
                    refresh()
                },
                onDelete = { next ->
                    repo.delete(next.id)
                    editing = null
                    refresh()
                },
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("TenSeven Tasks", style = MaterialTheme.typography.headlineSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { tab = "tasks" }) { Text("Tasks") }
                    OutlinedButton(onClick = { tab = "remind" }) { Text("Remind") }
                    Button(onClick = { editing = TaskRecord(title = "New task"); newDraft = true }) { Text("New") }
                }
                if (android.os.Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                        android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    OutlinedButton(onClick = {
                        (context as? android.app.Activity)?.requestPermissions(
                            arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7302)
                    }) { Text("Enable Android reminder alerts (optional)") }
                }
                Text(
                    "Local on-device tasks. Reminders stay available here even without Android notification permission.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val shown = records.filter {
                    tab != "remind" || (it.remindAtEpochMs != null || it.originPackage != null)
                }
                if (shown.isEmpty()) Text(if (tab == "remind") "No follow-ups yet" else "No tasks yet")
                val groups = if (tab == "remind") {
                    listOf("Overdue", "Upcoming", "Until complete", "Completed")
                } else {
                    listOf("Today", "Tomorrow", "Later this week", "Upcoming", "No due date", "Completed")
                }
                for (group in groups) {
                    val items = shown.filter { task ->
                        if (tab == "remind") {
                            when (group) {
                                "Completed" -> task.completed
                                "Until complete" -> !task.completed && task.remindAtEpochMs == null
                                "Overdue" -> !task.completed && task.remindAtEpochMs != null &&
                                    task.remindAtEpochMs <= System.currentTimeMillis()
                                else -> !task.completed && task.remindAtEpochMs != null &&
                                    task.remindAtEpochMs > System.currentTimeMillis()
                            }
                        } else TaskSchedule.group(task) == group
                    }
                    if (items.isNotEmpty()) {
                        Text(group, style = MaterialTheme.typography.titleMedium)
                        items.forEach { task ->
                            TaskItem(task,
                                onOpen = { editing = task; newDraft = false },
                                onComplete = { repo.complete(task); refresh() },
                                onDelete = { repo.delete(task.id); refresh() },
                                onTomorrow = {
                                    repo.save(task.copy(completed = false,
                                        remindAtEpochMs = TaskSchedule.preset(
                                            "Tomorrow morning", Instant.now(), ZoneId.systemDefault()),
                                        updatedAtEpochMs = System.currentTimeMillis()))
                                    refresh()
                                })
                        }
                    }
                }
            }
        }
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
                    listOfNotNull(task.dueAtEpochMs?.let { "Due " + whenFormatted(it) },
                        task.remindAtEpochMs?.let { "Remind " + whenFormatted(it) },
                        task.originPackage?.let { pkg ->
                            "From " + runCatching {
                                context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
                            }.getOrDefault("Unavailable app")
                        }).joinToString(" · ").ifBlank { "No date" },
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
                    if (launch != null) context.startActivity(launch)
                    else android.widget.Toast.makeText(context, "Source app unavailable", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("Open source") }
            }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
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
    var title by remember(task.id) { mutableStateOf(if (isNew) "" else task.title) }
    var details by remember(task.id) { mutableStateOf(task.details) }
    var priority by remember(task.id) { mutableStateOf(task.priority) }
    var repeat by remember(task.id) { mutableStateOf(task.repeat) }
    var due by remember(task.id) { mutableStateOf(task.dueAtEpochMs) }
    var reminder by remember(task.id) { mutableStateOf(task.remindAtEpochMs) }
    var tags by remember(task.id) { mutableStateOf(task.tags.joinToString(", ")) }
    var noteId by remember(task.id) { mutableStateOf(task.linkedNoteId.orEmpty()) }
    val zone = ZoneId.systemDefault()
    fun pick(current: Long?, set: (Long) -> Unit) {
        val at = current?.let { Instant.ofEpochMilli(it).atZone(zone) } ?: java.time.ZonedDateTime.now(zone)
        DatePickerDialog(context, { _, year, month, day ->
            TimePickerDialog(context, { _, hour, minute ->
                set(LocalDateTime.of(year, month + 1, day, hour, minute).atZone(zone).toInstant().toEpochMilli())
            }, at.hour, at.minute, true).show()
        }, at.year, at.monthValue - 1, at.dayOfMonth).show()
    }
    Column(
        modifier = modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (isNew) "New task" else "Task details", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(value = title, onValueChange = { title = it.take(240) },
            label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = details, onValueChange = { details = it.take(16000) },
            label = { Text("Details (optional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { priority = TaskPriority.entries[(priority.ordinal + 1) % TaskPriority.entries.size] }) {
                Text("Priority: " + priority.name)
            }
            OutlinedButton(onClick = { repeat = TaskRepeat.entries[(repeat.ordinal + 1) % TaskRepeat.entries.size] }) {
                Text("Repeat: " + repeat.name)
            }
        }
        Text("Due: " + (due?.let(::whenFormatted) ?: "No due date"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pick(due) { due = it } }) { Text("Set due") }
            TextButton(onClick = { due = null }) { Text("Clear") }
        }
        Text("Remind: " + (reminder?.let(::whenFormatted) ?: "Until complete / none"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pick(reminder) { reminder = it } }) { Text("Choose time") }
            TextButton(onClick = { reminder = null }) { Text("Clear") }
        }
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (preset in listOf("Later today", "This evening", "Tomorrow morning", "Until complete")) {
                OutlinedButton(onClick = { reminder = TaskSchedule.preset(preset, Instant.now(), zone) }) {
                    Text(preset)
                }
            }
        }
        OutlinedTextField(value = tags, onValueChange = { tags = it.take(1500) },
            label = { Text("Tags (comma separated)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = noteId, onValueChange = { noteId = it.take(240) },
            label = { Text("Linked note ID (optional)") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val cleaned = tags.split(",").map { it.trim().take(60) }.filter { it.isNotEmpty() }.take(24).toSet()
                if (title.isNotBlank()) onSave(task.copy(title = title.trim(), details = details,
                    priority = priority, repeat = repeat, dueAtEpochMs = due, remindAtEpochMs = reminder,
                    tags = cleaned, linkedNoteId = noteId.trim().ifEmpty { null }, zoneId = zone.id,
                    updatedAtEpochMs = System.currentTimeMillis()))
            }, enabled = title.isNotBlank()) { Text("Save") }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            if (!isNew) TextButton(onClick = { onDelete(task) }) { Text("Delete") }
        }
    }
}

private fun whenFormatted(epochMs: Long): String =
    DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
        .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
