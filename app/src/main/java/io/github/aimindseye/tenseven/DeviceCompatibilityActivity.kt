package io.github.aimindseye.tenseven

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.platform.DeviceCompatibility

/** Explicit foreground probe. Records only events delivered to its own focused Compose screen. */
class DeviceCompatibilityActivity : ComponentActivity() {
    private val probe = KeyCaptureSession()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LauncherTheme(window = window) {
                DeviceProbeContent(DeviceCompatibility.report(this@DeviceCompatibilityActivity), probe) { report ->
                    getSystemService(ClipboardManager::class.java).setPrimaryClip(
                        ClipData.newPlainText("TenSeven compatibility", report),
                    )
                }
            }
        }
    }

    override fun onPause() {
        probe.capturing = false
        super.onPause()
    }
}

private class KeyCaptureSession {
    var capturing by mutableStateOf(false)
    var events by mutableStateOf(emptyList<String>())
        private set

    fun record(event: KeyEvent) {
        if (capturing) events = (events + DeviceCompatibility.event(event)).takeLast(MAX_EVENTS)
    }

    private companion object {
        const val MAX_EVENTS = 24
    }
}

// Compose Unit renderers require PascalCase; this exception is local to the renderer.
@Suppress("FunctionNaming")
@Composable
private fun DeviceProbeContent(
    report: String,
    probe: KeyCaptureSession,
    onCopy: (String) -> Unit,
) {
    val focus = remember { FocusRequester() }
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .onPreviewKeyEvent {
                    probe.record(it.nativeKeyEvent)
                    false
                }.focusRequester(focus)
                .focusable()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Device compatibility", style = MaterialTheme.typography.titleLarge)
            Text(report)
            Button(onClick = {
                probe.capturing = !probe.capturing
                if (probe.capturing) focus.requestFocus()
            }) { Text(if (probe.capturing) "Stop key capture" else "Start key capture") }
            Text("Only key codes and modifiers appear here. Capture stops when this screen loses focus.")
            Text(probe.events.joinToString("\n"))
            Button(onClick = { onCopy(report + probe.events.joinToString("\n")) }) { Text("Copy report") }
        }
    }
}
