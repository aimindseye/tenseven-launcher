package io.github.aimindseye.tenseven.platform

import android.content.Context
import android.content.Intent

/**
 * The phone's own default apps, found the way Android intends: by app category, so whichever calendar, music,
 * email, gallery or weather app the user has chosen opens. Tenseven names no package for them.
 */
object DefaultApps {
    const val CALENDAR: String = Intent.CATEGORY_APP_CALENDAR
    const val EMAIL: String = Intent.CATEGORY_APP_EMAIL
    const val GALLERY: String = Intent.CATEGORY_APP_GALLERY
    const val MUSIC: String = Intent.CATEGORY_APP_MUSIC

    /** Intent.CATEGORY_APP_WEATHER, written out because it needs API 31 and the minimum here is 30. */
    const val WEATHER: String = "android.intent.category.APP_WEATHER"

    /** Opens the default app for [category]; false when the phone has none. */
    fun launch(
        context: Context,
        category: String,
    ): Boolean =
        runCatching {
            context.startActivity(
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
}
