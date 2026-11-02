package io.github.aimindseye.tenseven.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import io.github.aimindseye.tenseven.conversations.ConversationAccess
import io.github.aimindseye.tenseven.onboarding.SetupStatus

object PlatformCapabilityProbe {
    fun detect(context: Context): PlatformCapabilities = PlatformCapabilities.evaluate(
        hasListenerService = Capabilities.hasConversationService(context),
        listenerAllowed = ConversationAccess.isGranted(context),
        usageAllowed = SetupStatus.hasUsageAccess(context),
        calendarAllowed = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED,
        overlayAllowed = Settings.canDrawOverlays(context),
        // A normal Full APK has no privileged task-snapshot bridge. Never fake live frames.
        trustedSnapshotBridge = permission(context, "READ_FRAME_BUFFER") && permission(context, "REAL_GET_TASKS") && taskMethod("getTaskSnapshot"),
        frameAccess = FramePlatformAccess(
            tasks = permission(context, "REAL_GET_TASKS"),
            resume = permission(context, "START_TASKS_FROM_RECENTS") && taskMethod("startActivityFromRecents"),
            removal = permission(context, "REMOVE_TASKS") && taskMethod("removeTask"),
            keyboard = context.resources.configuration.keyboard != android.content.res.Configuration.KEYBOARD_NOKEYS,
            lock = when {
                !Capabilities.hasLockService(context) -> CapabilityStatus.Unavailable
                LockScreenAccessibilityService.isEnabled(context) -> CapabilityStatus.Ready
                else -> CapabilityStatus.PermissionRequired
            },
            widgets = context.packageManager.hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS),
            postNotifications = android.os.Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        ),
    )

    private fun permission(context: Context, name: String): Boolean =
        context.checkSelfPermission("android.permission.$name") == PackageManager.PERMISSION_GRANTED

    private fun taskMethod(name: String): Boolean = runCatching {
        val service = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)
        service?.javaClass?.methods?.any { it.name == name } == true
    }.getOrDefault(false)
}
