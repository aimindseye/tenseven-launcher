package io.github.aimindseye.tenseven.model

/**
 * The widgets the Widgets page shows, as the Android widget ids the launcher's host allocated.
 * Pure list policy, unit tested; the host itself is `platform/WidgetHostController`.
 */
object WidgetList {
    private const val SEPARATOR = ","

    /** Parses the saved "1,2,3" form; anything that is not a positive number, and repeats, are dropped. */
    fun parse(saved: String?): List<Int> =
        saved
            .orEmpty()
            .split(SEPARATOR)
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it > 0 }
            .distinct()

    fun format(ids: List<Int>): String = ids.joinToString(SEPARATOR)

    fun add(
        ids: List<Int>,
        id: Int,
    ): List<Int> = if (id <= 0 || id in ids) ids else ids + id

    fun remove(
        ids: List<Int>,
        id: Int,
    ): List<Int> = ids.filterNot { it == id }

    /** Moves [id] up (-1) or down (+1) one place; unchanged at either end or when it is not listed. */
    fun move(
        ids: List<Int>,
        id: Int,
        delta: Int,
    ): List<Int> {
        val from = ids.indexOf(id)
        val to = from + delta
        if (from < 0 || to !in ids.indices) return ids
        return ids.toMutableList().also { it.add(to, it.removeAt(from)) }
    }

    /** Keeps the saved order but only the ids Android still knows about. */
    fun keepKnown(
        ids: List<Int>,
        known: (Int) -> Boolean,
    ): List<Int> = ids.filter(known)
}

/** How tall a widget is drawn on the Widgets page. */
object WidgetSizing {
    const val MIN_DP = 64
    const val MAX_DP = 360
    const val DEFAULT_DP = 110

    /** The height a widget asks for ([minHeightPx] is in pixels, as AppWidgetProviderInfo reports it), kept in range. */
    fun heightDp(
        minHeightPx: Int,
        density: Float,
    ): Int {
        if (minHeightPx <= 0 || density <= 0f) return DEFAULT_DP
        return Math.round(minHeightPx / density).coerceIn(MIN_DP, MAX_DP)
    }

    /** "2 × 1": the cells a widget's minimum size covers on a launcher grid of 70dp cells. */
    fun cells(
        minPx: Int,
        density: Float,
    ): Int = if (minPx <= 0 || density <= 0f) 1 else ((minPx / density + 30f) / 70f).toInt().coerceAtLeast(1)
}
