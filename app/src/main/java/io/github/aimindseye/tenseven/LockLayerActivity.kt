package io.github.aimindseye.tenseven

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.MediaStore
import android.text.format.DateFormat
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import io.github.aimindseye.tenseven.conversations.ConversationHub
import io.github.aimindseye.tenseven.model.LockLayer
import io.github.aimindseye.tenseven.platform.LockLayerState
import io.github.aimindseye.tenseven.platform.StartStateRepository
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Tenseven lock layer: a BlackBerry-style screen shown over the system lock screen when the screen wakes. It is
 * only a cover. The phone's own lock stays underneath, so swiping up, pressing Enter or Space, or tapping an app asks
 * Android to dismiss the real lock first (PIN, pattern or fingerprint) and only then opens anything. It shows the
 * time, the date, an icon and count for each app with notifications waiting, and a camera shortcut. Message text
 * appears only if the user turned on "Show message text".
 */
class LockLayerActivity : ComponentActivity() {
    private val closer =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) = finish()
        }

    /** Whether this is a preview from a settings screen (it closes itself rather than asking for the PIN). */
    private val isPreview get() = intent?.hasExtra(EXTRA_RETURN_TO) == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
        ContextCompat.registerReceiver(this, closer, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val state = StartStateRepository(this)
        val excluded = state.loadHubExcludedApps()
        val showText = state.loadHubPeekText()
        setContent {
            LockLayerScreen(
                excluded = excluded,
                showText = showText,
                onUnlock = { unlock(null) },
                onOpenApp = { pkg -> unlock(packageManager.getLaunchIntentForPackage(pkg)) },
                onCamera = ::openCamera,
            )
        }
    }

    /** A preview started from Setup or Launcher Settings goes back to that screen, not to Home. */
    override fun finish() {
        val back = intent?.getStringExtra(EXTRA_RETURN_TO)
        if (back != null) {
            intent?.removeExtra(EXTRA_RETURN_TO)
            runCatching {
                startActivity(Intent().setClassName(this, back).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            }
        }
        super.finish()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(closer) }
        super.onDestroy()
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean =
        when (keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_CENTER -> {
                unlock(null)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }

    /** Asks Android to dismiss the real lock screen, then opens [then] (if any) and closes this cover. */
    private fun unlock(then: Intent?) {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (isPreview) {
            finish()
            return
        }
        if (keyguard == null || !keyguard.isKeyguardLocked) {
            then?.let { runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            finish()
            return
        }
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    then?.let { runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                    finish()
                }
            },
        )
    }

    /** The secure camera opens over the lock screen without unlocking; if none answers, unlock and open the normal one. */
    private fun openCamera() {
        val secure = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { startActivity(secure) }.isFailure) {
            unlock(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
        }
    }
}

/** Set on a preview: the class name of the screen to return to when the preview closes. */
const val EXTRA_RETURN_TO = "io.github.aimindseye.tenseven.extra.LOCK_PREVIEW_RETURN_TO"

@Composable
private fun LockLayerScreen(
    excluded: Set<String>,
    showText: Boolean,
    onUnlock: () -> Unit,
    onOpenApp: (String) -> Unit,
    onCamera: () -> Unit,
) {
    val context = LocalContext.current
    val counts = LockLayerState.counts
    val entries = remember(counts, excluded) { LockLayer.entries(counts, excluded) }
    val preview =
        remember(counts, excluded, showText) {
            if (showText) {
                ConversationHub
                    .conversations()
                    .firstOrNull { conversation -> conversation.sourcePackages.none { it in excluded } }
                    ?.let { it.name to it.latestMessage?.text.orEmpty() }
            } else {
                null
            }
        }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
        }
    }
    val is24 = DateFormat.is24HourFormat(context)
    val time = now.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm"))
    val suffix = if (is24) "" else now.format(DateTimeFormatter.ofPattern("a"))
    val date = now.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEMMMd")))
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total < -UNLOCK_SWIPE_PX) onUnlock() },
                        onVerticalDrag = { _, delta -> total += delta },
                    )
                },
    ) {
        Column(modifier = Modifier.statusBarsPadding().padding(horizontal = 28.dp, vertical = 24.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(time, color = Color.White, fontSize = 68.sp, fontWeight = FontWeight.Light)
                if (suffix.isNotEmpty()) {
                    Text(suffix, color = Color.White, fontSize = 22.sp, modifier = Modifier.padding(start = 6.dp, bottom = 12.dp))
                }
            }
            Text(date, color = Color.White.copy(alpha = 0.9f), fontSize = 20.sp)
            if (preview != null) {
                Spacer(Modifier.height(14.dp))
                Text(preview.first, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (preview.second.isNotBlank()) {
                    Text(preview.second, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                entries.forEach { entry -> LockIcon(entry.packageName, LockLayer.countLabel(entry.count)) { onOpenApp(entry.packageName) } }
            }
        }
        Text(
            stringResource(R.string.lock_hint),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp).clickable(onClick = onUnlock),
        )
        Canvas(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 22.dp)
                .size(44.dp)
                .clickable(onClick = onCamera),
        ) {
            val stroke = Stroke(width = 3.dp.toPx())
            drawRoundRect(
                Color.White,
                topLeft = Offset(size.width * 0.1f, size.height * 0.28f),
                size = Size(size.width * 0.8f, size.height * 0.52f),
                cornerRadius = CornerRadius(6.dp.toPx()),
                style = stroke,
            )
            drawCircle(Color.White, radius = size.width * 0.14f, center = Offset(size.width * 0.5f, size.height * 0.54f), style = stroke)
        }
    }
}

@Composable
private fun LockIcon(
    packageName: String,
    label: String,
    onClick: () -> Unit,
) {
    val packageManager = LocalContext.current.packageManager
    val icon = remember(packageName) { runCatching { packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(Modifier.size(40.dp)) {
            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(36.dp).align(Alignment.BottomStart))
            Box(Modifier.align(Alignment.TopEnd).size(12.dp).background(Color(0xFFE53935), CircleShape))
        }
        Text(label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

private const val UNLOCK_SWIPE_PX = 120f
