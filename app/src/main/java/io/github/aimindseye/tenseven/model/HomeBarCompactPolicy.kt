package io.github.aimindseye.tenseven.model

/** Four primary destinations remain visible on compact square devices.
 * User-saved shortcuts are never deleted; on compact screens they remain accessible in the app grid.
 */
internal object HomeBarCompactPolicy {
    fun compact(model: String, widthDp: Int): Boolean =
        model.contains("Q25", ignoreCase = true) || widthDp < 340

    /** The two optional slots at most on Titan 2; role destinations are already in the fixed dock. */
    fun optionalTokens(tokens: List<String>, compact: Boolean): List<String> =
        if (compact) {
            emptyList()
        } else {
            tokens.filter { it == QuickBar.PHONE || it == QuickBar.COMMAND || QuickBar.appKey(it) != null }
                .distinct()
                .take(2)
        }
}
