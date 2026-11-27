package io.github.aimindseye.tenseven.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.conversations.ConversationMessage
import io.github.aimindseye.tenseven.conversations.HubConversation
import io.github.aimindseye.tenseven.conversations.SableReplyChoice
import io.github.aimindseye.tenseven.design.sableFocusRing

/** Which Hub conversation is open as a chat, if any. Held here so the list and the chat share it. */
internal object HubChatState {
    var openId by mutableStateOf<String?>(null)
}

private const val CHAT_MAX_MESSAGES = 60
private const val CHAT_BUBBLE_MAX_DP = 420

/**
 * The approved Hub conversation board: one person across SMS, chat apps and email as bubbles, each tagged with the
 * app it came from, and a "Reply via" row that picks the source the reply is sent through. A reply is sent by the
 * app that received the message; when that app has no reply action, Open app is offered instead.
 */
@Composable
@Suppress("FunctionNaming")
internal fun HubChatScreen(
    conversation: HubConversation,
    ui: HubConversationsUi,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    LaunchedEffect(conversation.id) { if (ui.state.unreadCount(conversation) > 0) ui.onMarkRead(conversation) }
    val messages =
        remember(conversation) {
            conversation.messages.sortedBy(ConversationMessage::timestamp).takeLast(CHAT_MAX_MESSAGES)
        }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HubChatHeader(conversation, onClose)
        Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
            ) {
                item { Spacer(Modifier.padding(top = 6.dp)) }
                items(messages, key = { it.id }) { HubChatBubble(it) }
                item { Spacer(Modifier.padding(bottom = 6.dp)) }
            }
        }
        HubChatReply(conversation, ui)
    }
}

@Composable
@Suppress("FunctionNaming")
private fun HubChatHeader(
    conversation: HubConversation,
    onClose: () -> Unit,
) {
    val bb7 = rememberHubBb7()
    val bar = if (bb7) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier
    val ink = if (bb7) Color.White else Bb10Text
    val soft = if (bb7) Color.White.copy(alpha = 0.85f) else Bb10Muted
    Row(
        modifier = bar.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HubAvatar(conversation.name, 40.dp)
        Column(Modifier.weight(1f)) {
            Text(
                conversation.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = ink,
                maxLines = 1,
                modifier = Modifier.semantics { heading() },
            )
            val sources = conversation.notifications.sortedBy { it.postedAt }.map { it.appName }.distinct()
            Text(
                if (sources.size > 1) sources.joinToString(", ") + " in one conversation" else sources.firstOrNull().orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = soft,
                maxLines = 1,
            )
        }
        Text(
            "Back",
            style = MaterialTheme.typography.labelLarge,
            color = soft,
            modifier = Modifier.sableFocusRing().clickable(onClick = onClose).padding(horizontal = 8.dp, vertical = 10.dp),
        )
    }
}

@Composable
@Suppress("FunctionNaming")
private fun HubChatBubble(message: ConversationMessage) {
    val context = LocalContext.current
    val outgoing = message.isOutgoing
    val accent = MaterialTheme.colorScheme.primary
    val ink = if (outgoing) Color.White else MaterialTheme.colorScheme.onSurface
    val soft = if (outgoing) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = CHAT_BUBBLE_MAX_DP.dp)
                    .background(if (outgoing) accent else MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    DateUtils.formatDateTime(context, message.timestamp, DateUtils.FORMAT_SHOW_TIME),
                    style = MaterialTheme.typography.labelSmall,
                    color = soft,
                )
                Text(
                    message.appName,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (outgoing) Color.White else accent,
                    modifier = Modifier.border(1.dp, if (outgoing) Color.White else accent).padding(horizontal = 5.dp),
                    maxLines = 1,
                )
            }
            Text(message.text, style = MaterialTheme.typography.bodyLarge, color = ink)
        }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun HubChatReply(
    conversation: HubConversation,
    ui: HubConversationsUi,
) {
    var draft by remember(conversation.id) { mutableStateOf("") }
    var failed by remember(conversation.id) { mutableStateOf(false) }
    var choiceTick by remember(conversation.id) { mutableIntStateOf(0) }
    val targets = conversation.replyTargets
    val chosen = remember(conversation, choiceTick) { conversation.replyTarget }
    val accent = MaterialTheme.colorScheme.primary
    fun send() {
        if (draft.isBlank()) return
        failed = !ui.onReply(conversation, draft)
        if (!failed) draft = ""
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (targets.isEmpty()) "No reply here" else "Reply via",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            targets.forEach { target ->
                val selected = target.packageName == chosen?.packageName
                Text(
                    target.appName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier =
                        Modifier
                            .sableFocusRing()
                            .border(1.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant)
                            .clickable {
                                SableReplyChoice.choose(conversation.id, target.packageName)
                                choiceTick++
                            }.heightIn(min = 36.dp)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
            if (targets.isEmpty()) {
                Text(
                    "Open app",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    modifier =
                        Modifier
                            .sableFocusRing()
                            .border(1.dp, accent)
                            .clickable { ui.onOpen(conversation) }
                            .heightIn(min = 36.dp)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
        if (targets.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        failed = false
                    },
                    modifier = Modifier.weight(1f),
                    isError = failed,
                    singleLine = true,
                    placeholder = { Text("Message") },
                    keyboardOptions =
                        androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Send,
                        ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { send() }),
                )
                Box(
                    modifier =
                        Modifier
                            .sableFocusRing()
                            .background(accent)
                            .clickable { send() }
                            .heightIn(min = 52.dp)
                            .padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Send", color = Color.White, fontWeight = FontWeight.SemiBold) }
            }
            Text(
                if (failed) {
                    "That app could not send the reply. Open the app instead."
                } else {
                    "The reply is sent by the app that received the message. If that app cannot reply here, Open app is offered."
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
