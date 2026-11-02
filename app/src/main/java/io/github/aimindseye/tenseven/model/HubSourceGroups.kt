package io.github.aimindseye.tenseven.model

import io.github.aimindseye.tenseven.live.AppUnreadCount

/**
 * Counts-only model of the compact Hub navigation. An enabled source does not earn
 * an icon unless it has an unread notification. Message and mail sources roll up
 * into separate totals; other active sources remain individually addressable.
 */
object HubSourceGroups {
    data class AppSource(val packageName: String, val count: Int)
    data class Summary(val messages: Int, val email: Int, val otherApps: List<AppSource>)

    private val mailPackages = setOf(
        "com.google.android.gm", "com.microsoft.office.outlook",
        "com.yahoo.mobile.client.android.mail", "ch.protonmail.android",
        "com.readdle.spark", "me.bluemail.mail",
    )
    private val messagePackages = setOf(
        "com.google.android.apps.messaging", "com.whatsapp", "com.whatsapp.w4b",
        "org.thoughtcrime.securesms", "org.telegram.messenger", "org.telegram.messenger.web",
        "com.discord", "com.facebook.orca", "com.viber.voip",
        "jp.naver.line.android", "com.skype.raider",
    )

    fun summarize(
        counts: List<AppUnreadCount>,
        enabledPackages: Set<String>,
        emailSources: Set<String>,
        messageSources: Set<String>,
    ): Summary {
        var messages = 0
        var email = 0
        val other = mutableListOf<AppSource>()
        counts.asSequence()
            .filter { it.packageName in enabledPackages && it.packageName != HubBar.REDACTED && it.count > 0 }
            .groupBy { it.packageName }
            .forEach { (pkg, rows) ->
                val count = rows.sumOf { it.count }
                when {
                    pkg in emailSources || pkg in mailPackages -> email += count
                    pkg in messageSources || pkg in messagePackages -> messages += count
                    else -> other += AppSource(pkg, count)
                }
            }
        return Summary(messages, email,
            other.sortedWith(compareByDescending<AppSource> { it.count }.thenBy { it.packageName }))
    }
}
