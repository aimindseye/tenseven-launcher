// Derived from MinkLauncher OpenSource (github.com/katoaapps/openminilaunch, commit b9b26f7),
// platform/LockScreenAccessibilityService.kt, Copyright 2026 Katoa Apps, Apache License 2.0.
// Changed: package; adds isEnabled for the Appearance switch.
package io.github.aimindseye.tenseven.platform

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Performs only the system lock-screen action requested by the user's double-tap. This service does not
 * subscribe to events, read the screen or inspect windows (see res/xml/lock_accessibility_service.xml), and it
 * stays off until the user turns it on in Accessibility settings.
 */
class LockScreenAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        connectedService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (connectedService === this) connectedService = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var connectedService: LockScreenAccessibilityService? = null

        fun lockScreen(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                connectedService?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true

        /** Whether the user has turned the service on in Accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, LockScreenAccessibilityService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            return enabled?.split(':')?.any { it.equals(me, ignoreCase = true) } == true
        }
    }
}
