package io.github.aimindseye.tenseven.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.aimindseye.tenseven.live.LiveSurfaceSnapshot

/** Composition-scoped dependencies. It is rebuilt, never remembered with stale host callbacks. */
internal data class StartScreenContext(
    val inputs: StartScreenInputs,
    val state: StartSessionState,
    val grid: StartGridLayout,
    val pager: StartPager,
    val navigation: StartNavigation,
    val applications: StartApplicationActions,
    val home: StartHomeActions,
    val liveSnapshot: LiveSurfaceSnapshot,
    val liveRefreshing: Boolean,
    val speedDialLetters: Set<Char>,
)

@Composable
internal fun rememberStartScreenContext(inputs: StartScreenInputs): StartScreenContext {
    val state = rememberStartSession(inputs)
    val letters = rememberStartSpeedDialLetters(inputs, state.launches)
    val (snapshot, refreshing) =
        rememberLiveSurface(inputs.liveSurfaceRepository, inputs.refreshGeneration, inputs.homeGeneration)
    val grid = rememberStartGrid(inputs, state.home)
    val pager = rememberStartPager(inputs, grid.geometry.pages)
    val context = LocalContext.current
    val navigation =
        remember(state.navigation, pager, context, inputs.onStartIntent) {
            StartNavigation(state.navigation, pager::animateLogical, context, inputs.onStartIntent)
        }
    val applications =
        remember(inputs.apps, state.launches, navigation, inputs.onLaunchApp) {
            StartApplicationActions(inputs.apps, state.launches, navigation, inputs.onLaunchApp)
        }
    val bindings =
        StartHomeBindings(
            inputs.onGridOrderChanged,
            inputs.onQuickBarChanged,
            inputs.onGlanceWidgetsChanged,
            inputs.onHiddenHomeSectionsChanged,
            inputs.onStartIntent,
            inputs.letterShortcuts,
        )
    val home =
        remember(
            state.home,
            state.navigation,
            state.launches,
            grid.quickSlots,
            navigation,
            applications,
            pager,
            bindings,
        ) {
            StartHomeActions(bindings, state, grid.quickSlots, navigation, applications, pager::scrollHome)
        }
    return StartScreenContext(
        inputs,
        state,
        grid,
        pager,
        navigation,
        applications,
        home,
        snapshot,
        refreshing,
        letters,
    )
}

@Composable
internal fun StartScreenInputs.RenderStartScreen() {
    val inputs = this
    val context = rememberStartScreenContext(inputs)
    StartRequestEffects(context)
    StartBackEffects(context)
    StartContentSurface(
        style =
            StartSurfaceStyle(
                inputs.appearance,
                inputs.widgetsController,
                inputs.homeWidgetIds,
                inputs.wallpaperBehind,
            ),
        destination = context.state.navigation.destination,
        onDoubleTapEmpty = inputs.onDoubleTapEmpty,
    ) {
        StartDestinations(context)
    }
}
