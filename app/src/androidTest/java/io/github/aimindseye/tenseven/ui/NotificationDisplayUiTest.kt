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
        compose.setContent { LauncherTheme {
            Column(Modifier.width(200.dp)) {
                HomeNotificationStrip(counts, (1..8).map { "app$it" }.toSet(), expanded,
                    onOpenHub = { hubOpens++ }, onOpenApp = { opened = it })
            }
        } }
        compose.onNodeWithText("+4").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, hubOpens); expanded = true }
        compose.onNodeWithText("+4").assertDoesNotExist()
        (1..8).forEach { compose.onNodeWithTag("home-notification-app$it").assertIsDisplayed() }
        compose.onNodeWithTag("home-notification-disabled").assertDoesNotExist()
        compose.onNodeWithTag("home-notification-app8").performClick()
        compose.runOnIdle { assertEquals("app8", opened); assertEquals(1, hubOpens) }
    }

    @Test fun hubAllIconsAreVisibleWithoutMoreAndSelectOnlyTheirSource() {
        var all by mutableStateOf(false)
        var source: String? = null
        val ui = HubConversationsUi(
            accessGranted = true, enabledAppCount = 2, onChooseApps = {}, deviceLocked = false,
            conversations = emptyList(), filter = ConversationFilter(), sourcePackage = null,
            onSourceChange = { source = it }, state = ConversationState.EMPTY, now = 0L,
            onOpen = {}, onReply = { _, _ -> false }, onAllowAccess = {}, onFilterChange = {},
            onMarkRead = {}, onSnooze = { _, _ -> }, onWake = {}, onRemind = { _, _ -> },
            onCustomRemind = {}, reminders = emptyList(), remindersSelected = false, onShowReminders = {},
        )
        compose.setContent { LauncherTheme {
            HubUnifiedNavigation(ui, HubFilter.All, {}, remember { FocusRequester() },
                listOf(Triple("mail", "Mail fixture", 2), Triple("chat", "Chat fixture", 1)), 1, 2, all)
        } }
        compose.onNodeWithText("Mail fixture · 2").assertDoesNotExist()
        compose.runOnIdle { all = true }
        compose.onNodeWithText("Mail fixture · 2").assertIsDisplayed().performClick()
        compose.onNodeWithText("Chat fixture · 1").assertIsDisplayed()
        compose.runOnIdle { assertEquals("mail", source) }
    }

    @Test fun displayPreferencesAreIndependentAndInternalStorageNeedsNoPermission() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val state = StartStateRepository(context)
        val original = HomeStyle.entries.associateWith { style ->
            Triple(state.loadHomeNotificationExpanded(style), state.loadHubAllIcons(style), state.loadHomeDials(style))
        }
        try {
            state.saveHomeNotificationExpanded(HomeStyle.Bb7, true)
            state.saveHomeNotificationExpanded(HomeStyle.Bb10, false)
            state.saveHubAllIcons(HomeStyle.Bb7, false)
            state.saveHubAllIcons(HomeStyle.Bb10, true)
            state.saveHomeDials(HomeStyle.Bb7, setOf("storage"))
            state.saveHomeDials(HomeStyle.Bb10, setOf("cpu"))
            val reload = StartStateRepository(context)
            assertTrue(reload.loadHomeNotificationExpanded(HomeStyle.Bb7))
            assertFalse(reload.loadHomeNotificationExpanded(HomeStyle.Bb10))
            assertFalse(reload.loadHubAllIcons(HomeStyle.Bb7))
            assertTrue(reload.loadHubAllIcons(HomeStyle.Bb10))
            assertEquals(setOf("storage"), reload.loadHomeDials(HomeStyle.Bb7))
            assertEquals(setOf("cpu"), reload.loadHomeDials(HomeStyle.Bb10))
            val storage = DeviceStatsSampler().sample(context).storage
            assertNotNull(storage)
            assertTrue(storage!! in 0f..1f)
        } finally { original.forEach { (style, values) ->
            state.saveHomeNotificationExpanded(style, values.first)
            state.saveHubAllIcons(style, values.second)
            state.saveHomeDials(style, values.third)
        } }
    }
}
