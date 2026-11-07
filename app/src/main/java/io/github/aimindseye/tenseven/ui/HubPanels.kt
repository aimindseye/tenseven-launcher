package io.github.aimindseye.tenseven.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.R
import io.github.aimindseye.tenseven.conversations.ConversationView
import io.github.aimindseye.tenseven.conversations.HubConversation
import io.github.aimindseye.tenseven.conversations.HubSourceAction
import io.github.aimindseye.tenseven.conversations.Snooze
import io.github.aimindseye.tenseven.conversations.availableSourceActions
import io.github.aimindseye.tenseven.conversations.performHubSourceAction
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.platform.StartStateRepository
import kotlin.math.abs

internal val LocalHubRowSelection = staticCompositionLocalOf<(String, FocusRequester) -> Unit> { { _, _ -> } }

/** Owns the one selected row and both side panels, independent of notification read state. */
@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
internal fun HubPanelsHost(
    ui: HubConversationsUi,
    onAllSettings: () -> Unit,
    content: @Composable (() -> Unit) -> Unit,
) {
    var settings by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    var returnFocus by remember { mutableStateOf<FocusRequester?>(null) }
    val visible =
        ConversationView
            .apply(ui.conversations, ui.filter, ui.state, ui.now)
            .filter { ui.sourcePackage == null || ui.sourcePackage in it.sourcePackages }
    val conversation = visible.firstOrNull { it.id == selected }

    fun close() {
        settings = false
        selected = null
        returnFocus?.let { runCatching { it.requestFocus() } }
    }
    LaunchedEffect(ui.deviceLocked, conversation, ui.remindersSelected) {
        if (ui.deviceLocked || conversation == null || ui.remindersSelected) selected = null
    }
    BackHandler(settings || selected != null) { close() }
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).hubSettingsSwipe {
            settings = true
            selected = null
        },
    ) {
        CompositionLocalProvider(
            LocalHubRowSelection provides { id, focus ->
                selected = id
                returnFocus = focus
            },
        ) {
            content {
                settings = true
                selected = null
            }
        }
        val panelOpen = settings.or(conversation != null)
        if (panelOpen) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable { close() })
            Column(
                Modifier
                    .align(if (settings) Alignment.CenterStart else Alignment.CenterEnd)
                    .widthIn(max = 340.dp)
                    .fillMaxWidth(HUB_PANEL_FRACTION)
                    .fillMaxHeight()
                    .hubPanelDismiss(settings) { close() }
                    .background(MaterialTheme.colorScheme.background)
                    .testTag(if (settings) "hub-settings-panel" else "hub-message-panel")
                    .semantics {
                        paneTitle =
                            hubPaneTitle(
                                settings,
                                conversation?.latestNotification?.isEmail == true,
                            )
                    }.verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HubPanelBody(settings, conversation, ui, onAllSettings) { close() }
            }
        }
    }
}

/** Claim horizontal intent only in the left 48dp; leave vertical scrolling and inner calendar gestures alone. */
private fun Modifier.hubSettingsSwipe(onOpen: () -> Unit): Modifier =
    pointerInput(onOpen) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.position.x > 48.dp.toPx()) return@awaitEachGesture
            var claimed = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                if (!claimed && abs(dy) > viewConfiguration.touchSlop) return@awaitEachGesture
                if (!claimed && dx > viewConfiguration.touchSlop) {
                    claimed = true
                    onOpen()
                }
                if (claimed) change.consume()
            } while (change.pressed)
        }
    }

@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
private fun HubSettingsPanel(onAllSettings: () -> Unit) {
    val context = LocalContext.current
    val state = remember(context) { StartStateRepository(context) }
    var text by remember { mutableStateOf(state.loadHubPeekText()) }
    var calendar by remember { mutableStateOf(state.loadHubCalendarPeek()) }
    Text(
        "Hub settings",
        Modifier.semantics { heading() },
        color = Bb10Text,
        style = MaterialTheme.typography.titleLarge,
    )
    PanelToggle("Show message previews", text) {
        text = it
        state.saveHubPeekText(it)
    }
    PanelToggle("Upcoming calendar", calendar) {
        calendar = it
        state.saveHubCalendarPeek(it)
    }
    PanelAction("Notification access and apps", onClick = onAllSettings)
    PanelAction("Calendars and permissions", onClick = onAllSettings)
}

