// The web-address check is derived from MinkLauncher OpenSource (github.com/katoaapps/openminilaunch, commit
// b9b26f7, features/magic/QueryRouting.kt), Copyright 2026 Katoa Apps, Apache License 2.0. Changed: package and
// object wrapper. The prefix parsing follows Mink's Magic Box prefixes ("-" to-do, "/" note, "+" event) but is
// Tenseven's own code, written to sit beside CommandQuery's scope words.
package io.github.aimindseye.tenseven.model

import java.net.URI

enum class QuickActionKind { TODO, NOTE, EVENT }

/** A typed prefix command: [text] is what follows the prefix. */
data class QuickAction(
    val kind: QuickActionKind,
    val text: String,
)

/**
 * Prefix commands typed into Command: `-Buy milk` (to-do), `/Draft the release` (note) and `+Dinner Friday at 7`
 * (event). Each hands the text to the app that handles it; Tenseven keeps nothing. A `/` that starts one of
 * CommandQuery's scope words (`/settings`, `/apps`, `/hub`, `/private`, `/dual`, `/work`) is that scope, not a note.
 * Pure policy, unit tested.
 */
object QuickActions {
    private val SCOPE_WORDS = setOf("settings", "apps", "hub", "private", "dual", "work")

    fun parse(raw: String): QuickAction? {
        val text = raw.trimStart()
        val kind =
            when (text.firstOrNull()) {
                '-' -> QuickActionKind.TODO
                '/' -> QuickActionKind.NOTE
                '+' -> QuickActionKind.EVENT
                else -> return null
            }
        val body = text.drop(1).trim()
        if (body.isEmpty()) return null
        if (kind == QuickActionKind.NOTE && body.substringBefore(' ').lowercase() in SCOPE_WORDS) return null
        return QuickAction(kind, body)
    }

    private val BARE_DOMAIN =
        Regex("^(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,62}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,63}(?::\\d{1,5})?(?:[/?#].*)?$")

    /** An http(s) address for something typed like one (`example.com/path`, `www.x.org`), else null. */
    fun normalizedWebUrl(query: String): String? {
        val candidate = query.trim()
        if (candidate.isEmpty() || candidate.any(Char::isWhitespace)) return null
        val normalized =
            when {
                candidate.startsWith("https://", ignoreCase = true) || candidate.startsWith("http://", ignoreCase = true) -> candidate
                candidate.startsWith("www.", ignoreCase = true) -> "https://$candidate"
                BARE_DOMAIN.matches(candidate) -> "https://$candidate"
                else -> return null
            }
        return runCatching {
            val uri = URI(normalized)
            normalized
                .takeIf { uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true) }
                ?.takeIf { !uri.host.isNullOrBlank() }
        }.getOrNull()
    }
}
