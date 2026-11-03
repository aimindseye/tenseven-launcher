package io.github.aimindseye.tenseven.ui

import io.github.aimindseye.tenseven.homeStyleState
import io.github.aimindseye.tenseven.SwitchRow
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
internal fun FrameSettingsSection(repository: StartStateRepository, notificationAccess: Boolean? = null) {
    val styleHolder = homeStyleState(repository)
    val style by styleHolder
    val context = LocalContext.current
    val capabilities = rememberPlatformCapabilities()
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, repository) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                styleHolder.value = repository.loadHomeStyle()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val granted = notificationAccess ?: (capabilities.hubNotifications == CapabilityStatus.Ready)
    if (!io.github.aimindseye.tenseven.frames.FrameAvailability.setup(style == HomeStyle.Bb10, granted)) return
    var value by remember { mutableStateOf(repository.loadFrameSettings()) }
    FrameIntegrationControls()
    ScreenCaptureControls()
    FrameSettingsPanel(value, capabilities) { value = it.normalized(); repository.saveFrameSettings(value) }
}

/** Capability explanations accompany disabled choices; no switch promises force-stop or silent capture. */
@Composable
internal fun FrameSettingsPanel(value: FrameSettings, capabilities: PlatformCapabilities, onChange: (FrameSettings) -> Unit) {
    Text("Active Frames", style = MaterialTheme.typography.titleLarge)
    Text(capabilities.frameFallbackReason, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SwitchRow("Live tiles", "Show available media, Calendar and local summaries. Notification excerpts require a separate opt-in.",
        value.liveSummaries) { onChange(value.copy(liveSummaries = it)) }
    FrameCapabilitySwitch("New messages", "Counts from apps you enabled in Hub.",
        value.newMessages, capabilities.hubNotifications, "Enable notification access to show opted-in counts.") {
        onChange(value.copy(newMessages = it))
    }
    FrameCapabilitySwitch("Snapshots", "Show a protected-content-safe Preview when supplied by an authorized OS task bridge.",
        value.snapshots, capabilities.thirdPartySnapshots, "Automatic app snapshots are unavailable for this install.") {
        onChange(value.copy(snapshots = it))
    }
    SwitchRow("Open apps from a frame", "Enter or tap opens the exact app and profile. Closing a card remains available.",
        value.openApps) { onChange(value.copy(openApps = it)) }
    SwitchRow("Minimise into a frame", "Keep recent-app cards when returning to Home. Does not minimise or control another app.",
        value.rememberReturnedApps) { onChange(value.copy(rememberReturnedApps = it)) }
    Text("Last 8 opened apps", style = MaterialTheme.typography.titleMedium)
    Text("Opening a ninth app replaces the oldest frame. Summaries never create extra app frames.")
    val context = LocalContext.current
    Button(onClick = { FrameWindowRepository(context).clear() }) { Text("Clear app frames") }
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

@Composable
private fun ScreenCaptureControls() {
    val context = LocalContext.current
    val preview by ScreenCaptureState.state.collectAsState()
    var explain by remember { mutableStateOf(false) }
    Text("User-approved screen capture", style = MaterialTheme.typography.titleMedium)
    Text("One shared-screen preview, separate from app snapshots. Images stay in memory and freeze while Home is visible. No audio is captured.")
    if (preview.error) Text("Capture ended or could not start. Start again to request fresh consent.")
    Button(onClick = {
        if (preview.running) context.stopService(android.content.Intent(context, ScreenCaptureService::class.java))
        else explain = true
    }) { Text(if (preview.running) "Stop sharing and clear preview" else "Start screen capture") }
    if (explain) AlertDialog(onDismissRequest = { explain = false },
        title = { Text("Share a screen with Active Frames?") },
        text = { Text("Android will ask what to share. Choose a single app when available. Whole-screen sharing can include anything visible while away from Home. Return to Home to see the last preview. Stop from this setting or the sharing notification; locking the phone also ends capture. Protected screens may appear blank.") },
        confirmButton = { TextButton(onClick = {
            explain = false
            context.startActivity(android.content.Intent(context, io.github.aimindseye.tenseven.ScreenCaptureActivity::class.java))
        }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { explain = false }) { Text("Cancel") } })
}

@Composable
private fun FrameIntegrationControls() {
    val context = LocalContext.current
    Text("App integrations", style = MaterialTheme.typography.titleMedium)
    Text("Notification access is required for media/radio and Hub excerpts. Calendar needs Calendar access. TenSeven local summaries do not need notification access.")
    Button(onClick = { context.startActivity(android.content.Intent(context, io.github.aimindseye.tenseven.FrameIntegrationsActivity::class.java)) }) {
        Text("Configure app summaries")
    }
}
