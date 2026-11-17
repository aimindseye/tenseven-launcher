package io.github.aimindseye.tenseven.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.model.QuickBar
import io.github.aimindseye.tenseven.model.inLauncherUser
import io.github.aimindseye.tenseven.model.stableKey

@Composable
internal fun StartAppPeek(context: StartScreenContext) {
    val state = context.state.navigation
    val app = state.selectedApp
    if (app == null) {
        state.destination = state.returnDestination
    } else {
        val key = app.stableKey()
        val barIndex = context.grid.quickSlots.indexOfFirst { it.app?.stableKey() == key }
        SablePeekScreen(
            model = rememberStartPeekApp(context, app, barIndex),
            navigation =
                PeekNavigation(
                    onClose = context.navigation::navigateBack,
                    onOpen = { context.applications.launchApp(app) },
                    onOpenSecond = { context.applications.launchSecond(app) },
                    onOpenAppInfo = { context.inputs.onOpenAppInfo(app) },
                    onOpenNotificationSettings = { context.inputs.onOpenNotificationSettings(app) },
                ),
            edits =
                PeekEdits(
                    onToggleBaseBar = { context.home.toggleBaseBar(app, barIndex) },
                    onUninstall = { context.inputs.onUninstallApp(app) },
                    onSetLetterShortcut = { letter -> context.home.setLetterShortcut(app, letter) },
                ),
        )
    }
}

@Composable
private fun rememberStartPeekApp(
    context: StartScreenContext,
    app: AppEntry,
    barIndex: Int,
): PeekAppState =
    PeekAppState(
        app = app,
        inBaseBar = QuickBar.appState(app.inLauncherUser, barIndex >= 0),
        hasSecondInstance = context.applications.secondInstance(app) != null,
        letterShortcut =
            remember(app, context.state.launches.shortcutVersion) {
                context.inputs.letterShortcuts
                    ?.load()
                    ?.letterOf(app.component.flattenToString())
            },
        shortcutsAvailable = context.inputs.letterShortcuts != null && app.inLauncherUser,
    )
