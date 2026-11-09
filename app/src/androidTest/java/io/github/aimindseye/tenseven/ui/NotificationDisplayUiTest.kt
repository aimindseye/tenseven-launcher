package io.github.aimindseye.tenseven.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.github.aimindseye.tenseven.LauncherTheme
import io.github.aimindseye.tenseven.conversations.ConversationFilter
import io.github.aimindseye.tenseven.conversations.ConversationState
import io.github.aimindseye.tenseven.live.AppUnreadCount
import io.github.aimindseye.tenseven.model.HomeStyle
import io.github.aimindseye.tenseven.model.HubFilter
import io.github.aimindseye.tenseven.platform.DeviceStatsSampler
import io.github.aimindseye.tenseven.platform.StartStateRepository
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NotificationDisplayUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun homeAllIconsWrapAndEachIconOpensItsOwnApp() {
        val counts = (1..8).map { AppUnreadCount("app$it", 1) } + AppUnreadCount("disabled", 10)
        var expanded by mutableStateOf(false)
        var opened: String? = null
        var hubOpens = 0
        compose.setContent {
            LauncherTheme {
                Column(Modifier.width(200.dp)) {
                    HomeNotificationStrip(
                        counts,
                        (1..8).map { "app$it" }.toSet(),
                        expanded,
                        onOpenHub = { hubOpens++ },
                        onOpenApp = { opened = it },
                    )
                }
            }
        }
        compose.onNodeWithText("+4").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, hubOpens)
            expanded = true
        }
        compose.onNodeWithText("+4").assertDoesNotExist()
        (1..8).forEach { compose.onNodeWithTag("home-notification-app$it").assertIsDisplayed() }
        compose.onNodeWithTag("home-notification-disabled").assertDoesNotExist()
        compose.onNodeWithTag("home-notification-app8").performClick()
        compose.runOnIdle {
            assertEquals("app8", opened)
            assertEquals(1, hubOpens)
        }
    }

    @Test fun hubShortcutsStayInOneRowAndPerformTheirNamedActions() {
        var selectedTab = HubFilter.All
        var selectedFilter = ConversationFilter()
        var calendarOpens = 0
        var remindOpens = 0
        var composeOpens = 0
        var searchOpens = 0
        var viewsOpen by mutableStateOf(false)
        val ui =
            HubConversationsUi(
                accessGranted = true,
                enabledAppCount = 1,
                onChooseApps = {},
                deviceLocked = false,
                conversations = emptyList(),
                filter = ConversationFilter(),
                sourcePackage = null,
                onSourceChange = {},
                state = ConversationState.EMPTY,
                now = 0L,
                onOpen = {},
                onReply = { _, _ -> false },
                onAllowAccess = {},
                onFilterChange = { selectedFilter = it },
                onMarkRead = {},
                onSnooze = { _, _ -> },
                onWake = {},
                onRemind = { _, _ -> },
                onCustomRemind = {},
                reminders = emptyList(),
                remindersSelected = false,
                onShowReminders = { remindOpens++ },
            )
        compose.setContent {
            LauncherTheme {
                Column(Modifier.width(200.dp)) {
                    HubUnifiedNavigation(
                        ui,
                        HubFilter.All,
                        { selectedTab = it },
                        remember { FocusRequester() },
                        0,
                        0,
                        onOpenCalendar = { calendarOpens++ },
                    )
                    HubCompactBar(
                        onViews = { viewsOpen = true },
                        onCompose = { composeOpens++ },
                        onSearch = { searchOpens++ },
                    )
                    if (viewsOpen) {
                        HubCompactViews(
                            ui = ui,
                            onTabChange = { selectedTab = it },
                            onCalendar = { calendarOpens++ },
                            onDismiss = { viewsOpen = false },
                        )
                    }
                }
            }
        }
        // With no notifications the approved dynamic top tabs show All only.
        compose.onNodeWithText("All").assertIsDisplayed()
        compose.onNodeWithText("Messages").assertDoesNotExist()
        val nav = listOf("Views", "Compose", "Search")
        val top = compose.onNodeWithContentDescription("Views").fetchSemanticsNode().boundsInRoot.top
        nav.forEach { label ->
            val node = compose.onNodeWithContentDescription(label).assertIsDisplayed()
            assertEquals(top, node.fetchSemanticsNode().boundsInRoot.top, 0.1f)
        }
        compose.onNodeWithContentDescription("Compose").performClick()
        compose.onNodeWithContentDescription("Search").performClick()
        compose.runOnIdle {
            assertEquals(1, composeOpens)
            assertEquals(1, searchOpens)
        }
        // The old ten-item icon shelf was replaced by Views; check actual routes.
        compose.onNodeWithContentDescription("Views").performClick()
        compose.onNodeWithText("Calls").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(HubFilter.Calls, selectedTab) }
        compose.onNodeWithContentDescription("Views").performClick()
        compose.onNodeWithText("Calendar").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, calendarOpens) }
        compose.onNodeWithContentDescription("Views").performClick()
        compose.onNodeWithText("Snoozed").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(selectedFilter.snoozed)
            assertEquals(HubFilter.All, selectedTab)
        }
        compose.onNodeWithContentDescription("Views").performClick()
        compose.onNodeWithText("Remind").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, remindOpens) }
    }

    @Test fun sharedHomeDialsAndIndependentNotificationExpansionNeedNoStoragePermission() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val state = StartStateRepository(context)
        val original =
            HomeStyle.entries.associateWith { style ->
                state.loadHomeNotificationExpanded(style) to state.loadHomeDials(style)
            }
        try {
            // D4: notification expansion stays per-style; status dials are intentionally shared.
            state.saveHomeNotificationExpanded(HomeStyle.Bb7, true)
            state.saveHomeNotificationExpanded(HomeStyle.Bb10, false)
            state.saveHomeDials(HomeStyle.Bb7, setOf("storage"))
            val reload = StartStateRepository(context)
            assertTrue(reload.loadHomeNotificationExpanded(HomeStyle.Bb7))
            assertFalse(reload.loadHomeNotificationExpanded(HomeStyle.Bb10))
            assertEquals(setOf("storage"), reload.loadHomeDials(HomeStyle.Bb7))
            assertEquals(setOf("storage"), reload.loadHomeDials(HomeStyle.Bb10))
            reload.saveHomeDials(HomeStyle.Bb10, setOf("cpu"))
            assertEquals(setOf("cpu"), state.loadHomeDials(HomeStyle.Bb7))
            assertEquals(setOf("cpu"), reload.loadHomeDials(HomeStyle.Bb10))
            val storage = DeviceStatsSampler().sample(context).storage
            assertNotNull(storage)
            assertTrue(storage!! in 0f..1f)
        } finally {
            original.forEach { (style, values) ->
                state.saveHomeNotificationExpanded(style, values.first)
                state.saveHomeDials(style, values.second)
            }
        }
    }

}
