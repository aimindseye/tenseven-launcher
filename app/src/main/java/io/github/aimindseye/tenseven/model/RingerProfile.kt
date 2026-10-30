package io.github.aimindseye.tenseven.model

// AudioManager.RINGER_MODE_*: kept as numbers so this stays plain Kotlin.
const val RINGER_SILENT = 0
const val RINGER_VIBRATE = 1
const val RINGER_NORMAL = 2

/** Which line icon shows a ringer profile (the drawing is in the UI). */
enum class RingerIcon { Sound, Vibrate, Silent }

/** The phone's ringer profile, as the speaker icon on the home screens shows and cycles it. */
enum class RingerProfile(
    val ringerMode: Int,
    val icon: RingerIcon,
    val label: String,
) {
    Sound(RINGER_NORMAL, RingerIcon.Sound, "Sound"),
    Vibrate(RINGER_VIBRATE, RingerIcon.Vibrate, "Vibrate"),
    Silent(RINGER_SILENT, RingerIcon.Silent, "Silent"),
    ;

    /** The profile one tap switches to. Silent is only offered once Android allows it ([canSilence]). */
    fun next(canSilence: Boolean): RingerProfile =
        when (this) {
            Sound -> Vibrate
            Vibrate -> if (canSilence) Silent else Sound
            Silent -> Sound
        }

    companion object {
        fun fromRingerMode(mode: Int): RingerProfile = entries.firstOrNull { it.ringerMode == mode } ?: Sound
    }
}
