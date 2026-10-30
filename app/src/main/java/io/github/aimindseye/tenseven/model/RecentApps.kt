package io.github.aimindseye.tenseven.model

/** One time an app came to the foreground, from Android's usage events. */
data class ForegroundEvent(
    val packageName: String,
    val timeMs: Long,
)

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
