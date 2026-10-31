package io.github.aimindseye.tenseven.model

/**
 * Which apps feed the Hub. The user's list holds the apps they turned off, so a newly installed app is in the Hub by
 * default and the stored set stays small.
 */
object HubApps {
    fun isIncluded(
        excluded: Set<String>,
        packageName: String,
    ): Boolean = packageName !in excluded

    /** The list after turning [packageName] on or off. */
    fun toggle(
        excluded: Set<String>,
        packageName: String,
    ): Set<String> = if (packageName in excluded) excluded - packageName else excluded + packageName

    /** One row per package, sorted by name, so an app with two launcher entries or two profiles is listed once. */
    fun rows(apps: List<Pair<String, String>>): List<Pair<String, String>> =
        apps
            .distinctBy { it.first }
            .sortedWith(compareBy({ it.second.lowercase() }, { it.first }))
}
