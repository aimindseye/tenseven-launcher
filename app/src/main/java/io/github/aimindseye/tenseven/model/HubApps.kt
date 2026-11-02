package io.github.aimindseye.tenseven.model

/**
 * Explicitly selected notification-source apps. Android's notification-listener grant does not
 * imply consent to display any installed app in Hub. Empty/missing selection is intentionally
 * fail-closed, including on upgrades from the former "excluded apps" denylist.
 */
object HubApps {
    fun isIncluded(
        enabled: Set<String>,
        packageName: String,
    ): Boolean = packageName in enabled

    /** Explicitly enable or disable one package; new installed packages stay off. */
    fun toggle(
        enabled: Set<String>,
        packageName: String,
    ): Set<String> = if (packageName in enabled) enabled - packageName else enabled + packageName

    /** One row per package, sorted by name, so an app with two launcher entries or two profiles is listed once. */
    fun rows(apps: List<Pair<String, String>>): List<Pair<String, String>> =
        apps
            .distinctBy { it.first }
            .sortedWith(compareBy({ it.second.lowercase() }, { it.first }))
}
