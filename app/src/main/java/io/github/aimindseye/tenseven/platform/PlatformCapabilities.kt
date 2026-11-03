package io.github.aimindseye.tenseven.platform

/** Explicit platform contract; a sideloaded APK never assumes SableOS signature privileges. */
enum class CapabilityStatus { Ready, PermissionRequired, Unavailable }

data class FramePlatformAccess(
    val tasks: Boolean = false,
    val resume: Boolean = false,
    val removal: Boolean = false,
    val keyboard: Boolean = false,
    val lock: CapabilityStatus = CapabilityStatus.Unavailable,
    val postNotifications: Boolean = true,
    val widgets: Boolean = true,
)

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
        usageRecents == CapabilityStatus.Ready -> "Recent apps combine launcher history and usage access. Automatic app snapshots are unavailable for this install."
        else -> "Recent apps use launcher history. Usage access adds apps opened elsewhere. Automatic app snapshots are unavailable for this install."
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
            frameAccess: FramePlatformAccess = FramePlatformAccess(),
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
            taskEnumeration = if (frameAccess.tasks) CapabilityStatus.Ready else CapabilityStatus.Unavailable,
            taskResume = if (frameAccess.resume) CapabilityStatus.Ready else CapabilityStatus.Unavailable,
            taskRemoval = if (frameAccess.removal) CapabilityStatus.Ready else CapabilityStatus.Unavailable,
            widgetHosting = if (frameAccess.widgets) CapabilityStatus.Ready else CapabilityStatus.Unavailable,
            hardwareKeyboard = frameAccess.keyboard,
            deviceLock = frameAccess.lock,
            postNotifications = if (frameAccess.postNotifications) CapabilityStatus.Ready else CapabilityStatus.PermissionRequired,
        )
    }
}
