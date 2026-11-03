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
    val contacts: Boolean = false,
    val photos: Boolean = false,
    val camera: Boolean = false,
    val secureKeyguard: Boolean = false,
    val deviceLocked: Boolean = false,
    val lockService: Boolean = false,
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
    val contacts: CapabilityStatus = CapabilityStatus.PermissionRequired,
    val photos: CapabilityStatus = CapabilityStatus.PermissionRequired,
    val camera: CapabilityStatus = CapabilityStatus.Unavailable,
    val secureKeyguard: Boolean = false,
    val deviceLocked: Boolean = false,
    val lockServiceAvailable: Boolean = false,
    val deviceLock: CapabilityStatus = CapabilityStatus.Unavailable,
    val postNotifications: CapabilityStatus = CapabilityStatus.Ready,
) {
    val frameFallbackReason: String get() =
        when {
            thirdPartySnapshots == CapabilityStatus.Ready -> "Preview uses an OS-authorized task snapshot; protected content stays hidden."
            usageRecents == CapabilityStatus.Ready -> "Opened frames use launcher records and usage access; only the latest eight are kept. Automatic app snapshots are unavailable for this install."
            else -> "Open an app to create a frame. Usage access adds new openings outside TenSeven. Automatic app snapshots are unavailable for this install."
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
        ): PlatformCapabilities =
            PlatformCapabilities(
                hubNotifications =
                    when {
                        !hasListenerService -> CapabilityStatus.Unavailable
                        listenerAllowed -> CapabilityStatus.Ready
                        else -> CapabilityStatus.PermissionRequired
                    },
                usageRecents = permissionStatus(usageAllowed),
                calendarEvents = permissionStatus(calendarAllowed),
                overlay = permissionStatus(overlayAllowed),
                thirdPartySnapshots = availability(trustedSnapshotBridge),
                // Notes/Tasks/Remind local records never need Calendar or network permissions.
                localTasks = CapabilityStatus.Ready,
                taskEnumeration = availability(frameAccess.tasks),
                taskResume = availability(frameAccess.resume),
                taskRemoval = availability(frameAccess.removal),
                widgetHosting = availability(frameAccess.widgets),
                hardwareKeyboard = frameAccess.keyboard,
                contacts = permissionStatus(frameAccess.contacts),
                photos = permissionStatus(frameAccess.photos),
                camera = availability(frameAccess.camera),
                secureKeyguard = frameAccess.secureKeyguard,
                deviceLocked = frameAccess.deviceLocked,
                lockServiceAvailable = frameAccess.lockService,
                deviceLock = frameAccess.lock,
                postNotifications = permissionStatus(frameAccess.postNotifications),
            )

        private fun permissionStatus(allowed: Boolean): CapabilityStatus =
            if (allowed) CapabilityStatus.Ready else CapabilityStatus.PermissionRequired

        private fun availability(available: Boolean): CapabilityStatus =
            if (available) CapabilityStatus.Ready else CapabilityStatus.Unavailable
    }
}
