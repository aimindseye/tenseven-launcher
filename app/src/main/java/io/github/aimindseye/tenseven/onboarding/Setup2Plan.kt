package io.github.aimindseye.tenseven.onboarding

import io.github.aimindseye.tenseven.model.ExtraApps

/** Seven user-facing stages; keep the existing SetupStep deep links and permission flows intact. */
internal enum class Setup2Stage(
    val title: String,
) {
    WELCOME("Welcome & Home role"),
    HOME("Home screens"),
    APPEARANCE("Appearance & themes"),
    APPS("Apps & features"),
    HUB_LOCK("Hub & lock screen"),
    PERMISSIONS("Device & permissions"),
    REVIEW("Review & finish"),
}

/** Mapping covers every legacy setup choice exactly once, without changing its persisted key. */
internal object Setup2Plan {
    val stages: List<Setup2Stage> = Setup2Stage.entries

    fun steps(stage: Setup2Stage): List<SetupStep> =
        when (stage) {
            Setup2Stage.WELCOME -> listOf(SetupStep.HOME)
            Setup2Stage.HOME -> listOf(SetupStep.STYLE, SetupStep.GRID, SetupStep.WIDGETS)
            Setup2Stage.APPEARANCE -> listOf(SetupStep.LOOK)
            Setup2Stage.APPS -> listOf(SetupStep.EXTRAS)
            Setup2Stage.HUB_LOCK -> listOf(SetupStep.HUB, SetupStep.LOCK)
            Setup2Stage.PERMISSIONS -> listOf(SetupStep.CONTACTS, SetupStep.PHOTOS, SetupStep.NOTIFICATIONS, SetupStep.USAGE)
            Setup2Stage.REVIEW -> emptyList()
        }

    /** Presets choose only real, currently bundled app components. Calendar/Phone arrive later. */
    val essentials: Set<String> = setOf("calculator", "tasks", "notes")

    fun full(): Set<String> = ExtraApps.ALL.map { it.id }.toSet()

    fun normalizedCustom(ids: Collection<String>): Set<String> = ExtraApps.normalize(ids)
}
