package io.github.aimindseye.tenseven.platform

import android.content.Context
import androidx.core.content.edit
import io.github.aimindseye.tenseven.model.ExtraApp
import io.github.aimindseye.tenseven.model.ExtraApps

/** L0 optional-app choices are informational; installed apps remain independently discoverable. */
object ExtraAppsControl {
    private const val STORE = "l0_optional_app_choices"
    private const val IDS = "selected"

    fun isEnabled(
        context: Context,
        app: ExtraApp,
    ): Boolean = app.id in enabledIds(context)

    fun enabledIds(context: Context): Set<String> =
        ExtraApps.normalize(context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getStringSet(IDS, emptySet()))

    fun setEnabled(
        context: Context,
        app: ExtraApp,
        enabled: Boolean,
    ) {
        val old = enabledIds(context)
        apply(context, if (enabled) old + app.id else old - app.id)
    }

    fun apply(
        context: Context,
        ids: Set<String>,
    ) {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit {
            putStringSet(IDS, ExtraApps.normalize(ids))
        }
    }
}
