package io.github.aimindseye.tenseven.companions

/** User choice only: a selection never changes installed packages or bundled extras. */
enum class CompanionPreset {
    MINIMUM,
    FULL,
    CUSTOM,
}

object CompanionSetupPlan {
    fun choosePreset(
        preset: CompanionPreset,
        current: Set<String>,
    ): Set<String> =
        when (preset) {
            CompanionPreset.MINIMUM -> CompanionCatalog.minimumPreset
            CompanionPreset.FULL -> CompanionCatalog.fullPreset
            CompanionPreset.CUSTOM -> CompanionCatalog.normalize(current)
        }

    fun selectedPreset(selected: Collection<String>): CompanionPreset {
        val normalized = CompanionCatalog.normalize(selected)
        return when (normalized) {
            CompanionCatalog.minimumPreset -> CompanionPreset.MINIMUM
            CompanionCatalog.fullPreset -> CompanionPreset.FULL
            else -> CompanionPreset.CUSTOM
        }
    }

    fun toggle(
        selected: Collection<String>,
        id: String,
        enabled: Boolean,
    ): Set<String> {
        val normalized = CompanionCatalog.normalize(selected)
        if (id !in CompanionCatalog.fullPreset) return normalized
        return if (enabled) normalized + id else normalized - id
    }

    /** Stable, non-sensitive ids only. No private app records, APK paths, or certificates. */
    fun encode(selected: Collection<String>): String =
        CompanionCatalog
            .normalize(selected)
            .sorted()
            .joinToString(",")

    fun decode(encoded: String?): Set<String> =
        CompanionCatalog.normalize(
            encoded
                .orEmpty()
                .split(",")
                .filter { it.isNotBlank() },
        )
}
