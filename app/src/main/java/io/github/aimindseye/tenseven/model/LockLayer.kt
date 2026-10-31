package io.github.aimindseye.tenseven.model

/** One app's row on the lock layer: its package and how many notifications it has waiting. */
data class LockEntry(
    val packageName: String,
    val count: Int,
)

/**
 * What the Tenseven lock layer lists: app icons with a waiting count, most first, like the column of icons on a
 * BlackBerry lock screen. Only counts are kept here; message text never reaches this model.
 */
object LockLayer {
    const val MAX_ICONS = 6
    private const val MAX_SHOWN_COUNT = 99

    /** Drops apps the user left out of the Hub and empty counts, then orders by count (ties by package) and caps the list. */
    fun entries(
        counts: Map<String, Int>,
        excluded: Set<String> = emptySet(),
        max: Int = MAX_ICONS,
    ): List<LockEntry> =
        counts
            .filter { (pkg, count) -> count > 0 && pkg !in excluded }
            .map { (pkg, count) -> LockEntry(pkg, count) }
            .sortedWith(compareByDescending<LockEntry> { it.count }.thenBy { it.packageName })
            .take(max.coerceAtLeast(0))

    /** "5", or "99+" past ninety-nine so the badge stays small. */
    fun countLabel(count: Int): String = if (count > MAX_SHOWN_COUNT) "$MAX_SHOWN_COUNT+" else count.coerceAtLeast(0).toString()

    /** Notifications per package: ongoing ones and group summaries are not "waiting" and the launcher's own are left out. */
    fun tally(
        notifications: List<Triple<String, Boolean, Boolean>>,
        ownPackage: String,
    ): Map<String, Int> =
        notifications
            .filter { (pkg, ongoing, summary) -> pkg != ownPackage && !ongoing && !summary }
            .groupingBy { it.first }
            .eachCount()
}
