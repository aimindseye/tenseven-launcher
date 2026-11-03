package io.github.aimindseye.tenseven.platform

import android.app.Activity
import android.os.Build
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.WindowInsets
import io.github.aimindseye.tenseven.model.LauncherKeys

/** Read-only, local compatibility facts. No serial, descriptor, accounts or typed characters. */
object DeviceCompatibility {
    fun keyAction(event: KeyEvent): LauncherKeys.Action =
        LauncherKeys.action(event.keyCode, event.metaState, event.repeatCount > 0)

    fun report(activity: Activity): String {
        val metrics = activity.windowManager.currentWindowMetrics
        val density = activity.resources.displayMetrics.density
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
        val devices =
            InputDevice
                .getDeviceIds()
                .asSequence()
                .mapNotNull(InputDevice::getDevice)
                .filterNot { it.isVirtual }
                .toList()
        val keyboard = devices.any { it.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC }
        val profile = LauncherKeys.profile(keyboard, KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_MENU))
        return buildString {
            appendLine("${Build.MANUFACTURER} / ${Build.MODEL}")
            appendLine("Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}")
            appendLine("Window ${metrics.bounds.width()}×${metrics.bounds.height()} px · density $density")
            appendLine("Font scale ${activity.resources.configuration.fontScale}")
            appendLine("System bars L${insets.left} T${insets.top} R${insets.right} B${insets.bottom} px")
            appendLine("Keyboard profile: $profile")
            devices.forEach {
                appendLine(
                    "${it.name}: keyboard=${it.keyboardType}, sources=${it.sources}, " +
                        "vendor=${it.vendorId}, product=${it.productId}",
                )
            }
            appendLine("Menu or Alt/Fn+Enter: menu · Alt/Fn+Space: search")
            appendLine("OEM Sym/scan-code mappings require on-device capture; no guessed remapping is applied.")
        }
    }

    fun event(event: KeyEvent): String =
        "action=${event.action} key=${event.keyCode} scan=${event.scanCode} meta=${event.metaState} " +
            "repeat=${event.repeatCount} source=${event.source} device=${event.deviceId}"
}
