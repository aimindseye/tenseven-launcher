package io.github.aimindseye.tenseven.platform

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.media.AudioManager
import android.provider.Settings
import io.github.aimindseye.tenseven.model.RingerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class SoundProfileControlTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()
    private val audio get() = context.getSystemService(AudioManager::class.java)
    private val notifications get() = context.getSystemService(NotificationManager::class.java)

    @Before
    fun isolateAndroidAudioState() {
        shadowOf(notifications).setNotificationPolicyAccessGranted(false)
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
    }

    @Test
    fun absentCompanionDoesNotLaunchOrChangeSound() {
        assertFalse(SoundProfileControl.profilesEnabled(context))
        assertFalse(SoundProfileControl.openProfiles(context))
        assertNull(shadowOf(context).nextStartedActivity)
        assertEquals(RingerProfile.Sound, SoundProfileControl.current(context))
    }

    @Test
    fun installedCompanionIgnoresObsoleteSelectionWithoutRewritingIt() {
        registerCompanion()
        val preferences = context.getSharedPreferences("l0_optional_app_choices", Context.MODE_PRIVATE)
        preferences.edit().putStringSet("selected", emptySet()).commit()
        val before = preferences.all.toMap()
        assertTrue(SoundProfileControl.profilesEnabled(context))
        assertEquals(before, preferences.all)
    }

    @Test
    fun companionLaunchIsExplicitAndDoesNotChangeTheRinger() {
        registerCompanion()
        assertTrue(SoundProfileControl.openProfiles(context))
        val opened = shadowOf(context).nextStartedActivity
        assertEquals(PROFILES, opened.component?.packageName)
        assertEquals(RingerProfile.Sound, SoundProfileControl.current(context))
        assertFalse(SoundProfileControl.canSilence(context))
    }

    @Test
    fun withoutDndAccessOnlySoundAndVibrateAreSelected() {
        assertEquals(RingerProfile.Vibrate, SoundProfileControl.cycle(context))
        assertEquals(RingerProfile.Sound, SoundProfileControl.cycle(context))
        assertFalse(SoundProfileControl.canSilence(context))
    }

    @Test
    fun grantedDndAccessAllowsSilentAndReturnsToSound() {
        shadowOf(notifications).setNotificationPolicyAccessGranted(true)
        assertEquals(RingerProfile.Vibrate, SoundProfileControl.cycle(context))
        assertEquals(RingerProfile.Silent, SoundProfileControl.cycle(context))
        assertEquals(RingerProfile.Sound, SoundProfileControl.cycle(context))
    }

    @Test
    fun externalRingerChangesAreReadFromAndroid() {
        audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
        assertEquals(RingerProfile.Vibrate, SoundProfileControl.current(context))
    }

    @Test
    fun silentAccessOpensUserSettingsWithoutGrantingIt() {
        SoundProfileControl.openSilentAccess(context)
        assertEquals(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, shadowOf(context).nextStartedActivity.action)
        assertFalse(SoundProfileControl.canSilence(context))
    }

    private fun registerCompanion() {
        val info =
            ResolveInfo().apply {
                activityInfo =
                    ActivityInfo().apply {
                        name = "$PROFILES.MainActivity"
                        packageName = PROFILES
                        applicationInfo = ApplicationInfo().apply { packageName = PROFILES }
                    }
            }
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(PROFILES)
        shadowOf(context.packageManager).setResolveInfosForIntent(query, listOf(info))
    }

    private companion object {
        const val PROFILES = "io.github.aimindseye.tenseven.profiles"
    }
}
