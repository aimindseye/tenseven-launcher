package io.github.aimindseye.tenseven.model

/** How big the BlackBerry 7 tray's icons are; the cell width sets how many fit in a row. */
enum class Bb7IconSize(
    val stable: Int,
    val cellDp: Int,
    val iconDp: Int,
    val title: String,
) {
    Small(0, 56, 34, "Small"),
    Medium(1, 64, 40, "Medium"),
    Large(2, 76, 48, "Large"),
    ;

    companion object {
        val DEFAULT = Medium

        fun fromStable(value: Int?): Bb7IconSize = entries.firstOrNull { it.stable == value } ?: DEFAULT
    }
}

/** The widgets Launcher Settings can switch on or off; they are the Glance widgets of the BlackBerry 10 home too. */
object SettingsWidgets {
    val TOGGLEABLE: List<String> = listOf(GlanceWidgets.CALENDAR, GlanceWidgets.ALARM, GlanceWidgets.MEDIA)

    /** The BlackBerry 7 home's widget strip: the toggleable widgets that are on, in a fixed order. */
    fun strip(shown: List<String>): List<String> = TOGGLEABLE.filter { it in shown }
}
