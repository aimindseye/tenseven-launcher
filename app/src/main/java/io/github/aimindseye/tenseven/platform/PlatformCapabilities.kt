package io.github.aimindseye.tenseven.platform

/** Explicit platform contract; a sideloaded APK never assumes SableOS signature privileges. */
enum class CapabilityStatus { Ready, PermissionRequired, Unavailable }

data class PlatformCapabilities(
    val hubNotifications: CapabilityStatus,
    val usageRecents: CapabilityStatus,
    val calendarEvents: CapabilityStatus,
    val overlay: CapabilityStatus,
    val thirdPartySnapshots: CapabilityStatus,
    val localTasks: CapabilityStatus,
    val taskEnumeration: CapabilityStatus = CapabilityStatus.Unavailable,
    val taskResume: CapabilityStatus = CapabilityStatus.Unavailable,
    val taskRemoval: CapabilityStatus = CapabilityStatus.Unavailable,
    val widgetHosting: CapabilityStatus = CapabilityStatus.Ready,
    val hardwareKeyboard: Boolean = false,
    val deviceLock: CapabilityStatus = CapabilityStatus.Unavailable,
    val postNotifications: CapabilityStatus = CapabilityStatus.Ready,

) {
    val frameFallbackReason: String get() = when {
        thirdPartySnapshots == CapabilityStatus.Ready -> "Preview uses an OS-authorized task snapshot; protected content stays hidden."
        usageRecents == CapabilityStatus.Ready -> "Recent apps combine launcher history and usage access. Android does not provide other-app previews."
        else -> "Recent apps use launcher history. Usage access adds apps opened elsewhere. Android does not provide other-app previews."
    }

    companion object {
        /** Pure and independently testable; exactly the same matrix is shared by UI and actions. */
        fun evaluate(
            hasListenerService: Boolean,
            listenerAllowed: Boolean,
            usageAllowed: Boolean,
            calendarAllowed: Boolean,
            overlayAllowed: Boolean,
            trustedSnapshotBridge: Boolean,
            trustedTasks: Boolean = false,
            canResumeTasks: Boolean = false,
            canRemoveTasks: Boolean = false,
            keyboard: Boolean = false,
            lockService: Boolean = false,
            notificationPostingAllowed: Boolean = true,
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
            taskEnumeration = if (trustedTasks) CapabilityStatus.Ready else CapabilityStatus.Unavailable,
            taskResume = if (canResumeTasks) CapabilityStatus.Ready else CapabilityStatus.Unavailable,
            taskRemoval = if (canRemoveTasks) CapabilityStatus.Ready else CapabilityStatus.Unavailable,
            hardwareKeyboard = keyboard,
            deviceLock = if (lockService) CapabilityStatus.Ready else CapabilityStatus.Unavailable,
            postNotifications = if (notificationPostingAllowed) CapabilityStatus.Ready else CapabilityStatus.PermissionRequired,
        )
    }
}
