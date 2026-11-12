package io.github.aimindseye.tenseven.features.phone

/** Named presentation/protocol values; numeric values unchanged from the reviewed source. */
private const val PHONE_UI_RULES_BOUNDS_VALUE_2 = 2
private const val PHONE_UI_RULES_BOUNDS_VALUE_420 = 420
private const val PHONE_UI_RULES_BOUNDS_VALUE_46 = 46
private const val PHONE_UI_RULES_BOUNDS_VALUE_500 = 500
private const val PHONE_UI_RULES_BOUNDS_VALUE_52 = 52

/** No Android dependencies: this contract is shared by the activity and local JVM tests. */
internal object PhoneUiRules {
    data class Match(
        val contactId: Long,
        val name: String,
        val number: String,
    )

    data class Bounds(
        val compact: Boolean,
        val maxMatches: Int,
        val primaryCallHeightDp: Int,
    )

    fun bounds(
        availableWidthDp: Float,
        availableHeightDp: Float,
    ): Bounds {
        require(availableWidthDp > 0 && availableHeightDp > 0)
        val compact =
            availableWidthDp < PHONE_UI_RULES_BOUNDS_VALUE_420 ||
                availableHeightDp < PHONE_UI_RULES_BOUNDS_VALUE_500
        return Bounds(
            compact,
            if (compact) 1 else PHONE_UI_RULES_BOUNDS_VALUE_2,
            if (compact) PHONE_UI_RULES_BOUNDS_VALUE_46 else PHONE_UI_RULES_BOUNDS_VALUE_52,
        )
    }

    fun dialMatches(
        typed: String,
        contacts: List<Match>,
        limit: Int,
    ): List<Match> {
        val digits = typed.filter(Char::isDigit)
        if (digits.length < 2 || limit < 1) return emptyList()
        return contacts
            .asSequence()
            .filter { it.number.filter(Char::isDigit).contains(digits) }
            .distinctBy { it.contactId }
            .take(limit)
            .toList()
    }

    fun initials(displayName: String): String =
        displayName
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifEmpty { "?" }

    fun firstLetter(name: String): String =
        name.firstOrNull()?.uppercaseChar()?.let {
            if (it in 'A'..'Z') it.toString() else "#"
        } ?: "#"
}
