package io.github.aimindseye.tenseven.model

/** Compact, independent BB7/BB10 shared status size; one-row layout remains authoritative. */
enum class HomeStatusSize(
    val stableValue: String,
    val multiplier: Float,
    val title: String,
    val description: String,
) {
    Default("default", 1.10f, "Default", "Balanced for the selected Home layout"),
    Larger("larger", 1.27f, "Larger", "One step bigger; constrained to the available width"),
    ;

    companion object {
        fun decode(value: String?): HomeStatusSize = entries.firstOrNull { it.stableValue == value } ?: Default
    }
}
