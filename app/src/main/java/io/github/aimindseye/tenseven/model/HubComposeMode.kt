package io.github.aimindseye.tenseven.model

/** One per-user choice. The target app still comes from Android's installed handlers/defaults. */
internal enum class HubComposeMode(
    val key: String,
    val label: String,
    val description: String,
) {
    Ask("ask", "Ask every time", "Choose Email or Message when Compose is tapped"),
    Email("email", "Email", "Use Android's configured email composer"),
    Messages("messages", "Message", "Use Android's configured SMS composer"),
    ;

    companion object {
        fun decode(raw: String?): HubComposeMode = entries.firstOrNull { it.key == raw } ?: Ask
    }
}
