package io.github.aimindseye.tenseven.features

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowContentResolver

internal object AndroidViewFixtures {
    fun descendants(root: View): List<View> =
        listOf(root) +
            if (root is ViewGroup) {
                (0 until root.childCount).flatMap { descendants(root.getChildAt(it)) }
            } else {
                emptyList()
            }

    fun text(
        root: View,
        value: String,
    ): TextView = descendants(root).filterIsInstance<TextView>().first { it.text.toString() == value }

    fun described(
        root: View,
        value: String,
    ): View = descendants(root).first { it.contentDescription?.toString() == value }

    fun input(
        root: View,
        hint: String,
    ): EditText = descendants(root).filterIsInstance<EditText>().first { it.hint?.toString() == hint }

    fun permission(
        name: String,
        granted: Boolean,
    ) {
        val application = shadowOf(RuntimeEnvironment.getApplication())
        if (granted) application.grantPermissions(name) else application.denyPermissions(name)
    }

    fun provider(authority: String): FixtureProvider =
        FixtureProvider().also { ShadowContentResolver.registerProviderInternal(authority, it) }
}

/** Synthetic records only. No provider writes, real contacts, network or device calls. */
internal class FixtureProvider : ContentProvider() {
    val records = mutableMapOf<String, List<Map<String, Any>>>()
    val queries = mutableListOf<Uri>()

    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        synchronized(queries) { queries.add(uri) }
        val columns = projection?.toList().orEmpty()
        val rows =
            records.entries
                .firstOrNull { uri.path.orEmpty().startsWith(it.key) }
                ?.value
                .orEmpty()
        return MatrixCursor(columns.toTypedArray()).apply {
            rows.forEach { row -> addRow(columns.map { row[it] }.toTypedArray()) }
        }
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri = error("Unexpected provider write")

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = error("Unexpected provider delete")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = error("Unexpected provider update")
}
