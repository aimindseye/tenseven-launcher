package io.github.aimindseye.tenseven.platform

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import io.github.aimindseye.tenseven.model.ForegroundEvent
import io.github.aimindseye.tenseven.model.RecentApps
import io.github.aimindseye.tenseven.onboarding.SetupStatus

/**
 * Recent apps from Android's usage events: the fallback for Active Frames when the recent tasks are not
 * readable (an ordinary install; see RecentTasksRepository). Needs usage access, which the user grants in
 * Settings; reads package names and times only, keeps nothing. Call it off the main thread.
 */
class UsageRecentsRepository(
    context: Context,
) {
    private val appContext = context.applicationContext

    fun hasAccess(): Boolean = SetupStatus.hasUsageAccess(appContext)

    /** Most recent first, at most [limit]; empty without usage access. */
    fun recentPackages(
        excluded: Set<String> = emptySet(),
        limit: Int = RecentApps.MAX,
        windowMs: Long = WINDOW_MS,
    ): List<String> {
        if (!hasAccess()) return emptyList()
        val manager = appContext.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val now = System.currentTimeMillis()
        val events =
            runCatching {
                val stream = manager.queryEvents(now - windowMs, now)
                val event = UsageEvents.Event()
                buildList {
                    while (stream.hasNextEvent()) {
                        stream.getNextEvent(event)
                        if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                            add(ForegroundEvent(event.packageName.orEmpty(), event.timeStamp))
                        }
                    }
                }
            }.getOrDefault(emptyList())
        return RecentApps.select(events, excluded + appContext.packageName + RecentTasksRepository.excludedPackages, limit)
    }

    companion object {
        /** Two days back is plenty for a row of a dozen apps, and keeps the query small. */
        const val WINDOW_MS = 2L * 24 * 60 * 60 * 1000
    }
}
