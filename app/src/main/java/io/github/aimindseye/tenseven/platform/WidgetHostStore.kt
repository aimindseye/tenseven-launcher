package io.github.aimindseye.tenseven.platform

import android.content.Context
import io.github.aimindseye.tenseven.model.WidgetBinding
import io.github.aimindseye.tenseven.model.WidgetSizing

/** Device-local host transactions and sizes; excluded from backup with all launcher data. */
internal class WidgetHostStore(
    context: Context,
) {
    private val preferences = context.getSharedPreferences("widget_host_state", Context.MODE_PRIVATE)

    fun pending(): WidgetBinding? =
        runCatching {
            val id = preferences.getInt("pending_id", -1)
            if (id <= 0) return null
            WidgetBinding(
                id,
                preferences.getString("pending_provider", null) ?: return null,
                preferences.getLong("pending_profile", -1),
                preferences.getString("pending_target", null) ?: return null,
                WidgetBinding.Stage.valueOf(preferences.getString("pending_stage", null) ?: return null),
            )
        }.getOrNull()

    fun save(value: WidgetBinding?): Boolean {
        val editor = preferences.edit()
        if (value == null) {
            editor
                .remove("pending_id")
                .remove("pending_provider")
                .remove("pending_profile")
                .remove("pending_target")
                .remove("pending_stage")
        } else {
            editor
                .putInt("pending_id", value.id)
                .putString("pending_provider", value.provider)
                .putLong("pending_profile", value.profileSerial)
                .putString("pending_target", value.target)
                .putString("pending_stage", value.stage.name)
        }
        return editor.commit()
    }

    fun height(
        id: Int,
        fallback: Int,
    ): Int = preferences.getInt("height_$id", fallback)

    fun resize(
        id: Int,
        suggested: Int,
        delta: Int,
    ): Int {
        val height = WidgetSizing.resize(height(id, suggested), delta)
        preferences.edit().putInt("height_$id", height).apply()
        return height
    }

    fun forget(id: Int) {
        preferences.edit().remove("height_$id").apply()
    }
}
