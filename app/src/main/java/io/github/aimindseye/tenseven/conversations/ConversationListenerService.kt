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
import io.github.aimindseye.tenseven.model.HubApps
import io.github.aimindseye.tenseven.model.HubMessageKind
import io.github.aimindseye.tenseven.model.LockLayer
import io.github.aimindseye.tenseven.platform.HubSourceRepository
import io.github.aimindseye.tenseven.platform.LockLayerLauncher
import io.github.aimindseye.tenseven.platform.LockLayerState
import io.github.aimindseye.tenseven.platform.StartStateRepository

/**
 * Turns Android-delivered notifications into [HubNotification]s in [ConversationHub]. Only runs once the
 * user grants notification access; reads text for grouping and counting, keeps it in memory, and
 * never sends it anywhere.
 */
class ConversationListenerService : NotificationListenerService() {
    companion object {
        private var live = java.lang.ref.WeakReference<ConversationListenerService>(null)

        /** Main-thread callers can import a fresh snapshot without rebinding a connected service. */
        fun refreshConnected(): Boolean {
            val service = live.get() ?: return false
            if (!NotificationConnection.connected) return false
            service.importActiveNotifications()
            return true
        }
    }

    private fun importActiveNotifications() {
        val active = readActiveNotifications()
        io.github.aimindseye.tenseven.platform.FrameMediaNotifications
            .replace(this, active)
        // Parse before choosing summaries: a malformed/empty child must not hide usable mail content.
        val parsed = active.mapNotNull { sbn -> safeHubNotification(sbn)?.let { sbn to it } }
        val visible =
            NotificationGroupSelection.visibleKeys(
                parsed.map { (sbn, item) ->
                    NotificationGroupEntry(
                        item.key,
                        item.packageName,
                        sbn.groupKey,
                        sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
                    )
                },
            )
        ConversationHub.replace(parsed.map { it.second }.filter { it.key in visible })
        refreshLockCounts()
    }

    private val screenOn =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) = LockLayerLauncher.showIfLocked(context)
        }
    private var screenOnRegistered = false
    private val hubSources by lazy { HubSourceRepository(this) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // A single malformed third-party notification must not crash the entire listener.
        live = java.lang.ref.WeakReference(this)
        NotificationConnection.onConnected(0)
        importActiveNotifications()
        if (!screenOnRegistered) {
            // Receiver registration failure must not disable Hub, Peek and lock-screen counts.
            screenOnRegistered =
                runCatching {
                    ContextCompat.registerReceiver(
                        this,
                        screenOn,
                        IntentFilter(Intent.ACTION_SCREEN_ON),
                        ContextCompat.RECEIVER_NOT_EXPORTED,
                    )
                    true
                }.getOrDefault(false)
        }
    }

    override fun onListenerDisconnected() {
        live.clear()
        NotificationConnection.onDisconnected()
        io.github.aimindseye.tenseven.platform.FrameMediaNotifications
            .clear()
        ConversationHub.clear()
        LockLayerState.update(emptyMap())
        if (screenOnRegistered) {
            runCatching { unregisterReceiver(screenOn) }
            screenOnRegistered = false
        }
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        if (live.get() === this) {
            live.clear()
            NotificationConnection.onDisconnected()
            io.github.aimindseye.tenseven.platform.FrameMediaNotifications
                .clear()
            ConversationHub.clear()
            LockLayerState.update(emptyMap())
        }
        if (screenOnRegistered) {
            runCatching { unregisterReceiver(screenOn) }
            screenOnRegistered = false
        }
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        importActiveNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        importActiveNotifications()
    }

    private fun readActiveNotifications(): List<StatusBarNotification> =
        runCatching { activeNotifications.orEmpty().toList() }.getOrDefault(emptyList())

    private fun safeHubNotification(sbn: StatusBarNotification): HubNotification? = runCatching { toHubNotification(sbn) }.getOrNull()

    /** Counts per app for the lock layer: how many notifications wait, nothing of what they say. */
    private fun refreshLockCounts() {
        val current = readActiveNotifications()
        io.github.aimindseye.tenseven.platform.FrameMediaNotifications
            .replace(this, current)
        NotificationConnection.onSnapshot(current.size)
        val waiting =
            current.filter { it.user == android.os.Process.myUserHandle() && hubSources.includes(it.packageName) }.map {
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
        // Read only Android-delivered notifications allowed by the optional Hub source filter.
        val notification = sbn.notification?.takeIf { eligibleSource(sbn) } ?: return null
        val style = messagingStyleOf(notification)
        val extracted = style?.let(::extractMessages).orEmpty()
        val kind = hubNotificationKind(sbn.packageName, notification.category, extracted.isNotEmpty())
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
                contactIdentity =
                    if (isGroup || kind != HubMessageKind.Message) {
                        null
                    } else {
                        resolveContactIdentity(latestIncoming?.personUri, name)
                    },
                providerIdentity =
                    if (kind == HubMessageKind.Notification) {
                        sbn.key
                    } else {
                        notification.shortcutId?.takeIf(String::isNotBlank) ?: normalizeName(name)
                    },
                generic = kind == HubMessageKind.Notification,
            )
        val messages = toMessages(sbn, appName, id, extracted).ifEmpty { fallbackMessage(sbn, extras, appName, id, name) }
        val visibleMessages = messages.ifEmpty { genericHubNotice(sbn, appName, id, kind) }
        return HubNotification(
            key = sbn.key,
            conversationId = id,
            conversationName = name,
            packageName = sbn.packageName,
            appName = appName,
            postedAt = sbn.postTime,
            isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
            messages = visibleMessages,
            contentIntent = notification.contentIntent,
            replyAction = replyActionOf(notification),
            kind = kind,
            sourceActions = notification.actions.orEmpty().toList(),
        ).takeIf { visibleMessages.isNotEmpty() }
    }

    private fun eligibleSource(sbn: StatusBarNotification): Boolean =
        sbn.user == android.os.Process.myUserHandle() &&
            sbn.packageName != packageName && hubSources.includes(sbn.packageName)

    /** The action behind the notification's own Reply button: the first one that takes free-form text. */
    private fun replyActionOf(notification: Notification): Notification.Action? =
        notification.actions?.firstOrNull { action -> action.remoteInputs?.any(RemoteInput::getAllowFreeFormInput) == true }

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
            fallbackNotificationText(
                bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
                text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty().map { it.toString() },
                summary = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString(),
                title = titleOf(null, extras)?.toString(),
            ) ?: return emptyList()
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

/** A custom notification may have no text extras; keep its real identity without inventing preview content. */
private fun genericHubNotice(
    sbn: StatusBarNotification,
    appName: String,
    id: String,
    kind: HubMessageKind,
): List<ConversationMessage> =
    if (kind != HubMessageKind.Notification) {
        emptyList()
    } else {
        listOf(
            ConversationMessage(
                id = "notice:${sbn.key}",
                conversationId = id,
                notificationKey = sbn.key,
                packageName = sbn.packageName,
                appName = appName,
                text = "",
                timestamp = sbn.postTime,
                senderName = null,
                isOutgoing = false,
            ),
        )
    }
