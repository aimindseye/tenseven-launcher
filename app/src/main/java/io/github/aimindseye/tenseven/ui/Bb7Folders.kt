package io.github.aimindseye.tenseven.ui

import android.content.ComponentName
import android.content.Context
import android.os.Process
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.model.AppEntry

/**
 * BlackBerry 7 home folders. A folder sits on the home screen like an icon: its key is "folder:Name"
 * in the home screen's shortcut list, and the apps it holds are kept here (stable keys, in order).
 * Opening one lists its apps; the apps themselves stay installed and in the app lists.
 */
internal object Bb7Folders {
    const val PREFIX = "folder:"
    const val PACKAGE = "org.sableos.folder"
    private const val PREFS = "sable_bb7_folders"
    private const val SEPARATOR = "\n"

    /** Read by the home screen so it redraws after a folder changes. */
    var version by mutableIntStateOf(0)
        private set

    fun isFolder(app: AppEntry): Boolean = app.component.packageName == PACKAGE

    fun isFolderKey(key: String): Boolean = key.startsWith(PREFIX)

    fun key(name: String): String = PREFIX + name

    fun nameOf(app: AppEntry): String = app.component.className

    /** The folder as a home screen icon; its icon is drawn by [FolderGlyph]. */
    fun entry(name: String): AppEntry = AppEntry(name, ComponentName(PACKAGE, name), Process.myUserHandle())

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun names(context: Context): List<String> = prefs(context).all.keys.sorted()

    fun contents(
        context: Context,
        name: String,
    ): List<String> =
        prefs(context)
            .getString(name, null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    private fun write(
        context: Context,
        name: String,
        keys: List<String>,
    ) {
        prefs(context).edit().putString(name, keys.joinToString(SEPARATOR)).apply()
        version++
    }

    /** "Folder", then "Folder 2", "Folder 3" ... the first name not in use. */
    fun create(context: Context): String {
        val used = names(context).toSet()
        val name =
            generateSequence(1) { it + 1 }
                .map { if (it == 1) "Folder" else "Folder $it" }
                .first { it !in used }
        write(context, name, emptyList())
        return name
    }

    fun add(
        context: Context,
        name: String,
        appKey: String,
    ) = write(context, name, (contents(context, name) + appKey).distinct())

    fun remove(
        context: Context,
        name: String,
        appKey: String,
    ) = write(context, name, contents(context, name).filterNot { it == appKey })

    /**
     * Gives a folder a new name, keeping its apps. Returns the name used (trimmed, and made unique with a number
     * when taken), or null when [to] is blank or unchanged.
     */
    fun rename(
        context: Context,
        from: String,
        to: String,
    ): String? {
        val wanted = to.trim().replace(SEPARATOR, " ")
        if (wanted.isEmpty() || wanted == from) return null
        val used = names(context).toSet() - from
        val name = generateSequence(1) { it + 1 }.map { if (it == 1) wanted else "$wanted $it" }.first { it !in used }
        if (name == from) return null
        val held = contents(context, from)
        prefs(context).edit().remove(from).putString(name, held.joinToString(SEPARATOR)).apply()
        version++
        return name
    }

    fun delete(
        context: Context,
        name: String,
    ) {
        prefs(context).edit().remove(name).apply()
        version++
    }
}

/** A folder drawn as a tab-topped rectangle, the size of an app icon. */
@Composable
internal fun FolderGlyph(sizeDp: Int) {
    Box(Modifier.size(sizeDp.dp)) {
        Canvas(Modifier.size(sizeDp.dp)) {
            val w = this.size.width
            val h = this.size.height
            val blue = Color(0xFF2E9BD6)
            drawRoundRect(blue, Offset(w * 0.08f, h * 0.18f), Size(w * 0.4f, h * 0.2f), CornerRadius(w * 0.05f))
            drawRoundRect(
                blue.copy(alpha = 0.9f),
                Offset(w * 0.08f, h * 0.3f),
                Size(w * 0.84f, h * 0.52f),
                CornerRadius(w * 0.07f),
            )
        }
    }
}
