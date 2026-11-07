package io.github.aimindseye.tenseven.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.LauncherTheme
import io.github.aimindseye.tenseven.conversations.ConversationFilter
import io.github.aimindseye.tenseven.conversations.ConversationHub
import io.github.aimindseye.tenseven.conversations.ConversationMessage
import io.github.aimindseye.tenseven.conversations.ConversationState
import io.github.aimindseye.tenseven.conversations.HubNotification
import io.github.aimindseye.tenseven.conversations.availableSourceActions
import io.github.aimindseye.tenseven.conversations.performHubSourceAction
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.model.QuickBar
import io.github.aimindseye.tenseven.platform.FramePlatformAccess
import io.github.aimindseye.tenseven.platform.LockLayerState
import io.github.aimindseye.tenseven.platform.PlatformCapabilities
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class BlackBerryUiTest {
    @get:Rule val compose = createComposeRule()

    @After fun clearFixtures() {
        ConversationHub.replace(emptyList())
        LockLayerState.update(emptyMap())
    }

    @Test fun referenceBarKeepsActionsInOneRowAndRoutesPages() {
        var page = -1
        var opened = ""
        compose.setContent {
            LauncherTheme {
                Column(Modifier.width(360.dp)) {
                    ReferenceHomeBar(
                        listOf(QuickSlot(QuickBar.PHONE, "Phone", null), QuickSlot(QuickBar.COMMAND, "Command", null)),
                        HomeBarPages(1, 3, 0),
                        HomeBarActions({ page = it }, { opened = it.token }, {}),
                    )
                }
            }
        }
        val hub = compose.onNodeWithContentDescription("Hub").fetchSemanticsNode().boundsInRoot
        val camera = compose.onNodeWithContentDescription("Camera").fetchSemanticsNode().boundsInRoot
        assertEquals(hub.top, camera.top, 1f)
        compose.onNodeWithContentDescription("Apps page 1").performClick()
        compose.runOnIdle { assertEquals(2, page) }
        compose.onNodeWithContentDescription("Camera").performClick()
        compose.runOnIdle { assertEquals(QuickBar.CAMERA, opened) }
    }

    @Test fun lockedSelectionIsRedactedAndUnlockedDetailsStayWithinSelectedApp() {
        val first = fixture("one", "First app text")
        val second = fixture("two", "Second app text")
        ConversationHub.replace(listOf(first, second))
        LockLayerState.update(mapOf(first.packageName to 1, second.packageName to 1))
        val locked = mutableStateOf(true)
        var opens = 0
        var reveals = 0
        compose.setContent {
            var selected by remember { mutableStateOf<String?>(null) }
            CompositionLocalProvider(LocalPlatformCapabilities provides capabilities(locked.value)) {
                LauncherTheme {
                    LockNotificationScreen(
                        setOf(first.packageName, second.packageName),
                        true,
                        selected,
                        LockScreenActions({ selected = it }, {}, { reveals++ }, { opens++ }, {}),
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("${first.packageName}, 1 notifications").performClick()
        compose.onNodeWithText("Content hidden").assertIsDisplayed()
        compose.onNodeWithText("First app text").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, opens)
            assertEquals(0, reveals)
        }
        compose.onNodeWithText("Unlock to view").performClick()
        compose.runOnIdle {
            assertEquals(1, reveals)
            locked.value = false
        }
        compose.onNodeWithText("First app text").assertIsDisplayed()
        compose.onNodeWithText("Second app text").assertDoesNotExist()
        compose.onNodeWithContentDescription("${second.packageName}, 1 notifications").performClick()
        compose.onNodeWithText("Second app text").assertIsDisplayed()
        compose.onNodeWithText("First app text").assertDoesNotExist()
        compose.runOnIdle { locked.value = true }
        compose.onNodeWithText("Second app text").assertDoesNotExist()
        compose.onNodeWithText("Content hidden").assertIsDisplayed()
    }

    @Test fun selectingHubMessageDoesNotReadOrOpenIt() {
        ConversationHub.replace(listOf(fixture("one", "Message")))
        var reads = 0
        var opens = 0
        val ui = hubUi({ reads++ }, { opens++ })
        compose.setContent {
            LauncherTheme {
                HubPanelsHost(ui, {}) { settings ->
                    val select = LocalHubRowSelection.current
                    val rowFocus = remember { FocusRequester() }
                    Column {
                        Text("Views", Modifier.clickable(onClick = settings))
                        Text(
                            "Select message",
                            Modifier.focusRequester(rowFocus).sableFocusRing().clickable {
                                select(ui.conversations.single().id, rowFocus)
                            },
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("Select message").performClick()
        compose.onNodeWithTag("hub-message-panel").assertIsDisplayed()
        compose.onNodeWithText("Reply All").assertDoesNotExist()
        compose.onNodeWithText("Forward").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, reads)
            assertEquals(0, opens)
        }
        compose.onNodeWithText("Mark read").performClick()
        compose.runOnIdle {
            assertEquals(1, reads)
            assertEquals(0, opens)
        }
        compose.onNodeWithTag("hub-message-panel").assertDoesNotExist()
        compose.onNodeWithText("Views").performClick()
        compose.onNodeWithTag("hub-settings-panel").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithTag("hub-settings-panel").assertDoesNotExist()
    }

    @Test fun emailActionsUseProviderCapabilitiesAndNeverExecuteOnSelection() {
        val email =
            fixture("mail", "Email body").copy(
                isEmail = true,
                sourceActions = listOf(sourceAction("Reply All", true), sourceAction("Forward", false)),
            )
        ConversationHub.replace(listOf(email))
        val stale = email.availableSourceActions(false).first()
        compose.setContent {
            LauncherTheme {
                val revision = rememberHubRevision()
                val ui = remember(revision) { hubUi({}, {}) }
                HubPanelsHost(ui, {}) {
                    val select = LocalHubRowSelection.current
                    val focus = remember { FocusRequester() }
                    Text(
                        "Select email",
                        Modifier.focusRequester(focus).sableFocusRing().clickable {
                            select(ui.conversations.single().id, focus)
                        },
                    )
                }
            }
        }
        compose.onNodeWithText("Select email").performClick()
        compose.onNodeWithText("Email options").assertIsDisplayed()
        compose.onNodeWithText("Forward").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Reply All").performScrollTo().performClick()
        compose.onNodeWithText("Send").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            ConversationHub.replace(listOf(email.copy(sourceActions = emptyList())))
            val context =
                androidx.test.platform.app.InstrumentationRegistry
                    .getInstrumentation()
                    .targetContext
            assertFalse(performHubSourceAction(context, stale, "Do not send"))
        }
        compose.onNodeWithText("Reply All").assertDoesNotExist()
        compose.onNodeWithText("Forward").assertDoesNotExist()
    }

    private fun sourceAction(
        label: String,
        text: Boolean,
    ): android.app.Notification.Action {
        val context =
            androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        val intent = android.content.Intent(context, io.github.aimindseye.tenseven.SableStartActivity::class.java)
        val pending =
            android.app.PendingIntent.getActivity(
                context,
                label.hashCode(),
                intent,
                android.app.PendingIntent.FLAG_MUTABLE,
            )
        val icon =
            android.graphics.drawable.Icon
                .createWithResource(context, android.R.drawable.ic_dialog_email)
        return android.app.Notification.Action
            .Builder(icon, label, pending)
            .apply {
                if (text) {
                    addRemoteInput(
                        android.app.RemoteInput
                            .Builder("reply")
                            .setLabel(label)
                            .build(),
                    )
                }
            }.build()
    }

    private fun fixture(
        suffix: String,
        text: String,
    ): HubNotification {
        val pkg = "lock.fixture.$suffix"
        val message = ConversationMessage(suffix, suffix, suffix, pkg, suffix, text, 1L, "Sender", false)
        return HubNotification(suffix, suffix, "Sender $suffix", pkg, suffix, 1L, false, listOf(message), null, null)
    }

    private fun capabilities(locked: Boolean) =
        PlatformCapabilities.evaluate(
            true,
            true,
            false,
            false,
            false,
            false,
            FramePlatformAccess(deviceLocked = locked),
        )

    private fun hubUi(
        onRead: () -> Unit,
        onOpen: () -> Unit,
    ) = HubConversationsUi(
        accessGranted = true,
        enabledAppCount = 1,
        onChooseApps = {},
        deviceLocked = false,
        conversations = ConversationHub.conversations(),
        filter = ConversationFilter(),
        sourcePackage = null,
        onSourceChange = {},
        state = ConversationState.EMPTY,
        now = 1L,
        onOpen = { onOpen() },
        onReply = { _, _ -> false },
        onAllowAccess = {},
        onFilterChange = {},
        onMarkRead = { onRead() },
        onSnooze = { _, _ -> },
        onWake = {},
        onRemind = { _, _ -> },
        onCustomRemind = {},
        reminders = emptyList(),
        remindersSelected = false,
        onShowReminders = {},
    )
}
