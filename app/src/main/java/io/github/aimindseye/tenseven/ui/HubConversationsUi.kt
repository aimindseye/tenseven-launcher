package io.github.aimindseye.tenseven.ui

import android.text.format.DateUtils
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
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
import io.github.aimindseye.tenseven.model.TablerGlyphPaths
import io.github.aimindseye.tenseven.model.HubStreamSelection
import io.github.aimindseye.tenseven.model.HubFilter
import io.github.aimindseye.tenseven.platform.StartStateRepository
import io.github.aimindseye.tenseven.conversations.conversationPreview
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.onboarding.SetupStep

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
)

private const val MAX_CONVERSATIONS = 30

/**
 * The conversations the Hub groups per person, newest first, with Reply on each that the app lets us reply to.
 * Without notification access it offers to turn it on; the text stays hidden while the device is locked.
 */
internal fun LazyListScope.hubConversationItems(ui: HubConversationsUi) {
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
        ConversationView.apply(ui.conversations, ui.filter, ui.state, ui.now)
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
            onRemind = { whenAt -> ui.onRemind(conversation, whenAt) },
            onCustomRemind = { ui.onCustomRemind(conversation) },
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

/**
 * The six-icon Hub navigation is stable on a square Q25, even when dozens of apps are enabled.
 * Message/mail counts roll up; only other apps with live notifications appear under More.
 */
@Composable
internal fun HubUnifiedNavigation(
    ui: HubConversationsUi,
    currentTab: HubFilter,
    onTabChange: (HubFilter) -> Unit,
    firstTab: FocusRequester,
    sources: List<Triple<String, String, Int>>,
    messageCount: Int,
    emailCount: Int,
) {
    var searchOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    fun select(tab: HubFilter, next: ConversationFilter = ConversationFilter(), pkg: String? = null) {
        onTabChange(tab)
        ui.onFilterChange(next)
        ui.onSourceChange(pkg)
        moreOpen = false
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconHubFilterButton(
                "Hub", "folder",
                currentTab == HubFilter.All && !ui.filter.active && ui.sourcePackage == null,
                modifier = Modifier.focusRequester(firstTab),
            ) { select(HubFilter.All) }
            IconHubFilterButton("Messages", "message-dots", currentTab == HubFilter.Messages,
                count = messageCount) {
                select(HubFilter.Messages, ConversationFilter(kind = ConversationKind.Messages))
            }
            IconHubFilterButton("Email", "mail",
                currentTab == HubFilter.All && ui.filter.kind == ConversationKind.Email,
                count = emailCount) {
                select(HubFilter.All, ConversationFilter(kind = ConversationKind.Email))
            }
            IconHubFilterButton("Unread", "list-check", currentTab == HubFilter.All && ui.filter.unreadOnly) {
                select(HubFilter.All, ConversationFilter(unreadOnly = true))
            }
            IconHubFilterButton("Search", "search", searchOpen) { searchOpen = !searchOpen }
            IconHubFilterButton("More categories and active apps", "apps", moreOpen) { moreOpen = !moreOpen }
        }
        if (moreOpen) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconHubFilterButton("Calls", "phone-call", currentTab == HubFilter.Calls) {
                    select(HubFilter.Calls)
                }
                IconHubFilterButton("Contacts", "address-book", currentTab == HubFilter.Contacts) {
                    select(HubFilter.Contacts)
                }
                IconHubFilterButton("Remind", "clock", false, showLabel = true) {
                    context.startActivity(android.content.Intent(context,
                        io.github.aimindseye.tenseven.tasks.TasksActivity::class.java)
                        .putExtra(io.github.aimindseye.tenseven.tasks.TasksActivity.EXTRA_MODE, "remind"))
                }
                val snoozedCount = ConversationView.snoozedCount(ui.conversations, ui.state, ui.now)
                if (snoozedCount > 0 || ui.filter.snoozed) {
                    IconHubFilterButton("Snoozed", "clock",
                        currentTab == HubFilter.All && ui.filter.snoozed, count = snoozedCount, showLabel = true) {
                        select(HubFilter.All, ConversationFilter(snoozed = true))
                    }
                }
                sources.forEach { (pkg, name, count) ->
                    AppSourceFilterButton(pkg, name, count,
                        currentTab == HubFilter.All && ui.sourcePackage == pkg) {
                        select(HubFilter.All, pkg = pkg)
                    }
                }
            }
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
    val color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier.clip(RoundedCornerShape(7.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .sableFocusRing()
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (count > 0) "$label, $count notifications" else label
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LineIcon(
                paths = if (iconName == "search") LineIconPaths.SEARCH
                    else TablerGlyphPaths.BY_NAME[iconName].orEmpty(),
                modifier = Modifier.size(if (showLabel) 18.dp else 22.dp),
                tint = color,
            )
            if (showLabel) Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
        if (count > 0) {
            Text(
                text = if (count > 99) "99+" else count.toString(),
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 2.dp, top = 1.dp),
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
    }
}

/** Actual source icon disambiguates clients with similar names, including multiple mail apps. */
@Composable
private fun AppSourceFilterButton(
    packageName: String,
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val appIcon = remember(packageName) {
        runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(72, 72).asImageBitmap() }.getOrNull()
    }
    Row(
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .sableFocusRing().clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (appIcon != null) Image(appIcon, contentDescription = label,
            modifier = Modifier.size(21.dp).clip(RoundedCornerShape(4.dp)))
        Text("$label · $count", maxLines = 1, style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
    }
}

/** Compact action glyph with explicit screen-reader text and a keyboard focus ring. */
@Composable
private fun HubActionIcon(
    label: String,
    glyph: List<String>,
    showLabel: Boolean = false,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.sableFocusRing().semantics { contentDescription = label },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 7.dp),
    ) {
        LineIcon(paths = glyph, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
        if (showLabel) {
            Spacer(Modifier.width(6.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurface)
        }
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
    onRemind: (Long?) -> Unit,
    onCustomRemind: () -> Unit,
) {
    var expanded by remember(conversation.id) { mutableStateOf(false) }
    var replying by remember(conversation.id) { mutableStateOf(false) }
    var draft by remember(conversation.id) { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var remindMenu by remember(conversation.id) { mutableStateOf(false) }
    val preview = conversationPreview(conversation, locked, you = stringResource(R.string.hub_you))
    val canReply = !locked && conversation.replyTarget != null
    val latestAt = conversation.latestMessage?.timestamp ?: conversation.latestNotification.postedAt
    val whenText = DateUtils.getRelativeTimeSpanString(latestAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    val source = conversation.latestNotification.appName
    // Display installed source icons, not a letter avatar: several providers share initials.
    val sourcePackage = conversation.sourcePackages.firstOrNull()
    val context = LocalContext.current
    val sourceIcon = remember(sourcePackage) {
        sourcePackage?.let { pkg ->
            runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }

    fun send() {
        if (draft.isBlank()) return
        failed = !onReply(draft)
        if (!failed) { draft = ""; replying = false }
    }

    HubContrastTheme {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)),
    ) {
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
            if (sourceIcon != null) {
                Image(
                    bitmap = sourceIcon,
                    contentDescription = source,
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(7.dp)),
                )
            } else {
                Text(
                    text = source.take(1).uppercase(),
                    modifier = Modifier.clip(RoundedCornerShape(4.dp))
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
                HubActionIcon("Open in source app", LineIconPaths.EXTERNAL, onClick = onOpen)
                if (canReply) {
                    HubActionIcon("Reply", LineIconPaths.REPLY) { replying = !replying }
                }
                if (unread > 0) {
                    HubActionIcon("Mark read", LineIconPaths.CHECK, onClick = onMarkRead)
                }
                HubActionIcon("Remind", TablerGlyphPaths.BY_NAME["clock"].orEmpty(), showLabel = true) { remindMenu = true }
                HubActionIcon(
                    if (snoozed) "Show now" else "Snooze 1h",
                    TablerGlyphPaths.BY_NAME["clock"].orEmpty(),
                    showLabel = true,
                ) {
                    if (snoozed) onWake() else onSnooze(System.currentTimeMillis() + Snooze.ONE_HOUR_MS)
                }
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
        if (remindMenu) {
            val zone = java.time.ZoneId.systemDefault()
            AlertDialog(
                onDismissRequest = { remindMenu = false },
                title = { Text("TenSeven Remind") },
                text = { Text("Save a local follow-up. Only app identity is retained; message text is not copied.") },
                confirmButton = {
                    Column {
                        listOf("Later today", "This evening", "Tomorrow morning", "Until complete").forEach { choice ->
                            TextButton(onClick = {
                                onRemind(io.github.aimindseye.tenseven.tasks.TaskSchedule.preset(
                                    choice, java.time.Instant.now(), zone))
                                remindMenu = false
                            }) { Text(choice) }
                        }
                        TextButton(onClick = { remindMenu = false; onCustomRemind() }) { Text("Choose date and time…") }
                    }
                },
                dismissButton = { TextButton(onClick = { remindMenu = false }) { Text("Cancel") } },
            )
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
    val enabledAppCount = remember(refreshGeneration) { StartStateRepository(context).loadHubEnabledApps().size }
    val state = remember(refreshGeneration, accessGranted) { ConversationHub.state() }
    val now = remember(refreshGeneration, filter) { System.currentTimeMillis() }
    return remember(accessGranted, locked, conversations, filter, sourcePackage, enabledAppCount, state, now) {
        HubConversationsUi(
            accessGranted = accessGranted,
            canAllowAccess = Capabilities.hasConversationService(context),
            enabledAppCount = enabledAppCount,
            onChooseApps = onChooseApps,
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
            onRemind = { conversation, due ->
                val n = conversation.latestNotification
                io.github.aimindseye.tenseven.tasks.TaskRepository(context).followUp(n.packageName, n.key, n.appName, due)
                android.widget.Toast.makeText(context, "Saved in TenSeven Remind", android.widget.Toast.LENGTH_SHORT).show()
            },
            onCustomRemind = { conversation ->
                val n = conversation.latestNotification
                context.startActivity(android.content.Intent(context, io.github.aimindseye.tenseven.tasks.TasksActivity::class.java)
                    .putExtra(io.github.aimindseye.tenseven.tasks.TasksActivity.EXTRA_MODE, "remind")
                    .putExtra(io.github.aimindseye.tenseven.tasks.TasksActivity.EXTRA_SOURCE_PACKAGE, n.packageName)
                    .putExtra(io.github.aimindseye.tenseven.tasks.TasksActivity.EXTRA_SOURCE_KEY, n.key)
                    .putExtra(io.github.aimindseye.tenseven.tasks.TasksActivity.EXTRA_SOURCE_APP, n.appName))
            },
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
