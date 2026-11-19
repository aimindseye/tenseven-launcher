package io.github.aimindseye.tenseven.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.aimindseye.tenseven.design.SableFocusMemory
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.model.GlanceWidgets
import io.github.aimindseye.tenseven.model.HomeLayout
import io.github.aimindseye.tenseven.model.HubFilter
import io.github.aimindseye.tenseven.model.QuickBar

/** Delegates keep snapshot reads/writes observable; wrappers do not own extra lifetimes. */
internal data class StartNavigationState(
    private val destinationState: MutableState<SableStartDestination>,
    private val returnDestinationState: MutableState<SableStartDestination>,
    private val selectedAppState: MutableState<AppEntry?>,
    private val selectedSlotState: MutableState<QuickSlot?>,
    private val searchSeedState: MutableState<String>,
    private val hubFilterState: MutableState<HubFilter>,
    private val hubPeekState: MutableState<Boolean>,
    private val editBackState: MutableState<Int>,
) {
    var destination by destinationState
    var returnDestination by returnDestinationState
    var selectedApp by selectedAppState
    var selectedSlot by selectedSlotState
    var searchSeed by searchSeedState
    var hubFilter by hubFilterState
    var hubPeek by hubPeekState
    var editBackGeneration by editBackState
}

internal data class StartHomeEditState(
    private val gridOrderState: MutableState<List<String>?>,
    private val quickBarState: MutableState<List<String>>,
    private val glanceState: MutableState<List<String>>,
    private val hiddenState: MutableState<Set<String>>,
) {
    var savedGridOrder by gridOrderState
    var quickBarTokens by quickBarState
    var glanceWidgets by glanceState
    var hiddenSections by hiddenState
}

internal data class StartLaunchState(
    private val versionState: MutableState<Int>,
    private val recentState: MutableState<List<String>>,
) {
    var shortcutVersion by versionState
    var recentKeys by recentState
}

internal data class StartSessionState(
    val navigation: StartNavigationState,
    val home: StartHomeEditState,
    val launches: StartLaunchState,
    val focusMemory: SableFocusMemory,
)

@Composable
internal fun rememberStartSession(inputs: StartScreenInputs): StartSessionState =
    StartSessionState(
        navigation = rememberStartNavigation(inputs.initialScreen),
        home = rememberStartHomeEdits(inputs),
        launches =
            StartLaunchState(
                remember { mutableIntStateOf(0) },
                remember { mutableStateOf<List<String>>(emptyList()) },
            ),
        focusMemory = remember { SableFocusMemory() },
    )

@Composable
private fun rememberStartNavigation(initialScreen: String): StartNavigationState =
    StartNavigationState(
        remember(initialScreen) { mutableStateOf(destinationFor(initialScreen)) },
        remember { mutableStateOf(SableStartDestination.Start) },
        remember { mutableStateOf<AppEntry?>(null) },
        remember { mutableStateOf<QuickSlot?>(null) },
        remember { mutableStateOf("") },
        remember { mutableStateOf(HubFilter.All) },
        remember { mutableStateOf(false) },
        remember { mutableIntStateOf(0) },
    )

@Composable
private fun rememberStartHomeEdits(inputs: StartScreenInputs): StartHomeEditState =
    StartHomeEditState(
        remember(inputs.initialGridOrder) { mutableStateOf(inputs.initialGridOrder) },
        remember(inputs.initialQuickBar) { mutableStateOf(QuickBar.normalize(inputs.initialQuickBar)) },
        remember(inputs.initialGlanceWidgets) { mutableStateOf(GlanceWidgets.normalize(inputs.initialGlanceWidgets)) },
        remember(inputs.initialHiddenHomeSections) {
            mutableStateOf(HomeLayout.normalizeHidden(inputs.initialHiddenHomeSections))
        },
    )
