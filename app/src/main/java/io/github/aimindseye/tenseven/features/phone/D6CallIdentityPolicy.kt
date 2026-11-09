package io.github.aimindseye.tenseven.features.phone

/** Last-call identity is retained only in process memory for the Call ended screen. */
internal object D6CallIdentityPolicy {
    const val PRIVATE_LABEL = "Private caller"
    data class Snapshot(val number: String?, val networkName: String?)
    fun reconcile(previous: Snapshot, rawNumber: String?, rawName: String?, newCall: Boolean): Snapshot {
        val number = publicNumber(rawNumber)
        val networkName = rawName?.trim()?.takeIf { it.isNotEmpty() }
        // A genuinely new private call must not inherit the previous caller's identity.
        return if (newCall) Snapshot(number, networkName) else
            Snapshot(number ?: previous.number, networkName ?: previous.networkName)
    }
    fun publicNumber(raw: String?): String? = raw?.trim()?.takeIf {
        it.isNotEmpty() &&
            it.lowercase() !in setOf("private number", "unknown", "restricted", "anonymous")
    }
    fun displayNumber(number: String?): String = number ?: "Private number"
    fun callerName(contact: String?, network: String?, number: String): String =
        contact?.takeIf { it.isNotBlank() }
            ?: network?.takeIf { it.isNotBlank() }
            ?: number
}
