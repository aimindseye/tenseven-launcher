package io.github.aimindseye.tenseven.model

/** Named presentation/protocol values; numeric values unchanged from the reviewed source. */
private const val HOME_BAR_COMPACT_POLICY_COMPACT_VALUE_340 = 340

/** Four primary destinations remain visible on compact square devices.
 * User-saved shortcuts are never deleted; on compact screens they remain accessible in the app grid.
 */
internal object HomeBarCompactPolicy {
    fun compact(
        model: String,
        widthDp: Int,
    ): Boolean = model.contains("Q25", ignoreCase = true) || widthDp < HOME_BAR_COMPACT_POLICY_COMPACT_VALUE_340

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
