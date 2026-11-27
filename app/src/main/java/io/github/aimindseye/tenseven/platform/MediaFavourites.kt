package io.github.aimindseye.tenseven.platform

import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Process
import io.github.aimindseye.tenseven.model.AppEntry

/** Opens the Favourites list of Sable Media (shortcuts to songs, FM and online stations, lists). */
internal object MediaFavourites {
    private const val PACKAGE = "org.sableos.media"
    const val TILE_PACKAGE = "org.sableos.favourites"
    private const val ACTION = "org.sableos.media.action.FAVOURITES"

    private fun intent(): Intent = Intent(ACTION).setPackage(PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Sable Media is installed (the SableOS build ships it). */
    fun available(context: Context): Boolean =
        runCatching {
            context.packageManager.resolveActivity(intent(), PackageManager.MATCH_DEFAULT_ONLY) != null
        }.getOrDefault(false)

    /** The Favourites home tile: a pseudo app that opens the list; null when Sable Media is not installed. */
    fun tile(context: Context): AppEntry? =
        if (available(context)) {
            AppEntry("Favourites", ComponentName(TILE_PACKAGE, "Favourites"), Process.myUserHandle())
        } else {
            null
        }

    fun isTile(entry: AppEntry): Boolean = entry.component.packageName == TILE_PACKAGE

    fun open(context: Context) {
        runCatching { context.startActivity(intent()) }
    }
}
