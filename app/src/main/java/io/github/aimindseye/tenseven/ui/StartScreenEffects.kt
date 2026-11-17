package io.github.aimindseye.tenseven.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import io.github.aimindseye.tenseven.model.HubFilter
import io.github.aimindseye.tenseven.model.StartPages

@Composable
internal fun rememberStartSpeedDialLetters(
    inputs: StartScreenInputs,
    launches: StartLaunchState,
): Set<Char> {
    val letters by
        produceState(emptySet<Char>(), inputs.refreshGeneration, inputs.speedDial, launches.shortcutVersion) {
            value = inputs.speedDial?.assignedLetters() ?: emptySet()
        }
    return letters
}

@Composable
internal fun StartRequestEffects(context: StartScreenContext) {
    val inputs = context.inputs
    val state = context.state.navigation
    val pager = context.pager
    LaunchedEffect(inputs.homeGeneration) {
        if (inputs.homeGeneration > 0) {
            state.destination = SableStartDestination.Start
            state.returnDestination = SableStartDestination.Start
            state.selectedApp = null
            pager.state.animateScrollToPage(StartPages.HOME + pager.lead)
        }
    }
    LaunchedEffect(inputs.hubGeneration) {
        if (inputs.hubGeneration > 0) {
            state.destination = SableStartDestination.Start
            state.selectedApp = null
            state.hubFilter = HubFilter.fromName(inputs.hubRequestFilter) ?: HubFilter.All
            pager.state.animateScrollToPage(StartPages.HUB + pager.lead)
        }
    }
    LaunchedEffect(inputs.widgetsGeneration) {
        if (inputs.widgetsGeneration > 0 && pager.lead > 0) {
            state.destination = SableStartDestination.Start
            state.selectedApp = null
            pager.state.animateScrollToPage(StartPages.WIDGETS + pager.lead)
        }
    }
}

@Composable
internal fun StartBackEffects(context: StartScreenContext) {
    val inputs = context.inputs
    val state = context.state.navigation
    val pager = context.pager
    // Like the original local vals, these are the composition's page snapshot, not a later callback read.
    val currentPage = pager.currentPage
    val settledPage = pager.settledPage
    LaunchedEffect(inputs.backGeneration) {
        if (inputs.backGeneration > 0) {
            when {
                state.hubPeek -> {
                    state.hubPeek = false
                }

                state.destination == SableStartDestination.EditHome -> {
                    state.editBackGeneration += 1
                }

                state.destination != SableStartDestination.Start -> {
                    context.navigation.navigateBack()
                }

                currentPage != StartPages.HOME -> {
                    inputs.onBackToHomePage()
                    pager.state.animateScrollToPage(StartPages.HOME + pager.lead)
                }
            }
        }
    }
    LaunchedEffect(state.destination, currentPage, state.hubPeek) {
        inputs.onCanNavigateBackChanged(
            state.destination != SableStartDestination.Start || currentPage != StartPages.HOME || state.hubPeek,
        )
    }
    LaunchedEffect(settledPage) {
        if (settledPage == StartPages.HUB) inputs.onRefreshLive()
    }
}
