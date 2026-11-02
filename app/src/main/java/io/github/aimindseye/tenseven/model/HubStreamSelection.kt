package io.github.aimindseye.tenseven.model

/** Pure, in-memory source selection for the unified Hub. Multiple mail and chat clients may
 * coexist when explicitly enabled; selecting a source narrows rows without dropping data.
 */
object HubStreamSelection {
    fun <T> apply(rows: List<T>, sourcePackage: String?, packages: (T) -> List<String>): List<T> =
        if (sourcePackage == null) rows else rows.filter { sourcePackage in packages(it) }

    fun options(sources: List<Pair<String, String>>): List<Pair<String, String>> =
        sources.distinctBy { it.first }.sortedWith(compareBy({ it.second.lowercase() }, { it.first }))
}
