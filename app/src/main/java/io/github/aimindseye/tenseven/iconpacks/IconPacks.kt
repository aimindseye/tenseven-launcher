package io.github.aimindseye.tenseven.iconpacks

import android.content.Context
import android.content.ComponentName
import android.graphics.drawable.Drawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.aimindseye.tenseven.platform.StartStateRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * The icon pack the user chose (none by default) and its parsed mapping, cached for the process. [selected] is
 * Compose state so icons reload when it changes. Nothing is sent anywhere; the pack is read from its own APK.
 */
object IconPacks {
    var selected: String? by mutableStateOf(null)
        private set
    private var loaded = false
    private val definitions = ConcurrentHashMap<String, IconPackDefinition>()

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val saved = StartStateRepository(context).loadIconPack()
        selected = saved?.takeIf { isInstalled(context, it) }
    }

    fun select(
        context: Context,
        packageName: String?,
    ) {
        StartStateRepository(context).saveIconPack(packageName)
        selected = packageName
    }

    /** The pack's drawable for this activity, or null (no pack, no mapping): the caller draws the app's own icon. */
    fun drawableFor(
        context: Context,
        component: ComponentName,
    ): Drawable? {
        val pack = selected ?: return null
        val definition =
            definitions[pack] ?: IconPackLoader.loadDefinition(context, pack)?.also { definitions[pack] = it } ?: return null
        val icon = definition.iconForComponent(component.packageName + "/" + component.className, component.packageName) ?: return null
        return IconPackLoader.loadDrawable(context, pack, icon)
    }

    private fun isInstalled(
        context: Context,
        packageName: String,
    ): Boolean = runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess
}
