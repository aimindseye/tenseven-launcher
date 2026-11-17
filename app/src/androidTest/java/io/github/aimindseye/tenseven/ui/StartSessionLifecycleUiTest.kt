package io.github.aimindseye.tenseven.ui

import android.content.ComponentName
import android.content.Context
import android.os.Process
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.model.HubFilter
import io.github.aimindseye.tenseven.model.QuickBar
import io.github.aimindseye.tenseven.model.StartPages
import io.github.aimindseye.tenseven.platform.LiveSurfaceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class StartSessionLifecycleUiTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var inputs: StartScreenInputs? by mutableStateOf(null)
    private var attached by mutableStateOf(true)
    private lateinit var current: StartScreenContext
    private val owner = SessionLifecycleOwner()
    private val backStates = mutableListOf<Boolean>()
    private val homeCallbackPages = mutableListOf<Int>()
    private var refreshCalls = 0

    @Before
    fun initializeHost() {
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        inputs =
            StartScreenInputs(
                apps = emptyList(),
                liveSurfaceRepository = LiveSurfaceRepository(context),
                onLaunchApp = { true },
                onOpenSystemSettings = {},
                onOpenAppInfo = {},
                onRequestLivePermissions = {},
                onRefreshLive = { refreshCalls++ },
                onCanNavigateBackChanged = { backStates += it },
                onBackToHomePage = { homeCallbackPages += current.pager.currentPage },
            )
    }

    @Test
    fun keyedInputsResetOnlyTheirOwnStateAndKeepFocusAndSessionHistory() {
        show()
        val focus = current.state.focusMemory
        val pager = current.pager.state
        val app = fixtureApp()
        compose.runOnIdle {
            current.state.navigation.destination = SableStartDestination.Appearance
            current.state.navigation.returnDestination = SableStartDestination.Search
            current.state.navigation.selectedApp = app
            current.state.navigation.hubPeek = true
            current.state.home.savedGridOrder = listOf("local")
            current.state.home.quickBarTokens = listOf(QuickBar.COMMAND)
            current.state.launches.recentKeys = listOf("session")
        }
        update {
            it.copy(
                initialGridOrder = listOf("host"),
                initialQuickBar = QuickBar.DEFAULT,
                initialGlanceWidgets = emptyList(),
                initialHiddenHomeSections = emptySet(),
                initialScreen = "apps",
            )
        }
        compose.runOnIdle {
            assertEquals(listOf("host"), current.state.home.savedGridOrder)
            assertEquals(QuickBar.DEFAULT, current.state.home.quickBarTokens)
            assertTrue(
                current.state.home.glanceWidgets
                    .isEmpty(),
            )
            assertTrue(
                current.state.home.hiddenSections
                    .isEmpty(),
            )
            assertEquals(SableStartDestination.Apps, current.state.navigation.destination)
            assertEquals(SableStartDestination.Search, current.state.navigation.returnDestination)
            assertSame(app, current.state.navigation.selectedApp)
            assertTrue(current.state.navigation.hubPeek)
            assertEquals(listOf("session"), current.state.launches.recentKeys)
            assertSame(focus, current.state.focusMemory)
            assertSame(pager, current.pager.state)
        }
    }

    @Test
    fun pagerRestoresWhileRememberOnlySessionStateStartsFresh() {
        val restorer = StateRestorationTester(compose)
        show(restorer = restorer)
        val focus = current.state.focusMemory
        val target = current.pager.pageCount - 1
        compose.runOnIdle {
            current.state.navigation.destination = SableStartDestination.Search
            current.state.launches.recentKeys = listOf("ephemeral")
            current.pager.scope.launch { current.pager.state.scrollToPage(target) }
        }
        compose.waitUntil { current.pager.state.currentPage == target }
        restorer.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(target, current.pager.state.currentPage)
            assertEquals(SableStartDestination.Start, current.state.navigation.destination)
            assertTrue(
                current.state.launches.recentKeys
                    .isEmpty(),
            )
            assertNotSame(focus, current.state.focusMemory)
        }
    }

    @Test
    fun refreshedHostCallbacksDoNotRestartNavigationEffectsOrGestureReceivers() {
        show()
        val navigation = current.navigation
        val command = navigation::openCommand
        val pager = current.pager
        val laterBackStates = mutableListOf<Boolean>()
        val launches = mutableListOf<String>()
        compose.runOnIdle {
            current.state.navigation.destination = SableStartDestination.Apps
            current.state.navigation.hubPeek = true
        }
        compose.waitForIdle()
        val count = backStates.size
        update {
            it.copy(
                refreshGeneration = 1,
                onLaunchApp = {
                    launches += "latest"
                    true
                },
                onCanNavigateBackChanged = { value -> laterBackStates += value },
            )
        }
        compose.runOnIdle {
            assertSame(navigation, current.navigation)
            assertSame(pager, current.pager)
            assertEquals(command, current.navigation::openCommand)
            assertEquals(count, backStates.size)
            assertTrue(laterBackStates.isEmpty())
            assertEquals(SableStartDestination.Apps, current.state.navigation.destination)
            current.applications.launchApp(fixtureApp())
            assertEquals(listOf("latest"), launches)
            current.state.navigation.destination = SableStartDestination.Start
            current.state.navigation.hubPeek = false
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(false), laterBackStates) }
    }

    @Test
    fun backPrioritizesPeekThenEditorAndCallsHomeBeforeThePageAnimation() {
        show()
        compose.runOnIdle {
            current.state.navigation.destination = SableStartDestination.EditHome
            current.state.navigation.hubPeek = true
        }
        update { it.copy(backGeneration = 1) }
        compose.runOnIdle {
            assertFalse(current.state.navigation.hubPeek)
            assertEquals(SableStartDestination.EditHome, current.state.navigation.destination)
            assertEquals(0, current.state.navigation.editBackGeneration)
        }
        update { it.copy(backGeneration = 2) }
        compose.runOnIdle {
            assertEquals(1, current.state.navigation.editBackGeneration)
            current.state.navigation.destination = SableStartDestination.Search
            current.state.navigation.returnDestination = SableStartDestination.Start
        }
        update { it.copy(backGeneration = 3) }
        compose.runOnIdle {
            assertEquals(SableStartDestination.Start, current.state.navigation.destination)
            current.pager.scope.launch { current.pager.state.scrollToPage(StartPages.FIRST_GRID) }
        }
        compose.waitUntil { current.pager.currentPage == StartPages.FIRST_GRID }
        update { it.copy(backGeneration = 4) }
        compose.waitUntil { current.pager.currentPage == StartPages.HOME && !current.pager.state.isScrollInProgress }
        compose.runOnIdle { assertEquals(listOf(StartPages.FIRST_GRID), homeCallbackPages) }
    }

    @Test
    fun generationRequestsRetainWidgetLeadFilterAndResetBoundaries() {
        inputs = checkNotNull(inputs).copy(widgetsPage = true)
        show()
        compose.runOnIdle {
            current.state.navigation.destination = SableStartDestination.Search
            current.state.navigation.returnDestination = SableStartDestination.Apps
            current.state.navigation.selectedApp = fixtureApp()
            current.state.navigation.hubPeek = true
        }
        update { it.copy(homeGeneration = 1) }
        compose.waitUntil { current.pager.currentPage == StartPages.HOME && !current.pager.state.isScrollInProgress }
        compose.runOnIdle {
            assertEquals(SableStartDestination.Start, current.state.navigation.returnDestination)
            assertNull(current.state.navigation.selectedApp)
            assertTrue(current.state.navigation.hubPeek)
        }
        update { it.copy(hubGeneration = 1, hubRequestFilter = HubFilter.Contacts.name) }
        compose.waitUntil { current.pager.currentPage == StartPages.HUB && !current.pager.state.isScrollInProgress }
        compose.runOnIdle {
            assertEquals(HubFilter.Contacts, current.state.navigation.hubFilter)
            assertEquals(StartPages.HUB + 1, current.pager.state.currentPage)
        }
        update { it.copy(widgetsGeneration = 1) }
        compose.waitUntil { current.pager.currentPage == StartPages.WIDGETS && !current.pager.state.isScrollInProgress }
        compose.runOnIdle {
            assertEquals(0, current.pager.state.currentPage)
            assertEquals(HubFilter.Contacts, current.state.navigation.hubFilter)
        }
    }

    @Test
    fun disabledWidgetsRequestDoesNotClearSelectionOrLeaveItsDestination() {
        show()
        val app = fixtureApp()
        compose.runOnIdle {
            current.state.navigation.destination = SableStartDestination.Apps
            current.state.navigation.selectedApp = app
        }
        update { it.copy(widgetsGeneration = 1) }
        compose.runOnIdle {
            assertEquals(SableStartDestination.Apps, current.state.navigation.destination)
            assertSame(app, current.state.navigation.selectedApp)
            assertEquals(StartPages.HOME, current.pager.currentPage)
        }
    }

    @Test
    fun compositionScopeSurvivesLifecyclePauseAndCancelsOnRemoval() {
        inputs = checkNotNull(inputs).copy(initialScreen = "hub")
        show()
        val pager = current.pager
        val job = checkNotNull(pager.scope.coroutineContext[Job])
        val focus = current.state.focusMemory
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        update { it.copy(refreshGeneration = 1) }
        compose.runOnIdle {
            assertFalse(job.isCancelled)
            assertSame(pager, current.pager)
            assertSame(focus, current.state.focusMemory)
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1, refreshCalls)
            attached = false
        }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(job.isCancelled) }
    }

    @Test
    fun fullHubFromPeekKeepsTheExistingHubFilter() {
        show(renderHome = true)
        compose.runOnIdle {
            current.state.navigation.hubFilter = HubFilter.Contacts
            current.state.navigation.hubPeek = true
        }
        compose.onNodeWithText("Open Hub").assertIsDisplayed().performClick()
        compose.waitUntil { current.pager.currentPage == StartPages.HUB && !current.pager.state.isScrollInProgress }
        compose.runOnIdle {
            assertEquals(HubFilter.Contacts, current.state.navigation.hubFilter)
            assertFalse(current.state.navigation.hubPeek)
            assertEquals(SableStartDestination.Start, current.state.navigation.destination)
        }
    }

    private fun show(
        restorer: StateRestorationTester? = null,
        renderHome: Boolean = false,
    ) {
        val content: @Composable () -> Unit = {
            if (attached) {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    val value = rememberStartScreenContext(checkNotNull(inputs))
                    StartRequestEffects(value)
                    StartBackEffects(value)
                    SideEffect { current = value }
                    if (renderHome) {
                        StartHomeDestination(value)
                    } else {
                        HorizontalPager(state = value.pager.state, modifier = Modifier.fillMaxSize()) {
                            Text("Page " + it)
                        }
                    }
                }
            }
        }
        if (restorer == null) compose.setContent(content) else restorer.setContent(content)
        compose.waitForIdle()
    }

    private fun update(transform: (StartScreenInputs) -> StartScreenInputs) {
        compose.runOnIdle { inputs = transform(checkNotNull(inputs)) }
        compose.waitForIdle()
    }

    private fun fixtureApp(): AppEntry = AppEntry("Fixture", ComponentName("fixture.app", "fixture.app.Main"), Process.myUserHandle())
}

private class SessionLifecycleOwner : LifecycleOwner {
    val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
}
