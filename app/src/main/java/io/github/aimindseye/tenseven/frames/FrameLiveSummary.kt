package io.github.aimindseye.tenseven.frames

import io.github.aimindseye.tenseven.live.LiveAvailability
import io.github.aimindseye.tenseven.live.LiveDatum
import io.github.aimindseye.tenseven.live.LiveSurfaceSnapshot

/** Media belongs only to its package and personal profile; calendar data is a device summary. */
object FrameLiveSummary {
    /**
     * FM Radio plays on its own media session, but the person listens in Sable Media, so that session belongs to
     * Sable Media's frame. Other players own their own sessions.
     */
    private val sessionOwners = mapOf("com.android.fmradio" to "org.sableos.media")

    private fun ownerOf(source: String): String = sessionOwners[source] ?: source

    /** The media session that belongs to [item]'s app (or to the FM radio Sable Media drives), playing ones first. */
    fun sessionOf(
        item: FrameItem,
        snapshot: LiveSurfaceSnapshot,
    ): LiveDatum? =
        (snapshot.mediaSessions + snapshot.music)
            .filter { it.sourcePackage != null && ownerOf(it.sourcePackage) == item.app.packageName }
            .let { found -> found.firstOrNull { !it.detail.startsWith("Paused:") } ?: found.firstOrNull() }

    fun playsMedia(
        item: FrameItem,
        snapshot: LiveSurfaceSnapshot,
    ): Boolean = sessionOf(item, snapshot) != null

    fun detail(
        state: FramesState,
        item: FrameItem,
        snapshot: LiveSurfaceSnapshot,
        personalUser: Int,
    ): String? {
        if (!state.settings.liveSummaries || item.app.userId != personalUser) return null
        summary(state, item, snapshot, personalUser)?.let { return it.detail }
        val session = if (state.notificationsAllowed) sessionOf(item, snapshot) else null
        val datum =
            when {
                session != null -> session
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
