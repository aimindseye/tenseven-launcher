package io.github.aimindseye.tenseven.design

import android.view.KeyEvent
import io.github.aimindseye.tenseven.model.DeviceProfile

/**
 * I J K L stand in for the arrow keys on keyboard phones that have none (see [DeviceProfile.letterMoves]).
 * Pure, so the mapping is unit tested off-device. Typing wins: a focused text field, or any modifier, keeps the
 * letter.
 */
object LetterMoveKeys {
    private const val MODIFIERS =
        KeyEvent.META_ALT_ON or KeyEvent.META_CTRL_ON or KeyEvent.META_META_ON or KeyEvent.META_SYM_ON

    /** The d-pad key for a letter key, or null when the key must reach the app unchanged. */
    fun remap(
        profile: DeviceProfile,
        keyCode: Int,
        metaState: Int,
        typing: Boolean,
    ): Int? =
        when {
            !profile.letterMoves || typing || (metaState and MODIFIERS) != 0 -> null
            keyCode == KeyEvent.KEYCODE_I -> KeyEvent.KEYCODE_DPAD_UP
            keyCode == KeyEvent.KEYCODE_J -> KeyEvent.KEYCODE_DPAD_LEFT
            keyCode == KeyEvent.KEYCODE_K -> KeyEvent.KEYCODE_DPAD_DOWN
            keyCode == KeyEvent.KEYCODE_L -> KeyEvent.KEYCODE_DPAD_RIGHT
            else -> null
        }

    /** Returns [event] with a mapped key code, or null when it is not a letter move. */
    fun translate(
        profile: DeviceProfile,
        event: KeyEvent,
        typing: Boolean,
    ): KeyEvent? =
        remap(profile, event.keyCode, event.metaState, typing)?.let { code ->
            KeyEvent(
                event.downTime,
                event.eventTime,
                event.action,
                code,
                event.repeatCount,
                event.metaState,
                event.deviceId,
                event.scanCode,
                event.flags,
                event.source,
            )
        }
}
