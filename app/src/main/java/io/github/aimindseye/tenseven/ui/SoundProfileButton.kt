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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.model.RingerIcon
import io.github.aimindseye.tenseven.model.RingerProfile
import io.github.aimindseye.tenseven.platform.SoundProfileControl

/** Native quick sound control; an installed independent companion supplies full preset management. */
@Composable
private fun rememberSoundProfile(): RingerProfile {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var profile by remember(context) { mutableStateOf(SoundProfileControl.current(context)) }
    DisposableEffect(context, lifecycle) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context,
                    intent: Intent,
                ) {
                    profile = SoundProfileControl.current(context)
                }
            }
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) profile = SoundProfileControl.current(context)
            }
        lifecycle.addObserver(observer)
        runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        onDispose {
            lifecycle.removeObserver(observer)
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
    return profile
}

/** Tap opens an installed companion or cycles native modes. Hold switches quickly or opens Silent access. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SoundProfileButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val profile = rememberSoundProfile()
    LineIcon(
        paths =
            when (profile.icon) {
                RingerIcon.Sound -> LineIconPaths.SOUND
                RingerIcon.Vibrate -> LineIconPaths.VIBRATE
                RingerIcon.Silent -> LineIconPaths.SILENT
            },
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .semantics {
                    contentDescription = "Sound Profiles: ${profile.label}"
                    stateDescription = profile.label
                }.sableFocusRing()
                .combinedClickable(
                    onClickLabel = "Open installed Sound Profiles or switch sound mode",
                    onLongClickLabel = "Quick sound switch or Silent access",
                    onClick = {
                        if (!SoundProfileControl.openProfiles(context)) SoundProfileControl.cycle(context)
                    },
                    onLongClick = {
                        if (SoundProfileControl.profilesEnabled(context)) {
                            SoundProfileControl.cycle(context)
                        } else {
                            SoundProfileControl.openSilentAccess(context)
                        }
                    },
                ).padding(horizontal = 10.dp, vertical = 6.dp)
                .size(22.dp),
    )
}
