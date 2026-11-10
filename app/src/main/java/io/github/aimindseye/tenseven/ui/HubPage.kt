package io.github.aimindseye.tenseven.ui

import android.Manifest
import android.content.Intent
import android.provider.CallLog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.SableStartActivity
import io.github.aimindseye.tenseven.conversations.ConversationAccess
import io.github.aimindseye.tenseven.conversations.ConversationHub
import io.github.aimindseye.tenseven.live.LiveAvailability
import io.github.aimindseye.tenseven.live.LiveSurfaceSnapshot
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.model.HubBar
import io.github.aimindseye.tenseven.model.HubFilter
import io.github.aimindseye.tenseven.model.HubPeople
import io.github.aimindseye.tenseven.model.HubPerson
import io.github.aimindseye.tenseven.model.HubSourceGroups
import io.github.aimindseye.tenseven.model.StartText.t
import io.github.aimindseye.tenseven.model.inLauncherUser
import io.github.aimindseye.tenseven.platform.CapabilityStatus
import io.github.aimindseye.tenseven.platform.DefaultApps
import io.github.aimindseye.tenseven.platform.PeopleRepository
import io.github.aimindseye.tenseven.platform.StartStateRepository
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas as ComposeCanvas

@Composable
internal fun HubPage(
    snapshot: LiveSurfaceSnapshot,
    apps: List<AppEntry>,
    filter: HubFilter,
    active: Boolean,
    onFilter: (HubFilter) -> Unit,
    onLaunchApp: (AppEntry) -> Unit,
    onOpenHubSettings: () -> Unit,
    onStartIntent: (Intent) -> Unit,
    refreshGeneration: Int,
    onRequestContactsPermission: () -> Unit,
    onReturnHome: () -> Unit = {},
) {
    val context = LocalContext.current
    val capabilities = rememberPlatformCapabilities(refreshGeneration)
    // The phone's own default apps, whichever they are: Tenseven looks for no Sable app here.
    val messagesPackage = remember(context) { DefaultApps.packageFor(context, DefaultApps.MESSAGING) }
    val contactsPackage = remember(context) { DefaultApps.packageFor(context, DefaultApps.CONTACTS) }

    fun appOf(pkg: String?) = pkg?.let { p -> apps.firstOrNull { it.inLauncherUser && it.component.packageName == p } }
    val messagesApp = appOf(messagesPackage)
    val contactsApp = appOf(contactsPackage) ?: findLiveApp(apps, CONTACTS_PACKAGE)
    val enabledHubApps = remember(refreshGeneration) { StartStateRepository(context).loadHubEnabledApps() }
    val sourceGroups = rememberHubSourceGroups(snapshot, enabledHubApps, refreshGeneration)
    val contactsTab = filter == HubFilter.Contacts
    val canReadContacts = capabilities.contacts == CapabilityStatus.Ready
    val (people, peopleLoaded) = rememberHubPeople(contactsTab, canReadContacts, refreshGeneration)
    val tabFocus = remember { FocusRequester() }
    val personFocus = remember { mutableMapOf<Long, FocusRequester>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(active) {
        if (active) {
            withFrameNanos { }
            runCatching { tabFocus.requestFocus() }
        }
    }

    fun requesterFor(person: HubPerson): FocusRequester = personFocus.getOrPut(person.id) { FocusRequester() }

    fun openMessages() {
        val app = messagesApp
        if (app != null) {
            onLaunchApp(app)
        } else {
            onStartIntent(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING))
        }
    }

    val conversationsUi =
        rememberHubConversationsUi(context, refreshGeneration, hubSourceFallback(context), onOpenHubSettings)

    val actions =
        HubActions(
            onLaunchApp = onLaunchApp,
            onStartIntent = onStartIntent,
            onOpenMessages = ::openMessages,
            onOpenHubSettings = onOpenHubSettings,
            onRequestContactsPermission = onRequestContactsPermission,
        )

    /** Contacts tab: a letter (or digit) key jumps to the first person of that letter. */
    fun jumpKey(event: KeyEvent): Boolean {
        val native = event.nativeKeyEvent
        if (!contactsTab || people.isEmpty() || native.metaState and HUB_JUMP_BLOCKING_META != 0) return false
        val char = native.unicodeChar.takeIf { it > 0 }?.toChar() ?: return false
        val index = HubPeople.jump(people, char) ?: return false
        if (event.type == KeyEventType.KeyDown) {
            val person = people[index]
            scope.launch {
                // The header item comes first, so a person's list index is one more than theirs.
                listState.scrollToItem(index + 1)
                withFrameNanos { }
                runCatching { requesterFor(person).requestFocus() }
            }
        }
        return true
    }

    var hubViewsVisible by remember { mutableStateOf(false) }
    var hubSearchRequest by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var hubSearchCloseRequest by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var composePrompt by remember { mutableStateOf(false) }
    HubContrastTheme {
        Column(Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                HubPanelsHost(conversationsUi, onOpenHubSettings, onSwipeLeft = onReturnHome) { openPanel ->
                    val calendarPeekEnabled = StartStateRepository(context).loadHubCalendarPeek()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().onPreviewKeyEvent(::jumpKey),
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        item(key = "header") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                HomeBarControl("Hub settings", "menu-2", onClick = openPanel)
                                Text(
                                    text = HUB_LABEL,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Medium,
                                    // The strip's own text colour (BB10 palette), not the appearance's content colour.
                                    color = Bb10Text,
                                    modifier = Modifier.semantics { heading() },
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            // In the approved layout, today's calendar strip precedes the category tabs.
                            if (filter == HubFilter.All && calendarPeekEnabled && !conversationsUi.remindersSelected) {
                                HubCalendarPeek(
                                    refreshGeneration = refreshGeneration,
                                    onRequestCalendarPermission = {
                                        (context.findActivity() as? SableStartActivity)?.requestCalendarPermission()
                                    },
                                    onOpenCalendar = {
                                        onStartIntent(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
                                    },
                                )
                            }
                            HubUnifiedNavigation(
                                ui = conversationsUi,
                                currentTab = filter,
                                onTabChange = onFilter,
                                firstTab = tabFocus,
                                messageCount = sourceGroups.messages,
                                emailCount = sourceGroups.email,
                                searchRequest = hubSearchRequest,
                                closeSearchRequest = hubSearchCloseRequest,
                                onOpenCalendar = {
                                    onStartIntent(
                                        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR),
                                    )
                                },
                            )
                        }
                        if (contactsTab) {
                            hubContactsItems(
                                people = people,
                                loaded = peopleLoaded,
                                canRead = canReadContacts,
                                contactsApp = contactsApp,
                                messagesPackage = messagesApp?.component?.packageName,
                                requesterFor = ::requesterFor,
                                actions = actions,
                            )
                        } else {
                            hubSummaryItems(
                                filter = filter,
                                actions = actions,
                                conversations = conversationsUi,
                            )
                        }
                    }
                }
            }
            if (hubViewsVisible) {
                HubCompactViews(
                    ui = conversationsUi,
                    onTabChange = onFilter,
                    onCalendar = {
                        onStartIntent(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
                    },
                    onDismiss = { hubViewsVisible = false },
                )
            }
            HubCompactBar(
                onViews = {
                    hubViewsVisible = !hubViewsVisible
                    hubSearchCloseRequest += 1
                },
                onCompose = {
                    when (StartStateRepository(context).loadHubComposeMode()) {
                        io.github.aimindseye.tenseven.model.HubComposeMode.Ask -> {
                            composePrompt = true
                        }

                        io.github.aimindseye.tenseven.model.HubComposeMode.Email -> {
                            onStartIntent(Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:")))
                        }

                        io.github.aimindseye.tenseven.model.HubComposeMode.Messages -> {
                            onStartIntent(Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:")))
                        }
                    }
                },
                onSearch = {
                    onFilter(HubFilter.All)
                    hubViewsVisible = false
                    hubSearchRequest += 1
                    scope.launch { listState.animateScrollToItem(0) }
                },
            )
            if (composePrompt) {
                HubComposeDialog(
                    onEmail = {
                        composePrompt = false
                        onStartIntent(Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:")))
                    },
                    onMessages = {
                        composePrompt = false
                        onStartIntent(Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:")))
                    },
                    onDismiss = { composePrompt = false },
                )
            }
        }
    }
}

/**
 * The Hub page's unread bar: one item per app (its icon and count), most unread first, "+N" in
 * the last slot when they do not all fit; Enter or a tap on "+N" shows every app. While the device
 * is locked or Sable private mode is on, Hub sends one total without app names (a lock mark).
 */
@Composable
internal fun HubUnreadBar(
    items: List<HubBar.Item>,
    appFor: (String) -> AppEntry?,
    onOpen: (HubBar.Item) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val slots = HubBar.slots(maxWidth.value.toInt())
        val layout = HubBar.layout(items, slots)
        val rows = if (expanded) items.chunked(slots) else listOf(layout.visible)
        val overflow = if (expanded) 0 else layout.overflow
        if (items.isEmpty()) {
            Text(
                text = t("no_new_notifications"),
                style = MaterialTheme.typography.labelLarge,
                color = Bb10Muted,
                modifier = Modifier.height(HUB_BAR_ITEM_HEIGHT).padding(vertical = 6.dp),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                rows.forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(HubBar.GAP_DP.dp)) {
                        row.forEach { item ->
                            HubBarItem(
                                item = item,
                                app = appFor(item.packageName),
                                onOpen = { onOpen(item) },
                            )
                        }
                        if (rowIndex == rows.lastIndex && overflow > 0) {
                            HubBarChip(
                                description = t("more_apps_with_notifications", overflow),
                                onClick = { expanded = true },
                            ) {
                                Text(
                                    text = HubBar.overflowLabel(overflow),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = LocalContentColor.current,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HubBarItem(
    item: HubBar.Item,
    app: AppEntry?,
    onOpen: () -> Unit,
) {
    val count = HubBar.countLabel(item.count)
    val description =
        if (item.redacted) {
            t("new_notifications_apps_hidden", count)
        } else {
            t("new_label", app?.label ?: item.packageName, count)
        }
    HubBarChip(description = description, onClick = onOpen) {
        if (app != null) {
            AppMark(app = app, size = HUB_BAR_ICON_DP)
        } else {
            HubLockMark()
        }
        Text(
            text = count,
            style = MaterialTheme.typography.labelLarge,
            color = LocalContentColor.current,
            maxLines = 1,
        )
    }
}

/** One fixed-width, focusable slot of the unread bar; the focused one is BB10 blue. */
@Composable
internal fun HubBarChip(
    description: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier =
            Modifier
                .width(HubBar.ITEM_DP.dp)
                .height(HUB_BAR_ITEM_HEIGHT)
                .onFocusChanged { focused = it.isFocused }
                .clip(shape)
                .background(if (focused) Bb10Blue else Bb10Card)
                .border(1.dp, Bb10Line, shape)
                .clickable(onClick = onClick)
                .semantics { contentDescription = description }
                .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        // 56dp: 4 + 20dp icon + 4 + room for "99+" + 4.
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        CompositionLocalProvider(LocalContentColor provides if (focused) Bb10OnBlue else Bb10Text) {
            content()
        }
    }
}

/** A small padlock: Hub's counts-only total (locked device or Sable private mode). */
@Composable
internal fun HubLockMark() {
    val ink = LocalContentColor.current
    ComposeCanvas(Modifier.size(HUB_BAR_ICON_DP.dp)) {
        val unit = size.minDimension
        val stroke = unit * 0.12f
        drawArc(
            color = ink,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(unit * 0.3f, unit * 0.12f),
            size = Size(unit * 0.4f, unit * 0.44f),
            style = Stroke(stroke),
        )
        drawRoundRect(
            color = ink,
            topLeft = Offset(unit * 0.2f, unit * 0.42f),
            size = Size(unit * 0.6f, unit * 0.46f),
            cornerRadius = CornerRadius(unit * 0.08f),
        )
    }
}

/** What the Hub page's tabs and cards open. */
internal class HubActions(
    val onLaunchApp: (AppEntry) -> Unit,
    val onStartIntent: (Intent) -> Unit,
    val onOpenMessages: () -> Unit,
    val onOpenHubSettings: () -> Unit,
    val onRequestContactsPermission: () -> Unit,
)

/**
 * The All, Messages and Calls tabs: Messages and Mail, Missed calls, and on All the notification
 * summary ([summary] is null when Sable Hub is absent, which also hides Hub settings).
 */
internal fun androidx.compose.foundation.lazy.LazyListScope.hubSummaryItems(
    filter: HubFilter,
    actions: HubActions,
    conversations: HubConversationsUi,
) {
    if (filter != HubFilter.Calls) {
        hubConversationItems(conversations)
    }
    if (filter == HubFilter.Calls) {
        item(key = "calls-shortcut") {
            // Tenseven has no call log permission; the system Phone app owns the full log.
            MetroLink(text = t("missed_calls"), onClick = {
                actions.onStartIntent(Intent(Intent.ACTION_VIEW, CallLog.Calls.CONTENT_URI))
            })
        }
    }
    item(key = "hub-settings-link") {
        Spacer(Modifier.height(8.dp))
        MetroLink(text = t("hub_settings"), onClick = actions.onOpenHubSettings)
    }
}

/**
 * The Contacts tab: favourites, then A to Z, as in the Contacts app. Enter opens the contact,
 * Call and Message act on its default number (Message in Sable Messages); a letter key jumps
 * ([HubPage]). Without READ_CONTACTS the tab offers to allow it, or the Contacts app.
 */
internal fun androidx.compose.foundation.lazy.LazyListScope.hubContactsItems(
    people: List<HubPerson>,
    loaded: Boolean,
    canRead: Boolean,
    contactsApp: AppEntry?,
    messagesPackage: String?,
    requesterFor: (HubPerson) -> FocusRequester,
    actions: HubActions,
) {
    val onStartIntent = actions.onStartIntent
    val openContacts = contactsApp?.let { app -> { actions.onLaunchApp(app) } }
    if (!canRead || people.isEmpty()) {
        item(key = "contacts-empty") {
            if (!canRead) {
                GlanceCard(
                    title = t("allow_contacts"),
                    detail = t("start_shows_names_here_nothing"),
                    accent = SablePurple,
                    onClick = actions.onRequestContactsPermission,
                )
            } else {
                EmptyState(if (loaded) t("no_contacts_yet") else t("loading_contacts"))
            }
        }
        if (openContacts != null) {
            item(key = "contacts-open") {
                MetroLink(text = t("open_contacts"), onClick = openContacts)
            }
        }
        return
    }
    itemsIndexed(items = people, key = { _, person -> "person:" + person.id }) { index, person ->
        if (HubPeople.startsSection(people, index)) {
            Text(
                text = HubPeople.sectionOf(person),
                style = MaterialTheme.typography.titleMedium,
                color = Bb10Blue,
                modifier = Modifier.padding(top = 4.dp).semantics { heading() },
            )
        }
        val number = person.number
        HubPersonRow(
            person = person,
            nameModifier = Modifier.focusRequester(requesterFor(person)),
            onOpen = { onStartIntent(PeopleRepository.viewIntent(person)) },
            onCall = number?.let { { onStartIntent(PeopleRepository.callIntent(it)) } },
            onMessage = number?.let { { onStartIntent(PeopleRepository.messageIntent(it, messagesPackage)) } },
        )
    }
}

@Composable
internal fun HubPersonRow(
    person: HubPerson,
    nameModifier: Modifier,
    onOpen: () -> Unit,
    onCall: (() -> Unit)?,
    onMessage: (() -> Unit)?,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = (if (person.starred) "★ " else "") + person.name,
            modifier =
                nameModifier
                    .weight(1f)
                    .onFocusChanged { focused = it.isFocused }
                    .clip(shape)
                    .background(if (focused) Bb10Blue else Color.Transparent)
                    .clickable(onClick = onOpen)
                    .semantics { contentDescription = person.name + if (person.starred) t("favourite") else "" }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            color = if (focused) Bb10OnBlue else Bb10Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (onCall != null) HubPersonAction(label = "call", description = t("call", person.name), onClick = onCall)
        if (onMessage != null) HubPersonAction(label = "text", description = t("message", person.name), onClick = onMessage)
    }
}

@Composable
internal fun HubPersonAction(
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Text(
        text = label,
        modifier =
            Modifier
                .onFocusChanged { focused = it.isFocused }
                .clip(shape)
                .background(if (focused) Bb10Blue else Bb10Card)
                .border(1.dp, Bb10Line, shape)
                .clickable(onClick = onClick)
                .semantics { contentDescription = description }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = if (focused) Bb10OnBlue else Bb10Text,
        maxLines = 1,
    )
}

/*
 * Quick slot options (Long press / Menu / Fn+Enter on a slot): the Hub slot
 * offers its filters, the Phone slot Dial pad, Recent calls and Contacts, every
 * slot Edit Home (reorder, replace, reset).
 */

@Composable
private fun rememberHubSourceGroups(
    snapshot: LiveSurfaceSnapshot,
    enabledHubApps: Set<String>,
    refreshGeneration: Int,
): HubSourceGroups.Summary {
    // The listener tells us which notifications are conversations (including email).
    // The counts-only snapshot supplies totals; disabled and zero-count apps never get a chip.
    val conversationSources = remember(refreshGeneration) { ConversationHub.notifications() }
    return remember(snapshot.hubApps, enabledHubApps, conversationSources) {
        HubSourceGroups.summarize(
            counts = snapshot.hubApps,
            enabledPackages = enabledHubApps,
            emailSources = conversationSources.filter { it.isEmail }.map { it.packageName }.toSet(),
            messageSources =
                conversationSources
                    .filter {
                        it.kind == io.github.aimindseye.tenseven.model.HubMessageKind.Message
                    }.map { it.packageName }
                    .toSet(),
        )
    }
}

@Composable
private fun rememberHubPeople(
    contactsTab: Boolean,
    canReadContacts: Boolean,
    refreshGeneration: Int,
): Pair<List<HubPerson>, Boolean> {
    val context = LocalContext.current
    val repository = remember(context) { PeopleRepository(context) }
    var people by remember { mutableStateOf(emptyList<HubPerson>()) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(contactsTab, canReadContacts, refreshGeneration) {
        if (contactsTab) {
            people = if (canReadContacts) repository.load() else emptyList()
            loaded = true
        }
    }
    return people to loaded
}

private fun hubSourceFallback(context: android.content.Context): () -> Unit =
    {
        android.widget.Toast
            .makeText(context, "Source app unavailable", android.widget.Toast.LENGTH_SHORT)
            .show()
    }
