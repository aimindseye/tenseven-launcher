package io.github.aimindseye.tenseven.frames

/** The opened-app window, not an inventory of notifications, documents or old Android recents. */
data class FrameOpening(
    val app: FrameApp,
    val at: Long,
)

data class FrameWindow(
    val active: Map<String, Long> = emptyMap(),
    val seen: Map<String, Long> = emptyMap(),
) {
    fun record(
        events: List<FrameOpening>,
        since: Long,
    ): FrameWindow {
        var next = this
        events.filter { it.at >= since }.sortedBy { it.at }.forEach { event ->
            if (event.at > (next.seen[event.app.key] ?: -1L)) {
                next =
                    FrameWindow(
                        (next.active + (event.app.key to event.at))
                            .entries
                            .sortedByDescending { it.value }
                            .take(8)
                            .associate { it.toPair() },
                        (next.seen + (event.app.key to event.at))
                            .entries
                            .sortedByDescending { it.value }
                            .take(96)
                            .associate { it.toPair() },
                    )
            }
        }
        return next
    }

    fun close(
        app: FrameApp,
        at: Long,
    ): FrameWindow =
        copy(
            active = active - app.key,
            seen = seen + (app.key to maxOf(at, seen[app.key] ?: 0)),
        )

    fun openings(): List<FrameOpening> =
        active
            .mapNotNull { (key, at) ->
                val user = key.substringBefore(':').toIntOrNull() ?: return@mapNotNull null
                val target = key.substringAfter(':')
                FrameOpening(FrameApp(target.substringBefore('/'), user, target.substringAfter('/', "").ifBlank { null }), at)
            }.sortedByDescending { it.at }
}

object LocalFrameApps {
    private const val ROOT = "io.github.aimindseye.tenseven."
    val activities =
        mapOf(
            "local:notes" to ROOT + "extras.notes.MainActivity",
            "local:tasks" to ROOT + "tasks.TasksActivity",
            "local:calculator" to ROOT + "extras.calculator.MainActivity",
        )

    fun canonical(activity: String?): String? =
        if (activity == ROOT + "tasks.TasksLauncherActivity") {
            ROOT + "tasks.TasksActivity"
        } else {
            activity
        }

    fun key(activity: String?): String? = activities.entries.firstOrNull { it.value == canonical(activity) }?.key

    fun title(activity: String?): String? =
        when (key(activity)) {
            "local:notes" -> "TenSeven Notes"
            "local:tasks" -> "Tasks / Remind"
            "local:calculator" -> "TenSeven Calculator"
            else -> null
        }
}
