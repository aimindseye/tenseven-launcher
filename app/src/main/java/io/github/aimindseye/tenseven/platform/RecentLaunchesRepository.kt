package io.github.aimindseye.tenseven.platform

import android.content.Context
import io.github.aimindseye.tenseven.model.ForegroundEvent
import io.github.aimindseye.tenseven.model.RecentLaunches

/** Package names and times of apps started from this launcher, in Tenseven's own preferences; never leaves the phone. */
class RecentLaunchesRepository(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): List<ForegroundEvent> = RecentLaunches.decode(preferences.getString(KEY, null))

    fun record(packageName: String) {
        val next = RecentLaunches.record(load(), packageName, System.currentTimeMillis())
        preferences.edit().putString(KEY, RecentLaunches.encode(next)).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "recent_launches"
        const val KEY = "launches"
    }
}
