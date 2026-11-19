package io.github.aimindseye.tenseven.model

/** Actual local launches, newest first, resolved only against currently available apps in this profile. */
object Bb7RecentApps {
    fun select(
        apps: List<AppEntry>,
        events: List<ForegroundEvent>,
    ): List<AppEntry> {
        val available =
            apps
                .filter { it.inLauncherUser }
                .distinctBy { it.component.packageName }
                .associateBy { it.component.packageName }
        return RecentApps
            .select(events.filter { it.timeMs > 0 }, emptySet(), RecentLaunches.KEEP)
            .mapNotNull { available[it] }
    }
}
