package io.github.aimindseye.tenseven.ui

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.aimindseye.tenseven.conversations.ConversationHub
import io.github.aimindseye.tenseven.conversations.HubNotification
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.model.LockLayer
import io.github.aimindseye.tenseven.platform.CapabilityStatus
import io.github.aimindseye.tenseven.platform.LockLayerState
import kotlinx.coroutines.delay

/** Per-source discovery does not open, dismiss or mark any notification read. */
@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
internal fun LockNotificationScreen(
    enabled: Set<String>,
    showText: Boolean,
    selectedPackage: String?,
    actions: LockScreenActions,
) {
    val content = rememberLockContent(enabled, showText, selectedPackage)
    LaunchedEffect(content.source, selectedPackage) {
        if (content.source != selectedPackage) actions.select(content.source)
    }
    BackHandler(content.source != null) { actions.select(null) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Bb10Background)
            .lockUnlockSwipe(actions.unlock)
            .safeDrawingPadding()
            .padding(12.dp),
    ) {
        LockClock()
        LockNotificationBody(content, actions, Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            LockFooterAction(if (content.source == null) "Unlock" else "Collapse") {
                if (content.source == null) actions.unlock() else actions.select(null)
            }
            if (content.source != null) LockFooterAction("Unlock", actions.unlock)
            HomeBarControl("Camera", "camera", onClick = actions.camera)
        }
    }
}

private data class LockContent(
    val source: String?,
    val entries: List<io.github.aimindseye.tenseven.model.LockEntry>,
    val details: List<HubNotification>,
    val locked: Boolean,
    val optedIn: Boolean,
)

internal class LockScreenActions(
    val select: (String?) -> Unit,
    val unlock: () -> Unit,
    val reveal: () -> Unit,
    val openApp: (String) -> Unit,
    val camera: () -> Unit,
)

@Composable
private fun rememberLockContent(
    enabled: Set<String>,
    showText: Boolean,
    selected: String?,
): LockContent {
    val capabilities = rememberPlatformCapabilities()
    val revision = rememberHubRevision()
    val counts = if (capabilities.hubNotifications == CapabilityStatus.Ready) LockLayerState.counts else emptyMap()
    val entries = LockLayer.entries(counts, counts.keys - enabled)
    val source = selected?.takeIf { pkg -> entries.any { it.packageName == pkg } }
    val details =
        remember(revision, source, capabilities.deviceLocked, showText) {
            if (source == null || capabilities.deviceLocked || !showText) {
                emptyList()
            } else {
                ConversationHub.notifications().filter { it.packageName == source }.sortedByDescending { it.postedAt }
            }
        }
    return LockContent(source, entries, details, capabilities.deviceLocked, showText)
}

@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
private fun LockNotificationBody(content: LockContent, actions: LockScreenActions, modifier: Modifier) {
    var marker by remember { mutableStateOf(INITIAL_MARKER_PX) }
    val railScroll = rememberScrollState()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(64.dp).fillMaxHeight().verticalScroll(railScroll)) {
            content.entries.forEach { entry ->
                LockSourceIcon(
                    entry.packageName,
                    entry.count,
                    content.source == entry.packageName,
                    Modifier.onGloballyPositioned {
                        if (content.source == entry.packageName) {
                            marker = it.positionInParent().y + it.size.height / 2f - railScroll.value
                        }
                    },
                ) { actions.select(entry.packageName) }
            }
        }
        val blue = Bb10Blue
        val notch = with(LocalDensity.current) { 8.dp.toPx() }
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag("lock-source-details")
                .drawBehind {
                    if (content.source != null) {
                        val y = marker.coerceIn(notch, (size.height - notch).coerceAtLeast(notch))
                        val bracket =
                            Path().apply {
                                moveTo(0f, 0f)
                                lineTo(0f, y - notch)
                                lineTo(-notch, y)
                                lineTo(0f, y + notch)
                                lineTo(0f, size.height)
                            }
                        drawPath(bracket, blue, style = Stroke(2.dp.toPx()))
                    }
                }.padding(start = 12.dp)
                .verticalScroll(rememberScrollState()),
        ) { LockSourceDetails(content, actions) }
    }
}

