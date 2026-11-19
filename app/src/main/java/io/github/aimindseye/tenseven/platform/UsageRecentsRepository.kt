package io.github.aimindseye.tenseven.platform

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.SystemClock
import io.github.aimindseye.tenseven.model.ForegroundEvent
import io.github.aimindseye.tenseven.model.RecentApps
import io.github.aimindseye.tenseven.model.UsageSample
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

    private val launches by lazy { RecentLaunchesRepository(appContext) }

    /**
     * Most recent first, at most [limit]. Apps started from this launcher count at once; with usage access, apps
     * opened any other way (a notification, another app) are included from Android's usage events.
     */
    fun recentPackages(
        excluded: Set<String> = emptySet(),
        limit: Int = RecentApps.MAX,
        windowMs: Long = WINDOW_MS,
    ): List<String> = recentEvents(excluded, limit, windowMs).map(ForegroundEvent::packageName)

    fun recentEvents(
        excluded: Set<String> = emptySet(),
        limit: Int = RecentApps.MAX,
        windowMs: Long = WINDOW_MS,
    ): List<ForegroundEvent> {
        // Nothing from before the phone last started, or before the look-back window, counts as open.
        val bootMs = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val since = maxOf(System.currentTimeMillis() - windowMs, bootMs)
        val started = RecentApps.since(launches.load(), since)
        val manager =
            appContext.getSystemService(UsageStatsManager::class.java).takeIf { hasAccess() }
                ?: return selectEvents(started, excluded, limit)
        val now = System.currentTimeMillis()
        val events =
            runCatching {
                val stream = manager.queryEvents(since, now)
                val event = UsageEvents.Event()
                val samples = mutableListOf<UsageSample>()
                while (stream.hasNextEvent()) {
                    stream.getNextEvent(event)
                    val resumed = event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                    val left =
                        event.eventType == UsageEvents.Event.ACTIVITY_PAUSED || event.eventType == UsageEvents.Event.ACTIVITY_STOPPED
                    if (resumed || left) {
                        samples.add(UsageSample(event.packageName.orEmpty(), event.className.orEmpty(), resumed, event.timeStamp))
                    }
                }
                // Only apps that stayed in front: a permission prompt or picker another app flashes up is not "opened".
                RecentApps.opened(samples)
            }.getOrDefault(emptyList())
        return selectEvents(started + events, excluded, limit)
    }

    private fun selectEvents(
        events: List<ForegroundEvent>,
        excluded: Set<String>,
        limit: Int,
    ): List<ForegroundEvent> {
        val ordered = events.sortedByDescending(ForegroundEvent::timeMs).distinctBy(ForegroundEvent::packageName)
        val packages = RecentApps.select(ordered, excluded + appContext.packageName + RecentTasksRepository.excludedPackages, limit).toSet()
        return ordered.filter { it.packageName in packages }
    }

    companion object {
        /** Half a day back: Active Frames are what you have open now, not what you used last week. */
        const val WINDOW_MS = 12L * 60 * 60 * 1000
    }
}
