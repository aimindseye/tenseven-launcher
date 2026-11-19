package io.github.aimindseye.tenseven.ui

import io.github.aimindseye.tenseven.model.LauncherTextSize

internal data class HeaderOptions(
    val size: LauncherTextSize,
    val showWeather: Boolean,
    val city: String,
    val useDeviceLocation: Boolean,
    val statusSize: io.github.aimindseye.tenseven.model.HomeStatusSize,
)