@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
private fun PanelToggle(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Bb10Text)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
private fun HubMessagePanel(
    conversation: HubConversation,
    ui: HubConversationsUi,
    onClose: () -> Unit,
) {
    var remind by remember(conversation.id) { mutableStateOf(false) }
    Text(
        io.github.aimindseye.tenseven.model.HubActionPolicy
            .panelTitle(conversation.latestNotification.isEmail),
        Modifier.semantics { heading() },
        color = Bb10Text,
        style = MaterialTheme.typography.titleLarge,
    )
    Text(conversation.latestNotification.appName, color = Bb10Muted)
    PanelAction(
        io.github.aimindseye.tenseven.model.HubActionPolicy
            .openLabel(conversation.latestNotification.isEmail),
    ) {
        ui.onOpen(conversation)
        onClose()
    }
    HubMessageReplyActions(conversation, ui, onClose)
    if (ui.state.unreadCount(conversation) > 0) {
        PanelAction("Mark read") {
            ui.onMarkRead(conversation)
            onClose()
        }
    }
    PanelAction(if (ui.filter.snoozed) "Show now" else "Snooze 1 hour") {
        if (ui.filter.snoozed) {
            ui.onWake(conversation)
        } else {
            ui.onSnooze(conversation, System.currentTimeMillis() + Snooze.ONE_HOUR_MS)
        }
        onClose()
    }
    PanelAction("Remind") { remind = !remind }
    if (remind) {
        PanelAction("Tomorrow morning") {
            ui.onRemind(
                conversation,
                io.github.aimindseye.tenseven.tasks.TaskSchedule.preset(
                    "Tomorrow morning",
                    java.time.Instant.now(),
                    java.time.ZoneId.systemDefault(),
                ),
            )
            onClose()
        }
        PanelAction("Until complete") {
            ui.onRemind(conversation, null)
            onClose()
        }
        PanelAction("Choose date and time…") {
            ui.onCustomRemind(conversation)
            onClose()
        }
    }
}

@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
private fun HubPanelReply(label: String = "Reply", onSend: (String) -> Boolean) {
    var draft by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = draft,
        onValueChange = {
            draft = it
            failed = false
        },
        label = { Text(label) },
        placeholder = { Text(stringResource(R.string.hub_reply_hint)) },
        modifier = Modifier.fillMaxWidth(),
        isError = failed,
    )
    PanelAction(stringResource(R.string.hub_send)) {
        if (draft.isNotBlank()) {
            failed = !onSend(draft)
            if (!failed) draft = ""
        }
    }
    if (failed) Text(stringResource(R.string.hub_reply_failed), color = MaterialTheme.colorScheme.error)
}

@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
private fun PanelAction(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Text(
        label,
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .sableFocusRing()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        color = Bb10Text,
        style = MaterialTheme.typography.bodyLarge,
    )
}

private const val HUB_PANEL_FRACTION = 0.78f

@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
private fun HubPanelBody(
    settings: Boolean,
    conversation: HubConversation?,
    ui: HubConversationsUi,
    onAllSettings: () -> Unit,
    onClose: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(settings, conversation?.id) {
        withFrameNanos { }
        first.requestFocus()
    }
    PanelAction("Back", Modifier.focusRequester(first)) { onClose() }
    if (settings) {
        HubSettingsPanel(onAllSettings)
    } else {
        conversation?.let { HubMessagePanel(it, ui) { onClose() } }
    }
}

/** Return swipe complements Back without claiming vertical panel scrolling. */
private fun Modifier.hubPanelDismiss(
    settings: Boolean,
    onClose: () -> Unit,
): Modifier =
    pointerInput(settings, onClose) {
        var distance = 0f
        val threshold = 64.dp.toPx()
        detectHorizontalDragGestures(
            onDragStart = { distance = 0f },
            onDragEnd = {
                val closing = if (settings) distance < -threshold else distance > threshold
                if (closing) onClose()
            },
        ) { _, amount -> distance += amount }
    }

/** Capabilities come from the latest selected source, not older notifications grouped with the same person. */
@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
private fun HubMessageReplyActions(conversation: HubConversation, ui: HubConversationsUi, onClose: () -> Unit) {
    val source = conversation.latestNotification
    val context = LocalContext.current
    var selected by remember(source) { mutableStateOf<HubSourceAction?>(null) }
    var primary by remember(source) { mutableStateOf(false) }
    var failed by remember(source) { mutableStateOf(false) }
    if (ui.deviceLocked) return
    val primaryLabel =
        source.replyAction
            ?.title
            ?.toString()
            ?.takeIf(String::isNotBlank) ?: "Reply"
    if (source.canReply) {
        PanelAction(primaryLabel) {
            primary = !primary
            selected = null
        }
    }
    source.availableSourceActions(false).forEach { action ->
        PanelAction(action.label) {
            primary = false
            if (action.acceptsText) {
                selected = action
            } else {
                failed = !performHubSourceAction(context, action)
                if (!failed) onClose()
            }
        }
    }
    if (primary) {
        androidx.compose.runtime.key(source.key, primaryLabel) {
            HubPanelReply(primaryLabel) { ui.onReply(conversation, it) }
        }
    }
    selected?.let { action ->
        androidx.compose.runtime.key(action) {
            HubPanelReply(action.label) { text ->
                val sent = performHubSourceAction(context, action, text)
                if (sent) onClose()
                sent
            }
        }
    }
    if (failed) {
        Text("This action is no longer available. Open the source app.", color = MaterialTheme.colorScheme.error)
    }
}

private fun hubPaneTitle(
    settings: Boolean,
    email: Boolean,
): String =
    if (settings) {
        "Hub settings"
    } else {
        io.github.aimindseye.tenseven.model.HubActionPolicy
            .panelTitle(email)
    }
