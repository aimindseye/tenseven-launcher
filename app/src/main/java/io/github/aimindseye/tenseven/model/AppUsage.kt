package io.github.aimindseye.tenseven.model

/**
 * How often each app was opened from Tenseven, so Command can put the app you use most first when two letters
 * match several. Counts decay: when one passes [LIMIT] every count is halved, so a habit from last year fades.
 * Only app keys and small counts are kept, on the phone. Pure policy, unit tested.
 */
class AppUsage private constructor(
    private val counts: Map<String, Int>,
) {
    fun score(key: String): Int = counts[key] ?: 0

    val size: Int get() = counts.size

    fun record(key: String): AppUsage {
        if (key.isBlank() || key.any { it == '\n' || it == '\t' }) return this
        var next = counts + (key to (score(key) + 1))
        if ((next[key] ?: 0) > LIMIT) {
            next = next.mapValues { it.value / 2 }.filterValues { it > 0 }
        }
        if (next.size > MAX_APPS) {
            next = next.entries.sortedByDescending { it.value }.take(MAX_APPS).associate { it.key to it.value }
        }
        return AppUsage(next)
    }

    fun forget(key: String): AppUsage = AppUsage(counts - key)

    /** Keys of the apps used at least once, most used first (ties A to Z so the order is stable). */
    fun mostUsed(limit: Int): List<String> =
        counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(limit).map { it.key }

    fun encode(): String = counts.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}\t${it.value}" }

    companion object {
        val EMPTY = AppUsage(emptyMap())
        const val LIMIT = 100
        const val MAX_APPS = 400

        /** Reads what [encode] wrote; a damaged line is dropped. */
        fun decode(text: String?): AppUsage =
            AppUsage(
                text.orEmpty().lineSequence().mapNotNull { line ->
                    val key = line.substringBefore('\t')
                    val count = line.substringAfter('\t', "").toIntOrNull()
                    if (key.isBlank() || count == null || count <= 0) null else key to count.coerceAtMost(LIMIT)
                }.toMap(),
            )
    }
}
