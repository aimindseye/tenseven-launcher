package io.github.aimindseye.tenseven.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.aimindseye.tenseven.conversations.ConversationHub
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** The inbox and Peek observe the same signal, registered only while their screen is started. */
@Composable
internal fun rememberHubRevision(): Int {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val revision by produceState(0, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            callbackFlow {
                val listener: () -> Unit = {
                    trySend(Unit)
                    Unit
                }
                ConversationHub.addChangeListener(listener)
                trySend(Unit)
                awaitClose { ConversationHub.removeChangeListener(listener) }
            }.conflate().collect { value += 1 }
        }
    }
    return revision
}
