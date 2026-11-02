package io.github.aimindseye.tenseven.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.OnboardingActivity
import io.github.aimindseye.tenseven.R
import io.github.aimindseye.tenseven.conversations.ConversationAccess
import io.github.aimindseye.tenseven.platform.Capabilities
import io.github.aimindseye.tenseven.conversations.ConversationFilter
import io.github.aimindseye.tenseven.conversations.ConversationKind
import io.github.aimindseye.tenseven.conversations.ConversationState
import io.github.aimindseye.tenseven.conversations.ConversationView
import io.github.aimindseye.tenseven.conversations.Snooze
import io.github.aimindseye.tenseven.conversations.ConversationHub
import io.github.aimindseye.tenseven.conversations.HubConversation
import io.github.aimindseye.tenseven.conversations.conversationPreview
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.onboarding.SetupStep

/** What the Hub page needs to show conversations; built by [HubPage] from Tenseven's own notification listener. */
internal class HubConversationsUi(
    val accessGranted: Boolean,
    /** False in the lite build: there is no notification listener to ask for. */
    val canAllowAccess: Boolean = true,
    val deviceLocked: Boolean,
    val conversations: List<HubConversation>,
    val filter: ConversationFilter,
    val state: ConversationState,
    val now: Long,
    val onOpen: (HubConversation) -> Unit,
    val onReply: (HubConversation, String) -> Boolean,
    val onAllowAccess: () -> Unit,
    val onFilterChange: (ConversationFilter) -> Unit,
    val onMarkRead: (HubConversation) -> Unit,
    val onSnooze: (HubConversation, Long) -> Unit,
    val onWake: (HubConversation) -> Unit,
)

private const val MAX_CONVERSATIONS = 30

/**
 * The conversations the Hub groups per person, newest first, with Reply on each that the app lets us reply to.
 * Without notification access it offers to turn it on; the text stays hidden while the device is locked.
 */
