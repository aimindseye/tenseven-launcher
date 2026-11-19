package io.github.aimindseye.tenseven

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aimindseye.tenseven.model.HomeStyle
import io.github.aimindseye.tenseven.platform.StartStateRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Clean-emulator startup checks; physical keyboard, roles and Titan 2 acceptance remain separate. */
class L0CoreStartupInstrumentationTest {
    @Test
    fun bb7StartsWithoutWatchOrCompanions() = start(HomeStyle.Bb7)

    @Test
    fun bb10StartsWithoutWatchOrCompanions() = start(HomeStyle.Bb10)

    @Test
    fun launcherSettingsOpenIndependently() {
        ActivityScenario.launch(LauncherSettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertNotNull(activity.window.decorView)
            }
        }
    }

    private fun start(style: HomeStyle) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "io.github.aimindseye.tenseven.debug.l0")
        val state = StartStateRepository(context)
        val preferences = context.getSharedPreferences(StartStateRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val keys = listOf(StartStateRepository.KEY_HOME_STYLE, StartStateRepository.KEY_ONBOARDING_DONE)
        val prior = preferences.all.filterKeys { it in keys }
        try {
            instrumentation.runOnMainSync {
                state.saveOnboardingDone()
                state.saveHomeStyle(style)
            }
            ActivityScenario.launch(SableStartActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    assertFalse(activity.isFinishing)
                    assertNotNull(activity.window.decorView)
                }
            }
        } finally {
            val editor = preferences.edit()
            for (key in keys) {
                when (val value = prior[key]) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    null -> editor.remove(key)
                    else -> error("Unexpected preference type: $key")
                }
            }
            check(editor.commit())
        }
    }
}
