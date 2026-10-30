package io.github.aimindseye.tenseven.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.telecom.TelecomManager

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

    const val CONTACTS: String = Intent.CATEGORY_APP_CONTACTS
    const val MESSAGING: String = Intent.CATEGORY_APP_MESSAGING

    /** The package of the app the phone uses for [category], or null when there is none or Android would ask. */
    fun packageFor(
        context: Context,
        category: String,
    ): String? =
        runCatching {
            when (category) {
                MESSAGING -> Telephony.Sms.getDefaultSmsPackage(context)
                else ->
                    context.packageManager
                        .resolveActivity(
                            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category),
                            PackageManager.MATCH_DEFAULT_ONLY,
                        )?.activityInfo?.packageName
                        // With no default chosen, Android answers with its own chooser.
                        ?.takeIf { it != "android" }
            }
        }.getOrNull()

    /** The phone's default dialer (Phone) app package, or null. */
    fun dialerPackage(context: Context): String? =
        runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()

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
