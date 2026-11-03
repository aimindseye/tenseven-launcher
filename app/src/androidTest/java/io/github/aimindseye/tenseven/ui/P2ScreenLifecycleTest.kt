package io.github.aimindseye.tenseven.ui

import android.content.ComponentName
import android.content.Context
import android.os.Process
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import io.github.aimindseye.tenseven.SableStartActivity
import io.github.aimindseye.tenseven.conversations.*
import io.github.aimindseye.tenseven.design.SableFocusMemory
import io.github.aimindseye.tenseven.model.*
import io.github.aimindseye.tenseven.platform.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class P2ScreenLifecycleTest {
    @get:Rule val compose = createComposeRule()

    private fun capabilities(
        allowed: Boolean = true,
        locked: Boolean = false,
    ) = PlatformCapabilities.evaluate(
        true,
        allowed,
        false,
        false,
        false,
        false,
        FramePlatformAccess(deviceLocked = locked),
    )

    private fun notification(text: String) =
        HubNotification(
            "p2-fixture",
            "p2-conversation",
            "Fixture sender",
            "p2.fixture",
            "Fixture app",
            System.currentTimeMillis(),
            false,
            listOf(
                ConversationMessage(
                    text,
                    "p2-conversation",
                    "p2-fixture",
                    "p2.fixture",
                    "Fixture app",
                    text,
                    System.currentTimeMillis(),
                    "Fixture sender",
                    false,
                ),
            ),
            null,
            null,
        )

    @Test fun hubAndPeekReactToPermissionRevocationAndNotificationReplacement() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = StartStateRepository(context)
        val originalText = store.loadHubPeekText()
        val originalNotifications = ConversationHub.notifications()
        var access by mutableStateOf(capabilities())
        var rendered: HubConversationsUi? = null
        store.saveHubPeekText(true)
        ConversationHub.replace(listOf(notification("first fixture text")))
        try {
            compose.setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalPlatformCapabilities provides access) {
                        val current = rememberHubConversationsUi(LocalContext.current, 0, {}, {})
                        SideEffect { rendered = current }
                        HubPeekHost(true, false, {}, {}) {}
                    }
                }
            }
            compose.onNodeWithText("first fixture text", substring = true).assertExists()
            compose.runOnIdle { ConversationHub.replace(listOf(notification("replacement fixture text"))) }
            compose.waitUntil {
                rendered
                    ?.conversations
                    ?.singleOrNull()
                    ?.messages
                    ?.singleOrNull()
                    ?.text == "replacement fixture text"
            }
            compose.onNodeWithText("replacement fixture text", substring = true).assertExists()
            compose.onNodeWithText("first fixture text", substring = true).assertDoesNotExist()
            compose.runOnIdle { access = capabilities(allowed = false) }
            compose.onNodeWithText("replacement fixture text", substring = true).assertDoesNotExist()
            compose.runOnIdle {
                assertFalse(rendered!!.accessGranted)
                assertTrue(rendered!!.conversations.isEmpty())
            }
            compose.runOnIdle { access = capabilities(locked = true) }
            compose.onNodeWithText("replacement fixture text", substring = true).assertDoesNotExist()
            compose.runOnIdle { assertTrue(rendered!!.deviceLocked) }
        } finally {
            store.saveHubPeekText(originalText)
            ConversationHub.replace(originalNotifications)
        }
    }

    @Test fun extractedAppsScreenDoesNotExposeLockedPrivateProfileRows() {
        val personal = AppEntry("Personal fixture", ComponentName("personal", "Main"), Process.myUserHandle())
        val privateApp = personal.copy(label = "Hidden private fixture", profileLabel = AppProfiles.PRIVATE)
        compose.setContent {
            MaterialTheme {
                AppsScreen(listOf(personal, privateApp), remember { SableFocusMemory() }, {}, {}, {}, {}, {}, {}, true, {})
            }
        }
        compose.onNodeWithText("Personal fixture").assertExists()
        compose.onNodeWithText("Hidden private fixture").assertDoesNotExist()
    }

    @Test fun realHomeRecreationReloadsTheSelectedStyleAndKeepsBb7FramesHidden() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val state = StartStateRepository(context)
        val style = state.loadHomeStyle()
        state.saveHomeStyle(HomeStyle.Bb7)
        state.saveOnboardingDone()
        try {
            ActivityScenario.launch(SableStartActivity::class.java).use { scenario ->
                scenario.recreate()
                compose.onNodeWithTag("active-frames-grid").assertDoesNotExist()
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                compose.onNodeWithTag("active-frames-grid").assertDoesNotExist()
            }
        } finally {
            state.saveHomeStyle(style)
        }
    }
}
