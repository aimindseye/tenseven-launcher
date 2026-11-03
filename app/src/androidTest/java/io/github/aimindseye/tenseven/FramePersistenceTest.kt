package io.github.aimindseye.tenseven

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import io.github.aimindseye.tenseven.frames.FrameSettings
import io.github.aimindseye.tenseven.platform.StartStateRepository
import org.junit.Assert.*
import org.junit.Test

class FramePersistenceTest {
    @Test fun launcherSurvivesStopResumeAndActivityRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        StartStateRepository(context).saveOnboardingDone()
        val scenario = androidx.test.core.app.ActivityScenario.launch(SableStartActivity::class.java)
        try {
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            scenario.recreate()
            scenario.onActivity { activity ->
                assertFalse(activity.isDestroyed)
                val caps = io.github.aimindseye.tenseven.platform.PlatformCapabilityProbe.detect(activity)
                assertEquals(io.github.aimindseye.tenseven.platform.CapabilityStatus.Unavailable, caps.thirdPartySnapshots)
            }
        } finally { scenario.close() }
    }

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
