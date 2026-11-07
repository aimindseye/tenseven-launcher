package io.github.aimindseye.tenseven.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.aimindseye.tenseven.OnboardingActivity
import io.github.aimindseye.tenseven.R
import io.github.aimindseye.tenseven.conversations.ConversationAccess
import io.github.aimindseye.tenseven.conversations.ConversationFilter
import io.github.aimindseye.tenseven.conversations.ConversationHub
import io.github.aimindseye.tenseven.conversations.ConversationKind
import io.github.aimindseye.tenseven.conversations.ConversationState
import io.github.aimindseye.tenseven.conversations.ConversationView
import io.github.aimindseye.tenseven.conversations.HubConversation
import io.github.aimindseye.tenseven.conversations.Snooze
import io.github.aimindseye.tenseven.conversations.conversationPreview
import io.github.aimindseye.tenseven.conversations.openHubSource
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.model.HubBar
import io.github.aimindseye.tenseven.model.HubFilter
import io.github.aimindseye.tenseven.model.HubStreamSelection
import io.github.aimindseye.tenseven.model.TablerGlyphPaths
import io.github.aimindseye.tenseven.onboarding.SetupStep
import io.github.aimindseye.tenseven.platform.Capabilities
import io.github.aimindseye.tenseven.platform.CapabilityStatus
import io.github.aimindseye.tenseven.platform.StartStateRepository
import io.github.aimindseye.tenseven.tasks.ReminderPolicy
import io.github.aimindseye.tenseven.tasks.TaskRecord
import io.github.aimindseye.tenseven.tasks.TaskRepository
import kotlinx.coroutines.delay

/** What the Hub page needs to show conversations; built by [HubPage] from Tenseven's own notification listener. */
internal class HubConversationsUi(
    val accessGranted: Boolean,
    /** False only when the listener is unavailable on the installed device. */
    val canAllowAccess: Boolean = true,
    val enabledAppCount: Int,
    val onChooseApps: () -> Unit,
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
    val onRemind: (HubConversation, Long?) -> Unit,
    val onCustomRemind: (HubConversation) -> Unit,
    val reminders: List<TaskRecord>,
    val remindersSelected: Boolean,
    val onShowReminders: () -> Unit,
)

private const val MAX_CONVERSATIONS = 30

/**
 * The conversations the Hub groups per person, newest first, with Reply on each that the app lets us reply to.
 * Without notification access it offers to turn it on; the text stays hidden while the device is locked.
 */
