package io.github.aimindseye.tenseven.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
    val sourcePackage: String?,
    val onSourceChange: (String?) -> Unit,
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
    val visible =
        ConversationView.apply(ui.conversations, ui.filter, ui.state, ui.now)
            .filter { ui.sourcePackage == null || ui.sourcePackage in it.sourcePackages }
    item(key = "conversations-filter") {
        HubStreamToolbar(
            filter = ui.filter,
            sourcePackage = ui.sourcePackage,
            sources =
                ui.conversations.flatMap { it.notifications }
                    .distinctBy { it.packageName }
                    .sortedBy { it.appName.lowercase() }
                    .map { it.packageName to it.appName },
            snoozedCount = ConversationView.snoozedCount(ui.conversations, ui.state, ui.now),
            onChange = ui.onFilterChange,
            onSourceChange = ui.onSourceChange,
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
private fun HubStreamToolbar(
    filter: ConversationFilter,
    sourcePackage: String?,
    sources: List<Pair<String, String>>,
    snoozedCount: Int,
    onChange: (ConversationFilter) -> Unit,
    onSourceChange: (String?) -> Unit,
) {
    var showSearch by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // One horizontally scrollable filter/source line; no nested filter cards or second tab bar.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterButton(stringResource(R.string.hub_filter_all),
                sourcePackage == null && !filter.unreadOnly && filter.kind == ConversationKind.All && !filter.snoozed) {
                onSourceChange(null)
                onChange(filter.copy(unreadOnly = false, kind = ConversationKind.All, snoozed = false, person = ""))
            }
            FilterButton(stringResource(R.string.hub_filter_unread), filter.unreadOnly) {
                onChange(filter.copy(unreadOnly = !filter.unreadOnly, snoozed = false))
            }
            FilterButton(stringResource(R.string.hub_filter_email), filter.kind == ConversationKind.Email) {
                onChange(filter.copy(kind = if (filter.kind == ConversationKind.Email) ConversationKind.All else ConversationKind.Email))
            }
            if (snoozedCount > 0 || filter.snoozed) {
                FilterButton(stringResource(R.string.hub_filter_snoozed, snoozedCount), filter.snoozed) {
                    onChange(filter.copy(snoozed = !filter.snoozed))
                }
            }
            sources.forEach { (pkg, name) ->
                FilterButton(name, sourcePackage == pkg) {
                    onSourceChange(if (sourcePackage == pkg) null else pkg)
                }
            }
            FilterButton("⌕", showSearch) { showSearch = !showSearch }
        }
        if (showSearch) {
            OutlinedTextField(
                value = filter.person,
                onValueChange = { onChange(filter.copy(person = it)) },
                modifier = Modifier.fillMaxWidth().sableFocusRing(),
                placeholder = { Text(stringResource(R.string.hub_filter_person_hint)) },
                singleLine = true,
            )
        }
    }
}

@Composable
private fun FilterButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Text(
        text = label,
        modifier =
            Modifier
                .clip(shape)
                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .sableFocusRing()
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 7.dp),
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
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
    var expanded by remember(conversation.id) { mutableStateOf(false) }
    var replying by remember(conversation.id) { mutableStateOf(false) }
    var draft by remember(conversation.id) { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    val preview = conversationPreview(conversation, locked, you = stringResource(R.string.hub_you))
    val canReply = !locked && conversation.replyTarget != null
    val latestAt = conversation.latestMessage?.timestamp ?: conversation.latestNotification.postedAt
    val whenText = DateUtils.getRelativeTimeSpanString(latestAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    val source = conversation.latestNotification.appName

    fun send() {
        if (draft.isBlank()) return
        failed = !onReply(draft)
        if (!failed) { draft = ""; replying = false }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val plain = !event.nativeKeyEvent.isAltPressed && !event.nativeKeyEvent.isCtrlPressed
                        when {
                            !plain -> false
                            event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_M && unread > 0 -> {
                                onMarkRead(); true
                            }
                            event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_S -> {
                                if (snoozed) onWake() else onSnooze(System.currentTimeMillis() + Snooze.ONE_HOUR_MS)
                                true
                            }
                            else -> false
                        }
                    }
                    .sableFocusRing()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Flat source mark: visual distinction without surrounding every notification in a card.
            Text(
                text = source.take(1).uppercase(),
                modifier =
                    Modifier.clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = conversation.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(whenText, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    if (unread > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text(unread.toString(), color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(preview ?: stringResource(R.string.hub_locked_preview),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (expanded) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(start = 40.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedButton(onClick = onOpen, modifier = Modifier.sableFocusRing()) {
                    Text("Open")
                }
                if (canReply) {
                    OutlinedButton(onClick = { replying = !replying }, modifier = Modifier.sableFocusRing()) {
                        Text(stringResource(R.string.hub_reply))
                    }
                }
                if (unread > 0) {
                    OutlinedButton(onClick = onMarkRead, modifier = Modifier.sableFocusRing()) {
                        Text(stringResource(R.string.hub_mark_read))
                    }
                }
                OutlinedButton(
                    onClick = {
                        if (snoozed) onWake() else onSnooze(System.currentTimeMillis() + Snooze.ONE_HOUR_MS)
                    },
                    modifier = Modifier.sableFocusRing(),
                ) { Text(if (snoozed) stringResource(R.string.hub_wake) else stringResource(R.string.hub_snooze_hour)) }
            }
            if (replying && canReply) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = draft, onValueChange = { draft = it; failed = false },
                        modifier = Modifier.weight(1f).sableFocusRing(),
                        singleLine = true, isError = failed,
                        placeholder = { Text(stringResource(R.string.hub_reply_hint)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                    )
                    OutlinedButton(onClick = ::send, enabled = draft.isNotBlank(),
                        modifier = Modifier.sableFocusRing()) {
                        Text(stringResource(R.string.hub_send))
                    }
                }
                if (failed) Text(stringResource(R.string.hub_reply_failed), color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
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
    var sourcePackage by remember { mutableStateOf<String?>(null) }
    val state = remember(refreshGeneration, accessGranted) { ConversationHub.state() }
    val now = remember(refreshGeneration, filter) { System.currentTimeMillis() }
    return remember(accessGranted, locked, conversations, filter, sourcePackage, state, now) {
        HubConversationsUi(
            accessGranted = accessGranted,
            canAllowAccess = Capabilities.hasConversationService(context),
            deviceLocked = locked,
            conversations = conversations,
            filter = filter,
            sourcePackage = sourcePackage,
            onSourceChange = { sourcePackage = it },
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
