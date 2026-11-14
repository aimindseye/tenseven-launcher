package io.github.aimindseye.tenseven.ui

import io.github.aimindseye.tenseven.model.AppGrid
import io.github.aimindseye.tenseven.model.GlanceWidgets
import io.github.aimindseye.tenseven.model.HomeLayout
import io.github.aimindseye.tenseven.model.QuickBar

internal fun startHomeEditDefaults(
    preferredFirst: List<String>,
    installed: List<String>,
): HomeEdit =
    HomeEdit(
        gridOrder = AppGrid.resolve(saved = null, preferredFirst = preferredFirst, installed = installed),
        quickBar = QuickBar.DEFAULT,
        glanceWidgets = GlanceWidgets.DEFAULT,
        hiddenSections = HomeLayout.DEFAULT_HIDDEN,
    )
