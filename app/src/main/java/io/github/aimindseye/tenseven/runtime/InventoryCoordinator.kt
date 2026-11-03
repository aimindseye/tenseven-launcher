package io.github.aimindseye.tenseven.runtime

import java.util.concurrent.atomic.AtomicLong

/** Latest-result delivery; stopping invalidates both background work and already-posted results. */
internal class InventoryCoordinator<T : Any>(
    private val load: () -> T,
    private val execute: (() -> Unit) -> Unit,
    private val post: (() -> Unit) -> Unit,
    private val deliver: (T) -> Unit,
) {
    private val generation = AtomicLong()

    @Volatile private var active = false

    @Volatile private var closed = false

    fun start() {
        if (closed || active) return
        active = true
        refresh()
    }

    fun refresh() {
        if (!active || closed) return
        val token = generation.incrementAndGet()
        runCatching {
            execute {
                val value = runCatching(load).getOrNull() ?: return@execute
                post {
                    if (active && !closed && generation.get() == token) deliver(value)
                }
            }
        }
    }

    fun stop() {
        active = false
        generation.incrementAndGet()
    }

    fun close() {
        closed = true
        stop()
    }
}
