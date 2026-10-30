// Derived from MinkLauncher OpenSource (github.com/katoaapps/openminilaunch, commit b9b26f7),
// features/iconpacks/{IconPackXmlParser,IconPackDefinitionLoader,IconPackDiscovery,IconPackDrawableLoader}.kt,
// Copyright 2026 Katoa Apps, Apache License 2.0. Changed: merged into one file, package, simplified.
package io.github.aimindseye.tenseven.iconpacks

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Xml
import androidx.core.content.ContextCompat
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.LocalDate

/** Platform side of icon packs: discovery, appfilter reading and drawable loading. */
object IconPackLoader {
    fun parse(parser: XmlPullParser): IconPackDefinition {
        val staticMappings = linkedMapOf<String, IconPackDrawable>()
        val calendarMappings = linkedMapOf<String, IconPackDrawable>()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                val component = parser.getAttributeValue(null, "component")?.let(IconPackComponents::normalized)
                when (parser.name) {
                    "item" -> {
                        val drawable = parser.getAttributeValue(null, "drawable")
                        if (component != null && !drawable.isNullOrBlank()) {
                            staticMappings[component] = IconPackDrawable.Static(drawable)
                        }
                    }
                    "calendar" -> {
                        val prefix = parser.getAttributeValue(null, "prefix")
                        if (component != null && !prefix.isNullOrBlank()) {
                            calendarMappings[component] = IconPackDrawable.Calendar(prefix)
                        }
                    }
                }
            }
            parser.next()
        }
        return IconPackDefinition(IconPackComponents.combine(staticMappings, calendarMappings))
    }

    /** Packs that advertise a supported contract, labelled, sorted by name. */
    fun installed(context: Context): List<InstalledIconPack> {
        val packageManager = context.packageManager
        return IconPackContract.discoveryActions
            .flatMap { action -> queryThemeActivities(packageManager, action) }
            .distinctBy { it.activityInfo.packageName }
            .map { InstalledIconPack(it.activityInfo.packageName, it.loadLabel(packageManager).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    @Suppress("DEPRECATION")
    private fun queryThemeActivities(
        packageManager: PackageManager,
        action: String,
    ) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(Intent(action), PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
    } else {
        packageManager.queryIntentActivities(Intent(action), PackageManager.MATCH_ALL)
    }

    /** appfilter from resources (xml, then raw), then assets; a malformed first choice does not hide a valid fallback. */
    fun loadDefinition(
        context: Context,
        packageName: String,
    ): IconPackDefinition? {
        val resources =
            runCatching { context.packageManager.getResourcesForApplication(packageName) }.getOrNull() ?: return null
        val name = IconPackContract.MAPPING_RESOURCE_NAME
        val attempts: List<() -> IconPackDefinition?> =
            listOf(
                {
                    resources.getIdentifier(name, "xml", packageName).takeIf { it != 0 }?.let { parseXml(resources, it) }
                },
                {
                    resources.getIdentifier(name, "raw", packageName).takeIf { it != 0 }
                        ?.let { resources.openRawResource(it).use(::parseStream) }
                },
                { resources.assets.open("$name.xml").use(::parseStream) },
            )
        return attempts.firstNotNullOfOrNull { attempt -> runCatching(attempt).getOrNull() }
    }

    private fun parseXml(
        resources: Resources,
        resourceId: Int,
    ): IconPackDefinition {
        val parser = resources.getXml(resourceId)
        return try {
            parse(parser)
        } finally {
            parser.close()
        }
    }

    private fun parseStream(input: InputStream): IconPackDefinition {
        val parser = Xml.newPullParser().apply { setInput(input, Charsets.UTF_8.name()) }
        return parse(parser)
    }

    fun loadDrawable(
        context: Context,
        packageName: String,
        icon: IconPackDrawable,
        dayOfMonth: Int = LocalDate.now().dayOfMonth,
    ): Drawable? =
        runCatching {
            val packContext = context.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY)
            val resources = packContext.resources
            val drawableName = icon.drawableName(dayOfMonth)
            val resourceId =
                resources.getIdentifier(drawableName, "drawable", packageName).takeIf { it != 0 }
                    ?: resources.getIdentifier(drawableName, "mipmap", packageName).takeIf { it != 0 }
            resourceId?.let { ContextCompat.getDrawable(packContext, it) }
        }.getOrNull()
}
