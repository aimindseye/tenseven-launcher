package io.github.aimindseye.tenseven.platform

import android.content.Context
import androidx.core.content.edit
import io.github.aimindseye.tenseven.conversations.ConversationHub
import io.github.aimindseye.tenseven.model.HubSourcePolicy

/** New and upgraded installs use Android-delivered sources; saved manual lists remain available in selected mode. */
class HubSourceRepository(
    context: Context,
) {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            StartStateRepository.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    fun policy(): HubSourcePolicy =
        HubSourcePolicy(
            automatic = preferences.getBoolean(AUTOMATIC, true),
            selected = preferences.getStringSet(StartStateRepository.KEY_HUB_ENABLED, null).orEmpty().toSet(),
            hidden = preferences.getStringSet(HIDDEN, null).orEmpty().toSet(),
        )

    fun includes(packageName: String): Boolean = policy().includes(packageName)

    /** Resolve only observed sources; never enumerate apps or create notifications for installed packages. */
    fun enabledPackages(): Set<String> {
        val current = policy()
        val observed = ConversationHub.notifications().map { it.packageName } + LockLayerState.counts.keys
        return if (current.automatic) current.resolve(observed) else current.selected
    }

    fun saveManual(packages: Set<String>) {
        preferences.edit {
            putBoolean(AUTOMATIC, false)
            putStringSet(StartStateRepository.KEY_HUB_ENABLED, packages.toSet())
        }
    }

    fun setAutomatic(automatic: Boolean) {
        preferences.edit { putBoolean(AUTOMATIC, automatic) }
    }

    fun toggle(packageName: String) {
        val current = policy()
        if (current.automatic) {
            val next = if (packageName in current.hidden) current.hidden - packageName else current.hidden + packageName
            preferences.edit { putStringSet(HIDDEN, next) }
        } else {
            val next =
                if (packageName in current.selected) {
                    current.selected - packageName
                } else {
                    current.selected + packageName
                }
            saveManual(next)
        }
    }

    private companion object {
        const val AUTOMATIC = "hub_sources_automatic"
        const val HIDDEN = "hub_sources_hidden"
    }
}
