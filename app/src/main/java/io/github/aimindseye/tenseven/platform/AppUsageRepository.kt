package io.github.aimindseye.tenseven.platform

import android.content.Context
import io.github.aimindseye.tenseven.model.AppUsage

/** Launch counts kept in Tenseven's own preferences (app keys and numbers only; never leaves the phone). */
class AppUsageRepository(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): AppUsage = AppUsage.decode(preferences.getString(KEY_COUNTS, null))

    fun record(key: String) {
        preferences.edit().putString(KEY_COUNTS, load().record(key).encode()).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_COUNTS).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "app_usage"
        const val KEY_COUNTS = "counts"
    }
}
