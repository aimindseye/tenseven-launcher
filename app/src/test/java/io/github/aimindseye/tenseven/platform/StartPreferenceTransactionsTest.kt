package io.github.aimindseye.tenseven.platform

import android.content.Context
import androidx.core.content.edit
import io.github.aimindseye.tenseven.model.BackupValue
import io.github.aimindseye.tenseven.model.Bb7Panel
import io.github.aimindseye.tenseven.model.HomeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class StartPreferenceTransactionsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val preferences
        get() = context.getSharedPreferences(StartStateRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val state get() = StartStateRepository(context)

    @Before
    fun isolatePreferences() {
        preferences.edit(commit = true) { clear() }
    }

    @Test
    fun settingsRemainVisibleToNewRepositoriesImmediatelyAfterSave() {
        state.saveHomeStyle(HomeStyle.Bb7)
        state.saveBb7Panels(listOf(Bb7Panel.All, Bb7Panel.Recent))
        state.saveTypeToSearch(false)
        state.saveHubPeekText(true)
        state.saveHubSelectedCalendarIds(setOf(42L))
        assertEquals(HomeStyle.Bb7, state.loadHomeStyle())
        assertEquals(listOf(Bb7Panel.All, Bb7Panel.Recent), state.loadBb7Panels())
        assertFalse(state.loadTypeToSearch())
        assertTrue(state.loadHubPeekText())
        assertEquals(setOf(42L), state.loadHubSelectedCalendarIds())
    }

    @Test
    fun nullableSelectionsRemoveTheirKeysRatherThanStoringEmptyChoices() {
        state.saveHomeApps(listOf("fixture.app"))
        state.saveHubSelectedCalendarIds(setOf(42L))
        state.saveAppearance("dark", "blue")
        state.saveHomeApps(null)
        state.saveHubSelectedCalendarIds(null)
        state.saveAppearance(null, null)
        assertFalse(preferences.contains(StartStateRepository.KEY_HOME_APPS))
        assertNull(state.loadHubSelectedCalendarIds())
        assertNull(state.loadAppearanceMode())
        assertNull(state.loadAppearanceAccent())
    }

    @Test
    fun restoreReplacesOnlyBackupKeysAndRetainsUnrelatedPrivateState() {
        preferences.edit {
            putString("private_record", "keep")
            putString("appearance_mode", "old")
            putInt("grid_labels", 1)
        }
        state.restoreSettings(
            linkedMapOf(
                "home_style" to BackupValue.Text(HomeStyle.Bb7.stableValue),
                "grid_columns" to BackupValue.Number(4),
                "favorite_apps" to BackupValue.Items(setOf("fixture.app")),
                "private_record" to BackupValue.Text("unauthorized replacement"),
                "unknown_key" to BackupValue.Text("excluded"),
            ),
        )
        assertEquals("keep", preferences.getString("private_record", null))
        assertFalse(preferences.contains("unknown_key"))
        assertFalse(preferences.contains("appearance_mode"))
        assertFalse(preferences.contains("grid_labels"))
        assertEquals(HomeStyle.Bb7, state.loadHomeStyle())
        assertEquals(4, state.loadGridColumns())
        assertEquals(setOf("fixture.app"), state.loadFavoriteKeys())
    }

    @Test
    fun restoreAppliesOneCompleteTransactionToPreferenceObservers() {
        preferences.edit { putString("home_style", HomeStyle.Bb10.stableValue) }
        val observations = mutableListOf<Pair<String?, Int>>()
        val listener =
            android.content.SharedPreferences.OnSharedPreferenceChangeListener { shared, _ ->
                observations += shared.getString("home_style", null) to shared.getInt("grid_columns", 0)
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        try {
            state.restoreSettings(
                mapOf(
                    "home_style" to BackupValue.Text(HomeStyle.Bb7.stableValue),
                    "grid_columns" to BackupValue.Number(4),
                ),
            )
            org.robolectric.Shadows
                .shadowOf(android.os.Looper.getMainLooper())
                .idle()
            assertTrue(observations.isNotEmpty())
            val expected = HomeStyle.Bb7.stableValue to 4
            assertTrue(observations.all { it == expected })
        } finally {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
}