internal fun LazyListScope.hubConversationItems(ui: HubConversationsUi) {
    if (ui.remindersSelected) {
        item(key = "hub-reminders") { HubRemindersPanel(ui.reminders) }
        return
    }
    if (!ui.accessGranted) {
        // Avoid offering an impossible permission when the listener service is unavailable.
        if (!ui.canAllowAccess) {
            item(key = "hub-unavailable-summary") {
                Text(
                    "Notification listener unavailable on this device; Hub shortcuts remain available.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            return
        }
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
            if (ui.enabledAppCount == 0) {
                ConversationCard(
                    title = "No apps selected for Hub",
                    detail = "Choose which apps may contribute notifications and conversations",
                    onClick = ui.onChooseApps,
                )
            } else {
                Text(
                    text = stringResource(R.string.hub_conversations_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
        return
    }
    val visible =
        ConversationView
            .apply(ui.conversations, ui.filter, ui.state, ui.now)
            .let { HubStreamSelection.apply(it, ui.sourcePackage) { row -> row.sourcePackages } }
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
        ConversationRow(conversation, ui)
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

/**
 * All ten Hub actions share one row; narrow windows scroll without reducing touch targets.
 * Message/mail counts roll up; utility shortcuts remain visible together without per-app filter chips.
 */
@Composable
internal fun HubUnifiedNavigation(
    ui: HubConversationsUi,
    currentTab: HubFilter,
    onTabChange: (HubFilter) -> Unit,
    firstTab: FocusRequester,
    messageCount: Int,
    emailCount: Int,
    onOpenCalendar: () -> Unit,
) {
    var searchOpen by remember { mutableStateOf(false) }

    fun select(
        tab: HubFilter,
        next: ConversationFilter = ConversationFilter(),
    ) {
        onTabChange(tab)
        ui.onFilterChange(next)
        ui.onSourceChange(null)
    }
    BackHandler(enabled = ui.remindersSelected || ui.filter.snoozed) { select(HubFilter.All) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().testTag("hub-shortcuts").horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconHubFilterButton(
                "Hub",
                "folder",
                currentTab == HubFilter.All && !ui.filter.active && ui.sourcePackage == null && !ui.remindersSelected,
                modifier = Modifier.focusRequester(firstTab),
            ) { select(HubFilter.All) }
            IconHubFilterButton("Messages", "message-dots", currentTab == HubFilter.Messages, count = messageCount) {
                select(HubFilter.Messages, ConversationFilter(kind = ConversationKind.Messages))
            }
            IconHubFilterButton(
                "Email",
                "mail",
                currentTab == HubFilter.All && ui.filter.kind == ConversationKind.Email,
                count = emailCount,
            ) {
                select(HubFilter.All, ConversationFilter(kind = ConversationKind.Email))
            }
            IconHubFilterButton("Unread", "list-check", currentTab == HubFilter.All && ui.filter.unreadOnly && !ui.remindersSelected) {
                select(HubFilter.All, ConversationFilter(unreadOnly = true))
            }
            IconHubFilterButton("Search", "search", searchOpen) { searchOpen = !searchOpen }
            IconHubFilterButton("Calls", "phone-call", currentTab == HubFilter.Calls) { select(HubFilter.Calls) }
            IconHubFilterButton("Calendar", "calendar", false, onClick = onOpenCalendar)
            val snoozedCount = ConversationView.snoozedCount(ui.conversations, ui.state, ui.now)
            IconHubFilterButton("Snoozed", "clock", currentTab == HubFilter.All && ui.filter.snoozed, count = snoozedCount) {
                select(HubFilter.All, ConversationFilter(snoozed = true))
            }
            IconHubFilterButton(
                "Remind",
                "list-check",
                ui.remindersSelected,
                count = ReminderPolicy.pendingCount(ui.reminders),
            ) {
                onTabChange(HubFilter.All)
                ui.onShowReminders()
            }
            IconHubFilterButton("Contacts", "address-book", currentTab == HubFilter.Contacts) { select(HubFilter.Contacts) }
        }
        if (searchOpen && currentTab != HubFilter.Calls && currentTab != HubFilter.Contacts) {
            OutlinedTextField(
                value = ui.filter.person,
                onValueChange = { ui.onFilterChange(ui.filter.copy(person = it)) },
                modifier = Modifier.fillMaxWidth().sableFocusRing(),
                placeholder = { Text(stringResource(R.string.hub_filter_person_hint)) },
                singleLine = true,
            )
        }
    }
}

/** One keyboard-accessible icon with an optional unread total, not an always-enabled app chip. */
@Composable
private fun IconHubFilterButton(
    label: String,
    iconName: String,
    selected: Boolean,
    count: Int = 0,
    showLabel: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val color = MaterialTheme.colorScheme.onSurface
    Column(
        modifier =
            modifier
                .border(
                    if (selected) 1.dp else 0.dp,
                    if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                ).sableFocusRing()
                .clickable(onClick = onClick)
                .semantics {
                    contentDescription =
                        if (showLabel) {
                            "$label, $count items"
                        } else if (count > 0) {
                            "$label, $count notifications"
                        } else {
                            label
                        }
                    stateDescription = if (selected) "Selected" else "Not selected"
                }.widthIn(min = if (showLabel) 88.dp else 48.dp)
                .heightIn(min = if (showLabel) 60.dp else 48.dp)
                .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            LineIcon(
                paths =
                    if (iconName == "search") {
                        LineIconPaths.SEARCH
                    } else {
                        TablerGlyphPaths.BY_NAME[iconName].orEmpty()
                    },
                modifier = Modifier.size(22.dp),
                tint = color,
            )
            if (count > 0 || showLabel) {
                Text(
                    if (count > HubBar.MAX_COUNT) "99+" else count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    maxLines = 1,
                )
            }
        }
        if (showLabel) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: HubConversation,
    ui: HubConversationsUi,
) {
    val locked = ui.deviceLocked
    val unread = ui.state.unreadCount(conversation)
    val selectRow = LocalHubRowSelection.current
    val rowFocus = remember(conversation.id) { FocusRequester() }
    val preview = conversationPreview(conversation, locked, you = stringResource(R.string.hub_you))
    val latestAt = conversation.latestMessage?.timestamp ?: conversation.latestNotification.postedAt
    val whenText = DateUtils.getRelativeTimeSpanString(latestAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    val source = conversation.latestNotification.appName
    // Display installed source icons, not a letter avatar: several providers share initials.
    val sourcePackage = conversation.sourcePackages.firstOrNull()
    val context = LocalContext.current
    val sourceIcon =
        remember(sourcePackage) {
            sourcePackage?.let { pkg ->
                runCatching {
                    context.packageManager
                        .getApplicationIcon(pkg)
                        .toBitmap(NOTIFICATION_ICON_BITMAP_SIZE, NOTIFICATION_ICON_BITMAP_SIZE)
                        .asImageBitmap()
                }.getOrNull()
            }
        }

    HubContrastTheme {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            val plain = !event.nativeKeyEvent.isAltPressed && !event.nativeKeyEvent.isCtrlPressed
                            when {
                                !plain -> {
                                    false
                                }

                                event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_M && unread > 0 -> {
                                    ui.onMarkRead(conversation)
                                    true
                                }

                                event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_S -> {
                                    if (ui.filter.snoozed) {
                                        ui.onWake(conversation)
                                    } else {
                                        ui.onSnooze(conversation, System.currentTimeMillis() + Snooze.ONE_HOUR_MS)
                                    }
                                    true
                                }

                                else -> {
                                    false
                                }
                            }
                        }.focusRequester(rowFocus)
                        .sableFocusRing()
                        .clickable { selectRow(conversation.id, rowFocus) }
                        .padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (sourceIcon != null) {
                    Image(
                        bitmap = sourceIcon,
                        contentDescription = source,
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(7.dp)),
                    )
                } else {
                    Text(
                        text = source.take(1).uppercase(),
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = conversation.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            whenText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        if (unread > 0) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                unread.toString(),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    Text(
                        preview ?: stringResource(R.string.hub_locked_preview),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
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
    onChooseApps: () -> Unit,
): HubConversationsUi {
    val capabilities = rememberPlatformCapabilities(refreshGeneration)
    val revision = rememberHubRevision()
    val accessGranted = capabilities.hubNotifications == CapabilityStatus.Ready
    val locked = capabilities.deviceLocked
    remember(context) { ConversationHub.ensureStateLoaded(context) }
    val conversations =
        remember(refreshGeneration, revision, accessGranted) {
            if (accessGranted) ConversationHub.conversations() else emptyList()
        }
    var filter by remember { mutableStateOf(ConversationFilter()) }
    var remindersSelected by remember { mutableStateOf(false) }
    val taskRevision = TaskRepository.revision
    val reminders =
        remember(taskRevision) {
            ReminderPolicy.reminders(
                io.github.aimindseye.tenseven.platform
                    .taskRepository(context)
                    .list(),
            )
        }
    var sourcePackage by remember { mutableStateOf<String?>(null) }
    val enabledAppCount = remember(refreshGeneration) { StartStateRepository(context).loadHubEnabledApps().size }
    val state = remember(refreshGeneration, revision, accessGranted) { ConversationHub.state() }
    val now = rememberHubTime(state, conversations)
    return remember(
        capabilities,
        accessGranted,
        locked,
        conversations,
        filter,
        sourcePackage,
        enabledAppCount,
        state,
        now,
        remindersSelected,
        reminders,
    ) {
        HubConversationsUi(
            accessGranted = accessGranted,
            canAllowAccess = capabilities.hubNotifications != CapabilityStatus.Unavailable,
            enabledAppCount = enabledAppCount,
            onChooseApps = onChooseApps,
            deviceLocked = locked,
            conversations = conversations,
            filter = filter,
            sourcePackage = sourcePackage,
            onSourceChange = {
                sourcePackage = it
                remindersSelected = false
            },
            state = state,
            now = now,
            onFilterChange = {
                filter = it
                remindersSelected = false
            },
            reminders = reminders,
            remindersSelected = remindersSelected,
            onShowReminders = {
                remindersSelected = true
                filter = ConversationFilter()
                sourcePackage = null
            },
            onMarkRead = { ConversationHub.markRead(context, it) },
            onSnooze = { conversation, until -> ConversationHub.snooze(context, conversation, until) },
            onWake = { ConversationHub.wake(context, it) },
            onRemind = { conversation, due ->
                val n = conversation.latestNotification
                io.github.aimindseye.tenseven.platform
                    .taskRepository(context)
                    .followUp(n.packageName, n.key, n.appName, due)
                android.widget.Toast
                    .makeText(context, "Saved in TenSeven Remind", android.widget.Toast.LENGTH_SHORT)
                    .show()
            },
            onCustomRemind = { conversation ->
                chooseHubReminderTime(context) { due ->
                    val n = conversation.latestNotification
                    io.github.aimindseye.tenseven.platform
                        .taskRepository(context)
                        .followUp(n.packageName, n.key, n.appName, due)
                    android.widget.Toast
                        .makeText(context, "Saved in TenSeven Remind", android.widget.Toast.LENGTH_SHORT)
                        .show()
                }
            },
            onOpen = { conversation ->
                openHubSource(context, conversation, onOpenFallback)
            },
            onReply = { conversation, text -> ConversationHub.reply(context, conversation, text) },
            onAllowAccess = {
                runCatching {
                    context.startActivity(
                        android.content
                            .Intent(context, OnboardingActivity::class.java)
                            .putExtra(OnboardingActivity.EXTRA_STEP, SetupStep.NOTIFICATIONS.name),
                    )
                }
            },
        )
    }
}

private const val NOTIFICATION_ICON_BITMAP_SIZE = 96

/** Advances snooze eligibility only while the Hub is started. */
@Composable
private fun rememberHubTime(
    state: ConversationState,
    conversations: List<HubConversation>,
): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, state, conversations) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                val nextWake =
                    conversations
                        .mapNotNull { state.snoozedUntilOf(it.id) }
                        .filter { it > now }
                        .minOrNull()
                delay(nextWake?.let { (it - now).coerceIn(1L, 60_000L) } ?: 60_000L)
            }
        }
    }
    return now
}
