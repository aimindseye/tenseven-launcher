package io.github.aimindseye.tenseven.platform

import android.content.Context
import io.github.aimindseye.tenseven.model.ForegroundEvent
import io.github.aimindseye.tenseven.model.RecentLaunches

/** Package names and times of apps started from this launcher, in Tenseven's own preferences; never leaves the phone. */
class RecentLaunchesRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): List<ForegroundEvent> = RecentLaunches.decode(preferences.getString(KEY, null))

    /** Home never reads remembered launches while the existing history preference is disabled. */
    fun loadForHome(): List<ForegroundEvent> =
        if (StartStateRepository(appContext).loadFrameSettings().rememberReturnedApps) {
            load().filterNot { it.packageName == appContext.packageName }
        } else {
            emptyList()
        }

    fun record(packageName: String) {
        if (!StartStateRepository(appContext).loadFrameSettings().rememberReturnedApps) return
        val next = RecentLaunches.record(load(), packageName, System.currentTimeMillis())
        preferences.edit().putString(KEY, RecentLaunches.encode(next)).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "recent_launches"
        const val KEY = "launches"
    }
}
