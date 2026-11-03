package io.github.aimindseye.tenseven.platform

import android.content.Context
import androidx.core.content.edit
import androidx.core.net.toUri

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
    init {
        // Only this removed feature ever retained document grants. Forget links without deleting files.
        val keys = listOf("document_word", "document_excel", "document_powerpoint")
        keys.mapNotNull { prefs.getString(it, null) }.distinct().forEach { raw ->
            runCatching {
                val uri = raw.toUri()
                if (uri.scheme == "content") context.contentResolver.releasePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        if (keys.any(prefs::contains)) prefs.edit { keys.forEach(::remove) }
    }
}
