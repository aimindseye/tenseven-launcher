package io.github.aimindseye.tenseven.platform

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.core.net.toUri
import io.github.aimindseye.tenseven.conversations.ConversationAccess
import io.github.aimindseye.tenseven.conversations.ConversationHub
import io.github.aimindseye.tenseven.live.AppUnreadCount
import io.github.aimindseye.tenseven.model.HubUnreadCounts

/**
 * Read-only per-app counts for same-signature readers. Never publishes notification content.
 * The installed package identity isolates development from the production Watch contract.
 */
class HubCountsProvider : ContentProvider() {
    private var listening = false
    private var published: List<AppUnreadCount>? = null
    private val changed: () -> Unit = { notifyIfChanged() }

    override fun onCreate(): Boolean {
        val app = context ?: return false
        if (!listening) {
            published = snapshot(app)
            ConversationHub.addChangeListener(changed)
            LockLayerState.addChangeListener(changed)
            listening = true
            notifyIfChanged()
        }
        return true
    }

    override fun shutdown() {
        ConversationHub.removeChangeListener(changed)
        LockLayerState.removeChangeListener(changed)
        listening = false
        super.shutdown()
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val app = context?.takeIf { valid(uri, it.packageName) } ?: return null
        return snapshot(app)?.let { rows ->
            MatrixCursor(arrayOf("package", "count"), rows.size).apply {
                rows.forEach { addRow(arrayOf<Any>(it.packageName, it.count)) }
                setNotificationUri(app.contentResolver, appsUri(app.packageName))
            }
        }
    }

    override fun getType(uri: Uri): String? =
        context?.packageName?.takeIf { valid(uri, it) }?.let {
            "vnd.android.cursor.dir/vnd.$it.hubcounts"
        }

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = throw UnsupportedOperationException("Read-only provider")

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Read-only provider")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Read-only provider")

    private fun snapshot(app: android.content.Context): List<AppUnreadCount>? {
        if (!ConversationAccess.isGranted(app)) return null
        ConversationHub.ensureStateLoaded(app)
        return HubUnreadCounts.pick(LockLayerState.counts, ConversationHub.DEFAULT_MAX_APPS) {
            ConversationHub.unreadCounts()
        }
    }

    private fun notifyIfChanged() {
        val app = context ?: return
        val different =
            synchronized(this) {
                val current = snapshot(app)
                (current != published).also { if (it) published = current }
            }
        if (different) app.contentResolver.notifyChange(appsUri(app.packageName), null)
    }

    companion object {
        const val PATH_APPS = "apps"

        fun appsUri(applicationId: String): Uri = "content://$applicationId.hubcounts/$PATH_APPS".toUri()

        private fun valid(
            uri: Uri,
            applicationId: String,
        ): Boolean {
            val authority = "$applicationId.hubcounts"
            return uri.scheme == "content" && uri.authority == authority && uri.pathSegments == listOf(PATH_APPS)
        }
    }
}
