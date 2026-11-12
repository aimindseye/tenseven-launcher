package io.github.aimindseye.tenseven.companions

/**
 * Proposed independent package identities. These are NOT verified/registered release identities.
 * In particular, a matching package name does not establish APK authenticity.
 */
data class CompanionSpec(
    val id: String,
    val label: String,
    val packageName: String,
    val minimumPreset: Boolean,
)

object CompanionCatalog {
    val all: List<CompanionSpec> =
        listOf(
            CompanionSpec("phone", "Phone", "io.github.aimindseye.tenseven.phone", false),
            CompanionSpec("calendar", "Calendar", "io.github.aimindseye.tenseven.calendar", false),
            CompanionSpec("tasks", "Tasks / Remind", "io.github.aimindseye.tenseven.tasks", true),
            CompanionSpec("notes", "Notes", "io.github.aimindseye.tenseven.notes", true),
            CompanionSpec("calculator", "Calc Plus", "io.github.aimindseye.tenseven.calculator", true),
            CompanionSpec("profiles", "Sound Profiles", "io.github.aimindseye.tenseven.profiles", false),
            CompanionSpec("games", "Games Pack (10 games)", "io.github.aimindseye.tenseven.games", false),
        )

    val minimumPreset: Set<String> = all.filter { it.minimumPreset }.map { it.id }.toSet()
    val fullPreset: Set<String> = all.map { it.id }.toSet()

    /** Ignores obsolete or unrecognized input without toggling bundled component settings. */
    fun normalize(ids: Collection<String>): Set<String> = ids.filterTo(mutableSetOf()) { it in fullPreset }
}
