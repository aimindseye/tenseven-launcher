package io.github.aimindseye.tenseven.platform

import android.content.Context
import io.github.aimindseye.tenseven.model.Bb7Panel
import io.github.aimindseye.tenseven.model.Bb7Panels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class Bb7RecentRepositoryTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val state get() = StartStateRepository(context)

    @Before
    fun isolateJvmPreferences() {
        context
            .getSharedPreferences("recent_launches", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context
            .getSharedPreferences(StartStateRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun savedLaunchesAreAvailableToANewRepositoryWithoutUsageAccess() {
        RecentLaunchesRepository(context).record("fixture.opened")
        assertEquals(listOf("fixture.opened"), RecentLaunchesRepository(context).loadForHome().map { it.packageName })
        assertTrue(context.getSharedPreferences("recent_launches", Context.MODE_PRIVATE).contains("launches"))
    }

    @Test
    fun privacyOptOutHidesOldHistoryAndStopsNewRecordingWithoutDeletingData() {
        val repository = RecentLaunchesRepository(context)
        repository.record("fixture.old")
        val before = repository.load()
        state.saveFrameSettings(state.loadFrameSettings().copy(rememberReturnedApps = false))
        repository.record("fixture.new")
        assertTrue(repository.loadForHome().isEmpty())
        assertEquals(before, repository.load())
        state.saveFrameSettings(state.loadFrameSettings().copy(rememberReturnedApps = true))
        assertEquals(before, repository.loadForHome())
    }

    @Test
    fun launcherItselfIsNotARecentApplication() {
        val repository = RecentLaunchesRepository(context)
        repository.record(context.packageName)
        assertTrue(repository.loadForHome().isEmpty())
    }

    @Test
    fun oldPanelSettingsAreReadWithoutEagerRewriteAndExplicitOptOutPersists() {
        val preferences = context.getSharedPreferences(StartStateRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
        preferences.edit().putString(StartStateRepository.KEY_BB7_PANELS, "Frequent,All").commit()
        assertEquals(listOf(Bb7Panel.Frequent, Bb7Panel.All, Bb7Panel.Recent), state.loadBb7Panels())
        assertEquals("Frequent,All", preferences.getString(StartStateRepository.KEY_BB7_PANELS, null))
        state.saveBb7Panels(Bb7Panels.toggle(state.loadBb7Panels(), Bb7Panel.Recent))
        assertEquals(listOf(Bb7Panel.Frequent, Bb7Panel.All), StartStateRepository(context).loadBb7Panels())
    }
}
