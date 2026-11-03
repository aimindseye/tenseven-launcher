package io.github.aimindseye.tenseven.frames

/** Basic frames are a BB10 Home feature; configure summaries after notification access is granted. */
object FrameAvailability {
    fun frames(bb10: Boolean): Boolean = bb10
    fun setup(bb10: Boolean, notifications: Boolean): Boolean = frames(bb10) && notifications
}
