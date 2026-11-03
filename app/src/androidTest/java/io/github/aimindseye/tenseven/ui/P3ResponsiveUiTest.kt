package io.github.aimindseye.tenseven.ui

import android.content.ComponentName
import android.os.Process
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aimindseye.tenseven.DeviceCompatibilityActivity
import io.github.aimindseye.tenseven.LauncherSettingsActivity
import io.github.aimindseye.tenseven.frames.FrameApp
import io.github.aimindseye.tenseven.frames.FrameItem
import io.github.aimindseye.tenseven.frames.FramesState
import io.github.aimindseye.tenseven.live.LiveAvailability
import io.github.aimindseye.tenseven.live.LiveDatum
import io.github.aimindseye.tenseven.live.LiveSurfaceSnapshot
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.model.GridColumns
import io.github.aimindseye.tenseven.model.HomeViewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class P3ResponsiveUiTest {
    @get:Rule val compose = createComposeRule()
    private val empty = LiveDatum("", "", LiveAvailability.EMPTY)
    private val snapshot = LiveSurfaceSnapshot("Today", "12:00", empty, empty, empty, empty, empty, empty, 0)

    @Test fun largeTextNarrowFramesBecomeOneColumnAndLastFrameRemainsReachable() {
        val items = (1..8).map { FrameItem(FrameApp("p3.app$it", 0)) }
        val state = mutableStateOf(FramesState(items = items))
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                MaterialTheme {
                    ActiveFramesGrid(
                        state.value,
                        emptyList(),
                        snapshot,
                        emptySet(),
                        emptyList(),
                        { _, _ -> null },
                        true,
                        {},
                        { item -> state.value = state.value.copy(items = state.value.items - item) },
                        Modifier.size(300.dp, 300.dp),
                    )
                }
            }
        }
        val first = compose.onNodeWithTag(items[0].key).fetchSemanticsNode().boundsInRoot
        val next = compose.onNodeWithTag(items[1].key).fetchSemanticsNode().boundsInRoot
        assertEquals(first.left, next.left, 1f)
        assertTrue(next.top > first.top)
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(items.last().key))
        compose.onNodeWithTag(items.last().key).assertIsDisplayed()
        compose.onNodeWithTag("close:${items.last().key}", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(7, state.value.items.size) }
    }

    @Test fun largeTextGridRetainsTouchAndKeyboardLaunchInAvailableCellSpace() {
        val apps = (1..2).map { AppEntry("P3 app $it", ComponentName("p3.app$it", "Main"), Process.myUserHandle()) }
        var launches = 0
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                MaterialTheme {
                    Box(Modifier.size(300.dp, 180.dp)) {
                        AppGridPage(
                            apps,
                            HomeViewport.rows(180, 74, 2f),
                            HomeViewport.columns(300, GridColumns.AUTO, 2f),
                            1f,
                            apps.map { it.label }.toSet(),
                            true,
                            { launches++ },
                            {},
                            {},
                            {},
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("P3 app 1").assertIsDisplayed().performClick()
        compose.onNodeWithText("P3 app 1").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(2, launches) }
    }

    @Test fun fnEnterFallsBackToHomeMenuOnKeyboardsWithoutMenuKey() {
        var menus = 0
        var hostView: android.view.View? = null
        compose.setContent {
            val view = LocalView.current
            SideEffect { hostView = view }
            MaterialTheme {
                val pager =
                    androidx.compose.foundation.pager
                        .rememberPagerState { 1 }
                StartStrip(
                    state = pager,
                    pageCount = 1,
                    pageLead = 0,
                    quickSlots = emptyList(),
                    onMovePage = {},
                    onShowPage = {},
                    onQuickSlot = {},
                    onSlotOptions = {},
                    onHubPeek = {},
                    onDial = {},
                    onTypeToSearch = {},
                    speedDialLetters = emptySet(),
                    onSpeedDial = {},
                    onCommand = {},
                    onEditHome = { menus++ },
                    page = {},
                )
            }
        }
        compose.waitForIdle()
        awaitWindowFocus(checkNotNull(hostView))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val now = android.os.SystemClock.uptimeMillis()
        instrumentation.sendKeySync(
            android.view.KeyEvent(
                now,
                now,
                android.view.KeyEvent.ACTION_DOWN,
                android.view.KeyEvent.KEYCODE_ENTER,
                0,
                android.view.KeyEvent.META_FUNCTION_ON,
            ),
        )
        instrumentation.sendKeySync(
            android.view.KeyEvent(
                now,
                now,
                android.view.KeyEvent.ACTION_UP,
                android.view.KeyEvent.KEYCODE_ENTER,
                0,
                android.view.KeyEvent.META_FUNCTION_ON,
            ),
        )
        compose.runOnIdle { assertEquals(1, menus) }
    }

    @Test fun settingsDeviceEntryRemainsReachableOnSmallOrLargeTextWindows() {
        ActivityScenario.launch(LauncherSettingsActivity::class.java).use {
            compose.onNodeWithText("Device compatibility").performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun foregroundKeyProbeStopsCaptureWhenItsActivityPauses() {
        ActivityScenario.launch(DeviceCompatibilityActivity::class.java).use { scenario ->
            var window: android.view.View? = null
            scenario.onActivity { window = it.window.decorView }
            awaitWindowFocus(checkNotNull(window))
            compose.onNodeWithText("Start key capture").performScrollTo().performClick()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_A)
            compose.waitForIdle()
            compose.onNodeWithText("key=29", substring = true).performScrollTo().assertIsDisplayed()
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithText("Start key capture").performScrollTo().assertIsDisplayed()
        }
    }

    private fun awaitWindowFocus(view: android.view.View) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        compose.waitUntil(timeoutMillis = WINDOW_FOCUS_TIMEOUT_MS) {
            var focused = false
            instrumentation.runOnMainSync { focused = view.hasWindowFocus() }
            focused
        }
        compose.waitForIdle()
    }

    private companion object {
        const val WINDOW_FOCUS_TIMEOUT_MS = 5_000L
    }
}
