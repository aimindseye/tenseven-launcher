package io.github.aimindseye.tenseven.platform

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import io.github.aimindseye.tenseven.conversations.ConversationAccess
import io.github.aimindseye.tenseven.onboarding.SetupStatus

object PlatformCapabilityProbe {
    fun detect(context: Context): PlatformCapabilities =
        PlatformCapabilities.evaluate(
            hasListenerService = Capabilities.hasConversationService(context),
            listenerAllowed = ConversationAccess.isGranted(context),
            usageAllowed = SetupStatus.hasUsageAccess(context),
            calendarAllowed =
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                    PackageManager.PERMISSION_GRANTED,
            overlayAllowed = Settings.canDrawOverlays(context),
            // A normal Full APK has no privileged task-snapshot bridge. Never fake live frames.
            trustedSnapshotBridge =
                permission(context, "READ_FRAME_BUFFER") && permission(context, "REAL_GET_TASKS") &&
                    taskMethod(context, "getTaskSnapshot"),
            frameAccess =
                FramePlatformAccess(
                    tasks = permission(context, "REAL_GET_TASKS"),
                    resume =
                        permission(context, "START_TASKS_FROM_RECENTS") &&
                            taskMethod(
                                context,
                                "startActivityFromRecents",
                            ),
                    removal = permission(context, "REMOVE_TASKS") && taskMethod(context, "removeTask"),
                    keyboard =
                        context.resources.configuration.keyboard !=
                            android.content.res.Configuration
                                .KEYBOARD_NOKEYS,
                    lock =
                        when {
                            !Capabilities.hasLockService(context) -> CapabilityStatus.Unavailable
                            LockScreenAccessibilityService.isEnabled(context) -> CapabilityStatus.Ready
                            else -> CapabilityStatus.PermissionRequired
                        },
                    widgets = context.packageManager.hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS),
                    contacts = granted(context, Manifest.permission.READ_CONTACTS),
                    photos = granted(context, SetupStatus.photosPermission()),
                    camera = DefaultApps.cameraPackage(context) != null,
                    secureKeyguard = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true,
                    deviceLocked = context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == true,
                    lockService = Capabilities.hasLockService(context),
                    postNotifications =
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                            PackageManager.PERMISSION_GRANTED,
                ),
        )

    private fun granted(
        context: Context,
        permission: String,
    ): Boolean = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun permission(
        context: Context,
        name: String,
    ): Boolean = context.checkSelfPermission("android.permission.$name") == PackageManager.PERMISSION_GRANTED

    private fun taskMethod(
        context: Context,
        name: String,
    ): Boolean = RecentTasksRepository(context).hasTaskMethod(name)
}
