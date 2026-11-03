package io.github.aimindseye.tenseven.ui

import android.content.Context
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.model.CoreAppIdentity
import io.github.aimindseye.tenseven.model.inLauncherUser
import io.github.aimindseye.tenseven.model.stableKey
import io.github.aimindseye.tenseven.platform.DefaultApps

internal fun defaultHomeApps(
    apps: List<AppEntry>,
    context: Context,
): List<AppEntry> {
    val personal = apps.filter { it.inLauncherUser }
    // The phone's own default for each group first (Messages, Mail, Calendar, Contacts, Phone, Camera), then the rest.
    val defaults =
        listOf(
            DefaultApps.packageFor(context, DefaultApps.MESSAGING),
            DefaultApps.packageFor(context, DefaultApps.EMAIL),
            DefaultApps.packageFor(context, DefaultApps.CALENDAR),
            DefaultApps.packageFor(context, DefaultApps.CONTACTS),
            DefaultApps.dialerPackage(context),
            DefaultApps.cameraPackage(context),
        )
    return HOME_APP_DEFAULT_GROUPS
        .mapIndexedNotNull { index, group ->
            val phoneDefault = defaults.getOrNull(index)?.let { pkg -> personal.firstOrNull { it.component.packageName == pkg } }
            phoneDefault
                ?: group.packageNames.firstNotNullOfOrNull { packageName ->
                    personal.firstOrNull { CoreAppIdentity.canonicalPackage(it.component.packageName) == packageName }
                }
                ?: personal.firstOrNull { app -> group.labels.any { app.label.equals(it, ignoreCase = true) } }
        }.distinctBy { it.stableKey() }
}

internal data class PreferredStartAppGroup(
    /** Canonical packages for this tile, best first. */
    val packageNames: List<String> = emptyList(),
    val labels: List<String>,
)

internal val HOME_APP_DEFAULT_GROUPS =
    listOf(
        PreferredStartAppGroup(
            packageNames = listOf(MESSAGES_PACKAGE, HUB_PACKAGE),
            labels = listOf(MESSAGES_LABEL, "Messages", HUB_LABEL, "Hub"),
        ),
        PreferredStartAppGroup(
            packageNames = listOf(MAIL_PACKAGE),
            labels = listOf(MAIL_LABEL, "Mail"),
        ),
        PreferredStartAppGroup(
            packageNames = listOf(CALENDAR_PACKAGE),
            labels = listOf("Sable Calendar", "Calendar"),
        ),
        PreferredStartAppGroup(
            packageNames = listOf(CONTACTS_PACKAGE),
            labels = listOf("Contacts"),
        ),
        PreferredStartAppGroup(
            packageNames = listOf(DIALER_PACKAGE),
            labels = listOf("Phone"),
        ),
        PreferredStartAppGroup(
            packageNames = CoreAppIdentity.startTileCandidates(CoreAppIdentity.CAMERA),
            labels = listOf("Camera"),
        ),
    )
