package io.github.aimindseye.tenseven.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.platform.SoundProfileControl

/**
 * The speaker icon: shows Sound, Vibrate or Silent. A tap opens Sound Profiles when that extra is on, otherwise it
 * switches between Android's own modes; a long press is the quick switch (or, without the extra, Silent access).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SoundProfileButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var profile by remember { mutableStateOf(SoundProfileControl.current(context)) }
    // The profile also changes from the volume keys and Quick Settings, so follow the system.
    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context,
                    intent: Intent,
                ) {
                    profile = SoundProfileControl.current(context)
                }
            }
        profile = SoundProfileControl.current(context)
        runCatching { context.registerReceiver(receiver, IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION)) }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    Text(
        text = profile.symbol,
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
        modifier =
            modifier
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .semantics { contentDescription = profile.label }
                .combinedClickable(
                    onClick = {
                        // With the Sound Profiles extra on, the icon opens it; otherwise it uses Android's own modes.
                        if (!SoundProfileControl.openProfiles(context)) profile = SoundProfileControl.cycle(context)
                    },
                    onLongClick = {
                        if (SoundProfileControl.profilesEnabled(context)) {
                            profile = SoundProfileControl.cycle(context)
                        } else {
                            SoundProfileControl.openSilentAccess(context)
                        }
                    },
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
