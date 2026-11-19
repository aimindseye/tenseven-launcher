package io.github.aimindseye.tenseven

import android.content.Intent
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

/** Read-only companion presence and independent planning; never enables bundled activities or installs APKs. */
@Composable
internal fun CompanionAppsStatusSection() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val observations = remember(context, refresh) { CompanionDiscovery.snapshot(context) }

    Section("Optional companion apps · status")
    Text(
        "Phone and Calendar are independent development APKs. Tasks, Notes, Calculator and " +
            "Sound Profiles await separate extraction. Launcher works without any companion.",
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
    Card(
        "Open companion setup planner",
        "Informational selections only; this Launcher does not install APKs",
        false,
    ) {
        context.startActivity(Intent(context, CompanionWizardActivity::class.java))
    }
    Card("Refresh companion detection", "Read-only; does not install or change any application", false) {
        refresh++
    }
}
