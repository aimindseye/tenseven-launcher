package io.github.aimindseye.tenseven.frames

import io.github.aimindseye.tenseven.model.ActiveFrame
import io.github.aimindseye.tenseven.model.ForegroundEvent
import io.github.aimindseye.tenseven.platform.CapabilityStatus
import io.github.aimindseye.tenseven.platform.PlatformCapabilities

/** Stable identity always includes the Android profile; never fall back to another user's app. */
data class FrameApp(val packageName: String, val userId: Int, val activityClass: String? = null) {
    val key: String get() = "$userId:$packageName" + (activityClass?.let { "/" + it } ?: "")
}

data class FrameSettings(
    val liveSummaries: Boolean = true,
    val newMessages: Boolean = true,
    val snapshots: Boolean = true,
    val openApps: Boolean = true,
    val rememberReturnedApps: Boolean = true,
    val maximum: Int = 8,
) {
    fun normalized(): FrameSettings = copy(maximum = 8)
    companion object { val LIMITS = listOf(8) }
}

data class FrameItem(val app: FrameApp, val task: ActiveFrame? = null) {
    val key: String get() = task?.let { "${app.key}:task:${it.taskId}" } ?: "${app.key}:recent"
}

data class FrameInput(
    val native: List<ActiveFrame>,
    val recent: List<ForegroundEvent>,
    val availableApps: Set<FrameApp>,
    val personalUserId: Int,
    val capabilities: PlatformCapabilities,
    val settings: FrameSettings,
    val openings: List<FrameOpening>? = null,
)

data class FramesState(
    val items: List<FrameItem> = emptyList(),
    val generation: Long = 0,
    val settings: FrameSettings = FrameSettings(),
    val usageAllowed: Boolean = false,
    val snapshotsAllowed: Boolean = false,
    val notificationsAllowed: Boolean = false,
    val calendarAllowed: Boolean = false,
    val fallbackReason: String = "Open an app to create a frame. The latest eight opened apps are kept.",
)

/** Merge partial native results with recents. A dismissed native task cannot sneak back in. */
object FramePolicy {
    fun state(input: FrameInput, dismissals: Map<String, Long>, generation: Long): FramesState {
        val settings = input.settings.normalized()
        val recent = if (settings.rememberReturnedApps) input.recent.sortedByDescending { it.timeMs }
            .distinctBy { it.packageName } else emptyList()
        val opened = recent.associate { FrameApp(it.packageName, input.personalUserId).key to it.timeMs } +
            input.openings.orEmpty().associate { it.app.key to it.at }
        val native = input.native.filter { FrameApp(it.packageName, it.userId) in input.availableApps }
        val items = input.openings?.map { opening ->
            FrameItem(opening.app, native.firstOrNull { it.packageName == opening.app.packageName && it.userId == opening.app.userId &&
                (opening.app.activityClass == null || it.className == opening.app.activityClass) })
        } ?: (recent.map { event ->
            val app = FrameApp(event.packageName, input.personalUserId)
            FrameItem(app, native.firstOrNull { it.packageName == app.packageName && it.userId == app.userId })
        } + native.map { FrameItem(FrameApp(it.packageName, it.userId), it) })
        val shown = items.filter { item ->
            val closed = dismissals[item.key] ?: dismissals[item.app.key]
            item.app in input.availableApps && (closed == null || opened.getOrDefault(item.app.key, 0L) > closed)
        }.distinctBy { it.key }.take(settings.maximum)
        return FramesState(shown, generation, settings,
            usageAllowed = input.capabilities.usageRecents == CapabilityStatus.Ready,
            snapshotsAllowed = settings.snapshots && input.capabilities.thirdPartySnapshots == CapabilityStatus.Ready,
            notificationsAllowed = input.capabilities.hubNotifications == CapabilityStatus.Ready,
            calendarAllowed = input.capabilities.calendarEvents == CapabilityStatus.Ready,
            fallbackReason = input.capabilities.frameFallbackReason,
        )
    }
}

/** Bounded persistent metadata only; contains no captured app content. */
object FrameDismissals {
    const val MAX = 96
    fun decode(value: String?): Map<String, Long> = value.orEmpty().lineSequence().mapNotNull { line ->
        val parts = line.split('\t')
        val at = parts.getOrNull(1)?.toLongOrNull()
        if (parts.size != 2 || at == null || at < 0 || !parts[0].matches(Regex("[0-9]+:[a-zA-Z0-9_.]+(?:/[a-zA-Z0-9_.$]+)?(?::recent|:task:[0-9]+)?"))) null
        else parts[0] to at
    }.take(MAX).toMap()
    fun encode(entries: Map<String, Long>): String = entries.entries.sortedByDescending { it.value }
        .take(MAX).joinToString("\n") { "${it.key}\t${it.value}" }
}


/** Counts never cross profiles or opt-in/permission boundaries, even while a live snapshot is refreshing. */
object FrameMessages {
    fun count(state: FramesState, item: FrameItem, counts: List<io.github.aimindseye.tenseven.live.AppUnreadCount>,
        enabled: Set<String>, personalUser: Int): Int =
        if (state.settings.newMessages && state.notificationsAllowed && item.app.userId == personalUser && item.app.packageName in enabled)
            counts.firstOrNull { it.packageName == item.app.packageName }?.count ?: 0 else 0
}
