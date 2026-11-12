package io.github.aimindseye.tenseven

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.aimindseye.tenseven.companions.CompanionDiscovery
import io.github.aimindseye.tenseven.companions.CompanionPresence

/**
 * Visible M2-A read-only slice. It does not replace the bundled Extras toggles,
 * alter default apps, request permissions, or offer unverified APK installation.
 */
@Composable
internal fun CompanionAppsStatusSection() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val observations = remember(context, refresh) { CompanionDiscovery.snapshot(context) }

    Section("Independent companion APKs · preview")
    Text(
        "Future optional Phone, Calendar, Tasks, Notes, Calculator, Sound Profiles and Games " +
            "will install separately. Existing switches above still control bundled apps.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "Package names are proposed. No signer trust, installer, migration or release approval yet.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    observations.forEach { observed ->
        val detail =
            when (observed.presence) {
                CompanionPresence.DETECTED_SIGNER_UNVERIFIED -> {
                    "Detected in this profile · signature NOT verified" +
                        (observed.versionLabel?.let { " · reported version $it" } ?: "")
                }

                CompanionPresence.NOT_DETECTED_IN_PROFILE -> {
                    "Not detected in this Android profile"
                }
            }
        Text(
            "${observed.spec.label} — $detail",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Card("Refresh companion detection", "Read-only; does not install or change any application", false) {
        refresh++
    }
}
