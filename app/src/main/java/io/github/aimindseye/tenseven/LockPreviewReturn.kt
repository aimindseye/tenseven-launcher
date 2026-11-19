package io.github.aimindseye.tenseven

import android.content.Context
import android.content.Intent

/** Preview return targets are local constants, never a class name forwarded from an Intent. */
internal fun lockPreviewReturnIntent(
    context: Context,
    destination: String?,
): Intent? {
    val target =
        when (destination) {
            LauncherSettingsActivity::class.java.name -> LauncherSettingsActivity::class.java
            OnboardingActivity::class.java.name -> OnboardingActivity::class.java
            else -> return null
        }
    return Intent(context, target).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
}
