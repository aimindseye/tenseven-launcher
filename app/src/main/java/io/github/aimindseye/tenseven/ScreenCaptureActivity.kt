package io.github.aimindseye.tenseven

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import io.github.aimindseye.tenseven.platform.ScreenCaptureService
import io.github.aimindseye.tenseven.platform.ScreenCaptureState

/** Android owns consent and the shared-region choice. Tokens are consumed once, never persisted. */
class ScreenCaptureActivity : ComponentActivity() {
    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            runCatching {
                ContextCompat.startForegroundService(this, Intent(this, ScreenCaptureService::class.java)
                    .putExtra(ScreenCaptureService.CONSENT, data))
            }.onFailure {
                ScreenCaptureState.stopped(error = true)
                Toast.makeText(this, "Screen capture could not start. Please try again.", Toast.LENGTH_LONG).show()
            }
        }
        finish()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ScreenCaptureState.state.value.running) { finish(); return }
        if (savedInstanceState == null) {
            runCatching { consent.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent()) }
                .onFailure { ScreenCaptureState.stopped(error = true); finish() }
        }
    }
}
