package io.github.aimindseye.tenseven.conversations

/**
 * D0 diagnostic only. The listener keeps its ordinary delivery behavior; this policy merely explains
 * why an Android-delivered notification did or did not reach the current Hub snapshot.
 *
 * No StatusBarNotification, notification key, sender, body, contact, timestamp from a source,
 * or PendingIntent is retained. Only per-package aggregate counts stay in process memory.
 */
internal enum class HubAuditReason {
    VISIBLE,
    OTHER_PROFILE,
    OWN_APP,
    USER_FILTER,
    PARSE_FAILURE,
    NO_HUB_CONTENT,
    GROUP_SUMMARY_REPLACED,
}

/** Best-effort channel/ranking classification; DND and device OEM behavior may differ. */
internal enum class HubAuditSound {
    SILENT_CANDIDATE,
    NOT_IDENTIFIED_SILENT,
    RANKING_UNKNOWN,
}

internal data class HubAuditEvent(
    val packageName: String,
    val reason: HubAuditReason,
    val sound: HubAuditSound,
)

/** Conversion/group outcome is independent of Android's sound or ranking classification. */
internal data class HubAuditConversion(
    val failed: Boolean,
    val converted: Boolean,
    val retained: Boolean,
)

internal object HubAuditPolicy {
    /** Sound is deliberately NOT an eligibility input. Silence must never hide a Hub notification. */
    fun reason(
        sameUser: Boolean,
        ownApp: Boolean,
        sourceEnabled: Boolean,
        conversion: HubAuditConversion,
    ): HubAuditReason =
        when {
            !sameUser -> HubAuditReason.OTHER_PROFILE
            ownApp -> HubAuditReason.OWN_APP
            !sourceEnabled -> HubAuditReason.USER_FILTER
            conversion.failed -> HubAuditReason.PARSE_FAILURE
            !conversion.converted -> HubAuditReason.NO_HUB_CONTENT
            !conversion.retained -> HubAuditReason.GROUP_SUMMARY_REPLACED
            else -> HubAuditReason.VISIBLE
        }
}

internal object HubNotificationAudit {
    @Volatile
    private var latest = "No Android notification snapshot recorded yet. Grant Notification access and open Hub."

    fun publish(events: List<HubAuditEvent>) {
        val categories = HubAuditReason.entries
        val lines =
            events.groupBy(HubAuditEvent::packageName).toSortedMap().map { (pkg, rows) ->
                val counts = categories.map { reason -> rows.count { it.reason == reason } }
                val likelySilent = rows.count { it.sound == HubAuditSound.SILENT_CANDIDATE }
                val unknown = rows.count { it.sound == HubAuditSound.RANKING_UNKNOWN }
                // Package names are Android identities, never notification text. Keep the export single-line safe.
                val safePackage = pkg.replace(Regex("[^A-Za-z0-9_.]"), "_")
                (
                    listOf(safePackage, rows.size.toString()) +
                        counts.map(Int::toString) +
                        listOf(likelySilent.toString(), unknown.toString())
                ).joinToString(" | ")
            }
        val totals = categories.map { reason -> events.count { it.reason == reason } }
        latest =
            buildString {
                appendLine("TenSeven D0 Hub snapshot diagnostics (in-memory counts only)")
                appendLine("This is a snapshot, not WhatsApp/chat history or an Android notification-shade dump.")
                appendLine(
                    "Silent candidate means low channel importance or channel sound disabled, " +
                        "when ranking is available.",
                )
                appendLine("No message text, sender, phone number, notification ID/key or contact data is collected.")
                appendLine("Observed by listener: ${events.size}; Hub-visible: ${totals.first()}")
                appendLine(
                    "package | observed | ${categories.joinToString(" | ") { it.name.lowercase() }} | " +
                        "silent_candidate | ranking_unknown",
                )
                lines.forEach(::appendLine)
            }
    }

    fun report(): String = latest

    fun clear() {
        latest = "Notification listener disconnected. No current snapshot available."
    }
}
