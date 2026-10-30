package io.github.aimindseye.tenseven.conversations

/** The Mail tile's line, built from the email notifications Tenseven's listener already reads. Pure, unit tested. */
object MailSummary {
    private const val PREVIEW_CHARS = 60

    /**
     * "No new email", "3 new emails" (nothing more while the phone is locked), or "3 new · Ann: Lunch tomorrow?"
     * with the newest sender and the start of its text.
     */
    fun headline(
        count: Int,
        sender: String?,
        preview: String?,
        locked: Boolean,
    ): String {
        if (count <= 0) return "No new email"
        val noun = if (count == 1) "1 new email" else "$count new emails"
        if (locked || sender.isNullOrBlank()) return noun
        val text = preview?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        val clipped = if (text.length > PREVIEW_CHARS) text.take(PREVIEW_CHARS - 1).trimEnd() + "…" else text
        val head = "$count new · $sender"
        return if (clipped.isEmpty()) head else "$head: $clipped"
    }

    /** The same for messages: "No new messages", "3 new messages" while locked, or "3 new · Ann: Running late". */
    fun messagesHeadline(
        count: Int,
        sender: String?,
        preview: String?,
        locked: Boolean,
    ): String {
        if (count <= 0) return "No new messages"
        if (locked || sender.isNullOrBlank()) return if (count == 1) "1 new message" else "$count new messages"
        return headline(count, sender, preview, locked = false)
    }
}