internal fun LazyListScope.hubConversationItems(ui: HubConversationsUi) {
    if (!ui.accessGranted) {
        // The lite build has no notification listener to allow, so it shows no conversations section at all.
        if (!ui.canAllowAccess) return
        item(key = "conversations-allow") {
            ConversationCard(
                title = stringResource(R.string.hub_allow_title),
                detail = stringResource(R.string.hub_allow_detail),
                onClick = ui.onAllowAccess,
            )
        }
        return
    }
    if (ui.conversations.isEmpty()) {
        item(key = "conversations-empty") {
            Text(
                text = stringResource(R.string.hub_conversations_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        return
    }
    val visible = ConversationView.apply(ui.conversations, ui.filter, ui.state, ui.now)
    item(key = "conversations-filter") {
        FilterBar(
            filter = ui.filter,
            snoozedCount = ConversationView.snoozedCount(ui.conversations, ui.state, ui.now),
            onChange = ui.onFilterChange,
        )
    }
    if (visible.isEmpty()) {
        item(key = "conversations-nomatch") {
            Text(
                text = stringResource(R.string.hub_no_match),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        return
    }
    conversationItems(visible.take(MAX_CONVERSATIONS), key = { "conversation:" + it.id }) { conversation ->
        ConversationRow(
            conversation = conversation,
            locked = ui.deviceLocked,
            unread = ui.state.unreadCount(conversation),
            snoozed = ui.filter.snoozed,
            onOpen = { ui.onOpen(conversation) },
            onReply = { text -> ui.onReply(conversation, text) },
            onMarkRead = { ui.onMarkRead(conversation) },
            onSnooze = { until -> ui.onSnooze(conversation, until) },
            onWake = { ui.onWake(conversation) },
        )
    }
}

private fun LazyListScope.conversationItems(
    list: List<HubConversation>,
    key: (HubConversation) -> Any,
    content: @Composable (HubConversation) -> Unit,
) {
    list.forEach { conversation -> item(key = key(conversation)) { content(conversation) } }
}

@Composable
private fun ConversationCard(
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .sableFocusRing()
                .clickable(onClick = onClick)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FilterBar(
    filter: ConversationFilter,
    snoozedCount: Int,
    onChange: (ConversationFilter) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            FilterButton(stringResource(R.string.hub_filter_all), !filter.unreadOnly && filter.kind == ConversationKind.All && !filter.snoozed) {
                onChange(filter.copy(unreadOnly = false, kind = ConversationKind.All, snoozed = false))
            }
            FilterButton(stringResource(R.string.hub_filter_unread), filter.unreadOnly) {
                onChange(filter.copy(unreadOnly = !filter.unreadOnly, snoozed = false))
            }
            FilterButton(stringResource(R.string.hub_filter_messages), filter.kind == ConversationKind.Messages) {
                onChange(filter.copy(kind = if (filter.kind == ConversationKind.Messages) ConversationKind.All else ConversationKind.Messages))
            }
            FilterButton(stringResource(R.string.hub_filter_email), filter.kind == ConversationKind.Email) {
                onChange(filter.copy(kind = if (filter.kind == ConversationKind.Email) ConversationKind.All else ConversationKind.Email))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = filter.person,
                onValueChange = { onChange(filter.copy(person = it)) },
                modifier = Modifier.weight(1f).sableFocusRing(),
                placeholder = { Text(stringResource(R.string.hub_filter_person_hint)) },
                singleLine = true,
            )
            if (snoozedCount > 0 || filter.snoozed) {
                FilterButton(stringResource(R.string.hub_filter_snoozed, snoozedCount), filter.snoozed) {
                    onChange(filter.copy(snoozed = !filter.snoozed))
                }
            }
        }
    }
}

@Composable
private fun FilterButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, modifier = Modifier.sableFocusRing()) {
        Text(if (selected) "✓ $label" else label, maxLines = 1)
    }
}

@Composable
private fun ConversationRow(
    conversation: HubConversation,
    locked: Boolean,
    unread: Int,
    snoozed: Boolean,
    onOpen: () -> Unit,
    onReply: (String) -> Boolean,
    onMarkRead: () -> Unit,
    onSnooze: (Long) -> Unit,
    onWake: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    var replying by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    val preview = conversationPreview(conversation, locked, you = stringResource(R.string.hub_you))
    val canReply = !locked && conversation.replyTarget != null
    val latestAt = conversation.latestMessage?.timestamp ?: conversation.latestNotification.postedAt
    val whenText = DateUtils.getRelativeTimeSpanString(latestAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    val apps = conversation.notifications.map { it.appName }.distinct().joinToString(", ")

    fun send() {
        if (draft.isBlank()) return
        failed = !onReply(draft)
        if (!failed) {
            draft = ""
            replying = false
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    // M marks read, S snoozes for an hour, on the focused conversation.
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val plain = !event.nativeKeyEvent.isAltPressed && !event.nativeKeyEvent.isCtrlPressed
                        when {
                            !plain -> false
                            event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_M && unread > 0 -> {
                                onMarkRead()
                                true
                            }
                            event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_S -> {
                                if (snoozed) onWake() else onSnooze(System.currentTimeMillis() + Snooze.ONE_HOUR_MS)
                                true
                            }
                            else -> false
                        }
                    }.sableFocusRing()
                    .clickable(onClick = onOpen)
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                if (unread > 0) conversation.name + "  ·  " + stringResource(R.string.hub_unread_count, unread) else conversation.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = preview ?: stringResource(R.string.hub_locked_preview),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text("$apps · $whenText", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!replying) {
                if (canReply) {
                    OutlinedButton(onClick = { replying = true }, modifier = Modifier.sableFocusRing()) {
                        Text(stringResource(R.string.hub_reply))
                    }
                }
                if (unread > 0) {
                    OutlinedButton(onClick = onMarkRead, modifier = Modifier.sableFocusRing()) {
                        Text(stringResource(R.string.hub_mark_read))
                    }
                }
                if (snoozed) {
                    OutlinedButton(onClick = onWake, modifier = Modifier.sableFocusRing()) {
                        Text(stringResource(R.string.hub_wake))
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSnooze(System.currentTimeMillis() + Snooze.ONE_HOUR_MS) },
                        modifier = Modifier.sableFocusRing(),
                    ) {
                        Text(stringResource(R.string.hub_snooze_hour))
                    }
                    OutlinedButton(
                        onClick = { onSnooze(Snooze.nextMorning(System.currentTimeMillis())) },
                        modifier = Modifier.sableFocusRing(),
                    ) {
                        Text(stringResource(R.string.hub_snooze_tomorrow))
                    }
                }
            } else {
                if (canReply) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = {
                            draft = it
                            failed = false
                        },
                        modifier = Modifier.weight(1f).sableFocusRing(),
                        placeholder = { Text(stringResource(R.string.hub_reply_hint)) },
                        singleLine = true,
                        isError = failed,
                        supportingText = { if (failed) Text(stringResource(R.string.hub_reply_failed)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                    )
                    OutlinedButton(
                        onClick = ::send,
                        enabled = draft.isNotBlank(),
                        modifier = Modifier.padding(start = 8.dp).sableFocusRing(),
                    ) {
                        Text(stringResource(R.string.hub_send))
                    }
                }
            }
        }
    }
}

/**
 * The conversation list for the Hub page. [refreshGeneration] changes whenever the listener posts or removes a
 * notification (SableStartActivity bumps it), which is when this is read again.
 */
@Composable
internal fun rememberHubConversationsUi(
    context: android.content.Context,
    refreshGeneration: Int,
    onOpenFallback: () -> Unit,
): HubConversationsUi {
    val accessGranted = remember(refreshGeneration) {
        Capabilities.hasConversationService(context) && ConversationAccess.isGranted(context)
    }
    val locked =
        remember(refreshGeneration) {
            context.getSystemService(android.app.KeyguardManager::class.java)?.isDeviceLocked == true
        }
    remember(context) { ConversationHub.ensureStateLoaded(context) }
    val conversations =
        remember(refreshGeneration, accessGranted) {
            if (accessGranted) ConversationHub.conversations() else emptyList()
        }
    var filter by remember { mutableStateOf(ConversationFilter()) }
    val state = remember(refreshGeneration, accessGranted) { ConversationHub.state() }
    val now = remember(refreshGeneration, filter) { System.currentTimeMillis() }
    return remember(accessGranted, locked, conversations, filter, state, now) {
        HubConversationsUi(
            accessGranted = accessGranted,
            canAllowAccess = Capabilities.hasConversationService(context),
            deviceLocked = locked,
            conversations = conversations,
            filter = filter,
            state = state,
            now = now,
            onFilterChange = { filter = it },
            onMarkRead = { ConversationHub.markRead(context, it) },
            onSnooze = { conversation, until -> ConversationHub.snooze(context, conversation, until) },
            onWake = { ConversationHub.wake(context, it) },
            onOpen = { conversation ->
                if (!ConversationHub.open(context, conversation.openTarget)) onOpenFallback()
            },
            onReply = { conversation, text -> ConversationHub.reply(context, conversation, text) },
            onAllowAccess = {
                runCatching {
                    context.startActivity(
                        android.content.Intent(context, OnboardingActivity::class.java)
                            .putExtra(OnboardingActivity.EXTRA_STEP, SetupStep.NOTIFICATIONS.name),
                    )
                }
            },
        )
    }
}
