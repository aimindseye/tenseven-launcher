package io.github.aimindseye.tenseven

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import io.github.aimindseye.tenseven.frames.FrameSettings
import io.github.aimindseye.tenseven.platform.StartStateRepository
import org.junit.Assert.*
import org.junit.Test

class FramePersistenceTest {
    @Test fun settingsAndDismissalsSurviveRepositoryRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = StartStateRepository(context)
        val original = first.loadFrameSettings()
        val dismissals = first.loadFrameDismissals()
        try {
            first.saveFrameSettings(FrameSettings(false, false, false, false, false, 12))
            first.saveFrameDismissals(mapOf("0:mail" to 123L))
            val recreated = StartStateRepository(context)
            assertEquals(FrameSettings(false, false, false, false, false, 12), recreated.loadFrameSettings())
            assertEquals(mapOf("0:mail" to 123L), recreated.loadFrameDismissals())
        } finally {
            first.saveFrameSettings(original)
            first.saveFrameDismissals(dismissals)
        }
    }
}
