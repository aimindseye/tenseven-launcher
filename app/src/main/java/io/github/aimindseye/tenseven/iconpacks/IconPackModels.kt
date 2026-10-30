// Derived from MinkLauncher OpenSource (github.com/katoaapps/openminilaunch, commit b9b26f7),
// features/iconpacks/{IconPackContract,IconPackModels,IconPackXmlParser}.kt, Copyright 2026 Katoa Apps,
// Apache License 2.0. Changed: package, visibility, a shortened model without preview icons.
package io.github.aimindseye.tenseven.iconpacks

/** External launcher contracts understood by Tenseven: Nova and ADW icon packs. */
object IconPackContract {
    val discoveryActions =
        listOf(
            "com.novalauncher.THEME",
            "org.adw.ActivityStarter.THEMES",
        )
    const val MAPPING_RESOURCE_NAME = "appfilter"
}

sealed interface IconPackDrawable {
    data class Static(val drawableName: String) : IconPackDrawable

    data class Calendar(val drawablePrefix: String) : IconPackDrawable

    fun drawableName(dayOfMonth: Int): String =
        when (this) {
            is Static -> drawableName
            is Calendar -> drawablePrefix + dayOfMonth.coerceIn(1, 31)
        }
}

/** An appfilter mapping: `package/class` to a drawable, with a package-level fallback. */
data class IconPackDefinition(
    val iconsByComponent: Map<String, IconPackDrawable>,
) {
    val iconsByPackage: Map<String, IconPackDrawable> =
        iconsByComponent.entries
            .groupBy { it.key.substringBefore('/') }
            .mapValues { (_, mappings) ->
                mappings.firstOrNull { it.value is IconPackDrawable.Calendar }?.value ?: mappings.first().value
            }

    fun iconForComponent(
        componentName: String,
        packageName: String,
    ): IconPackDrawable? = iconsByComponent[componentName] ?: iconsByPackage[packageName]
}

/** An installed icon pack as listed in Appearance. */
data class InstalledIconPack(
    val packageName: String,
    val label: String,
)

object IconPackComponents {
    /** `ComponentInfo{pkg/.Cls}`, `pkg/.Cls` and `pkg/pkg.Cls` all become `pkg/pkg.Cls`; anything else is null. */
    fun normalized(value: String): String? {
        val flattened = value.trim().removePrefix("ComponentInfo{").removeSuffix("}")
        val separator = flattened.indexOf('/')
        if (separator <= 0 || separator == flattened.lastIndex) return null
        val packageName = flattened.substring(0, separator)
        val rawClassName = flattened.substring(separator + 1)
        val className = if (rawClassName.startsWith('.')) packageName + rawClassName else rawClassName
        return "$packageName/$className"
    }

    /** Nova requires calendar entries to win even when a static item appears later in the XML. */
    fun combine(
        staticMappings: Map<String, IconPackDrawable>,
        calendarMappings: Map<String, IconPackDrawable>,
    ): Map<String, IconPackDrawable> = staticMappings + calendarMappings
}
