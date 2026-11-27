package io.github.aimindseye.tenseven.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The waiting-notification counts the lock layer shows, per app package. Kept in memory only and filled by the
 * notification listener; it holds counts, never message text, and is empty when notification access is off.
 */
object LockLayerState {
    var counts: Map<String, Int> by mutableStateOf(emptyMap())
        private set

    fun update(newCounts: Map<String, Int>) {
        counts = newCounts
    }
}
