package io.github.aimindseye.tenseven.model

/** Release 2.0 status presentation is independent for BB7 and BB10. */
enum class HomeStatusMode(val key: String) {
    Dials("dials"),
    Text("text"),
    Hidden("hidden");

    companion object {
        /** Migration: a legacy explicitly empty dial set was equivalent to hidden. */
        fun decode(saved: String?, legacyDialCount: Int): HomeStatusMode =
            entries.firstOrNull { it.key == saved }
                ?: if (legacyDialCount == 0) Hidden else Dials

        fun preference(style: HomeStyle): String =
            if (style == HomeStyle.Bb7) "home_status_mode_bb7" else "home_status_mode_bb10"
    }
}
