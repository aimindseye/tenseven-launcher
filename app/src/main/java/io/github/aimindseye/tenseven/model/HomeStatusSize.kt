package io.github.aimindseye.tenseven.model

/** Named presentation/protocol values; numeric values unchanged from the reviewed source. */
private const val HOME_STATUS_SIZE_TOP_RATIO_1_10_F = 1.10f
private const val HOME_STATUS_SIZE_TOP_RATIO_1_27_F = 1.27f

/** Compact, independent BB7/BB10 shared status size; one-row layout remains authoritative. */
enum class HomeStatusSize(
    val stableValue: String,
    val multiplier: Float,
    val title: String,
    val description: String,
) {
    Default("default", HOME_STATUS_SIZE_TOP_RATIO_1_10_F, "Default", "Balanced for the selected Home layout"),
    Larger(
        "larger",
        HOME_STATUS_SIZE_TOP_RATIO_1_27_F,
        "Larger",
        "One step bigger; constrained to the available width",
    ),
    ;

    companion object {
        fun decode(value: String?): HomeStatusSize = entries.firstOrNull { it.stableValue == value } ?: Default
    }
}
