package io.github.aimindseye.tenseven.frames

import io.github.aimindseye.tenseven.live.LiveAvailability
import io.github.aimindseye.tenseven.live.LiveSurfaceSnapshot

/** Media belongs only to its package and personal profile; calendar data is a device summary. */
object FrameLiveSummary {
    fun detail(
        state: FramesState,
        item: FrameItem,
        snapshot: LiveSurfaceSnapshot,
        personalUser: Int,
    ): String? {
        if (!state.settings.liveSummaries || item.app.userId != personalUser) return null
        return summary(state, item, snapshot, personalUser)?.detail ?: deviceDetail(state, item, snapshot)
    }

    private fun deviceDetail(
        state: FramesState,
        item: FrameItem,
        snapshot: LiveSurfaceSnapshot,
    ): String? {
        val datum =
            when {
                item.app.packageName == snapshot.music.sourcePackage && state.notificationsAllowed -> snapshot.music
                item.app.packageName in snapshot.calendarPackages && state.calendarAllowed -> snapshot.calendar
                else -> null
            }
        return datum?.takeIf { it.availability in setOf(LiveAvailability.LIVE, LiveAvailability.EMPTY) }?.detail
    }

    fun summary(
        state: FramesState,
        item: FrameItem,
        snapshot: LiveSurfaceSnapshot,
        personalUser: Int,
    ): AppSummary? =
        if (!state.settings.liveSummaries || item.app.userId != personalUser) {
            null
        } else {
            snapshot.appSummaries.firstOrNull {
                (
                    (
                        item.app.activityClass != null && it.source == SummarySource.Local && LocalFrameApps.key(
                            item.app.activityClass,
                        ) == it.key
                    ) ||
                        (item.app.activityClass == null && it.packageName == item.app.packageName)
                ) &&
                    AppSummaryPolicy.allowed(it, state.notificationsAllowed)
            }
        }
}