@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
private fun LockFooterAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        Modifier
            .heightIn(min = 48.dp)
            .sableFocusRing()
            .clickable(onClick = onClick)
            .padding(12.dp),
        color = Bb10Text,
    )
}

@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
private fun LockClock() {
    val context = LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(CLOCK_INTERVAL_MS - now % CLOCK_INTERVAL_MS)
        }
    }
    Column(Modifier.padding(start = 76.dp, bottom = 16.dp)) {
        Text(
            DateFormat.getTimeFormat(context).format(now),
            style = MaterialTheme.typography.displayLarge,
            color = Bb10Text,
            fontWeight = FontWeight.Light,
        )
        Text(
            DateFormat.format("EEE, MMM d", now).toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = Bb10Muted,
        )
    }
}

@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
private fun LockSourceIcon(
    packageName: String,
    count: Int,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val icon =
        remember(packageName) {
            runCatching {
                context.packageManager
                    .getApplicationIcon(packageName)
                    .toBitmap(LOCK_ICON_PX, LOCK_ICON_PX)
                    .asImageBitmap()
            }.getOrNull()
        }
    val name =
        remember(packageName) {
            runCatching {
                context.packageManager
                    .getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0))
                    .toString()
            }.getOrDefault(packageName)
        }
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .border(
                if (isSelected) 1.dp else 0.dp,
                if (isSelected) Bb10Blue else androidx.compose.ui.graphics.Color.Transparent,
            ).sableFocusRing()
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "$name, $count notifications"
                selected = isSelected
            }.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) Image(icon, null, Modifier.size(28.dp)) else Text(name.take(1), color = Bb10Text)
        Text(
            LockLayer.countLabel(count),
            color = if (isSelected) Bb10Blue else Bb10Text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
private fun LockSourceDetails(
    content: LockContent,
    actions: LockScreenActions,
) {
    when {
        content.source == null -> {
            Text("Select a notification icon", color = Bb10Muted, style = MaterialTheme.typography.bodyMedium)
        }

        content.locked -> {
            Text("Content hidden", color = Bb10Text, style = MaterialTheme.typography.titleMedium)
            Text("Unlock to view this app’s notifications.", color = Bb10Muted)
            Text(
                "Unlock to view",
                Modifier
                    .heightIn(min = 48.dp)
                    .sableFocusRing()
                    .clickable(onClick = actions.reveal)
                    .padding(vertical = 12.dp),
                color = Bb10Blue,
            )
        }

        !content.optedIn -> {
            Text("Message previews are off in Hub settings.", color = Bb10Muted)
        }

        else -> {
            if (content.details.isEmpty()) Text("No message preview available for this app.", color = Bb10Muted)
            content.details.forEach { notification ->
                Text(notification.conversationName, color = Bb10Text, style = MaterialTheme.typography.titleMedium)
                Text(
                    notification.messages
                        .lastOrNull { !it.isHistoric }
                        ?.text
                        .orEmpty(),
                    color = Bb10Text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
            Text(
                "Open app",
                Modifier
                    .heightIn(min = 48.dp)
                    .sableFocusRing()
                    .clickable {
                        content.source?.let(actions.openApp)
                    }.padding(vertical = 12.dp),
                color = Bb10Blue,
            )
        }
    }
}

private const val INITIAL_MARKER_PX = 24f
private const val CLOCK_INTERVAL_MS = 60_000L
private const val LOCK_ICON_PX = 96

/** An unused upward drag asks Android to unlock; scrolling notification lists consumes its own drags first. */
private fun Modifier.lockUnlockSwipe(onUnlock: () -> Unit): Modifier =
    pointerInput(onUnlock) {
        var distance = 0f
        val threshold = 64.dp.toPx()
        detectVerticalDragGestures(
            onDragStart = { distance = 0f },
            onDragEnd = { if (distance < -threshold) onUnlock() },
        ) { _, amount -> distance += amount }
    }
