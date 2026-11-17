package io.github.aimindseye.tenseven.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.aimindseye.tenseven.R
import io.github.aimindseye.tenseven.conversations.ConversationHub
import io.github.aimindseye.tenseven.conversations.HubConversation
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.model.HubPeekGesture
import io.github.aimindseye.tenseven.platform.CapabilityStatus
import io.github.aimindseye.tenseven.platform.StartStateRepository

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
    val open = rememberUpdatedState(peekOpen)
    val change = rememberUpdatedState(onPeekChange)
    val edge = with(LocalDensity.current) { HubPeekGesture.EDGE_DP.dp.toPx() }
    val exclusionHeight = with(LocalDensity.current) { HubPeekGesture.EXCLUSION_DP.dp.toPx() }
    // Compose translates this local rectangle to the view, preserving other gesture exclusions.
    val exclusion =
        if (edgeEnabled && !peekOpen) {
            Modifier.systemGestureExclusion { coordinates ->
                val height = minOf(exclusionHeight, coordinates.size.height.toFloat())
                val top = (coordinates.size.height - height) / 2f
                Rect(0f, top, edge, top + height)
            }
        } else {
            Modifier
        }
    BackHandler(enabled = peekOpen) { onPeekChange(false) }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag("hub-peek-host")
                .then(exclusion)
                .peekEdgeInput(edgeEnabled, edge, open, change)
                .dismissPeekOnEscape(open, change),
    ) {
        content()
        if (peekOpen) {
            HubContrastTheme {
                HubPeekPanel(onClose = { onPeekChange(false) }, onOpenHub = {
                    onPeekChange(false)
                    onOpenHub()
                })
            }
        }
    }
}

/** Keep consuming the initiating drag when opening Peek recomposes the overlay. */
private fun Modifier.peekEdgeInput(
    enabled: Boolean,
    edge: Float,
    open: State<Boolean>,
    change: State<(Boolean) -> Unit>,
): Modifier =
    pointerInput(enabled, edge) {
        if (!enabled) return@pointerInput
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (open.value || !HubPeekGesture.startsAtEdge(down.position.x, edge)) return@awaitEachGesture
            var opened = false
            var dragging = true
            while (dragging) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pointer = event.changes.firstOrNull { it.id == down.id }
                dragging = pointer?.pressed == true
                if (pointer != null && dragging) {
                    val delta = pointer.position - down.position
                    if (!opened && HubPeekGesture.opens(delta.x, delta.y, slop)) {
                        opened = true
                        change.value(true)
                    }
                    if (opened) {
                        pointer.consume()
                    } else if (HubPeekGesture.cancels(delta.x, delta.y, slop)) {
                        dragging = false
                    }
                }
            }
        }
    }

private fun Modifier.dismissPeekOnEscape(
    open: State<Boolean>,
    change: State<(Boolean) -> Unit>,
): Modifier =
    onPreviewKeyEvent { event ->
        if (open.value && event.key == Key.Escape) {
            if (event.type == KeyEventType.KeyDown) change.value(false)
            true
        } else {
            false
        }
    }

@Composable
private fun rememberPeekFocus(): FocusRequester {
    val focus = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current.inputMode
    LaunchedEffect(inputMode) {
        if (inputMode == InputMode.Keyboard) runCatching { focus.requestFocus() }
    }
    return focus
}

private data class PeekPanelActions(
    val onClose: () -> Unit,
    val onOpenHub: () -> Unit,
)

// Keep Peek's content hierarchy independent of animation and scrim state.
@Composable
private fun PeekPanelContents(
    conversations: List<HubConversation>,
    showText: Boolean,
    accent: Color,
    focus: FocusRequester,
    actions: PeekPanelActions,
) {
    val onClose = actions.onClose
    val onOpenHub = actions.onOpenHub
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.peek_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                val total = conversations.sumOf { it.notifications.size }
                if (total > 0) {
                    Surface(shape = RoundedCornerShape(PEEK_BADGE_CORNER_PERCENT), color = accent) {
                        Text(
                            stringResource(R.string.peek_count, total),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                PeekConversations(conversations, showText, onClose)
            }
            PeekButton(stringResource(R.string.peek_open_hub), onOpenHub, Modifier.focusRequester(focus))
        }
    }
}

