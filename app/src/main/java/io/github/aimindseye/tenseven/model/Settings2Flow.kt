package io.github.aimindseye.tenseven.model

/** Six user-facing Settings pages in the Image 1 sequence; Hub is an Activity. */
object Settings2Flow {
    val ordered: List<String> =
        listOf(
            "permissions",
            "appearance_home",
            "hub",
            "lock",
            "frames",
            "review",
        )

    /** Preserve saved/deep-linked destinations while retiring the bundled-app page. */
    fun destination(candidate: String?): String =
        when (candidate) {
            "apps" -> "companions"
            "home" -> "appearance_home"
            in ordered, "themes", "companions" -> candidate.orEmpty()
            else -> "root"
        }

    fun previous(page: String): String? =
        when (page) {
            "root" -> null
            "themes" -> "appearance_home"
            "companions", "apps" -> "root"
            else -> ordered.indexOf(page).let { index -> if (index <= 0) "root" else ordered[index - 1] }
        }

    fun next(page: String): String? =
        when (page) {
            "root" -> {
                ordered.first()
            }

            "companions", "apps" -> {
                "root"
            }

            "themes" -> {
                "appearance_home"
            }

            else -> {
                ordered.indexOf(page).let { index ->
                    if (index < 0 || index == ordered.lastIndex) null else ordered[index + 1]
                }
            }
        }

    fun position(page: String): Int? = ordered.indexOf(page).takeIf { it >= 0 }?.plus(1)
}
