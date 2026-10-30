package io.github.aimindseye.tenseven.model

/** `assign k camera` puts the Camera app on a held K; `assign k` lists apps to choose from; `unassign k` clears it. */
sealed interface AssignRequest {
    val letter: Char

    data class Assign(
        override val letter: Char,
        val appTerm: String,
    ) : AssignRequest

    data class Clear(
        override val letter: Char,
    ) : AssignRequest
}

/** Typed in Command to set the hold-a-letter shortcuts without opening each app's actions. Pure, unit tested. */
object AssignCommand {
    fun parse(raw: String): AssignRequest? {
        val text = raw.trim()
        val verb = text.substringBefore(' ').lowercase()
        if (verb != "assign" && verb != "unassign") return null
        val rest = text.drop(verb.length).trim()
        val letterWord = rest.substringBefore(' ')
        val letter = letterWord.singleOrNull()?.lowercaseChar()?.takeIf { it in 'a'..'z' } ?: return null
        val appTerm = rest.drop(letterWord.length).trim()
        return if (verb == "unassign") AssignRequest.Clear(letter) else AssignRequest.Assign(letter, appTerm)
    }
}
