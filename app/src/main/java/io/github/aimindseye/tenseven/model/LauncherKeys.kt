package io.github.aimindseye.tenseven.model

/** Standard Android key contracts; OEM scan-code remapping requires captured device evidence. */
object LauncherKeys {
    enum class Profile { Touch, MenuKey, ModifierMenu }

    enum class Action { Menu, Command, None }

    const val MENU = 82
    const val ENTER = 66
    const val SPACE = 62
    private const val ALT = 0x02
    private const val FUNCTION = 0x08
    private const val CTRL_META = 0x1000 or 0x10000

    fun profile(
        hasKeyboard: Boolean,
        hasMenu: Boolean,
    ): Profile =
        when {
            !hasKeyboard -> Profile.Touch
            hasMenu -> Profile.MenuKey
            else -> Profile.ModifierMenu
        }

    /** Ctrl/Meta chords belong to apps/IME; repeated key-downs must not open multiple panels. */
    fun action(
        keyCode: Int,
        metaState: Int,
        repeated: Boolean,
    ): Action {
        if (repeated || metaState and CTRL_META != 0) return Action.None
        val modifier = metaState and (ALT or FUNCTION) != 0
        return when {
            keyCode == MENU -> Action.Menu
            modifier && keyCode == ENTER -> Action.Menu
            modifier && keyCode == SPACE -> Action.Command
            else -> Action.None
        }
    }
}
