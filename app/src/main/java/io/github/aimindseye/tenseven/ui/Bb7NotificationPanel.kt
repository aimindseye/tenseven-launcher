package io.github.aimindseye.tenseven.ui

import android.text.format.DateFormat
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.aimindseye.tenseven.conversations.ConversationHub
import io.github.aimindseye.tenseven.conversations.ConversationListenerService
import io.github.aimindseye.tenseven.conversations.HubNotification
import io.github.aimindseye.tenseven.model.HubMessageKind
import java.util.Date

private val PanelBlue = Color(0xFF2E9BD6)

/** What the Menu key offers on the highlighted row (BlackBerry 7 notification list). */
internal enum class NotificationMenuItem(val label: String) {
    Open("Open"),
    MarkOpened("Mark Opened"),
    MarkUnopened("Mark Unopened"),
    Delete("Delete"),
    ClearAll("Clear All"),
    Close("Close"),
}

/** The one-letter tag standing in for the envelope, bubble and handset of the BlackBerry 7 rows. */
internal fun notificationTag(item: HubNotification): String =
    when {
        item.kind == HubMessageKind.Email -> "@"
        item.packageName.contains("dialer") || item.packageName.contains("telecom") -> "T"
        item.packageName.contains("calendar") -> "C"
        item.packageName.contains("messag") || item.packageName.contains("sms") -> "M"
        else -> item.appName.firstOrNull()?.uppercaseChar()?.toString() ?: "N"
    }

/**
 * The BlackBerry 7 notification list: a translucent dark panel over the Home screen, opened by the
 * banner. Newest first; Enter or the trackpad click opens the row's app and clears it, Menu opens
 * the row menu, Delete clears the row, Back closes. It lists what the Hub already holds, so only
 * apps the user turned on in the Hub appear.
 */
