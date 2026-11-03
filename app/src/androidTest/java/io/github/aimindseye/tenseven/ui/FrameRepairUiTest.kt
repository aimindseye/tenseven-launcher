package io.github.aimindseye.tenseven.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.graphics.toPixelMap
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import io.github.aimindseye.tenseven.FrameIntegrationsActivity
import io.github.aimindseye.tenseven.platform.StartStateRepository
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FrameRepairUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun integrationActivityUsesLauncherDarkSurfaceInsteadOfWindowWhite() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val state = StartStateRepository(context)
        val mode = state.loadAppearanceMode()
        val accent = state.loadAppearanceAccent()
        val custom = state.loadCustomThemes().firstOrNull { it.id == state.loadActiveCustomThemeId() }
        state.saveAppearance("dark", "blue")
        try {
            ActivityScenario.launch(FrameIntegrationsActivity::class.java).use {
                val image = compose.onNodeWithTag("frame-integrations-surface").captureToImage().toPixelMap()
                val background = image[0, 0]
                assertTrue("Integration background must match dark launcher theme", background.red + background.green + background.blue < 1f)
            }
        } finally {
            state.saveAppearance(mode, accent)
            if (custom != null) state.saveActiveCustomTheme(custom)
        }
    }
}
