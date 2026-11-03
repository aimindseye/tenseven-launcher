package io.github.aimindseye.tenseven.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The existing launcher profile receiver invalidates hosted content before refreshing inventory. */
internal object WidgetProfileEpoch {
    private val state = MutableStateFlow(0)
    val revisions = state.asStateFlow()

    fun invalidate() {
        state.update { it + 1 }
    }
}
