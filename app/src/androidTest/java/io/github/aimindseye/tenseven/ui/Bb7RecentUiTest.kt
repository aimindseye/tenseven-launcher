package io.github.aimindseye.tenseven.ui

import android.content.ComponentName
import android.content.Context
import android.os.Process
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aimindseye.tenseven.design.SableAppearance
import io.github.aimindseye.tenseven.design.SableTheme
import io.github.aimindseye.tenseven.model.ForegroundEvent
import io.github.aimindseye.tenseven.model.RecentLaunches
import io.github.aimindseye.tenseven.platform.LiveSurfaceRepository
import io.github.aimindseye.tenseven.platform.StartStateRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class Bb7RecentUiTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences get() = context.getSharedPreferences("recent_launches", Context.MODE_PRIVATE)
    private var originalHistory: String? = null
    private var originalRemember = true
    private val opened = mutableListOf<String>()

    @Before
    fun retainTestTargetPreferences() {
        check(context.packageName == "io.github.aimindseye.tenseven.debug.l0")
        originalHistory = preferences.getString("launches", null)
        val state = StartStateRepository(context)
        originalRemember = state.loadFrameSettings().rememberReturnedApps
        state.saveFrameSettings(state.loadFrameSettings().copy(rememberReturnedApps = true))
    }

    @After
    fun restoreTestTargetPreferences() {
        preferences.edit().putString("launches", originalHistory).commit()
        val state = StartStateRepository(context)
        state.saveFrameSettings(state.loadFrameSettings().copy(rememberReturnedApps = originalRemember))
    }

    @Test
    fun recentUsesRealHistoryOrderForTouchAndKeyboard() {
        saveHistory(listOf(ForegroundEvent(OLD_APP, OLD_TIME), ForegroundEvent(NEW_APP, NEW_TIME)))
        showHome(mutableIntStateOf(0))
        compose.onRoot().performKeyInput {
            pressKey(Key.Spacebar)
            pressKey(Key.Spacebar)
        }
        compose.onNodeWithText("Recent").assertIsDisplayed()
        compose.onNodeWithText("Zulu newest").assertIsDisplayed()
        compose.onNodeWithText("Alpha older").assertIsDisplayed()
        val newest = compose.onNodeWithText("Zulu newest").fetchSemanticsNode().boundsInRoot
        val oldest = compose.onNodeWithText("Alpha older").fetchSemanticsNode().boundsInRoot
        assertTrue(newest.left < oldest.left)
        compose.onNodeWithText("Recent").performClick()
        compose.onRoot().performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionLeft)
            pressKey(Key.Enter)
        }
        compose.runOnIdle { assertEquals(listOf(NEW_APP), opened) }
        compose.onNodeWithText("Alpha older").performClick()
        compose.runOnIdle { assertEquals(listOf(NEW_APP, OLD_APP), opened) }
    }

    @Test
    fun resumeRefreshReordersAndDeduplicatesRecentApps() {
        saveHistory(listOf(ForegroundEvent(NEW_APP, NEW_TIME), ForegroundEvent(OLD_APP, OLD_TIME)))
        val generation = mutableIntStateOf(0)
        showHome(generation)
        repeat(2) { compose.onNodeWithText("›").performClick() }
        compose.runOnIdle {
            saveHistory(listOf(ForegroundEvent(OLD_APP, LATEST_TIME), ForegroundEvent(NEW_APP, NEW_TIME)))
            generation.intValue++
        }
        val oldest = compose.onNodeWithText("Alpha older").fetchSemanticsNode().boundsInRoot
        val newest = compose.onNodeWithText("Zulu newest").fetchSemanticsNode().boundsInRoot
        assertTrue(oldest.left < newest.left)
    }

    @Test
    fun unavailableHistoryShowsAnHonestEmptyState() {
        saveHistory(listOf(ForegroundEvent("fixture.uninstalled", NEW_TIME)))
        showHome(mutableIntStateOf(0))
        repeat(2) { compose.onNodeWithText("›").performClick() }
        compose.onNodeWithText("Recent").assertIsDisplayed()
        compose.onNodeWithText(EMPTY).assertIsDisplayed()
        compose.onNodeWithText("Zulu newest").assertDoesNotExist()
    }

    @Test
    fun privacyOptOutDoesNotShowPreviouslyStoredApps() {
        saveHistory(listOf(ForegroundEvent(NEW_APP, NEW_TIME)))
        val state = StartStateRepository(context)
        state.saveFrameSettings(state.loadFrameSettings().copy(rememberReturnedApps = false))
        showHome(mutableIntStateOf(0))
        repeat(2) { compose.onNodeWithText("›").performClick() }
        compose.onNodeWithText(EMPTY).assertIsDisplayed()
        compose.onNodeWithText("Zulu newest").assertDoesNotExist()
    }

    private fun showHome(generation: MutableIntState) {
        val apps = listOf(app("Alpha older", OLD_APP), app("Zulu newest", NEW_APP))
        compose.setContent {
            SableTheme {
                Bb7HomeScreen(
                    apps = apps,
                    favoriteKeys = emptyList(),
                    homeRows = 1,
                    liveSurfaceRepository = LiveSurfaceRepository(context),
                    appearance = SableAppearance(),
                    refreshGeneration = generation.intValue,
                    homeGeneration = 0,
                    onLaunchApp = {
                        opened.add(it.component.packageName)
                        true
                    },
                    onOpenAppInfo = {},
                    onOpenHub = {},
                    onFavoritesChanged = {},
                    onHomeRowsChanged = {},
                    onOpenMenu = {},
                    gridColumns = GRID_COLUMNS,
                )
            }
        }
    }

    private fun app(
        label: String,
        packageName: String,
    ) = io.github.aimindseye.tenseven.model.AppEntry(
        label,
        ComponentName(packageName, "$packageName.Main"),
        Process.myUserHandle(),
    )

    private fun saveHistory(events: List<ForegroundEvent>) {
        preferences.edit().putString("launches", RecentLaunches.encode(events)).commit()
    }

    private companion object {
        const val OLD_APP = "fixture.old"
        const val NEW_APP = "fixture.new"
        const val OLD_TIME = 10L
        const val NEW_TIME = 20L
        const val LATEST_TIME = 30L
        const val GRID_COLUMNS = 4
        const val EMPTY =
            "No recent apps. Open an app from TenSeven; " +
                "history follows Remember returned apps in Settings."
    }
}
