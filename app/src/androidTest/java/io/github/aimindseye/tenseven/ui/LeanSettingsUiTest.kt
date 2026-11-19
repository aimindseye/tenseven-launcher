package io.github.aimindseye.tenseven.ui

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aimindseye.tenseven.LauncherSettingsActivity
import org.junit.Rule
import org.junit.Test

class LeanSettingsUiTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun obsoletePageIsRemovedAndCompanionPlanningRemainsSeparate() {
        ActivityScenario.launch(LauncherSettingsActivity::class.java).use {
            compose.onNodeWithText("6. Review & Finish").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("6. Apps & Features").assertDoesNotExist()
            compose.onNodeWithText("7. Review & Finish").assertDoesNotExist()
            compose.onNodeWithText("Optional companion apps").performScrollTo().performClick()
            compose.onNodeWithText("Open companion setup planner").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Minimum apps · Essentials").assertDoesNotExist()
            compose.onNodeWithText("Full apps").assertDoesNotExist()
            compose.onNodeWithText("Custom app selection").assertDoesNotExist()
            compose.onNodeWithText("Done").performClick()
            compose.onNodeWithText("6. Review & Finish").assertExists()
        }
    }

    @Test
    fun flowSkipsObsoleteStepAndRestoresOldCompanionDeepLink() {
        val intent =
            Intent(context, LauncherSettingsActivity::class.java)
                .putExtra("tenseven.settings.category", "frames")
        ActivityScenario.launch<LauncherSettingsActivity>(intent).use {
            compose.onNodeWithText("Next").performClick()
            compose.onNodeWithText("Review & Finish").assertIsDisplayed()
            compose.onNodeWithText("Back").performClick()
            compose.onNodeWithText("Active Frames Settings").assertExists()
        }
        val oldLink =
            Intent(context, LauncherSettingsActivity::class.java)
                .putExtra("tenseven.settings.category", "apps")
        ActivityScenario.launch<LauncherSettingsActivity>(oldLink).use { scenario ->
            compose.onNodeWithText("Optional companion apps").assertExists()
            scenario.recreate()
            compose.onNodeWithText("Optional companion apps").assertExists()
        }
    }

    @Test
    fun survivingControlsBelongToTheirNewCategories() {
        val appearance =
            Intent(context, LauncherSettingsActivity::class.java)
                .putExtra("tenseven.settings.category", "appearance_home")
        ActivityScenario.launch<LauncherSettingsActivity>(appearance).use {
            compose.onNodeWithText("Type to search").performScrollTo().assertIsDisplayed()
        }
        val permissions =
            Intent(context, LauncherSettingsActivity::class.java)
                .putExtra("tenseven.settings.category", "permissions")
        ActivityScenario.launch<LauncherSettingsActivity>(permissions).use {
            compose.onNodeWithText("Device compatibility").performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun keyboardPreferenceSurvivesItsMoveAndActivityRecreation() {
        val state =
            io.github.aimindseye.tenseven.platform
                .StartStateRepository(context)
        val previous = state.loadTypeToSearch()
        state.saveTypeToSearch(false)
        try {
            val intent =
                Intent(context, LauncherSettingsActivity::class.java)
                    .putExtra("tenseven.settings.category", "appearance_home")
            ActivityScenario.launch<LauncherSettingsActivity>(intent).use { scenario ->
                compose.onNodeWithText("Type to search").performScrollTo().performClick()
                compose.runOnIdle { org.junit.Assert.assertTrue(state.loadTypeToSearch()) }
                scenario.recreate()
                compose.runOnIdle { org.junit.Assert.assertTrue(state.loadTypeToSearch()) }
                compose.onNodeWithText("Type to search").performScrollTo().assertIsDisplayed()
            }
        } finally {
            state.saveTypeToSearch(previous)
        }
    }
}
