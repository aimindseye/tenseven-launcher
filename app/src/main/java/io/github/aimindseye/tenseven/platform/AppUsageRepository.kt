package io.github.aimindseye.tenseven.platform

import android.content.Context
import io.github.aimindseye.tenseven.model.AppUsage

/** Launch counts kept in Tenseven's own preferences (app keys and numbers only; never leaves the phone). */
class AppUsageRepository(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): AppUsage {
        // SableOS: the Recent panel reads what was saved, so it survives the home restarting.
        io.github.aimindseye.tenseven.model.SableRecents.keys =
            io.github.aimindseye.tenseven.model.SableRecents.decode(preferences.getString(KEY_RECENT, null))
        return AppUsage.decode(preferences.getString(KEY_COUNTS, null))
    }

    fun record(key: String) {
        val recent =
            io.github.aimindseye.tenseven.model.SableRecents.record(
                io.github.aimindseye.tenseven.model.SableRecents.decode(preferences.getString(KEY_RECENT, null)),
                key,
            )
        val counts = load().record(key).encode()
        io.github.aimindseye.tenseven.model.SableRecents.keys = recent
        preferences
            .edit()
            .putString(KEY_COUNTS, counts)
            .putString(KEY_RECENT, io.github.aimindseye.tenseven.model.SableRecents.encode(recent))
            .apply()
    }

    fun clear() {
        io.github.aimindseye.tenseven.model.SableRecents.keys = emptyList()
        preferences.edit().remove(KEY_COUNTS).remove(KEY_RECENT).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "app_usage"
        const val KEY_COUNTS = "counts"
        const val KEY_RECENT = "recent"
    }
}
