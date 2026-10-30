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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.OnboardingActivity
import io.github.aimindseye.tenseven.R
import io.github.aimindseye.tenseven.conversations.ConversationAccess
import io.github.aimindseye.tenseven.conversations.ConversationHub
import io.github.aimindseye.tenseven.conversations.HubConversation
import io.github.aimindseye.tenseven.conversations.conversationPreview
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.onboarding.SetupStep

/** What the Hub page needs to show conversations; built by [HubPage] from Tenseven's own notification listener. */
internal class HubConversationsUi(
    val accessGranted: Boolean,
    val deviceLocked: Boolean,
    val conversations: List<HubConversation>,
    val onOpen: (HubConversation) -> Unit,
    val onReply: (HubConversation, String) -> Boolean,
    val onAllowAccess: () -> Unit,
)

private const val MAX_CONVERSATIONS = 30

/**
 * The conversations the Hub groups per person, newest first, with Reply on each that the app lets us reply to.
 * Without notification access it offers to turn it on; the text stays hidden while the device is locked.
 */
internal fun LazyListScope.hubConversationItems(ui: HubConversationsUi) {
    if (!ui.accessGranted) {
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
    conversationItems(ui.conversations.take(MAX_CONVERSATIONS), key = { "conversation:" + it.id }) { conversation ->
        ConversationRow(
            conversation = conversation,
            locked = ui.deviceLocked,
            onOpen = { ui.onOpen(conversation) },
            onReply = { text -> ui.onReply(conversation, text) },
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
private fun ConversationRow(
    conversation: HubConversation,
    locked: Boolean,
    onOpen: () -> Unit,
    onReply: (String) -> Boolean,
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
            modifier = Modifier.fillMaxWidth().sableFocusRing().clickable(onClick = onOpen).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(conversation.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = preview ?: stringResource(R.string.hub_locked_preview),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text("$apps · $whenText", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (canReply) {
            Row(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!replying) {
                    OutlinedButton(onClick = { replying = true }, modifier = Modifier.sableFocusRing()) {
                        Text(stringResource(R.string.hub_reply))
                    }
                } else {
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
    val accessGranted = remember(refreshGeneration) { ConversationAccess.isGranted(context) }
    val locked =
        remember(refreshGeneration) {
            context.getSystemService(android.app.KeyguardManager::class.java)?.isDeviceLocked == true
        }
    val conversations =
        remember(refreshGeneration, accessGranted) {
            if (accessGranted) ConversationHub.conversations() else emptyList()
        }
    return remember(accessGranted, locked, conversations) {
        HubConversationsUi(
            accessGranted = accessGranted,
            deviceLocked = locked,
            conversations = conversations,
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
