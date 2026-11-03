package io.github.aimindseye.tenseven.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.toPixelMap
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import io.github.aimindseye.tenseven.FrameIntegrationsSurface
import io.github.aimindseye.tenseven.LauncherTheme
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import io.github.aimindseye.tenseven.model.HomeStyle
import io.github.aimindseye.tenseven.platform.StartStateRepository
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FrameRepairUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun integrationActivityUsesLauncherDarkSurfaceInsteadOfWindowWhite() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val state = StartStateRepository(context)
        val mode = state.loadAppearanceMode()
        val accent = state.loadAppearanceAccent()
        val custom = state.loadCustomThemes().firstOrNull { it.id == state.loadActiveCustomThemeId() }
        state.saveAppearance("dark", "blue")
        try {
            compose.setContent { LauncherTheme { FrameIntegrationsSurface { Text("Integration theme fixture") } } }
            run {
                val image = compose.onNodeWithTag("frame-integrations-surface").captureToImage().toPixelMap()
                val background = image[0, 0]
                assertTrue("Integration background must match dark launcher theme", background.red + background.green + background.blue < 1f)
            }
        } finally {
            state.saveAppearance(mode, accent)
            if (custom != null) state.saveActiveCustomTheme(custom)
        }
    }
    @Test fun hubPeekClipsItsOuterCorners() {
        compose.setContent { LauncherTheme {
            Box(Modifier.fillMaxSize().background(Color.Red)) {
                HubPeekHost(peekOpen = true, edgeEnabled = false, onPeekChange = {}, onOpenHub = {}) {}
            }
        } }
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag("hub-peek-panel").captureToImage().toPixelMap()
        val corner = pixels[0, 0]
        val panel = pixels[pixels.width / 2, 10]
        assertTrue("Rounded outer corner must expose the scrim instead of the panel background",
            corner.alpha < panel.alpha || corner.red > panel.red + 0.1f)
    }

    @Test fun setupRequiresBb10AndGrantedNotificationAccess() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = StartStateRepository(context)
        val prior = repository.loadHomeStyle()
        var access by mutableStateOf(false)
        var version by mutableIntStateOf(0)
        repository.saveHomeStyle(HomeStyle.Bb10)
        try {
            compose.setContent { LauncherTheme { Column { key(version) { FrameSettingsSection(repository, access) } } } }
            compose.onNodeWithText("Active Frames").assertDoesNotExist()
            compose.runOnIdle { access = true }
            compose.onNodeWithText("Active Frames").assertExists()
            compose.onNodeWithText("Choose file").assertDoesNotExist()
            compose.runOnIdle { repository.saveHomeStyle(HomeStyle.Bb7); version++ }
            compose.onNodeWithText("Active Frames").assertDoesNotExist()
            compose.runOnIdle { access = false; repository.saveHomeStyle(HomeStyle.Bb10); version++ }
            compose.onNodeWithText("Active Frames").assertDoesNotExist()
        } finally { repository.saveHomeStyle(prior) }
    }

    @Test fun bb7RecentsIntentNeverDisplaysActiveFrames() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val state = StartStateRepository(context)
        val prior = state.loadHomeStyle()
        state.saveOnboardingDone()
        state.saveHomeStyle(HomeStyle.Bb7)
        try {
            val open = android.content.Intent(context, io.github.aimindseye.tenseven.SableStartActivity::class.java)
                .setAction(io.github.aimindseye.tenseven.SableStartActivity.ACTION_SHOW_ACTIVE_FRAMES)
            ActivityScenario.launch<io.github.aimindseye.tenseven.SableStartActivity>(open).use { scenario ->
                compose.onNodeWithTag("active-frames-grid").assertDoesNotExist()
                scenario.onActivity { activity -> activity.startActivity(android.content.Intent(open)) }
                compose.waitForIdle()
                compose.onNodeWithTag("active-frames-grid").assertDoesNotExist()
            }
        } finally { state.saveHomeStyle(prior) }
    }

}
