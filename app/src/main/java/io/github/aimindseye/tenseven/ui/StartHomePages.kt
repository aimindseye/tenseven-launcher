package io.github.aimindseye.tenseven.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.aimindseye.tenseven.design.SableRefreshableSurface
import io.github.aimindseye.tenseven.model.AppGrid
import io.github.aimindseye.tenseven.model.HubFilter
import io.github.aimindseye.tenseven.model.StartPages

@Composable
internal fun StartHomeDestination(context: StartScreenContext) {
    val inputs = context.inputs
    val state = context.state.navigation
    val pager = context.pager
    val navigation = context.navigation
    Bb10ThemeWithPeek(
        appearance = inputs.appearance,
        peekOpen = state.hubPeek,
        edgeEnabled = pager.currentPage == StartPages.HOME,
        onPeekChange = { state.hubPeek = it },
        // Opening full Hub from Peek preserves its current filter.
        onOpenHub = { pager.animateLogical(StartPages.HUB) },
    ) {
        StartStrip(
            state = pager.state,
            pageCount = pager.pageCount,
            pageLead = pager.lead,
            quickSlots = context.grid.quickSlots,
            onMovePage = pager::movePage,
            onShowPage = pager::animateAbsolute,
            onQuickSlot = context.home::activateSlot,
            onSlotOptions = { slot ->
                state.selectedSlot = slot
                state.destination = SableStartDestination.SlotOptions
            },
            onHubPeek = { state.hubPeek = true },
            onDial = { char -> navigation.dial(char.toString()) },
            onTypeToSearch = { seed ->
                if (inputs.typeToSearch) navigation.openSearch(SableStartDestination.Start, seed)
            },
            speedDialLetters = context.speedDialLetters,
            onSpeedDial = { letter -> inputs.speedDial?.call(letter) },
            onCommand = navigation::openCommand,
            onEditHome = { state.destination = SableStartDestination.EditHome },
        ) { pagerIndex -> StartPagerPage(context, pagerIndex) }
    }
}

@Composable
private fun StartPagerPage(
    context: StartScreenContext,
    pagerIndex: Int,
) {
    val page = pagerIndex - context.pager.lead
    when {
        page == StartPages.WIDGETS -> StartWidgetPage(context)
        page == StartPages.HUB -> StartHubPage(context)
        page == StartPages.HOME && context.inputs.blankHomePage -> Spacer(Modifier.fillMaxSize())
        page == StartPages.HOME -> StartFramePage(context)
        else -> StartGridPage(context, page)
    }
}

@Composable
private fun StartWidgetPage(context: StartScreenContext) {
    val inputs = context.inputs
    StartWidgetsPage(
        controller = inputs.widgetsController,
        ids = inputs.widgetIds,
        content =
            StartWidgetContent(
                context.state.home.glanceWidgets,
                context.liveSnapshot,
                inputs.apps,
                inputs.refreshGeneration,
            ),
        actions =
            StartWidgetActions(
                onLaunchApp = context.applications::launchApp,
                onStartIntent = inputs.onStartIntent,
                onRequestPermissions = inputs.onRequestLivePermissions,
                onShowHub = { context.navigation.showHub(HubFilter.All) },
            ),
    )
}

@Composable
private fun StartHubPage(context: StartScreenContext) {
    val inputs = context.inputs
    SableRefreshableSurface(isRefreshing = context.liveRefreshing, onRefresh = inputs.onRefreshLive) {
        HubPage(
            model =
                HubPageModel(
                    snapshot = context.liveSnapshot,
                    apps = inputs.apps,
                    filter = context.state.navigation.hubFilter,
                    active = context.pager.settledPage == StartPages.HUB,
                    refreshGeneration = inputs.refreshGeneration,
                ),
            events =
                HubPageEvents(
                    onFilter = { context.state.navigation.hubFilter = it },
                    onLaunchApp = context.applications::launchApp,
                    onOpenHubSettings = context.navigation::openHubSettings,
                    onStartIntent = inputs.onStartIntent,
                    onRequestContactsPermission = inputs.onRequestContactsPermission,
                ),
            onReturnHome = { context.pager.animateLogical(StartPages.HOME) },
        )
    }
}

@Composable
private fun StartFramePage(context: StartScreenContext) {
    val inputs = context.inputs
    ActiveFramesPage(
        state = inputs.framesState,
        apps = inputs.apps,
        hiddenSections = context.state.home.hiddenSections,
        onOpenSettings = context.navigation::openLauncherSettings,
        onSearch = context.navigation::openCommand,
        snapshot = context.liveSnapshot,
        refreshGeneration = inputs.refreshGeneration,
        frameSnapshot = inputs.frameSnapshot,
        active = context.pager.settledPage == StartPages.HOME,
        onResume = { inputs.onResumeFrame(it) },
        onClose = inputs.onCloseFrame,
        onShowHub = { context.navigation.showHub(HubFilter.All) },
        onOpenNotificationApp = context.applications::openNotificationApp,
        onAllowUsageAccess = inputs.onAllowUsageAccess,
    )
}

@Composable
private fun StartGridPage(
    context: StartScreenContext,
    page: Int,
) {
    val grid = context.grid
    val state = context.state.navigation
    AppGridPage(
        apps = AppGrid.page(grid.apps, page - StartPages.FIRST_GRID, grid.geometry.perPage),
        rows = grid.geometry.rows,
        columns = grid.geometry.columns,
        iconScale = grid.geometry.iconScale,
        gridLabels = grid.labels,
        active = context.pager.settledPage == page,
        onLaunchApp = context.applications::launchApp,
        onLaunchSecond = context.applications::launchSecond,
        onOpenPeek = { app -> context.navigation.openPeek(app, SableStartDestination.Start) },
        onEditHome = { state.destination = SableStartDestination.EditHome },
        showLabels = context.inputs.showIconLabels,
    )
}
