package io.github.aimindseye.tenseven.platform

import android.app.Notification
import android.content.Context
import android.os.Process
import android.service.notification.StatusBarNotification
import io.github.aimindseye.tenseven.frames.AppSummary
import io.github.aimindseye.tenseven.frames.AppSummaryPolicy
import io.github.aimindseye.tenseven.frames.SummarySource

/** Explicitly selected media/radio sources only. Notification text stays in process memory. */
object FrameMediaNotifications {
    @Volatile private var values = emptyMap<String, AppSummary>()
    fun replace(context: Context, notifications: List<StatusBarNotification>) {
        val enabled = FrameIntegrationPreferences(context).mediaPackages
        values = notifications.filter { it.user == Process.myUserHandle() && it.packageName in enabled }
            .sortedByDescending { it.postTime }.mapNotNull { sbn ->
                runCatching {
                    val extras = sbn.notification.extras
                    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                    val body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                        ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
                    val text = AppSummaryPolicy.text(listOf(title, body).filter(String::isNotBlank).distinct().joinToString(" · "))
                    if (text.isBlank()) null else sbn.packageName to AppSummary("radio:" + sbn.packageName,
                        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(sbn.packageName, 0)).toString(),
                        text, SummarySource.Notification, sbn.packageName, sbn.postTime)
                }.getOrNull()
            }.distinctBy { it.first }.toMap()
    }
    fun snapshot(context: Context): List<AppSummary> {
        val enabled = FrameIntegrationPreferences(context).mediaPackages
        return values.filterKeys { it in enabled }.values.toList()
    }
    fun clear() { values = emptyMap() }
}
