package io.github.aimindseye.tenseven.model

/** The phone's ringer profile, as the speaker icon on the home screens shows and cycles it. */
enum class SoundProfile(
    val ringerMode: Int,
    val symbol: String,
    val label: String,
) {
    Sound(RINGER_NORMAL, "🔊", "Sound"),
    Vibrate(RINGER_VIBRATE, "📳", "Vibrate"),
    Silent(RINGER_SILENT, "🔇", "Silent"),
    ;

    /** The profile one tap switches to. Silent is only offered once Android allows it ([canSilence]). */
    fun next(canSilence: Boolean): SoundProfile =
        when (this) {
            Sound -> Vibrate
            Vibrate -> if (canSilence) Silent else Sound
            Silent -> Sound
        }

    companion object {
        // AudioManager.RINGER_MODE_*: kept as numbers so this stays plain Kotlin.
        const val RINGER_SILENT = 0
        const val RINGER_VIBRATE = 1
        const val RINGER_NORMAL = 2

        fun fromRingerMode(mode: Int): SoundProfile = entries.firstOrNull { it.ringerMode == mode } ?: Sound
    }
}
