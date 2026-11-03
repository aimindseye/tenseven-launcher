package io.github.aimindseye.tenseven

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.platform.DeviceCompatibility
import io.github.aimindseye.tenseven.ui.LauncherTheme

/** Explicit foreground probe. Captures key metadata only, never Unicode/typed text. */
class DeviceCompatibilityActivity : ComponentActivity() {
    private var capturing by mutableStateOf(false)
    private var events by mutableStateOf(emptyList<String>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LauncherTheme(window = window) {
                Surface(color = MaterialTheme.colorScheme.background) {
                Column(
                    Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Device compatibility", style = MaterialTheme.typography.titleLarge)
                    val report = DeviceCompatibility.report(this@DeviceCompatibilityActivity)
                    Text(report)
                    Button(onClick = { capturing = !capturing }) {
                        Text(if (capturing) "Stop key capture" else "Start key capture")
                    }
                    Text("Only key codes and modifiers appear here. Capture stops when this screen loses focus.")
                    Text(events.joinToString("\n"))
                    Button(onClick = {
                        getSystemService(ClipboardManager::class.java).setPrimaryClip(
                            ClipData.newPlainText("TenSeven compatibility", report + events.joinToString("\n")),
                        )
                    }) { Text("Copy report") }
                }
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (capturing) events = (events + DeviceCompatibility.event(event)).takeLast(MAX_EVENTS)
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        capturing = false
        super.onPause()
    }

    private companion object {
        const val MAX_EVENTS = 24
    }
}
