package io.github.aimindseye.tenseven.companions

/**
 * No companion identity/signing pins are yet approved for independent release.
 * Presence or a matching APK package name is never evidence of an authentic APK.
 * Any future installer must verify Android's package signing information against
 * an explicitly reviewed, release-specific trust policy before invoking Android.
 */
enum class CompanionInstallGate {
    NOT_SELECTED,
    RELEASE_IDENTITY_UNAPPROVED,
}

object CompanionInstallPolicy {
    fun installationGate(
        id: String,
        selected: Collection<String>,
    ): CompanionInstallGate =
        if (id !in CompanionCatalog.normalize(selected)) {
            CompanionInstallGate.NOT_SELECTED
        } else {
            CompanionInstallGate.RELEASE_IDENTITY_UNAPPROVED
        }
}
