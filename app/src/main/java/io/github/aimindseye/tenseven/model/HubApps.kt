package io.github.aimindseye.tenseven.model

/**
 * Helpers for optional manual source selection and installed-app filter rows.
 * Automatic notification delivery is governed by HubSourcePolicy; manual mode stays fail-closed.
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
