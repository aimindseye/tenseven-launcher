package io.github.aimindseye.tenseven.platform

import android.content.Context
import androidx.core.content.edit

/** Device-local choices, separate from backed-up launcher preferences and notification counts. */
class FrameIntegrationPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("frame_integrations_device", Context.MODE_PRIVATE)
    var excerpts: Boolean
        get() = prefs.getBoolean("notification_excerpts", false)
        set(value) { prefs.edit { putBoolean("notification_excerpts", value) } }
    var local: Boolean
        get() = prefs.getBoolean("local_summaries", true)
        set(value) { prefs.edit { putBoolean("local_summaries", value) } }
    var mediaPackages: Set<String>
        get() = prefs.getStringSet("media_sources", emptySet()).orEmpty().toSet()
        set(value) { prefs.edit { putStringSet("media_sources", value.toSet()) } }
    fun document(kind: String): String? = prefs.getString("document_" + kind, null)
    fun document(kind: String, uri: String?) { prefs.edit { putString("document_" + kind, uri) } }
}
