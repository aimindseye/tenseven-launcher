package io.github.aimindseye.tenseven.model

/** Strict keypad input policy; actual call placement is always delegated to Android ACTION_DIAL. */
object D5PhoneDialPolicy {
    private val accepted = "0123456789*#+"

    fun append(
        current: String,
        key: String,
    ): String {
        if (key.length != 1 || key[0] !in accepted || current.length >= 48) return current
        return if (key == "+" && current.isNotEmpty()) current else current + key
    }

    fun normalize(number: String): String = number.filter { it in accepted }.take(48)

    fun canDial(number: String) = number.isNotBlank() && number.any(Char::isDigit)
}
