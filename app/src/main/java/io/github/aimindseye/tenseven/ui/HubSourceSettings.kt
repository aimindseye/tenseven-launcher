package io.github.aimindseye.tenseven.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.aimindseye.tenseven.conversations.ConversationHub
import io.github.aimindseye.tenseven.conversations.ConversationListenerService
import io.github.aimindseye.tenseven.platform.HubSourceRepository
import io.github.aimindseye.tenseven.platform.LockLayerState
import io.github.aimindseye.tenseven.platform.NotificationConnectionRecovery

/** Reactive source controls, separated from Activity lifecycle and the settings layout. */
internal class HubSourceSettings(
    private val context: Context,
) {
    private val repository = HubSourceRepository(context)
    var policy by mutableStateOf(repository.policy())
        private set

    fun setAutomatic(value: Boolean) {
        repository.setAutomatic(value)
        refresh()
    }

    fun toggle(packageName: String) {
        repository.toggle(packageName)
        refresh()
    }

    private fun refresh() {
        policy = repository.policy()
        val hidden =
            ConversationHub
                .notifications()
                .map { it.packageName }
                .filterNot(policy::includes)
                .toSet()
        ConversationHub.dropPackages(hidden)
        LockLayerState.update(LockLayerState.counts.filterKeys(policy::includes))
        if (!ConversationListenerService.refreshConnected()) NotificationConnectionRecovery.retryFromSettings(context)
    }
}
