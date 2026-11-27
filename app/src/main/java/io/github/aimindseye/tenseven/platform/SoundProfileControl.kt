package io.github.aimindseye.tenseven.platform

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import io.github.aimindseye.tenseven.model.RINGER_NORMAL
import io.github.aimindseye.tenseven.model.RingerProfile

/**
 * Reads and changes the ringer profile. Sound and Vibrate need no permission. Silent does: Android only lets an app
 * switch to it once the user has given it Do Not Disturb access, so until then a tap cycles Sound and Vibrate, and
 * a long press opens the screen where that access is given.
 */
object SoundProfileControl {
    fun current(context: Context): RingerProfile =
        RingerProfile.fromRingerMode(
            runCatching { context.getSystemService(AudioManager::class.java)?.ringerMode }.getOrNull() ?: RINGER_NORMAL,
        )

    fun canSilence(context: Context): Boolean =
        runCatching { context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true }
            .getOrDefault(false)

    /** Switches to the next profile and returns the one now in effect. */
    fun cycle(context: Context): RingerProfile {
        val now = current(context)
        val target = now.next(canSilence(context))
        runCatching { context.getSystemService(AudioManager::class.java)?.ringerMode = target.ringerMode }
        return current(context)
    }

    /** Opens Do Not Disturb access, where the user lets Tenseven use Silent. */
    fun openSilentAccess(context: Context) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Whether Sable Profiles, the SableOS sound profiles app, is installed. */
    fun profilesEnabled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage("org.sableos.titan2.profiles") != null

    /** Opens Sable Profiles; false (and nothing opened) when it is missing. */
    fun openProfiles(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage("org.sableos.titan2.profiles")
        return intent != null &&
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }
}
