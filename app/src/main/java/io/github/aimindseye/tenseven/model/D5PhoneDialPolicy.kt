package io.github.aimindseye.tenseven.model

/** Named presentation/protocol values; numeric values unchanged from the reviewed source. */
private const val PHONE_DIAL_POLICY_APPEND_VALUE_48 = 48
private const val PHONE_DIAL_POLICY_NORMALIZE_MAX_48 = 48

/** Strict keypad input policy; actual call placement is always delegated to Android ACTION_DIAL. */
object D5PhoneDialPolicy {
    private const val ACCEPTED = "0123456789*#+"

    fun append(
        current: String,
        key: String,
    ): String {
        if (key.length != 1 || key[0] !in ACCEPTED || current
                .length >= PHONE_DIAL_POLICY_APPEND_VALUE_48
        ) {
            return current
        }
        return if (key == "+" && current.isNotEmpty()) current else current + key
    }

    fun normalize(number: String): String = number.filter { it in ACCEPTED }.take(PHONE_DIAL_POLICY_NORMALIZE_MAX_48)

    fun canDial(number: String) = number.isNotBlank() && number.any(Char::isDigit)
}
