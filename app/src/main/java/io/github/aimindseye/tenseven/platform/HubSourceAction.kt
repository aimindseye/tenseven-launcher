package io.github.aimindseye.tenseven.platform

import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.github.aimindseye.tenseven.conversations.ConversationAccess
import io.github.aimindseye.tenseven.conversations.ConversationHub
import io.github.aimindseye.tenseven.conversations.HubConversation
import io.github.aimindseye.tenseven.conversations.HubNotification
import io.github.aimindseye.tenseven.model.HubActionCapability
import io.github.aimindseye.tenseven.model.HubActionInput
import io.github.aimindseye.tenseven.model.HubActionPolicy
import io.github.aimindseye.tenseven.model.HubApps

/** A user-visible capability tied to one exact active notification/action snapshot. Never persisted. */
internal data class HubSourceAction(
    val notificationKey: String,
    val index: Int,
    val label: String,
    val original: Notification.Action,
) {
    val acceptsText: Boolean get() = original.remoteInputs?.any(RemoteInput::getAllowFreeFormInput) == true
}

/** Do not infer action semantics from translated labels; display the provider's own label. */
internal fun HubNotification.availableSourceActions(locked: Boolean): List<HubSourceAction> {
    val capabilities =
        sourceActions.map { action ->
            HubActionCapability(
                label = action.title?.toString().orEmpty(),
                hasTarget = action.actionIntent != null,
                input = actionInput(action),
                alreadyHandled =
                    action === replyAction ||
                        action.semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ,
            )
        }
    return HubActionPolicy.visibleIndices(capabilities, locked).map { index ->
        HubSourceAction(key, index, capabilities[index].label, sourceActions[index])
    }
}

private fun actionInput(action: Notification.Action): HubActionInput =
    when {
        action.remoteInputs.isNullOrEmpty() -> HubActionInput.None
        action.remoteInputs?.any(RemoteInput::getAllowFreeFormInput) == true -> HubActionInput.Text
        else -> HubActionInput.Unsupported
    }

/** Recheck lock, consent and exact action identity at activation, not just when the panel was composed. */
internal fun performHubSourceAction(
    context: Context,
    target: HubSourceAction,
    text: String? = null,
): Boolean {
    val liveAction = activeSourceAction(context, target) ?: return false
    val fillIn = if (target.acceptsText) replyIntent(liveAction, text) else null
    return if (target.acceptsText && fillIn == null) {
        false
    } else {
        sendHubPendingIntent(context, liveAction.actionIntent, fillIn)
    }
}

private fun activeSourceAction(
    context: Context,
    target: HubSourceAction,
): Notification.Action? {
    val current = ConversationHub.notifications().firstOrNull { it.key == target.notificationKey } ?: return null
    return current.sourceActions.getOrNull(target.index)?.takeIf {
        it === target.original && canUseHubActions(context, current.packageName)
    }
}

internal fun canUseHubActions(
    context: Context,
    source: String,
): Boolean =
    context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == false &&
        ConversationAccess.isGranted(context) &&
        HubSourceRepository(context).includes(source)

private fun replyIntent(
    action: Notification.Action,
    text: String?,
): Intent? {
    val clean = text?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val inputs =
        action.remoteInputs
            .orEmpty()
            .filter(RemoteInput::getAllowFreeFormInput)
            .toTypedArray()
    val results = Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, clean) } }
    return Intent().also { RemoteInput.addResultsToIntent(inputs, it, results) }
}

/** Foreground, explicitly selected provider action; payload is only the text the user entered. */
internal fun sendHubPendingIntent(
    context: Context,
    pending: PendingIntent?,
    fillIn: Intent? = null,
): Boolean =
    pending?.let {
        runCatching {
            val options =
                android.app.ActivityOptions
                    .makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                        android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                    ).toBundle()
            it.send(context, 0, fillIn, null, null, null, options)
            true
        }.getOrDefault(false)
    } ?: false

/** An editor may outlive a notification update; never send its stale primary reply target. */
internal fun currentHubReplyTarget(
    context: Context,
    conversation: HubConversation,
): HubNotification? {
    val target = conversation.replyTarget ?: return null
    return target.takeIf {
        ConversationHub.notifications().any { current -> current === target } &&
            canUseHubActions(context, target.packageName)
    }
}

/** A notification without an open intent falls back to its own app, never a different messaging provider. */
internal fun openHubSource(
    context: Context,
    conversation: HubConversation,
    onUnavailable: () -> Unit,
) {
    if (!ConversationHub.open(context, conversation.openTarget)) {
        val intent = context.packageManager.getLaunchIntentForPackage(conversation.latestNotification.packageName)
        val opened =
            intent?.let {
                runCatching {
                    context.startActivity(it)
                    true
                }.getOrDefault(false)
            } ?: false
        if (!opened) onUnavailable()
    }
}
