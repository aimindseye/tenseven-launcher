package io.github.aimindseye.tenseven.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.frames.*
import io.github.aimindseye.tenseven.live.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class ActiveFramesUiTest {
    @get:Rule val compose = createComposeRule()
    private val datum = LiveDatum("", "", LiveAvailability.EMPTY)
    private val snapshot = LiveSurfaceSnapshot("Today", "12:00", datum, datum, datum, datum, datum, datum, 0)

    @Test fun keyboardEnterAndXUseSeparateOpenAndSafeCloseCallbacks() {
        var opened = 0
        var closed = 0
        compose.setContent {
            MaterialTheme {
                FrameTile("test", "Mail", "Recent app", "2 new", true, { opened++ }, { closed++ }, Modifier.size(170.dp))
            }
        }
        compose.onNodeWithTag("test").performClick()
        compose.onNodeWithTag("test").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        compose.onNodeWithTag("test").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(2, opened) }
        compose.onNodeWithTag("test").performKeyInput { pressKey(Key.X) }
        compose.runOnIdle { assertEquals(1, closed) }
        compose.onNodeWithTag("close:test", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(2, closed) }
    }

    @Test fun gridFitsTwoColumnsAndRowsAndRefocusesAfterClose() {
        val values = (1..6).map { FrameItem(FrameApp("app$it", 0)) }
        val state = mutableStateOf(FramesState(items = values))
        compose.setContent {
            MaterialTheme {
                ActiveFramesGrid(state.value, emptyList(), snapshot, emptySet(), emptyList(), { _, _ -> null },
                    true, {}, { closed -> state.value = state.value.copy(items = state.value.items - closed) }, Modifier.size(360.dp))
            }
        }
        val first = compose.onNodeWithTag(values[0].key).fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag(values[1].key).fetchSemanticsNode().boundsInRoot
        val third = compose.onNodeWithTag(values[2].key).fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, 1f)
        assertTrue(second.left > first.left)
        assertTrue(third.top > first.top)
        compose.onNodeWithTag(values[3].key).assertIsDisplayed()
        compose.onNodeWithTag("close:${values[0].key}", useUnmergedTree = true).performClick()
        compose.onNodeWithTag(values[0].key).assertDoesNotExist()
        compose.onNodeWithTag(values[1].key).assertIsFocused()
    }

    @Test fun permissionOrLifecycleChangeDropsPreviewAndLiveTileStaysExplicit() {
        val item = FrameItem(FrameApp("mail", 0))
        val state = mutableStateOf(FramesState(listOf(item), snapshotsAllowed = true))
        var captures = 0
        compose.setContent {
            MaterialTheme {
                ActiveFramesGrid(state.value, emptyList(), snapshot, emptySet(),
                    listOf(FrameTileView("tile:calendar", "Calendar", "Meeting at 3")),
                    { _, _ -> captures++; null }, true, {}, {}, Modifier.size(360.dp))
            }
        }
        compose.onNodeWithText("Live tile", useUnmergedTree = true).assertExists()
        compose.runOnIdle { state.value = state.value.copy(items = emptyList(), snapshotsAllowed = false, generation = 2) }
        compose.onNodeWithTag(item.key).assertDoesNotExist()
        compose.runOnIdle { assertTrue(captures <= 1) }
    }
    @Test fun liveSummaryReplacesRecentBadgeOnMatchingAppEvenWithFullGrid() {
        val media = snapshot.copy(music = LiveDatum("Music", "Song by Artist", LiveAvailability.LIVE, sourcePackage = "yt.music"))
        val items = listOf(FrameItem(FrameApp("yt.music", android.os.Process.myUserHandle().hashCode()))) +
            (1..3).map { FrameItem(FrameApp("app$it", 0)) }
        compose.setContent {
            MaterialTheme {
                ActiveFramesGrid(FramesState(items, notificationsAllowed = true, settings = FrameSettings(maximum = 4)),
                    emptyList(), media, emptySet(), emptyList(), { _, _ -> null }, true, {}, {}, Modifier.size(360.dp))
            }
        }
        compose.onNodeWithText("Song by Artist", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Live summary", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun sharedCaptureRetainsSlotAndCloseInvokesStop() {
        var stopped = false
        compose.setContent {
            MaterialTheme {
                ActiveFramesGrid(FramesState((1..4).map { FrameItem(FrameApp("app$it", 0)) }, settings = FrameSettings(maximum = 4)),
                    emptyList(), snapshot, emptySet(), listOf(FrameTileView("tile:capture", "Shared screen", "Frozen", onClose = { stopped = true })),
                    { _, _ -> null }, true, {}, {}, Modifier.size(360.dp))
            }
        }
        compose.onNodeWithTag("tile:capture").assertIsDisplayed()
        compose.onNodeWithTag("0:app4:recent").assertDoesNotExist()
        compose.onNodeWithTag("close:tile:capture", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertTrue(stopped) }
    }

    @Test fun ownedIntegrationsDoNotDisplaceLiveAppFromFullGrid() {
        val media = snapshot.copy(music = LiveDatum("Music", "Playing track", LiveAvailability.LIVE, sourcePackage = "yt.music"))
        val item = FrameItem(FrameApp("yt.music", android.os.Process.myUserHandle().hashCode()))
        compose.setContent {
            MaterialTheme {
                ActiveFramesGrid(FramesState(listOf(item), notificationsAllowed = true, settings = FrameSettings(maximum = 4)),
                    emptyList(), media, emptySet(), (1..4).map { FrameTileView("owned:$it", "Local $it", "Saved data") },
                    { _, _ -> null }, true, {}, {}, Modifier.size(360.dp))
            }
        }
        compose.onNodeWithText("Playing track", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("owned:4").assertDoesNotExist()
    }

    @Test fun notificationsCalendarDocumentsAndLocalDataNeverCreateFrames() {
        io.github.aimindseye.tenseven.platform.ScreenCaptureState.stopped()
        val live = snapshot.copy(calendar = LiveDatum("Calendar", "Old calendar event", LiveAvailability.LIVE),
            appSummaries = listOf(
                AppSummary("messages:mail", "Mail", "Old mail text", SummarySource.Notification, "mail"),
                AppSummary("office:word", "Word", "Saved file", SummarySource.Document, "com.microsoft.office.word"),
                AppSummary("local:notes", "Notes", "Old note", SummarySource.Local)))
        compose.setContent {
            MaterialTheme {
                ActiveFramesPage(FramesState(usageAllowed = true, calendarAllowed = true, notificationsAllowed = true),
                    emptyList(), setOf(io.github.aimindseye.tenseven.model.HomeLayout.STATUS_RINGS,
                        io.github.aimindseye.tenseven.model.HomeLayout.WEATHER, io.github.aimindseye.tenseven.model.HomeLayout.SEARCH_ROW),
                    {}, {}, live, 0, { _, _ -> null }, true, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag("frames-empty").assertIsDisplayed()
        compose.onNodeWithText("Old calendar event").assertDoesNotExist()
        compose.onNodeWithText("Old mail text").assertDoesNotExist()
        compose.onNodeWithText("Saved file").assertDoesNotExist()
        compose.onNodeWithText("Old note").assertDoesNotExist()
    }

}
