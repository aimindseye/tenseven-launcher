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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.model.HubFilter
import io.github.aimindseye.tenseven.model.HubStreamSelection
import io.github.aimindseye.tenseven.onboarding.SetupStep
import io.github.aimindseye.tenseven.platform.CapabilityStatus
import io.github.aimindseye.tenseven.platform.PlatformCapabilities
import io.github.aimindseye.tenseven.platform.StartStateRepository
import io.github.aimindseye.tenseven.platform.openHubSource
import io.github.aimindseye.tenseven.tasks.ReminderPolicy
import io.github.aimindseye.tenseven.tasks.TaskRecord
import io.github.aimindseye.tenseven.tasks.TaskRepository
import kotlinx.coroutines.delay

/** What the Hub page needs to show conversations; built by [HubPage] from Tenseven's own notification listener. */
internal class HubConversationsUi(
    val accessGranted: Boolean,
    /** False only when the listener is unavailable on the installed device. */
    val canAllowAccess: Boolean = true,
    /** -1 means automatic sources; zero means an empty manual selection. */
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
internal data class HubNavigationState(
    val currentTab: HubFilter,
    val firstTab: FocusRequester,
    val messageCount: Int,
    val emailCount: Int,
    val searchRequest: Int,
    val closeSearchRequest: Int,
)

@Suppress("UNUSED_PARAMETER")
@Composable
internal fun HubUnifiedNavigation(
    ui: HubConversationsUi,
    navigation: HubNavigationState,
    onTabChange: (HubFilter) -> Unit,
    onOpenCalendar: () -> Unit,
) {
    val currentTab = navigation.currentTab
    val firstTab = navigation.firstTab
    val messageCount = navigation.messageCount
    val emailCount = navigation.emailCount
    val searchRequest = navigation.searchRequest
    val closeSearchRequest = navigation.closeSearchRequest
    var searchOpen by remember { mutableStateOf(false) }
    LaunchedEffect(searchRequest) {
        if (searchRequest > 0) {
            searchOpen = !searchOpen
            if (!searchOpen) ui.onFilterChange(ui.filter.copy(person = ""))
        }
    }
    LaunchedEffect(closeSearchRequest) {
        if (closeSearchRequest > 0) {
            searchOpen = false
            ui.onFilterChange(ui.filter.copy(person = ""))
        }
    }
    BackHandler(enabled = searchOpen) {
        searchOpen = false
        ui.onFilterChange(ui.filter.copy(person = ""))
    }
    val unreadCount = ui.conversations.sumOf { ui.state.unreadCount(it) }
    val snoozedCount = ConversationView.snoozedCount(ui.conversations, ui.state, ui.now)
    val remindCount = ReminderPolicy.pendingCount(ui.reminders)
    val tabs =
        io.github.aimindseye.tenseven.model.HubVisibleTabs.items(
            messages = messageCount,
            email = emailCount,
            unread = unreadCount,
            snoozed = snoozedCount,
            reminders = remindCount,
        )

    BackHandler(enabled = ui.remindersSelected || ui.filter.snoozed) {
        hubNavSelect(HubFilter.All, ui = ui, onTabChange = onTabChange)
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        HubNavigationTabStrip(tabs, currentTab, ui, firstTab, onTabChange)
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

// Tab state/action routing stays separate from the navigation's Compose state.
private fun hubNavSelect(
    tab: HubFilter,
    next: ConversationFilter = ConversationFilter(),
    ui: HubConversationsUi,
    onTabChange: (HubFilter) -> Unit,
) {
    onTabChange(tab)
    ui.onFilterChange(next)
    ui.onSourceChange(null)
}

private fun hubNavSelected(
    name: String,
    currentTab: HubFilter,
    ui: HubConversationsUi,
): Boolean =
    when (name) {
        "All" -> {
            hubAllTabSelected(currentTab, ui)
        }

        "Unread" -> {
            hubUnreadTabSelected(currentTab, ui)
        }

        "Messages" -> {
            currentTab == HubFilter.Messages
        }

        "Email" -> {
            currentTab == HubFilter.All && ui.filter.kind == ConversationKind.Email
        }

        "Snoozed" -> {
            currentTab == HubFilter.All && ui.filter.snoozed
        }

        "Remind" -> {
            ui.remindersSelected
        }

        else -> {
            false
        }
    }

private fun hubAllTabSelected(
    currentTab: HubFilter,
    ui: HubConversationsUi,
): Boolean = currentTab == HubFilter.All && !ui.filter.active && ui.sourcePackage == null && !ui.remindersSelected

private fun hubUnreadTabSelected(
    currentTab: HubFilter,
    ui: HubConversationsUi,
): Boolean = currentTab == HubFilter.All && ui.filter.unreadOnly && !ui.remindersSelected

private fun hubNavActivate(
    name: String,
    ui: HubConversationsUi,
    onTabChange: (HubFilter) -> Unit,
) {
    when (name) {
        "All" -> {
            hubNavSelect(HubFilter.All, ui = ui, onTabChange = onTabChange)
        }

        "Unread" -> {
            hubNavSelect(HubFilter.All, ConversationFilter(unreadOnly = true), ui, onTabChange)
        }

        "Messages" -> {
            hubNavSelect(HubFilter.Messages, ConversationFilter(kind = ConversationKind.Messages), ui, onTabChange)
        }

        "Email" -> {
            hubNavSelect(HubFilter.All, ConversationFilter(kind = ConversationKind.Email), ui, onTabChange)
        }

        "Snoozed" -> {
            hubNavSelect(HubFilter.All, ConversationFilter(snoozed = true), ui, onTabChange)
        }

        "Remind" -> {
            onTabChange(HubFilter.All)
            ui.onShowReminders()
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun HubNavigationTabStrip(
    tabs: List<String>,
    currentTab: HubFilter,
    ui: HubConversationsUi,
    firstTab: FocusRequester,
    onTabChange: (HubFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("hub-shortcuts").horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The approved reference is a flat, readable strip, not an always-populated icon shelf.
        tabs.forEach { name ->
            val active = hubNavSelected(name, currentTab, ui)
            val focus = if (name == "All") Modifier.focusRequester(firstTab) else Modifier
            Column(
                modifier =
                    focus
                        .sableFocusRing()
                        .clickable { hubNavActivate(name, ui, onTabChange) }
                        .padding(horizontal = 3.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                if (active) {
                    Spacer(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.primary))
                }
            }
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
    io.github.aimindseye.tenseven.iconpacks.IconPresentationState
        .init(context)
    val sourceGlyph =
        if (io.github.aimindseye.tenseven.iconpacks.IconPresentationState.selected ==
            io.github.aimindseye.tenseven.model.IconPresentation.TenSeven
        ) {
            sourcePackage?.let(io.github.aimindseye.tenseven.model.AppGlyphs::forPackage)
        } else {
            null
        }
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
                if (sourceGlyph != null) {
                    SableGlyphIcon(glyph = sourceGlyph, modifier = Modifier.size(30.dp))
                } else if (sourceIcon != null) {
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
    val enabledAppCount = remember(refreshGeneration) { hubSourceCount(context) }
    val state = remember(refreshGeneration, revision, accessGranted) { ConversationHub.state() }
    val now = rememberHubTime(state, conversations)
    val selection =
        HubConversationRenderSelection(
            filter = filter,
            sourcePackage = sourcePackage,
            remindersSelected = remindersSelected,
            onSourceChange = {
                sourcePackage = it
                remindersSelected = false
            },
            onFilterChange = {
                filter = it
                remindersSelected = false
            },
            onShowReminders = {
                remindersSelected = true
                filter = ConversationFilter()
                sourcePackage = null
            },
        )
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
        buildHubConversationUi(
            HubConversationRenderSnapshot(capabilities, conversations, enabledAppCount, state, now, reminders),
            selection,
            HubConversationRenderDependencies(context, onOpenFallback, onChooseApps),
        )
    }
}

private class HubConversationRenderSnapshot(
    val capabilities: PlatformCapabilities,
    val conversations: List<HubConversation>,
    val enabledAppCount: Int,
    val state: ConversationState,
    val now: Long,
    val reminders: List<TaskRecord>,
)

private class HubConversationRenderSelection(
    val filter: ConversationFilter,
    val sourcePackage: String?,
    val remindersSelected: Boolean,
    val onSourceChange: (String?) -> Unit,
    val onFilterChange: (ConversationFilter) -> Unit,
    val onShowReminders: () -> Unit,
)

private class HubConversationRenderDependencies(
    val context: android.content.Context,
    val onOpenFallback: () -> Unit,
    val onChooseApps: () -> Unit,
)

private fun saveHubConversationReminder(
    context: android.content.Context,
    conversation: HubConversation,
    due: Long?,
) {
    val n = conversation.latestNotification
    io.github.aimindseye.tenseven.platform
        .taskRepository(context)
        .followUp(n.packageName, n.key, n.appName, due)
    android.widget.Toast
        .makeText(context, "Saved in TenSeven Remind", android.widget.Toast.LENGTH_SHORT)
        .show()
}

private fun buildHubConversationUi(
    snapshot: HubConversationRenderSnapshot,
    selection: HubConversationRenderSelection,
    dependencies: HubConversationRenderDependencies,
): HubConversationsUi =
    HubConversationsUi(
        accessGranted = snapshot.capabilities.hubNotifications == CapabilityStatus.Ready,
        canAllowAccess = snapshot.capabilities.hubNotifications != CapabilityStatus.Unavailable,
        enabledAppCount = snapshot.enabledAppCount,
        onChooseApps = dependencies.onChooseApps,
        deviceLocked = snapshot.capabilities.deviceLocked,
        conversations = snapshot.conversations,
        filter = selection.filter,
        sourcePackage = selection.sourcePackage,
        onSourceChange = selection.onSourceChange,
        state = snapshot.state,
        now = snapshot.now,
        onFilterChange = selection.onFilterChange,
        reminders = snapshot.reminders,
        remindersSelected = selection.remindersSelected,
        onShowReminders = selection.onShowReminders,
        onMarkRead = { ConversationHub.markRead(dependencies.context, it) },
        onSnooze = { conversation, until -> ConversationHub.snooze(dependencies.context, conversation, until) },
        onWake = { ConversationHub.wake(dependencies.context, it) },
        onRemind = { conversation, due -> saveHubConversationReminder(dependencies.context, conversation, due) },
        onCustomRemind = { conversation ->
            chooseHubReminderTime(dependencies.context) { due ->
                saveHubConversationReminder(dependencies.context, conversation, due)
            }
        },
        onOpen = { conversation -> openHubSource(dependencies.context, conversation, dependencies.onOpenFallback) },
        onReply = { conversation, text -> ConversationHub.reply(dependencies.context, conversation, text) },
        onAllowAccess = {
            runCatching {
                dependencies.context.startActivity(
                    android.content
                        .Intent(dependencies.context, OnboardingActivity::class.java)
                        .putExtra(OnboardingActivity.EXTRA_STEP, SetupStep.NOTIFICATIONS.name),
                )
            }
        },
    )

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

private fun hubSourceCount(context: android.content.Context): Int {
    val policy =
        io.github.aimindseye.tenseven.platform
            .HubSourceRepository(context)
            .policy()
    return if (policy.automatic) -1 else policy.selected.size
}
