package io.github.aimindseye.tenseven.ui

import android.graphics.Rect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.aimindseye.tenseven.R
import io.github.aimindseye.tenseven.conversations.ConversationHub
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.platform.StartStateRepository
import kotlin.math.abs

/**
 * The BlackBerry 10 "peek": drag in from the left edge of Home and the Hub's latest conversations slide over the
 * screen without leaving Home. Tapping a conversation opens it in its app; "Open Hub" goes to the full Hub page;
 * Back, Escape or a tap outside closes the peek. Names and counts only, unless "Show message text" is on.
 */
@Composable
internal fun HubPeekHost(
    peekOpen: Boolean,
    edgeEnabled: Boolean,
    onPeekChange: (Boolean) -> Unit,
    onOpenHub: () -> Unit,
    content: @Composable () -> Unit,
) {
    // With gesture navigation Android takes a swipe from the left edge for Back before the app sees it (a small "<"
    // arrow appears). Ask it to leave a strip of the edge to us while the peek can be opened. Android allows at most
    // 200dp of height per edge, so the strip sits halfway down.
    val view = LocalView.current
    val density = LocalDensity.current.density
    var heightPx by remember { mutableIntStateOf(0) }
    DisposableEffect(view, edgeEnabled, peekOpen, heightPx) {
        if (edgeEnabled && !peekOpen && heightPx > 0) {
            val half = (EXCLUSION_DP * density / 2).toInt()
            val mid = heightPx / 2
            view.systemGestureExclusionRects = listOf(Rect(0, mid - half, (EDGE_DP * density).toInt(), mid + half))
        }
        onDispose { view.systemGestureExclusionRects = emptyList() }
    }
    Box(
        modifier =
            Modifier.fillMaxSize().onSizeChanged { heightPx = it.height }.pointerInput(edgeEnabled, peekOpen) {
                if (!edgeEnabled || peekOpen) return@pointerInput
                val edge = EDGE_DP.dp.toPx()
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (down.position.x > edge) return@awaitEachGesture
                    var opened = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val dx = change.position.x - down.position.x
                        if (!opened && dx > slop) {
                            opened = true
                            onPeekChange(true)
                        }
                        // Once the peek opens, keep the rest of this drag from also scrolling the page underneath.
                        if (opened) change.consume() else if (abs(change.position.y - down.position.y) > slop * 2) break
                    }
                }
            },
    ) {
        content()
        if (peekOpen) HubPeekPanel(onClose = { onPeekChange(false) }, onOpenHub = {
            onPeekChange(false)
            onOpenHub()
        })
    }
}

@Composable
private fun HubPeekPanel(
    onClose: () -> Unit,
    onOpenHub: () -> Unit,
) {
    val context = LocalContext.current
    val showText = remember { StartStateRepository(context).loadHubPeekText() }
    val conversations = remember { ConversationHub.conversations().take(MAX_ROWS) }
    val focus = remember { FocusRequester() }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClose)
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                        onClose()
                        true
                    } else {
                        false
                    }
                },
    ) {
        Surface(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(0.84f)
                    .widthIn(max = 340.dp)
                    // Taps inside the panel belong to it, not to the scrim that closes the peek.
                    .pointerInput(Unit) { detectTapGestures { } },
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.peek_title), style = MaterialTheme.typography.titleLarge)
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (conversations.isEmpty()) {
                        Text(
                            stringResource(R.string.peek_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    conversations.forEach { conversation ->
                        PeekRow(
                            name = conversation.name,
                            detail =
                                if (showText) {
                                    conversation.latestMessage?.text.orEmpty()
                                } else {
                                    stringResource(R.string.peek_count, conversation.notifications.size)
                                },
                            packageName = conversation.sourcePackages.firstOrNull(),
                        ) {
                            ConversationHub.open(context, conversation.openTarget)
                            onClose()
                        }
                    }
                }
                PeekButton(stringResource(R.string.peek_open_hub), onOpenHub, Modifier.focusRequester(focus))
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
private fun PeekRow(
    name: String,
    detail: String,
    packageName: String?,
    onClick: () -> Unit,
) {
    val packageManager = LocalContext.current.packageManager
    val icon =
        remember(packageName) {
            packageName?.let { runCatching { packageManager.getApplicationIcon(it).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
        }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().sableFocusRing(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail.isNotBlank()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PeekButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().sableFocusRing(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            label,
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

private const val EDGE_DP = 20

/** Android lets an app exclude at most this much height from the system gestures on one edge. */
private const val EXCLUSION_DP = 200
private const val MAX_ROWS = 8
