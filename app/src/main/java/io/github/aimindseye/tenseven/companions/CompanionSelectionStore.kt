package io.github.aimindseye.tenseven.companions

import android.content.Context
import androidx.core.content.edit

/** Stores requested companion ids separately from the existing bundled Extras settings. */
object CompanionSelectionStore {
    private const val STORE = "tenseven_companion_wizard_v1"
    private const val SELECTED_IDS = "requested_companion_ids"

    fun load(context: Context): Set<String> =
        CompanionSetupPlan.decode(
            context
                .getSharedPreferences(STORE, Context.MODE_PRIVATE)
                .getString(SELECTED_IDS, ""),
        )

    fun save(
        context: Context,
        selected: Collection<String>,
    ) {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit {
            putString(SELECTED_IDS, CompanionSetupPlan.encode(selected))
        }
    }
}
