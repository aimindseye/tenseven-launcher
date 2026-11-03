package io.github.aimindseye.tenseven

import android.content.Context
import android.os.Process
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aimindseye.tenseven.frames.FrameApp
import io.github.aimindseye.tenseven.model.HomeStyle
import io.github.aimindseye.tenseven.platform.FrameWindowRepository
import io.github.aimindseye.tenseven.platform.StartStateRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** CI runs this twice with a real force-stop between phases; normal test discovery skips it. */
class ProcessReentryTest {
    @Test
    fun savedFrameWindowAndHomeStyleSurviveNewProcess() {
        val phase = InstrumentationRegistry.getArguments().getString("p2Phase")
        assumeTrue(phase == "seed" || phase == "verify")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val state = StartStateRepository(context)
        val window = FrameWindowRepository(context)
        val probe = context.getSharedPreferences("p2_process_probe", Context.MODE_PRIVATE)
        if (phase == "seed") {
            assertTrue(
                probe
                    .edit()
                    .putInt("pid", Process.myPid())
                    .putString("style", state.loadHomeStyle().name)
                    .commit(),
            )
            state.saveHomeStyle(HomeStyle.Bb10)
            state.saveOnboardingDone()
            window.clear()
            window.record(FrameApp("com.android.settings", Process.myUserHandle().hashCode()))
            assertTrue(
                context
                    .getSharedPreferences(StartStateRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("p2_flush", true)
                    .commit(),
            )
            assertTrue(
                context
                    .getSharedPreferences("active_frame_window_v12", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("p2_flush", true)
                    .commit(),
            )
        } else {
            assertNotEquals("Verification must run in a new OS process", probe.getInt("pid", -1), Process.myPid())
            assertEquals(HomeStyle.Bb10, state.loadHomeStyle())
            assertTrue(window.update(emptyList()).any { it.app.packageName == "com.android.settings" })
            ActivityScenario.launch(SableStartActivity::class.java).use { scenario ->
                scenario.onActivity { assertEquals(HomeStyle.Bb10, StartStateRepository(it).loadHomeStyle()) }
            }
            window.clear()
            state.saveHomeStyle(HomeStyle.entries.firstOrNull { it.name == probe.getString("style", null) } ?: HomeStyle.DEFAULT)
            context
                .getSharedPreferences(StartStateRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove("p2_flush")
                .commit()
            probe.edit().clear().commit()
        }
    }
}
