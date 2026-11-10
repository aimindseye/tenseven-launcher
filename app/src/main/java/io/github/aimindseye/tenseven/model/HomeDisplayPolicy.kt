package io.github.aimindseye.tenseven.model

/** Per-home-style visibility preferences: neither BB7 nor BB10 overwrites the other. */
object HomeDisplayPolicy {
    const val WIFI = "wifi"
    const val MOBILE = "mobile"
    const val CPU = "cpu"
    const val RAM = "ram"
    const val BATTERY = "battery"
    const val CELL = "cell"
    const val STORAGE = "storage"
    val ALL_DIALS: Set<String> = linkedSetOf(WIFI, MOBILE, CPU, RAM, BATTERY, CELL, STORAGE)
    val DEFAULT_DIALS: Set<String> = ALL_DIALS // New installs only; explicit saved selections remain authoritative

    fun normalizedDials(selected: Set<String>): Set<String> = selected.intersect(ALL_DIALS)

    fun dialPreference(style: HomeStyle): String = if (style == HomeStyle.Bb7) "home_dials_bb7" else "home_dials_bb10"

    fun stripPreference(style: HomeStyle): String =
        if (style == HomeStyle.Bb7) "home_notification_strip_bb7" else "home_notification_strip_bb10"

    fun stripExpandedPreference(style: HomeStyle): String =
        if (style == HomeStyle.Bb7) "home_notification_expanded_bb7" else "home_notification_expanded_bb10"
}
