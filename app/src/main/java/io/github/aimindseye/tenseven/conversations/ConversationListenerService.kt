// Derived from MinkLauncher OpenSource (github.com/katoaapps/openminilaunch, commit b9b26f7,
// features/conversations/MinkNotificationListenerService.kt), Copyright 2026 Katoa Apps, Apache License 2.0.
// Changed: package; split into small functions; the pre-Android-9 message path is not ported (minSdk is 30);
// messages carry a historic flag so only new ones are counted.
package io.github.aimindseye.tenseven.conversations

import android.Manifest
import android.app.Notification
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.content.ContextCompat
import io.github.aimindseye.tenseven.model.LockLayer
import io.github.aimindseye.tenseven.platform.LockLayerLauncher
import io.github.aimindseye.tenseven.platform.LockLayerState

/**
 * Turns message and email notifications into [HubNotification]s in [ConversationHub]. Only runs once the
 * user grants notification access; reads text for grouping and counting, keeps it in memory, and
 * never sends it anywhere.
 */
class ConversationListenerService : NotificationListenerService() {
    private val screenOn =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) = LockLayerLauncher.showIfLocked(context)
        }
    private var screenOnRegistered = false

    override fun onListenerConnected() {
        super.onListenerConnected()
        ConversationHub.replace(activeNotifications.orEmpty().mapNotNull(::toHubNotification))
        refreshLockCounts()
        if (!screenOnRegistered) {
            ContextCompat.registerReceiver(this, screenOn, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
            screenOnRegistered = true
        }
    }

    override fun onListenerDisconnected() {
        ConversationHub.clear()
        LockLayerState.update(emptyMap())
        if (screenOnRegistered) {
            runCatching { unregisterReceiver(screenOn) }
            screenOnRegistered = false
        }
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        toHubNotification(sbn)?.let(ConversationHub::upsert)
        refreshLockCounts()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        ConversationHub.remove(sbn.key)
        refreshLockCounts()
    }

    /** Counts per app for the lock layer: how many notifications wait, nothing of what they say. */
    private fun refreshLockCounts() {
        val waiting =
            activeNotifications.orEmpty().map {
                val flags = it.notification?.flags ?: 0
                Triple(
                    it.packageName,
                    flags and Notification.FLAG_ONGOING_EVENT != 0,
                    flags and Notification.FLAG_GROUP_SUMMARY != 0,
                )
            }
        LockLayerState.update(LockLayer.tally(waiting, packageName))
    }

    private fun toHubNotification(sbn: StatusBarNotification): HubNotification? {
        val notification = sbn.notification ?: return null
        if (sbn.packageName == packageName || notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val style = messagingStyleOf(notification)
        val extracted = style?.let(::extractMessages).orEmpty()
        if (!isConversationNotification(notification.category, extracted.isNotEmpty())) return null
        val extras = notification.extras ?: Bundle.EMPTY
        val appName = appLabel(sbn.packageName)
        val latestIncoming = extracted.lastOrNull { !it.isOutgoing && !it.isHistoric } ?: extracted.lastOrNull { !it.isOutgoing }
        val isGroup = style?.isGroupConversation == true
        val name =
            conversationName(
                isGroup = isGroup,
                title = titleOf(style, extras)?.toString(),
                sender = latestIncoming?.senderName,
                appName = appName,
            )
        val id =
            conversationId(
                packageName = sbn.packageName,
                isGroup = isGroup,
                contactIdentity = if (isGroup) null else resolveContactIdentity(latestIncoming?.personUri, name),
                providerIdentity = notification.shortcutId?.takeIf(String::isNotBlank) ?: normalizeName(name),
            )
        val messages = toMessages(sbn, appName, id, extracted).ifEmpty { fallbackMessage(sbn, extras, appName, id, name) }
        if (messages.isEmpty()) return null
        return HubNotification(
            key = sbn.key,
            conversationId = id,
            conversationName = name,
            packageName = sbn.packageName,
            appName = appName,
            postedAt = sbn.postTime,
            isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
            messages = messages,
            contentIntent = notification.contentIntent,
            replyAction = replyActionOf(notification),
            isEmail = notification.category == Notification.CATEGORY_EMAIL,
            markReadAction = markReadActionOf(notification),
        )
    }

    /** The action behind the notification's own Reply button: the first one that takes free-form text. */
    private fun replyActionOf(notification: Notification): Notification.Action? =
        notification.actions?.firstOrNull { action -> action.remoteInputs?.any(RemoteInput::getAllowFreeFormInput) == true }

    private fun markReadActionOf(notification: Notification): Notification.Action? =
        notification.actions?.firstOrNull { it.semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ }

    private fun messagingStyleOf(notification: Notification): Notification.MessagingStyle? =
        runCatching {
            Notification.Builder.recoverBuilder(this, notification).style as? Notification.MessagingStyle
        }.getOrNull()

    private fun titleOf(
        style: Notification.MessagingStyle?,
        extras: Bundle,
    ): CharSequence? =
        style?.conversationTitle
            ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)

    @Suppress("DEPRECATION")
    private fun extractMessages(style: Notification.MessagingStyle): List<ExtractedMessage> {
        fun convert(
            message: Notification.MessagingStyle.Message,
            historic: Boolean,
        ): ExtractedMessage? {
            val body = message.text?.toString()?.takeIf(String::isNotBlank) ?: return null
            val person = message.senderPerson
            val sender = person?.name?.toString() ?: message.sender?.toString()
            return ExtractedMessage(body, message.timestamp, sender, person?.uri, isOutgoing = sender == null, isHistoric = historic)
        }
        return style.historicMessages.mapNotNull { convert(it, historic = true) } +
            style.messages.mapNotNull { convert(it, historic = false) }
    }

    private fun toMessages(
        sbn: StatusBarNotification,
        appName: String,
        conversationId: String,
        extracted: List<ExtractedMessage>,
    ): List<ConversationMessage> =
        extracted.mapIndexed { index, message ->
            val timestamp = message.timestamp.takeIf { it > 0 } ?: sbn.postTime
            ConversationMessage(
                id = "${sbn.packageName}|$timestamp|${message.senderName.orEmpty()}|${message.text}",
                conversationId = conversationId,
                notificationKey = sbn.key,
                packageName = sbn.packageName,
                appName = appName,
                text = message.text,
                timestamp = timestamp + index,
                senderName = message.senderName,
                isOutgoing = message.isOutgoing,
                isHistoric = message.isHistoric,
            )
        }

    /** Email and apps without MessagingStyle: the notification's own text is the one message. */
    private fun fallbackMessage(
        sbn: StatusBarNotification,
        extras: Bundle,
        appName: String,
        conversationId: String,
        name: String,
    ): List<ConversationMessage> {
        val body =
            (
                extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                    ?: extras.getCharSequence(Notification.EXTRA_TEXT)
                    ?: extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)
            )?.toString()?.takeIf(String::isNotBlank) ?: return emptyList()
        return listOf(
            ConversationMessage(
                id = "${sbn.packageName}|${sbn.postTime}|$body",
                conversationId = conversationId,
                notificationKey = sbn.key,
                packageName = sbn.packageName,
                appName = appName,
                text = body,
                timestamp = sbn.postTime,
                senderName = name,
                isOutgoing = false,
            ),
        )
    }

    private fun appLabel(packageName: String): String =
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString() }
            .getOrDefault(packageName)

    private fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** A phone number or contact key, so one person's messages from two apps become one conversation. */
    private fun resolveContactIdentity(
        personUri: String?,
        displayName: String,
    ): String? {
        val uri = personUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val digits = if (uri?.scheme.equals("tel", ignoreCase = true)) uri?.schemeSpecificPart.orEmpty().filter(Char::isDigit) else ""
        return when {
            digits.isNotBlank() -> lookupKeyForNumber(digits) ?: "tel:$digits"
            hasContactsPermission() -> lookupKeyForName(displayName)
            else -> null
        }
    }

    private fun lookupKeyForNumber(digits: String): String? {
        if (!hasContactsPermission()) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(digits))
        return runCatching {
            contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.LOOKUP_KEY), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    /** Only an unambiguous exact-name match counts; two contacts with the same name stay separate. */
    private fun lookupKeyForName(displayName: String): String? {
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(displayName))
        val projection = arrayOf(ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
        val wanted = normalizeName(displayName)
        val matches = linkedSetOf<String>()
        runCatching {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (normalizeName(cursor.getString(1).orEmpty()) == wanted) matches += cursor.getString(0)
                }
            }
        }
        return matches.singleOrNull()
    }
}

private data class ExtractedMessage(
    val text: String,
    val timestamp: Long,
    val senderName: String?,
    val personUri: String?,
    val isOutgoing: Boolean,
    val isHistoric: Boolean,
)
