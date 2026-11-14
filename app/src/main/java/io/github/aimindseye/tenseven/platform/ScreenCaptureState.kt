package io.github.aimindseye.tenseven.platform

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process memory only. A shared screen is never attributed to an app or Android profile. */
data class ScreenCapturePreview(
    val running: Boolean = false,
    val bitmap: Bitmap? = null,
    val error: Boolean = false,
)

object ScreenCaptureState {
    private val mutable = MutableStateFlow(ScreenCapturePreview())
    val state = mutable.asStateFlow()

    @Volatile var homeVisible: Boolean = true

    fun started() {
        mutable.value = ScreenCapturePreview(running = true)
    }

    fun preview(bitmap: Bitmap) {
        if (mutable.value.running) mutable.value = mutable.value.copy(bitmap = bitmap)
    }

    fun stopped(error: Boolean = false) {
        mutable.value = ScreenCapturePreview(error = error)
    }
}
