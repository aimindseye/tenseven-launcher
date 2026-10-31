package io.github.aimindseye.tenseven.model

/** One time an app came to the foreground, from Android's usage events. */
data class ForegroundEvent(
    val packageName: String,
    val timeMs: Long,
)

/** One step of an activity's life from Android's usage events: it came to the front, or left it. */
data class UsageSample(
    val packageName: String,
    val className: String,
    val resumed: Boolean,
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

    /**
     * [events] that happened at or after [sinceMs]. Nothing from before the phone started or before the look-back
     * window is "open" any more, however recently it was launched from here.
     */
    fun since(
        events: List<ForegroundEvent>,
        sinceMs: Long,
    ): List<ForegroundEvent> = events.filter { it.timeMs >= sinceMs }

    /** An app has to stay in front this long to count as opened; anything quicker is a pass-through screen. */
    const val MIN_FOREGROUND_MS = 2_000L

    /**
     * The times apps were really opened, from usage events: an activity counts when it stayed in front for at least
     * [minForegroundMs], or is in front now. Dialogs and pass-through screens that other apps start for a moment
     * (permission prompts, pickers, sign-in hops) come to the front for a blink and are left out.
     */
    fun opened(
        samples: List<UsageSample>,
        minForegroundMs: Long = MIN_FOREGROUND_MS,
    ): List<ForegroundEvent> {
        val inFront = mutableMapOf<Pair<String, String>, Long>()
        val opened = mutableListOf<ForegroundEvent>()
        for (sample in samples.sortedBy(UsageSample::timeMs)) {
            val key = sample.packageName to sample.className
            if (sample.resumed) {
                inFront[key] = sample.timeMs
            } else {
                val since = inFront.remove(key) ?: continue
                if (sample.timeMs - since >= minForegroundMs) opened.add(ForegroundEvent(sample.packageName, since))
            }
        }
        // Whatever is still in front now has been opened.
        inFront.forEach { (key, since) -> opened.add(ForegroundEvent(key.first, since)) }
        return opened
    }

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