@Composable
internal fun Bb7NotificationPanel(onClose: () -> Unit) {
    val context = LocalContext.current
    var items by remember { mutableStateOf(ConversationHub.notifications().filterNot { it.isOngoing }) }
    DisposableEffect(Unit) {
        val listener = { items = ConversationHub.notifications().filterNot { it.isOngoing } }
        ConversationHub.addChangeListener(listener)
        onDispose { ConversationHub.removeChangeListener(listener) }
    }
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val listener: () -> Unit = { tick++ }
        ConversationHub.addChangeListener(listener)
        onDispose { ConversationHub.removeChangeListener(listener) }
    }
    var selected by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var menuIndex by remember { mutableIntStateOf(0) }
    val state = rememberLazyListState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(items.size) {
        if (selected > items.lastIndex) selected = items.lastIndex.coerceAtLeast(0)
    }
    LaunchedEffect(selected) { if (items.isNotEmpty()) state.animateScrollToItem(selected) }

    fun delete(item: HubNotification) {
        ConversationListenerService.cancelKeys(listOf(item.key))
        ConversationHub.remove(item.key)
    }

    fun open(item: HubNotification) {
        ConversationHub.open(context, item)
        delete(item)
        onClose()
    }

    fun isUnopened(item: HubNotification): Boolean = ConversationHub.state().unreadCount(item) > 0

    fun conversationOf(item: HubNotification) = ConversationHub.conversations().firstOrNull { it.id == item.conversationId }

    val menuItems =
        items.getOrNull(selected).let { current ->
            val unopened = current?.let(::isUnopened) ?: false
            NotificationMenuItem.entries.filter {
                when (it) {
                    NotificationMenuItem.MarkOpened -> unopened
                    NotificationMenuItem.MarkUnopened -> !unopened
                    else -> true
                }
            }
        }

    fun run(action: NotificationMenuItem) {
        val item = items.getOrNull(selected)
        menuOpen = false
        when (action) {
            NotificationMenuItem.Open -> item?.let(::open)
            NotificationMenuItem.MarkOpened -> item?.let { conversationOf(it)?.let { c -> ConversationHub.markRead(context, c) } }
            NotificationMenuItem.MarkUnopened -> item?.let { conversationOf(it)?.let { c -> ConversationHub.markUnread(context, c) } }
            NotificationMenuItem.Delete -> item?.let(::delete)
            NotificationMenuItem.ClearAll -> {
                ConversationListenerService.cancelKeys(items.map(HubNotification::key))
                items.forEach { ConversationHub.remove(it.key) }
            }
            NotificationMenuItem.Close -> onClose()
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(onClick = onClose),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .background(Color(0xE6101010))
                        .focusRequester(focus)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val code = event.key.nativeKeyCode
                            if (menuOpen) {
                                when (code) {
                                    KeyEvent.KEYCODE_DPAD_DOWN ->
                                        menuIndex = (menuIndex + 1) % menuItems.size
                                    KeyEvent.KEYCODE_DPAD_UP ->
                                        menuIndex =
                                            (menuIndex - 1 + menuItems.size) %
                                                menuItems.size
                                    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER ->
                                        menuItems.getOrNull(menuIndex)?.let(::run)
                                    KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BACK -> menuOpen = false
                                    else -> return@onPreviewKeyEvent false
                                }
                                return@onPreviewKeyEvent true
                            }
                            when (code) {
                                KeyEvent.KEYCODE_DPAD_DOWN -> selected = (selected + 1).coerceAtMost(items.lastIndex.coerceAtLeast(0))
                                KeyEvent.KEYCODE_DPAD_UP -> selected = (selected - 1).coerceAtLeast(0)
                                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER ->
                                    items.getOrNull(selected)?.let(::open)
                                KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL ->
                                    items.getOrNull(selected)?.let(::delete)
                                KeyEvent.KEYCODE_MENU -> {
                                    menuIndex = 0
                                    menuOpen = true
                                }
                                else -> return@onPreviewKeyEvent false
                            }
                            true
                        },
            ) {
                if (items.isEmpty()) {
                    Text(
                        text = "No new notifications",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    LazyColumn(state = state) {
                        itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
                            val dayLabel = dayLabel(context, item.postedAt)
                            if (index == 0 || dayLabel != dayLabel(context, items[index - 1].postedAt)) {
                                Text(
                                    text = dayLabel,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .background(Color.White.copy(alpha = 0.10f))
                                            .padding(horizontal = 10.dp, vertical = 2.dp),
                                )
                            }
                            NotificationRow(
                                item = item,
                                unopened = tick >= 0 && isUnopened(item),
                                selected = index == selected && !menuOpen,
                                onClick = {
                                    selected = index
                                    open(item)
                                },
                            )
                        }
                    }
                }
                if (menuOpen) {
                    NotificationMenu(menuItems, menuIndex) { run(it) }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    item: HubNotification,
    unopened: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val preview = item.messages.lastOrNull()?.text.orEmpty()
    val time = DateFormat.getTimeFormat(context).format(Date(item.postedAt))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(if (selected) PanelBlue else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(28.dp).background(Color.White.copy(alpha = 0.16f)),
        ) {
            Text(text = notificationTag(item), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(
                text = item.conversationName,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = if (unopened) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = preview,
                color = if (selected) Color.White else Color(0xFFB8B8B8),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(text = time, color = if (selected) Color.White else Color(0xFFB8B8B8), fontSize = 11.sp)
    }
}

@Composable
private fun NotificationMenu(
    entries: List<NotificationMenuItem>,
    index: Int,
    onPick: (NotificationMenuItem) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier.fillMaxWidth().background(Color(0xFF1C1C1C)),
    ) {
        entries.forEachIndexed { i, entry ->
            Text(
                text = entry.label,
                color = Color.White,
                fontSize = 15.sp,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(if (i == index) PanelBlue else Color.Transparent)
                        .clickable { onPick(entry) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
    }
}

/** "Today", "Yesterday" or the date: the divider above each day's rows. */
private fun dayLabel(
    context: android.content.Context,
    at: Long,
): String {
    val day = 24L * 60 * 60 * 1000
    val zone = java.util.TimeZone.getDefault()
    fun dayNumber(t: Long) = (t + zone.getOffset(t)) / day
    return when (dayNumber(System.currentTimeMillis()) - dayNumber(at)) {
        0L -> "Today"
        1L -> "Yesterday"
        else -> DateFormat.getMediumDateFormat(context).format(Date(at))
    }
}
