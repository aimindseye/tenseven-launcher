package io.github.aimindseye.tenseven.ui

import android.content.Intent
import androidx.compose.runtime.Composable
import io.github.aimindseye.tenseven.live.LiveSurfaceSnapshot
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.platform.WidgetHostController

internal data class StartWidgetContent(
    val widgets: List<String>,
    val snapshot: LiveSurfaceSnapshot,
    val apps: List<AppEntry>,
    val refreshGeneration: Int,
)

internal data class StartWidgetActions(
    val onLaunchApp: (AppEntry) -> Unit,
    val onStartIntent: (Intent) -> Unit,
    val onRequestPermissions: () -> Unit,
    val onShowHub: () -> Unit,
)

/** Stateless widgets-page rendering; state and effects stay in SableStartScreen. */
@Composable
internal fun StartWidgetsPage(
    controller: WidgetHostController?,
    ids: List<Int>,
    content: StartWidgetContent,
    actions: StartWidgetActions,
) {
    WidgetsPage(
        controller = controller,
        ids = ids,
        builtIn =
            if (content.widgets.isEmpty()) {
                null
            } else {
                {
                    BuiltInWidgets(
                        widgets = content.widgets,
                        snapshot = content.snapshot,
                        apps = content.apps,
                        refreshGeneration = content.refreshGeneration,
                        onLaunchApp = actions.onLaunchApp,
                        onStartIntent = actions.onStartIntent,
                        onRequestPermissions = actions.onRequestPermissions,
                        onShowHub = actions.onShowHub,
                    )
                }
            },
    )
}
