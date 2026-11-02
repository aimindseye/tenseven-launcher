package io.github.aimindseye.tenseven

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.frames.FrameSettings
import io.github.aimindseye.tenseven.model.HomeStyle
import io.github.aimindseye.tenseven.platform.*

@Composable
internal fun FrameSettingsSection(repository: StartStateRepository) {
    val style by homeStyleState(repository)
    if (style != HomeStyle.Bb10) return
    var value by remember { mutableStateOf(repository.loadFrameSettings()) }
    val context = LocalContext.current
    val capabilities = remember { PlatformCapabilityProbe.detect(context) }
    FrameSettingsPanel(value, capabilities) { value = it.normalized(); repository.saveFrameSettings(value) }
}

/** Capability explanations accompany disabled choices; no switch promises force-stop or silent capture. */
@Composable
internal fun FrameSettingsPanel(value: FrameSettings, capabilities: PlatformCapabilities, onChange: (FrameSettings) -> Unit) {
    Text("Active Frames", style = MaterialTheme.typography.titleLarge)
    Text(capabilities.frameFallbackReason, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SwitchRow("Live tiles", "Show available TenSeven Calendar and Now playing summaries. These are not embedded app screens.",
        value.liveSummaries) { onChange(value.copy(liveSummaries = it)) }
    FrameCapabilitySwitch("New messages", "Counts from apps you enabled in Hub.",
        value.newMessages, capabilities.hubNotifications, "Enable notification access to show opted-in counts.") {
        onChange(value.copy(newMessages = it))
    }
    FrameCapabilitySwitch("Snapshots", "Show a protected-content-safe Preview when supplied by an authorized OS task bridge.",
        value.snapshots, capabilities.thirdPartySnapshots, "Android does not authorize other-app snapshots for this install.") {
        onChange(value.copy(snapshots = it))
    }
    SwitchRow("Open apps from a frame", "Enter or tap opens the exact app and profile. Closing a card remains available.",
        value.openApps) { onChange(value.copy(openApps = it)) }
    SwitchRow("Minimise into a frame", "Keep recent-app cards when returning to Home. Does not minimise or control another app.",
        value.rememberReturnedApps) { onChange(value.copy(rememberReturnedApps = it)) }
    Text("Maximum frames", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FrameSettings.LIMITS.forEach { limit ->
            FilterChip(selected = value.maximum == limit, onClick = { onChange(value.copy(maximum = limit)) }, label = { Text("$limit") })
        }
    }
    FrameCapabilitySwitch("Force-stop when closing", "Close removes only the TenSeven card; the underlying app keeps its data and tasks.",
        false, CapabilityStatus.Unavailable, "Force-stop of other apps is not supported by this launcher.") {}
}

@Composable
private fun FrameCapabilitySwitch(title: String, detail: String, value: Boolean, status: CapabilityStatus,
    explanation: String, onChange: (Boolean) -> Unit) {
    if (status == CapabilityStatus.Ready) SwitchRow(title, detail, value, onChange)
    else Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(explanation, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = false, onCheckedChange = null, enabled = false)
        }
    }
}