/** Peek inherits the same user-selected Launcher/Phone appearance as Hub. */
@Composable
private fun HubPeekPanel(
    onClose: () -> Unit,
    onOpenHub: () -> Unit,
) {
    val context = LocalContext.current
    val showText = remember { StartStateRepository(context).loadHubPeekText() }
    val capabilities = rememberPlatformCapabilities()
    val deviceLocked = capabilities.deviceLocked
    val revision = rememberHubRevision()
    val conversations =
        remember(revision, capabilities) {
            if (capabilities.hubNotifications == CapabilityStatus.Ready && !deviceLocked) {
                ConversationHub.conversations().take(MAX_ROWS)
            } else {
                emptyList()
            }
        }
    val focus = rememberPeekFocus()
    // The panel slides in from the left edge while the rest of Home dims.
    val slide = remember { Animatable(0f) }
    LaunchedEffect(Unit) { slide.animateTo(1f, tween(durationMillis = 220, easing = FastOutSlowInEasing)) }
    val accent = MaterialTheme.colorScheme.primary
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f * slide.value))
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
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = 8.dp)
                    .fillMaxWidth(0.88f)
                    .testTag("hub-peek-panel")
                    .widthIn(max = 360.dp)
                    .heightIn(max = maxHeight * 0.92f)
                    .graphicsLayer { translationX = (slide.value - 1f) * size.width }
                    // Taps inside the panel belong to it, not to the scrim that closes the peek.
                    .pointerInput(Unit) { detectTapGestures { } },
            shape = RoundedCornerShape(2.dp),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            PeekPanelContents(
                conversations = conversations,
                showText = showText && !deviceLocked,
                accent = accent,
                focus = focus,
                actions = PeekPanelActions(onClose, onOpenHub),
            )
        }
    }
}

@Composable
private fun PeekRow(
    name: String,
    detail: String,
    count: Int,
    packageName: String?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(0.dp),
        color = if (focused) accent.copy(alpha = 0.16f) else Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PeekRowSourceIcon(packageName)
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail.isNotBlank()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (count > 1) {
                Surface(shape = RoundedCornerShape(PEEK_BADGE_CORNER_PERCENT), color = accent) {
                    Text(
                        count.toString(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

// Icon pack and system icon selection are shared by every Peek row.
@Composable
private fun PeekRowSourceIcon(packageName: String?) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    io.github.aimindseye.tenseven.iconpacks.IconPresentationState
        .init(context)
    val glyph =
        if (io.github.aimindseye.tenseven.iconpacks.IconPresentationState.selected ==
            io.github.aimindseye.tenseven.model.IconPresentation.TenSeven
        ) {
            packageName?.let(io.github.aimindseye.tenseven.model.AppGlyphs::forPackage)
        } else {
            null
        }
    val icon =
        remember(packageName) {
            packageName?.let {
                runCatching {
                    packageManager
                        .getApplicationIcon(
                            it,
                        ).toBitmap(PEEK_SOURCE_ICON_PX, PEEK_SOURCE_ICON_PX)
                        .asImageBitmap()
                }.getOrNull()
            }
        }
    if (glyph != null) {
        SableGlyphIcon(glyph = glyph, modifier = Modifier.size(34.dp))
    } else if (icon != null) {
        Image(
            icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
        )
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
        modifier = modifier.heightIn(min = 48.dp).sableFocusRing(),
        shape = RoundedCornerShape(3.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
            )
            Text("  \u203A", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private const val MAX_ROWS = 8

// How opaque the peek panel is; the rest lets Home show through.

// Compose lint requires PascalCase for Unit renderers; detekt does not recognize that contract.
@Suppress("FunctionNaming")
@Composable
private fun PeekConversations(
    conversations: List<HubConversation>,
    showText: Boolean,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    if (conversations.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("\u2713", style = MaterialTheme.typography.headlineMedium, color = accent)
            Text(
                stringResource(R.string.peek_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
            count = if (showText) conversation.notifications.size else 0,
            packageName = conversation.sourcePackages.firstOrNull(),
        ) {
            ConversationHub.open(context, conversation.openTarget)
            onClose()
        }
    }
}

private const val PEEK_SOURCE_ICON_PX = 120

private const val PEEK_BADGE_CORNER_PERCENT = 50
