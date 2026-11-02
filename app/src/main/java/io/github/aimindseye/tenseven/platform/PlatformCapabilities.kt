package io.github.aimindseye.tenseven.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import io.github.aimindseye.tenseven.conversations.ConversationAccess
import io.github.aimindseye.tenseven.onboarding.SetupStatus

/** Explicit platform contract; a sideloaded APK never assumes SableOS signature privileges. */
enum class CapabilityStatus { Ready, PermissionRequired, Unavailable }

data class PlatformCapabilities(
    val hubNotifications: CapabilityStatus,
    val usageRecents: CapabilityStatus,
    val calendarEvents: CapabilityStatus,
    val overlay: CapabilityStatus,
    val thirdPartySnapshots: CapabilityStatus,
    val localTasks: CapabilityStatus,
) {
    companion object {
        /** Pure and independently testable; exactly the same matrix is shared by UI and actions. */
        fun evaluate(
            hasListenerService: Boolean,
            listenerAllowed: Boolean,
            usageAllowed: Boolean,
            calendarAllowed: Boolean,
            overlayAllowed: Boolean,
            trustedSnapshotBridge: Boolean,
        ): PlatformCapabilities = PlatformCapabilities(
            hubNotifications = when {
                !hasListenerService -> CapabilityStatus.Unavailable
                listenerAllowed -> CapabilityStatus.Ready
                else -> CapabilityStatus.PermissionRequired
            },
            usageRecents = if (usageAllowed) CapabilityStatus.Ready else CapabilityStatus.PermissionRequired,
            calendarEvents = if (calendarAllowed) CapabilityStatus.Ready else CapabilityStatus.PermissionRequired,
            overlay = if (overlayAllowed) CapabilityStatus.Ready else CapabilityStatus.PermissionRequired,
            thirdPartySnapshots = if (trustedSnapshotBridge) CapabilityStatus.Ready else CapabilityStatus.Unavailable,
            // Notes/Tasks/Remind local records never need Calendar or network permissions.
            localTasks = CapabilityStatus.Ready,
        )
    }
}

object PlatformCapabilityProbe {
    fun detect(context: Context): PlatformCapabilities = PlatformCapabilities.evaluate(
        hasListenerService = Capabilities.hasConversationService(context),
        listenerAllowed = ConversationAccess.isGranted(context),
        usageAllowed = SetupStatus.hasUsageAccess(context),
        calendarAllowed = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED,
        overlayAllowed = Settings.canDrawOverlays(context),
        // A normal Full APK has no privileged task-snapshot bridge. Never fake live frames.
        trustedSnapshotBridge = false,
    )
}
