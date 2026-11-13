package io.github.aimindseye.tenseven.companions

import android.content.Context
import android.content.pm.PackageManager

/**
 * Package presence is informational only. Never use this result to install, trust,
 * launch privileged IPC, or represent a verified release. Signer pins are not yet approved.
 */
enum class CompanionPresence {
    DETECTED_SIGNER_UNVERIFIED,
    NOT_DETECTED_IN_PROFILE,
}

data class CompanionObservation(
    val spec: CompanionSpec,
    val presence: CompanionPresence,
    val versionLabel: String?,
)

private const val MAX_VERSION_LABEL_LENGTH = 80

object CompanionDiscovery {
    fun snapshot(context: Context): List<CompanionObservation> =
        CompanionCatalog.all.map { spec ->
            val packageInfo =
                try {
                    context.packageManager.getPackageInfo(spec.packageName, PackageManager.PackageInfoFlags.of(0))
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                } catch (_: SecurityException) {
                    null
                }
            CompanionObservation(
                spec = spec,
                presence =
                    if (packageInfo == null) {
                        CompanionPresence.NOT_DETECTED_IN_PROFILE
                    } else {
                        CompanionPresence.DETECTED_SIGNER_UNVERIFIED
                    },
                versionLabel = packageInfo?.versionName?.take(MAX_VERSION_LABEL_LENGTH),
            )
        }
}
