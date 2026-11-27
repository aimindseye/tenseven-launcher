package io.github.aimindseye.tenseven.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

private const val NOW_PLAYING_POLL_MS = 1_500L

/**
 * A slim "Now playing" strip for the Home screens: cover, title, artist and Previous, Play or Pause, Next for whatever
 * media session is active (Sable Media, FM Radio, the Reader's audiobook or any other player). It reads the same
 * session as the lock screen card, so it behaves the same for every media app. Tapping the title opens the playing app.
 */
@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
internal fun HomeNowPlayingStrip(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var now by remember { mutableStateOf<LockMediaNow?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            now = readLockMedia(context)
            delay(NOW_PLAYING_POLL_MS)
        }
    }
    val media = now ?: return
    SableMediaCard(media, compact = true, modifier = modifier)
}
