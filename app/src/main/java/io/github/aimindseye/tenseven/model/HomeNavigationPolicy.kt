package io.github.aimindseye.tenseven.model

/** Display policy only. Reserved Android call/end keys are never intercepted by this policy. */
object HomeNavigationPolicy {
    fun compact(
        hasKeyboard: Boolean,
        hasCallKey: Boolean,
        hasEndKey: Boolean,
    ): Boolean = hasKeyboard && hasCallKey && hasEndKey

    /** The reference bar owns Hub and Camera; never erase a user's saved custom app shortcuts. */
    fun shortcuts(
        tokens: List<String>,
        compact: Boolean,
    ): List<String> =
        if (compact && tokens == QuickBar.DEFAULT) {
            emptyList()
        } else {
            tokens.filterNot { it == QuickBar.HUB || it == QuickBar.CAMERA || it == QuickBar.ALL_APPS }
        }
}
