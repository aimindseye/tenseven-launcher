package io.github.aimindseye.tenseven.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aimindseye.tenseven.design.SableAppearance
import io.github.aimindseye.tenseven.design.SableTheme
import io.github.aimindseye.tenseven.model.RingerProfile
import io.github.aimindseye.tenseven.platform.LiveSurfaceRepository
import io.github.aimindseye.tenseven.platform.SoundProfileControl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class HomeSoundRecoveryUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var inputMode: InputModeManager
    private val audio get() = context.getSystemService(AudioManager::class.java)
    private var originalMode = AudioManager.RINGER_MODE_NORMAL

    @Before
    fun preserveEmulatorAudio() {
        check(context.packageName == "io.github.aimindseye.tenseven.debug.l0")
        originalMode = audio.ringerMode
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
    }

    @After
    fun restoreEmulatorAudio() {
        audio.ringerMode = originalMode
    }

    @Test
    fun bb10ShowsWorkingNativeSoundControlWithoutACompanion() {
        assertFalse(SoundProfileControl.profilesEnabled(context))
        showHome(bb7 = false)
        compose.onNodeWithContentDescription("Sound Profiles: Sound").assertIsDisplayed().performClick()
        compose.waitUntil { compose.onNodeWithContentDescription("Sound Profiles: Vibrate").isDisplayed() }
        compose.onNodeWithContentDescription("Sound Profiles: Vibrate").assertIsDisplayed()
        compose.runOnIdle { assertEquals(AudioManager.RINGER_MODE_VIBRATE, audio.ringerMode) }
    }

    @Test
    fun bb7ShowsWorkingNativeSoundControlWithoutACompanion() {
        assertFalse(SoundProfileControl.profilesEnabled(context))
        showHome(bb7 = true)
        compose.onNodeWithContentDescription("Sound Profiles: Sound").assertIsDisplayed().performClick()
        compose.waitUntil { compose.onNodeWithContentDescription("Sound Profiles: Vibrate").isDisplayed() }
        compose.onNodeWithContentDescription("Sound Profiles: Vibrate").assertIsDisplayed()
        compose.runOnIdle { assertEquals(AudioManager.RINGER_MODE_VIBRATE, audio.ringerMode) }
    }

    @Test
    fun volumeModeBroadcastRefreshesTheVisibleIcon() {
        showHome(bb7 = false)
        compose.runOnIdle { audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE }
        compose.waitUntil { compose.onNodeWithContentDescription("Sound Profiles: Vibrate").isDisplayed() }
        compose.onNodeWithContentDescription("Sound Profiles: Vibrate").assertIsDisplayed()
    }

    @Test
    fun focusedSoundControlSupportsKeyboardAndDoesNotAcquireDndAccess() {
        assertFalse(SoundProfileControl.canSilence(context))
        showHome(bb7 = true)
        val sound = compose.onNodeWithContentDescription("Sound Profiles: Sound")
        compose.runOnIdle { check(inputMode.requestInputMode(InputMode.Keyboard)) }
        sound.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        sound.assertIsFocused()
        sound.performKeyInput { pressKey(Key.Enter) }
        compose.waitUntil { compose.onNodeWithContentDescription("Sound Profiles: Vibrate").isDisplayed() }
        compose.onNodeWithContentDescription("Sound Profiles: Vibrate").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(RingerProfile.Sound, SoundProfileControl.current(context))
            assertFalse(SoundProfileControl.canSilence(context))
        }
    }

    @Test
    fun normalAudioPermissionIsPresentWithoutRestrictedAccess() {
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS))
        assertFalse(SoundProfileControl.canSilence(context))
    }

    private fun showHome(bb7: Boolean) {
        compose.setContent {
            inputMode = LocalInputModeManager.current
            SableTheme {
                if (bb7) {
                    Bb7HomeScreen(
                        apps = emptyList(),
                        favoriteKeys = emptyList(),
                        homeRows = 1,
                        liveSurfaceRepository = LiveSurfaceRepository(context),
                        appearance = SableAppearance(),
                        refreshGeneration = 0,
                        homeGeneration = 0,
                        onLaunchApp = { true },
                        onOpenAppInfo = {},
                        onOpenHub = {},
                        onFavoritesChanged = {},
                        onHomeRowsChanged = {},
                        onOpenMenu = {},
                    )
                } else {
                    SableStartScreen(
                        apps = emptyList(),
                        liveSurfaceRepository = LiveSurfaceRepository(context),
                        onLaunchApp = { true },
                        onOpenSystemSettings = {},
                        onOpenAppInfo = {},
                        onRequestLivePermissions = {},
                        onRefreshLive = {},
                    )
                }
            }
        }
    }
}
