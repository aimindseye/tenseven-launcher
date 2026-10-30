package io.github.aimindseye.tenseven.extras

import android.view.KeyEvent

enum class ExtraKeyAction {
    /** Escape: leave the app, as Back does. */
    Leave,

    /** Menu: show the keys of this screen. */
    Help,
}

/**
 * The launcher's key habits in the bundled apps: Escape goes back (and out, from the first screen) and Menu shows
 * the keys, as they do on Home. Applied only to a key the app itself did not use, so typing always wins. Pure,
 * unit tested.
 */
object ExtraKeys {
    fun action(
        keyCode: Int,
        repeatCount: Int,
        alt: Boolean = false,
        ctrl: Boolean = false,
    ): ExtraKeyAction? {
        if (repeatCount > 0 || alt || ctrl) return null
        return when (keyCode) {
            KeyEvent.KEYCODE_ESCAPE -> ExtraKeyAction.Leave
            KeyEvent.KEYCODE_MENU -> ExtraKeyAction.Help
            else -> null
        }
    }
}
