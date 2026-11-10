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
        summary(state, item, snapshot, personalUser)?.let { return it.detail }
        val datum =
            when {
                item.app.packageName == snapshot.music.sourcePackage && state.notificationsAllowed -> snapshot.music
                item.app.packageName in snapshot.calendarPackages && state.calendarAllowed -> snapshot.calendar
                else -> return null
            }
        return if (datum.availability in setOf(LiveAvailability.LIVE, LiveAvailability.EMPTY)) datum.detail else null
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
