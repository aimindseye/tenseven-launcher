package io.github.aimindseye.tenseven.ui

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipe
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aimindseye.tenseven.conversations.ConversationHub
import io.github.aimindseye.tenseven.conversations.ConversationMessage
import io.github.aimindseye.tenseven.conversations.HubNotification
import io.github.aimindseye.tenseven.platform.FramePlatformAccess
import io.github.aimindseye.tenseven.platform.LiveSurfaceRepository
import io.github.aimindseye.tenseven.platform.PlatformCapabilities
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalTestApi::class)
class HomePeekRecoveryUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var inputMode: InputModeManager

    @After
    fun clearSyntheticNotifications() {
        ConversationHub.replace(emptyList())
    }

    @Test
    fun homeEdgeSwipeOverlaysHomeWithoutPagingWithWidgetsEnabled() {
        showHome(widgets = true)
        val density = context.resources.displayMetrics.density
        compose.onRoot().performTouchInput {
            swipe(Offset(EDGE_START_DP * density, centerY), Offset(width * END_FRACTION, centerY))
        }
        awaitPeek()
        compose.onNodeWithTag("hub-peek-panel", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Active Frames").assertIsSelected()
    }

    @Test
    fun hubLongPressOpensPeekAndOpenHubNavigatesToFullHub() {
        showHome()
        openFromHub()
        awaitPeek()
        compose.onNodeWithTag("hub-peek-panel", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Open Hub").performClick()
        compose.onNodeWithTag("hub-peek-panel", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("home-reference-bar").assertDoesNotExist()
        compose.onNodeWithText("All").assertIsDisplayed()
    }

    @Test
    fun ordinaryHubTapStillNavigatesWithoutPeek() {
        showHome()
        compose.onNodeWithContentDescription("Hub").performClick()
        compose.onNodeWithTag("hub-peek-panel", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("home-reference-bar").assertDoesNotExist()
        compose.onNodeWithText("All").assertIsDisplayed()
    }

    @Test
    fun focusedHubSpaceOpensPeek() {
        showHome()
        compose.runOnIdle { check(inputMode.requestInputMode(InputMode.Keyboard)) }
        compose.onNodeWithContentDescription("Hub").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithContentDescription("Hub").assertIsFocused()
        compose.onNodeWithContentDescription("Hub").performKeyInput { pressKey(Key.Spacebar) }
        awaitPeek()
        compose.onNodeWithTag("hub-peek-panel", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun escapeAndOutsideTapClosePeekWithoutLeavingHome() {
        showHome()
        openFromHub()
        compose.runOnIdle { check(inputMode.requestInputMode(InputMode.Keyboard)) }
        compose.onNodeWithText("Open Hub").performKeyInput { pressKey(Key.Escape) }
        compose.onNodeWithTag("hub-peek-panel", useUnmergedTree = true).assertDoesNotExist()
        openFromHub()
        compose.onRoot().performTouchInput { click(Offset(width - 1f, centerY)) }
        compose.onNodeWithTag("hub-peek-panel", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Active Frames").assertIsSelected()
    }

    @Test
    fun androidBackDismissesOnlyPeek() {
        showHome()
        openFromHub()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("hub-peek-panel", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Active Frames").assertIsSelected()
    }

    @Test
    fun conversationTapUsesTheNotificationTargetAndDismissesPeek() {
        val opened = AtomicInteger()
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    opened.incrementAndGet()
                }
            }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(OPEN_FIXTURE), ContextCompat.RECEIVER_NOT_EXPORTED)
        val pending =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(OPEN_FIXTURE).setPackage(context.packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        try {
            ConversationHub.replace(listOf(notification(pending)))
            showHome(access = true)
            openFromHub()
            compose.onNodeWithText("Recovery conversation").assertIsDisplayed().performClick()
            compose.waitUntil { opened.get() == 1 }
            compose.onNodeWithTag("hub-peek-panel", useUnmergedTree = true).assertDoesNotExist()
            compose.runOnIdle { assertEquals(1, opened.get()) }
        } finally {
            pending.cancel()
            context.unregisterReceiver(receiver)
        }
    }

    @Test
    fun accessDenialNeverExposesCachedConversationText() {
        ConversationHub.replace(listOf(notification(null)))
        showHome()
        openFromHub()
        awaitPeek()
        compose.onNodeWithTag("hub-peek-panel", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Recovery conversation").assertDoesNotExist()
        compose.onNodeWithText("Private fixture text").assertDoesNotExist()
    }

    private fun openFromHub() {
        compose.onNodeWithContentDescription("Hub").performTouchInput { longClick() }
        awaitPeek()
    }

    private fun awaitPeek() {
        compose.waitUntil { compose.onNodeWithTag("hub-peek-panel", useUnmergedTree = true).isDisplayed() }
    }

    private fun showHome(
        widgets: Boolean = false,
        access: Boolean = false,
    ) {
        val capabilities =
            PlatformCapabilities.evaluate(
                access,
                true,
                false,
                false,
                false,
                false,
                FramePlatformAccess(deviceLocked = false),
            )
        compose.setContent {
            inputMode = LocalInputModeManager.current
            CompositionLocalProvider(LocalPlatformCapabilities provides capabilities) {
                SableStartScreen(
                    apps = emptyList(),
                    liveSurfaceRepository = LiveSurfaceRepository(context),
                    widgetsPage = widgets,
                    onLaunchApp = { true },
                    onOpenSystemSettings = {},
                    onOpenAppInfo = {},
                    onRequestLivePermissions = {},
                    onRefreshLive = {},
                )
            }
        }
    }

    private fun notification(pending: PendingIntent?): HubNotification {
        val message =
            ConversationMessage(
                "recovery",
                "recovery",
                "recovery",
                "fixture.messages",
                "Fixture",
                "Private fixture text",
                1L,
                "Sender",
                false,
            )
        return HubNotification(
            "recovery",
            "recovery",
            "Recovery conversation",
            "fixture.messages",
            "Fixture",
            1L,
            false,
            listOf(message),
            pending,
            null,
        )
    }

    private companion object {
        const val EDGE_START_DP = 24f
        const val END_FRACTION = 0.6f
        const val OPEN_FIXTURE = "io.github.aimindseye.tenseven.test.RECOVERY_OPEN"
    }
}
