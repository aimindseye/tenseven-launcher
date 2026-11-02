package io.github.aimindseye.tenseven.platform

import android.content.ComponentName
import android.content.Context
import io.github.aimindseye.tenseven.conversations.ConversationListenerService

/**
 * Runtime availability of optional services in the Full-only install.
 * Android still requires the user's explicit grant of each special access.
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
