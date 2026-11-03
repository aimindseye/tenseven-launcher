package io.github.aimindseye.tenseven.frames

/** Read-only, bounded presentation data. No PendingIntents, document URIs or account tokens. */
enum class SummarySource(val label: String) {
    Media("Media summary"), Notification("Notification summary"), Local("Local summary"), Document("Selected file")
}
data class AppSummary(
    val key: String, val title: String, val detail: String, val source: SummarySource,
    val packageName: String? = null, val updatedAt: Long = 0L,
)
object AppSummaryPolicy {
    fun allowed(value: AppSummary, notifications: Boolean): Boolean =
        notifications || value.source !in setOf(SummarySource.Media, SummarySource.Notification)
    fun text(value: String): String = value.replace(Regex("\\s+"), " ").trim().take(240)
    fun select(values: List<AppSummary>, notifications: Boolean): List<AppSummary> = values
        .filter { allowed(it, notifications) && it.detail.isNotBlank() }
        .distinctBy { it.key }.sortedByDescending { it.updatedAt }.take(24)
}
