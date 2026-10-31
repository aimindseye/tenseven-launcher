package io.github.aimindseye.tenseven.model

/** One time an app came to the foreground, from Android's usage events. */
data class ForegroundEvent(
    val packageName: String,
    val timeMs: Long,
)

/**
 * Apps started from this launcher, with the time, kept so the recent-apps row is right the moment you come back
 * (Android's usage events can lag, and need a permission the user may not have given). Pure, unit tested.
 */
object RecentLaunches {
    private const val SEPARATOR = '\n'
    private const val FIELD = '\t'
    const val KEEP = 24

    fun decode(saved: String?): List<ForegroundEvent> =
        saved
            .orEmpty()
            .split(SEPARATOR)
            .mapNotNull { line ->
                val at = line.lastIndexOf(FIELD)
                val time = if (at > 0) line.substring(at + 1).toLongOrNull() else null
                if (time == null) null else ForegroundEvent(line.substring(0, at), time)
            }

    fun encode(events: List<ForegroundEvent>): String = events.joinToString(SEPARATOR.toString()) { "${it.packageName}$FIELD${it.timeMs}" }

    /** [events] with [packageName] launched at [timeMs]: one entry per app, newest kept, at most [KEEP]. */
    fun record(
        events: List<ForegroundEvent>,
        packageName: String,
        timeMs: Long,
    ): List<ForegroundEvent> =
        (events.filterNot { it.packageName == packageName } + ForegroundEvent(packageName, timeMs))
            .sortedByDescending(ForegroundEvent::timeMs)
            .take(KEEP)
}

/**
 * The recent-apps row for Active Frames on an ordinary install, where Android hides other apps' tasks from a
 * home screen that is not built into the phone. Pure policy, unit tested.
 */
object RecentApps {
    const val MAX = 12

    /** Most recent first, each app once, without [excluded] packages. */
    fun select(
        events: List<ForegroundEvent>,
        excluded: Set<String>,
        limit: Int = MAX,
    ): List<String> =
        events
            .sortedByDescending(ForegroundEvent::timeMs)
            .map(ForegroundEvent::packageName)
            .filter { it.isNotBlank() && it !in excluded }
            .distinct()
            .take(limit)
}
