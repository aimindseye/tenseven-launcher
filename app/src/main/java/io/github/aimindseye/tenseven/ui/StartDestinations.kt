package io.github.aimindseye.tenseven.ui

import androidx.compose.runtime.Composable
import io.github.aimindseye.tenseven.model.StartPages
import io.github.aimindseye.tenseven.model.stableKey
import io.github.aimindseye.tenseven.platform.CapabilityStatus

@Composable
internal fun StartDestinations(context: StartScreenContext) {
    when (context.state.navigation.destination) {
        SableStartDestination.Start -> StartHomeDestination(context)
        SableStartDestination.SlotOptions -> StartSlotOptions(context)
        SableStartDestination.SlotPicker -> StartSlotPicker(context)
        SableStartDestination.EditHome -> StartEditHome(context)
        SableStartDestination.Apps -> StartAllApps(context)
        SableStartDestination.Search -> StartSearch(context)
        SableStartDestination.Appearance -> StartAppearance(context)
        SableStartDestination.Peek -> StartAppPeek(context)
    }
}

@Composable
private fun StartSlotOptions(context: StartScreenContext) {
    val state = context.state.navigation
    val slot = state.selectedSlot
    if (slot == null) {
        state.destination = SableStartDestination.Start
    } else {
        SlotOptionsScreen(
            slot = slot,
            apps = context.inputs.apps,
            onBack = context.navigation::navigateBack,
            onOpen = {
                state.destination = SableStartDestination.Start
                context.home.activateSlot(slot)
            },
            onHubFilter = context.navigation::showHub,
            onOpenHubSettings = {
                state.destination = SableStartDestination.Start
                context.navigation.openHubSettings()
            },
            onStartIntent = { intent ->
                state.destination = SableStartDestination.Start
                context.inputs.onStartIntent(intent)
            },
            onLaunchApp = { app ->
                state.destination = SableStartDestination.Start
                context.applications.launchApp(app)
            },
            onAppInfo = { app -> context.inputs.onOpenAppInfo(app) },
            onEditHome = { state.destination = SableStartDestination.EditHome },
        )
    }
}

@Composable
private fun StartSlotPicker(context: StartScreenContext) {
    val state = context.state.navigation
    val app = state.selectedApp
    if (app == null) {
        state.destination = SableStartDestination.Start
    } else {
        SlotPickerScreen(
            app = app,
            tokens = context.state.home.quickBarTokens,
            apps = context.inputs.apps,
            appByKey = context.grid.appByKey,
            onBack = { state.destination = SableStartDestination.Peek },
            onReplace = { index -> context.home.replaceSlot(app, index) },
        )
    }
}

@Composable
private fun StartEditHome(context: StartScreenContext) {
    val inputs = context.inputs
    val state = context.state.navigation
    EditHomeScreen(
        showFrameOptions =
            !inputs.blankHomePage &&
                rememberPlatformCapabilities(inputs.refreshGeneration).hubNotifications == CapabilityStatus.Ready,
        apps = inputs.apps,
        initial =
            HomeEdit(
                gridOrder = context.grid.keys,
                quickBar = context.state.home.quickBarTokens,
                glanceWidgets = context.state.home.glanceWidgets,
                hiddenSections = context.state.home.hiddenSections,
            ),
        defaults = startHomeEditDefaults(context.grid.defaultHomeKeys, context.grid.candidates.map { it.stableKey() }),
        backGeneration = state.editBackGeneration,
        onCancel = { state.destination = SableStartDestination.Start },
        onDone = context.home::saveEdit,
        onAppearance = context.navigation::openAppearance,
    )
}

@Composable
private fun StartAllApps(context: StartScreenContext) {
    val inputs = context.inputs
    AppsScreen(
        apps = inputs.apps,
        focusMemory = context.state.focusMemory,
        onBack = context.navigation::navigateBack,
        onSearch = { context.navigation.openSearch(SableStartDestination.Apps) },
        onLaunchApp = context.applications::launchApp,
        onLaunchSecond = context.applications::launchSecond,
        onOpenPeek = { app -> context.navigation.openPeek(app, SableStartDestination.Apps) },
        onOpenAppInfo = inputs.onOpenAppInfo,
        privateSpaceLocked = inputs.privateSpaceLocked,
        onTogglePrivateSpace = inputs.onTogglePrivateSpace,
    )
}

@Composable
private fun StartSearch(context: StartScreenContext) {
    val inputs = context.inputs
    val state = context.state.navigation
    SearchScreen(
        apps = inputs.apps,
        recentKeys = context.state.launches.recentKeys,
        letterShortcuts = inputs.letterShortcuts,
        initialQuery = state.searchSeed,
        privateSpaceLocked = inputs.privateSpaceLocked,
        onTogglePrivateSpace = inputs.onTogglePrivateSpace,
        workProfilePaused = inputs.workProfilePaused,
        onToggleWorkProfile = inputs.onToggleWorkProfile,
        onBack = context.navigation::navigateBack,
        onLaunchApp = context.applications::launchApp,
        secondInstanceOf = context.applications::secondInstance,
        onOpenAppInfo = inputs.onOpenAppInfo,
        onOpenPeek = { app -> context.navigation.openPeek(app, SableStartDestination.Search) },
        onAllApps = { state.destination = SableStartDestination.Apps },
        onAppearance = context.navigation::openAppearance,
        onFrames = if (inputs.blankHomePage) null else ({ context.navigation.showPage(StartPages.HOME) }),
        onGrid = { context.navigation.showPage(StartPages.FIRST_GRID) },
        onHub = context.navigation::showHub,
        onEditHome = { state.destination = SableStartDestination.EditHome },
        onOpenSettings = inputs.onOpenSystemSettings,
        onStartIntent = inputs.onStartIntent,
    )
}

@Composable
private fun StartAppearance(context: StartScreenContext) {
    AppearanceScreen(
        appearance = context.inputs.appearance,
        onBack = context.navigation::navigateBack,
        onAppearanceChanged = context.inputs.onAppearanceChanged,
        onReset = context.inputs.onResetAppearance,
    )
}
