package io.github.aimindseye.tenseven

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.companions.CompanionCatalog
import io.github.aimindseye.tenseven.companions.CompanionDiscovery
import io.github.aimindseye.tenseven.companions.CompanionInstallGate
import io.github.aimindseye.tenseven.companions.CompanionInstallPolicy
import io.github.aimindseye.tenseven.companions.CompanionObservation
import io.github.aimindseye.tenseven.companions.CompanionPresence
import io.github.aimindseye.tenseven.companions.CompanionPreset
import io.github.aimindseye.tenseven.companions.CompanionSelectionStore
import io.github.aimindseye.tenseven.companions.CompanionSetupPlan

private enum class WizardPage {
    PRESET,
    CUSTOMIZE,
    REVIEW,
}

private val wizardOuterPadding = 16.dp
private val wizardItemSpacing = 12.dp
private val wizardNavigationSpacing = 10.dp

/** M2-B selection wizard. No APK installation, role acquisition or migration is performed. */
class CompanionWizardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CompanionWizardScreen(this)
            }
        }
    }
}

@Composable
private fun CompanionWizardScreen(activity: CompanionWizardActivity) {
    var page by rememberSaveable { mutableStateOf(WizardPage.PRESET) }
    var requestedIds by rememberSaveable {
        mutableStateOf(CompanionSetupPlan.encode(CompanionSelectionStore.load(activity)))
    }
    val selected = CompanionSetupPlan.decode(requestedIds)
    var refresh by remember { mutableIntStateOf(0) }
    val observations = remember(refresh) { CompanionDiscovery.snapshot(activity) }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(wizardOuterPadding),
        verticalArrangement = Arrangement.spacedBy(wizardItemSpacing),
    ) {
        Text("TenSeven Companion Apps", style = MaterialTheme.typography.titleLarge)
        Text(
            "Independent APKs · setup preview. Your current Launcher and bundled apps stay unchanged.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (page) {
            WizardPage.PRESET -> {
                CompanionPresetPage(
                    selected,
                    onSelect = { requestedIds = CompanionSetupPlan.encode(it) },
                    onCustom = { page = WizardPage.CUSTOMIZE },
                )
            }

            WizardPage.CUSTOMIZE -> {
                CompanionCustomizePage(selected) { requestedIds = CompanionSetupPlan.encode(it) }
            }

            WizardPage.REVIEW -> {
                CompanionReviewPage(selected, observations) { refresh++ }
            }
        }
        CompanionWizardNavigation(
            finish = activity::finish,
            page = page,
            onPageChange = { page = it },
            onSave = { CompanionSelectionStore.save(activity, selected) },
        )
    }
}

@Composable
private fun CompanionPresetPage(
    selected: Set<String>,
    onSelect: (Set<String>) -> Unit,
    onCustom: () -> Unit,
) {
    Section("Choose a preset")
    Card(
        "Minimum apps",
        "Tasks / Remind, Notes, Calc Plus",
        CompanionSetupPlan.selectedPreset(selected) == CompanionPreset.MINIMUM,
    ) {
        onSelect(CompanionSetupPlan.choosePreset(CompanionPreset.MINIMUM, selected))
    }
    Card(
        "Full apps",
        "All six optional tool companions",
        CompanionSetupPlan.selectedPreset(selected) == CompanionPreset.FULL,
    ) {
        onSelect(CompanionSetupPlan.choosePreset(CompanionPreset.FULL, selected))
    }
    Card("Custom apps", "Choose individual companions on the next screen", false) {
        onCustom()
    }
}

@Composable
private fun CompanionCustomizePage(
    selected: Set<String>,
    onSelect: (Set<String>) -> Unit,
) {
    Section("Choose individual companions")
    CompanionCatalog.all.forEach { spec ->
        SwitchRow(
            spec.label,
            "Optional, independent APK · ${spec.packageName}",
            spec.id in selected,
        ) { checked ->
            onSelect(CompanionSetupPlan.toggle(selected, spec.id, checked))
        }
    }
}

@Composable
private fun CompanionReviewPage(
    selected: Set<String>,
    observations: List<CompanionObservation>,
    refresh: () -> Unit,
) {
    Section("Review your selection")
    Text(
        "${selected.size} of ${CompanionCatalog.all.size} companions selected. " +
            "Saving these choices will not install or enable any app.",
        style = MaterialTheme.typography.bodyMedium,
    )
    CompanionCatalog.all.filter { it.id in selected }.forEach { spec ->
        val observation = observations.firstOrNull { it.spec.id == spec.id }
        val presence =
            when (observation?.presence) {
                CompanionPresence.DETECTED_SIGNER_UNVERIFIED -> {
                    "Detected, but signer is not verified"
                }

                else -> {
                    "Not detected in this Android profile"
                }
            }
        Text("${spec.label}: $presence", style = MaterialTheme.typography.bodyMedium)
        val installGate = CompanionInstallPolicy.installationGate(spec.id, selected)
        if (installGate == CompanionInstallGate.RELEASE_IDENTITY_UNAPPROVED) {
            Text(
                "Installation unavailable: release package identity and signing are not approved.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Card("Refresh package status", "Detection only; signatures remain unverified", false) {
        refresh()
    }
}

@Composable
private fun CompanionWizardNavigation(
    finish: () -> Unit,
    page: WizardPage,
    onPageChange: (WizardPage) -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(wizardNavigationSpacing),
    ) {
        OutlinedButton(
            onClick = {
                when (page) {
                    WizardPage.PRESET -> finish()
                    WizardPage.CUSTOMIZE -> onPageChange(WizardPage.PRESET)
                    WizardPage.REVIEW -> onPageChange(WizardPage.CUSTOMIZE)
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            Text("Back")
        }
        Button(
            onClick = {
                when (page) {
                    WizardPage.PRESET -> {
                        onPageChange(WizardPage.CUSTOMIZE)
                    }

                    WizardPage.CUSTOMIZE -> {
                        onPageChange(WizardPage.REVIEW)
                    }

                    WizardPage.REVIEW -> {
                        onSave()
                        finish()
                    }
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            Text(if (page == WizardPage.REVIEW) "Save & finish" else "Next")
        }
    }
}
