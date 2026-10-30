package io.github.aimindseye.tenseven.platform

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import io.github.aimindseye.tenseven.model.ExtraApp
import io.github.aimindseye.tenseven.model.ExtraApps

/**
 * Turns the bundled extra apps on and off. Each one is a launcher activity that ships switched off; enabling it
 * gives it a launcher entry (so it appears in All Apps and the grid), disabling it takes the entry away. Nothing is
 * installed or downloaded, and the state lives in Android's own component settings.
 */
object ExtraAppsControl {
    fun isEnabled(
        context: Context,
        app: ExtraApp,
    ): Boolean =
        runCatching {
            context.packageManager.getComponentEnabledSetting(component(context, app)) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }.getOrDefault(false)

    fun enabledIds(context: Context): Set<String> = ExtraApps.ALL.filter { isEnabled(context, it) }.map { it.id }.toSet()

    fun setEnabled(
        context: Context,
        app: ExtraApp,
        enabled: Boolean,
    ) {
        runCatching {
            context.packageManager.setComponentEnabledSetting(
                component(context, app),
                if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                },
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    /** Makes exactly [ids] enabled. */
    fun apply(
        context: Context,
        ids: Set<String>,
    ) {
        val (on, off) = ExtraApps.changes(enabledIds(context), ids)
        ExtraApps.ALL.forEach { app ->
            when (app.id) {
                in on -> setEnabled(context, app, true)
                in off -> setEnabled(context, app, false)
            }
        }
    }

    private fun component(
        context: Context,
        app: ExtraApp,
    ) = ComponentName(context.packageName, app.className)
}
