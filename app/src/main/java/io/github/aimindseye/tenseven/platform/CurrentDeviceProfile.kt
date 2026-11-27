package io.github.aimindseye.tenseven.platform

import android.content.res.Configuration
import android.os.Build
import io.github.aimindseye.tenseven.model.DeviceProfile

/**
 * The only place that reads the build's identity for device decisions (a contract test keeps it that way).
 * Everything else asks the [DeviceProfile].
 */
object CurrentDeviceProfile {
    fun from(configuration: Configuration): DeviceProfile =
        DeviceProfile.detect(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            hasQwerty = configuration.keyboard == Configuration.KEYBOARD_QWERTY,
            hasArrows =
                configuration.navigation == Configuration.NAVIGATION_DPAD ||
                    configuration.navigation == Configuration.NAVIGATION_TRACKBALL,
        )
}
