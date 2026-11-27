package io.github.aimindseye.tenseven.model

/** Named presentation/protocol values; numeric values unchanged from the reviewed source. */
private const val HOME_BAR_COMPACT_POLICY_COMPACT_VALUE_340 = 340

/** Four primary destinations remain visible on compact square devices.
 * User-saved shortcuts are never deleted; on compact screens they remain accessible in the app grid.
 */
internal object HomeBarCompactPolicy {
    fun compact(
        profile: DeviceProfile,
        widthDp: Int,
    ): Boolean = profile.compactHomeBar || widthDp < HOME_BAR_COMPACT_POLICY_COMPACT_VALUE_340

    /** Name-based entry point kept for callers that only have the build model. */
    fun compact(
        model: String,
        widthDp: Int,
    ): Boolean = compact(DeviceProfile.detect("", model, "", hasQwerty = false, hasArrows = false), widthDp)

    /** The two optional slots at most on Titan 2; role destinations are already in the fixed dock. */
    fun optionalTokens(
        tokens: List<String>,
        compact: Boolean,
    ): List<String> =
        if (compact) {
            emptyList()
        } else {
            tokens
                .filter { it == QuickBar.PHONE || it == QuickBar.COMMAND || QuickBar.appKey(it) != null }
                .distinct()
                .take(2)
        }
}
