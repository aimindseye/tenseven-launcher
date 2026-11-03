package io.github.aimindseye.tenseven.model

/** Persisted before Android opens bind/configure UI; no widget content or message text. */
data class WidgetBinding(
    val id: Int,
    val provider: String,
    val profileSerial: Long,
    val target: String,
    val stage: Stage,
) {
    enum class Stage { Bind, Configure }

    enum class Result { Ignore, Cancel, Configure, Commit }

    /** A late result for a different widget or phase cannot finish the current transaction. */
    fun result(
        expected: Stage,
        returnedId: Int?,
        accepted: Boolean,
    ): Result =
        when {
            expected != stage -> Result.Ignore
            returnedId != null && returnedId != id -> Result.Ignore
            !accepted -> Result.Cancel
            stage == Stage.Bind -> Result.Configure
            else -> Result.Commit
        }
}

object WidgetRecovery {
    /** An unavailable/locked provider is not evidence that Android deleted its allocated id. */
    fun retained(
        saved: List<Int>,
        allocated: Set<Int>?,
    ): List<Int> = saved.distinct().filter { it > 0 && (allocated == null || it in allocated) }

    fun orphaned(
        allocated: Set<Int>,
        saved: Set<Int>,
        pending: Int?,
    ): Set<Int> = allocated - saved - setOfNotNull(pending)
}
