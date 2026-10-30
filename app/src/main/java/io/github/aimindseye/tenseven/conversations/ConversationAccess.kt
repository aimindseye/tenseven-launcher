package io.github.aimindseye.tenseven.conversations

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat

/** Whether the user has given Tenseven notification access, and the way to ask for it. */
object ConversationAccess {
    fun isGranted(context: Context): Boolean = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    fun settingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    /** Asks Android to reconnect the listener after it was killed; harmless when access is off. */
    fun requestReconnect(context: Context) {
        runCatching {
            NotificationListenerService.requestRebind(ComponentName(context, ConversationListenerService::class.java))
        }
    }
}
