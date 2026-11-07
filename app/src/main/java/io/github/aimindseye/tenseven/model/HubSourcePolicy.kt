package io.github.aimindseye.tenseven.model

/** Android delivers notifications; this policy only applies optional Hub filtering. */
data class HubSourcePolicy(
    val automatic: Boolean = true,
    val selected: Set<String> = emptySet(),
    val hidden: Set<String> = emptySet(),
) {
    fun includes(packageName: String): Boolean =
        packageName.isNotBlank() && if (automatic) packageName !in hidden else packageName in selected

    fun resolve(candidates: Collection<String>): Set<String> = candidates.filter(::includes).toSet()
}
