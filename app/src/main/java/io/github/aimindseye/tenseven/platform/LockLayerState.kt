package io.github.aimindseye.tenseven.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The waiting-notification counts the lock layer shows, per app package. Kept in memory only and filled by the
 * notification listener; it holds counts, never message text, and is empty when notification access is off.
 */
object LockLayerState {
    var counts: Map<String, Int> by mutableStateOf(emptyMap())
        private set

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun update(newCounts: Map<String, Int>) {
        val changed = newCounts != counts
        counts = newCounts
        if (changed) listeners.forEach { it() }
    }

    /** Called after the counts change, on the caller's thread. */
    fun addChangeListener(listener: () -> Unit) {
        listeners += listener
    }
}
