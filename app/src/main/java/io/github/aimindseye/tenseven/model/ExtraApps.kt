package io.github.aimindseye.tenseven.model

enum class ExtraGroup { Tools, Games }

/** One optional app that ships inside Tenseven; [className] is its launcher activity. */
data class ExtraApp(
    val id: String,
    val label: String,
    val summary: String,
    val className: String,
    val glyph: AppGlyph,
    val group: ExtraGroup,
)

/**
 * The extra apps that come with Tenseven (ported from SableOS): Calculator with a unit converter, Notes, and ten
 * games. None is on by default. Setup lets the user turn each on; an app that is off has no launcher entry. Pure
 * policy, unit tested.
 */
object ExtraApps {
    private const val ROOT = "io.github.aimindseye.tenseven.extras."

    // SableOS: none. Each extra duplicated a Sable app (Calculator, Notes, Profiles, Tasks, Games,
    // Phone, Calendar), so the SableOS home ships without them.
    val ALL: List<ExtraApp> = emptyList()

    private val byClass = ALL.associateBy { it.className }

    fun byClassName(className: String): ExtraApp? = byClass[className]

    fun isExtra(className: String): Boolean = className in byClass

    fun ofGroup(group: ExtraGroup): List<ExtraApp> = ALL.filter { it.group == group }

    /** Drops unknown ids; the default (nothing chosen) is the empty set. */
    fun normalize(ids: Collection<String>?): Set<String> = ids.orEmpty().filter { id -> ALL.any { it.id == id } }.toSet()

    /** What to switch on and off to move from [current] to [wanted], by id. */
    fun changes(
        current: Set<String>,
        wanted: Set<String>,
    ): Pair<Set<String>, Set<String>> {
        val target = normalize(wanted)
        val now = normalize(current)
        return (target - now) to (now - target)
    }
}
