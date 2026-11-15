package io.github.aimindseye.tenseven.platform

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.core.net.toUri
import io.github.aimindseye.tenseven.conversations.ConversationAccess
import io.github.aimindseye.tenseven.conversations.ConversationHub
import io.github.aimindseye.tenseven.model.HubUnreadCounts

/**
 * The Hub's unread counts for a paired watch app (Sable Watch): one row per app, package and count, the same numbers
 * as the Hub bar. No message text, title or sender. Read-only.
 *
 * Protected by READ_HUB_COUNTS, a signature permission: only an app signed with Tenseven's own key can read it.
 * Returns no cursor while notification access is off, so the reader can fall back to a source of its own.
 */
class HubCountsProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val context = context ?: return false
        val notify = { context.contentResolver.notifyChange(APPS_URI, null) }
        ConversationHub.addChangeListener(notify)
        LockLayerState.addChangeListener(notify)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val context = context?.takeIf { uri.pathSegments == listOf(PATH_APPS) && ConversationAccess.isGranted(it) }
        return context?.let { ctx ->
            ConversationHub.ensureStateLoaded(ctx)
            val rows =
                HubUnreadCounts.pick(LockLayerState.counts, ConversationHub.DEFAULT_MAX_APPS) {
                    ConversationHub.unreadCounts()
                }
            MatrixCursor(COLUMNS, rows.size).apply {
                rows.forEach { addRow(arrayOf<Any>(it.packageName, it.count)) }
                setNotificationUri(ctx.contentResolver, APPS_URI)
            }
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.io.github.aimindseye.tenseven.hubcounts"

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

    companion object {
        const val AUTHORITY = "io.github.aimindseye.tenseven.hubcounts"
        const val PATH_APPS = "apps"
        val APPS_URI: Uri = "content://$AUTHORITY/$PATH_APPS".toUri()
        private val COLUMNS = arrayOf("package", "count")
    }
}
