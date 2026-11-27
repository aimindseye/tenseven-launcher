package io.github.aimindseye.tenseven

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.design.DesignText
import io.github.aimindseye.tenseven.model.StartText
import io.github.aimindseye.tenseven.platform.StartStateRepository

/**
 * The lock-screen choice: the media player (FM included) and the Hub notices on the lock screen, as
 * one switch. Opened from Sable Setup (org.sableos.start.action.CHOOSE_LOCK_SCREEN) and shown by
 * the same section as Launcher Settings.
 */
class LockScreenChoiceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StartText.t.bindTo(this)
        DesignText.t.bindTo(this)
        val state = StartStateRepository(this)
        setContent {
            LauncherTheme(window = window) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier =
                            Modifier
                                .safeDrawingPadding()
                                .verticalScroll(rememberScrollState())
                                .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.ls_lock_media), style = MaterialTheme.typography.headlineSmall)
                        LockLayerSection(state)
                    }
                }
            }
        }
    }
}
