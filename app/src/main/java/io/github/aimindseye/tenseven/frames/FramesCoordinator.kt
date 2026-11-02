package io.github.aimindseye.tenseven.frames

import io.github.aimindseye.tenseven.platform.CapabilityStatus

/** Android operations are injected. All state transitions run on the host's UI dispatcher. */
interface FrameSource {
    fun read(): FrameInput
    fun loadDismissals(): Map<String, Long>
    fun saveDismissals(value: Map<String, Long>)
    fun resume(item: FrameItem): Boolean
    fun launch(app: FrameApp)
}

class FramesCoordinator(
    private val source: FrameSource,
    private val background: (() -> Unit) -> Unit,
    private val foreground: (() -> Unit) -> Unit,
    private val now: () -> Long,
    private val changed: (FramesState) -> Unit,
) {
    @Volatile var state = FramesState()
        private set
    @Volatile private var epoch = 0L
    @Volatile private var active = false
    @Volatile private var destroyed = false
    private var dismissed = source.loadDismissals()

    fun onResume() {
        if (destroyed) return
        active = true
        refresh()
    }

    fun refresh() {
        if (!active || destroyed) return
        dismissed = source.loadDismissals()
        val ticket = ++epoch
        publish(state.copy(items = emptyList(), generation = ticket, snapshotsAllowed = false))
        background {
            val input = runCatching { source.read() }.getOrNull()
            foreground {
                if (current(ticket) && input != null) publish(FramePolicy.state(input, dismissed, ticket))
            }
        }
    }

    fun onStop() {
        active = false
        ++epoch
        publish(state.copy(items = emptyList(), generation = epoch, snapshotsAllowed = false))
    }

    fun destroy() {
        onStop()
        destroyed = true
    }

    fun close(item: FrameItem) {
        if (!active || item !in state.items) return
        dismissed = (dismissed + (item.app.key to now())).entries.sortedByDescending { it.value }
            .take(FrameDismissals.MAX).associate { it.toPair() }
        source.saveDismissals(dismissed)
        // Closing a launcher card never force-stops an app or removes someone else's task.
        ++epoch
        publish(state.copy(items = state.items.filterNot { it.app == item.app }, generation = epoch))
    }

    fun open(item: FrameItem) {
        if (!active || !state.settings.openApps || item !in state.items) return
        val ticket = epoch
        background {
            val input = runCatching { source.read() }.getOrNull() ?: return@background
            if (!current(ticket) || !input.settings.openApps || item.app !in input.availableApps) return@background
            val canResume = item.task != null && input.capabilities.taskResume == CapabilityStatus.Ready &&
                input.native.any { it == item.task }
            val resumed = canResume && source.resume(item)
            foreground {
                if (current(ticket) && !resumed) source.launch(item.app)
            }
        }
    }

    fun acceptsSnapshot(item: FrameItem, generation: Long): Boolean =
        current(generation) && state.snapshotsAllowed && item in state.items

    private fun current(ticket: Long): Boolean = active && !destroyed && epoch == ticket
    private fun publish(next: FramesState) { state = next; changed(next) }
}
