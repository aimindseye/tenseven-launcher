package io.github.aimindseye.tenseven.frames

/** Bounded thumbnails; no file storage, audio, persisted consent, or automatic session restart. */
object CapturePolicy {
    fun size(
        width: Int,
        height: Int,
    ): Pair<Int, Int> {
        val scale = 640.0 / maxOf(width, height, 640)
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }

    fun accept(
        homeVisible: Boolean,
        locked: Boolean,
        elapsed: Long,
        last: Long,
    ): Boolean = !homeVisible && !locked && elapsed - last >= 1000L
}
