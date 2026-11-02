package io.github.aimindseye.tenseven.platform

import android.content.ComponentName
import android.content.Context
import io.github.aimindseye.tenseven.conversations.ConversationListenerService

/**
 * Which optional services this build declares. The lite build (`:app:assembleLite`) leaves out the accessibility
 * service (double-tap to lock) and the notification listener (Hub conversations), because Google Play Protect blocks
 * an app installed from a download when it declares either. Screens that only exist to switch them on check here,
 * so the lite build never offers a switch that cannot work.
 */
object Capabilities {
    /** True when the double-tap lock accessibility service is part of this build. */
    fun hasLockService(context: Context): Boolean = declared(context, LockScreenAccessibilityService::class.java)

    /** True when the notification listener behind Hub conversations and Now playing is part of this build. */
    fun hasConversationService(context: Context): Boolean = declared(context, ConversationListenerService::class.java)

    private fun declared(
        context: Context,
        service: Class<*>,
    ): Boolean = runCatching { context.packageManager.getServiceInfo(ComponentName(context, service), 0) }.isSuccess
}
