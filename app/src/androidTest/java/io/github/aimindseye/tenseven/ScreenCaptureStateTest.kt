package io.github.aimindseye.tenseven

import android.graphics.Bitmap
import io.github.aimindseye.tenseven.platform.ScreenCaptureState
import org.junit.Assert.*
import org.junit.Test

class ScreenCaptureStateTest {
    @Test fun stopDropsPixelsAndLateFramesCannotResurrectConsent() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            ScreenCaptureState.started()
            ScreenCaptureState.preview(bitmap)
            assertSame(bitmap, ScreenCaptureState.state.value.bitmap)
            ScreenCaptureState.stopped()
            assertFalse(ScreenCaptureState.state.value.running)
            assertNull(ScreenCaptureState.state.value.bitmap)
            ScreenCaptureState.preview(bitmap)
            assertNull(ScreenCaptureState.state.value.bitmap)
            ScreenCaptureState.started()
            assertNull(ScreenCaptureState.state.value.bitmap)
            ScreenCaptureState.stopped(error = true)
            assertTrue(ScreenCaptureState.state.value.error)
        } finally { ScreenCaptureState.stopped(); bitmap.recycle() }
    }
}
