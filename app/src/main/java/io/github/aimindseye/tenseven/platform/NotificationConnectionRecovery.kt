package io.github.aimindseye.tenseven.platform

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.aimindseye.tenseven.conversations.ConversationAccess
import io.github.aimindseye.tenseven.conversations.ConversationListenerService
import io.github.aimindseye.tenseven.conversations.NotificationConnection
import io.github.aimindseye.tenseven.conversations.NotificationRecoveryController

/** Android adapter; retains the application context rather than an Activity. */
object NotificationConnectionRecovery {
    private var controller: NotificationRecoveryController? = null

    @Synchronized
    private fun controller(context: Context): NotificationRecoveryController {
        controller?.let { return it }
        val app = context.applicationContext
        val handler = Handler(Looper.getMainLooper())
        return NotificationRecoveryController(
            now = SystemClock::elapsedRealtime,
            declared = { Capabilities.hasConversationService(app) },
            granted = { runCatching { ConversationAccess.isGranted(app) }.getOrDefault(false) },
            connected = { NotificationConnection.connected },
            rebind = { ConversationAccess.requestReconnect(app) },
            refresh = { ConversationListenerService.refreshConnected() },
            postDelayed = { delay, action ->
                handler.postDelayed({ action() }, delay)
                Unit
            },
        ).also { controller = it }
    }

    fun onLauncherResume(context: Context) = controller(context).onResume()

    fun retryFromSettings(context: Context) = controller(context).retryFromSettings()
}
