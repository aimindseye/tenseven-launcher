package io.github.aimindseye.tenseven.tasks

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * Transactional app-private task files. This does not change NoteCodec or migrate existing Notes.
 * The first revision deliberately excludes raw notification text and third-party PendingIntents.
 */
class TaskStore(
    private val directory: File,
) {
    fun list(): List<TaskRecord> =
        directory
            .listFiles { file -> file.isFile && file.name.endsWith(SUFFIX) }
            .orEmpty()
            .mapNotNull { file ->
                runCatching { decode(file.name.removeSuffix(SUFFIX), file.inputStream().use(::loadProperties)) }.getOrNull()
            }.sortedWith(compareBy<TaskRecord> { it.completed }.thenByDescending { it.updatedAtEpochMs })

    @Synchronized
    fun save(task: TaskRecord) {
        directory.mkdirs()
        val destination = file(task.id)
        val temporary = File(directory, task.id + ".tmp-" + java.util.UUID.randomUUID())
        temporary.outputStream().use { properties(task).store(it, "TenSeven Tasks v1") }
        try {
            try {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (temporary.exists()) Files.delete(temporary.toPath())
        }
    }

    fun delete(id: String) {
        Files.deleteIfExists(file(id).toPath())
    }

    private fun file(id: String): File {
        require(ID.matches(id)) { "Invalid task ID" }
        return File(directory, "$id$SUFFIX")
    }

    private fun decode(
        id: String,
        props: Properties,
    ): TaskRecord {
        require(props.getProperty("format") == FORMAT && props.getProperty("version") == "1")

        fun optionalLong(key: String): Long? = props.getProperty(key)?.takeIf(String::isNotBlank)?.toLong()
        val tagsCount = props.getProperty("tags.count", "0").toInt()
        require(tagsCount in 0..24) { "Invalid tag count" }
        return TaskRecord(
            id = id,
            title = props.getProperty("title") ?: error("Missing task title"),
            details = props.getProperty("details", ""),
            completed = props.getProperty("completed") == "true",
            dueAtEpochMs = optionalLong("dueAt"),
            remindAtEpochMs = optionalLong("remindAt"),
            zoneId = props.getProperty("zone", "UTC"),
            priority = TaskPriority.valueOf(props.getProperty("priority", TaskPriority.Normal.name)),
            repeat = TaskRepeat.valueOf(props.getProperty("repeat", TaskRepeat.Never.name)),
            tags = (0 until tagsCount).map { props.getProperty("tags.$it") ?: error("Missing tag") }.toSet(),
            linkedNoteId = props.getProperty("linkedNote")?.takeIf(String::isNotBlank),
            originPackage = props.getProperty("originPackage")?.takeIf(String::isNotBlank),
            originToken = props.getProperty("originToken")?.takeIf(String::isNotBlank),
            updatedAtEpochMs = props.getProperty("updatedAt", "0").toLong(),
        )
    }

    private fun properties(task: TaskRecord) =
        Properties().apply {
            setProperty("format", FORMAT)
            setProperty("version", "1")
            setProperty("title", task.title)
            setProperty("details", task.details)
            setProperty("completed", task.completed.toString())
            setProperty("dueAt", task.dueAtEpochMs?.toString().orEmpty())
            setProperty("remindAt", task.remindAtEpochMs?.toString().orEmpty())
            setProperty("zone", task.zoneId)
            setProperty("priority", task.priority.name)
            setProperty("repeat", task.repeat.name)
            setProperty("updatedAt", task.updatedAtEpochMs.toString())
            setProperty("linkedNote", task.linkedNoteId.orEmpty())
            setProperty("originPackage", task.originPackage.orEmpty())
            setProperty("originToken", task.originToken.orEmpty())
            val ordered = task.tags.sorted()
            setProperty("tags.count", ordered.size.toString())
            ordered.forEachIndexed { index, tag -> setProperty("tags.$index", tag) }
        }

    private fun loadProperties(stream: java.io.InputStream): Properties = Properties().apply { load(stream) }

    companion object {
        private const val FORMAT = "tenseven-task"
        private const val SUFFIX = ".task"
        private val ID = Regex("[0-9a-f-]{36}")
    }
}
