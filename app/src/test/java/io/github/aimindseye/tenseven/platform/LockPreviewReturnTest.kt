package io.github.aimindseye.tenseven.platform

import android.content.Intent
import io.github.aimindseye.tenseven.LauncherSettingsActivity
import io.github.aimindseye.tenseven.OnboardingActivity
import io.github.aimindseye.tenseven.lockPreviewReturnIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LockPreviewReturnTest {
    @Test
    fun legitimatePreviewScreensKeepReorderNavigation() {
        val context = RuntimeEnvironment.getApplication()
        listOf(LauncherSettingsActivity::class.java, OnboardingActivity::class.java).forEach { screen ->
            val result = requireNotNull(lockPreviewReturnIntent(context, screen.name))
            assertEquals(context.packageName, result.component?.packageName)
            assertEquals(screen.name, result.component?.className)
            assertEquals(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT, result.flags)
            assertNull(result.data)
            assertNull(result.extras)
        }
    }

    @Test
    fun unapprovedClassesAndMalformedTargetsNeverBecomeIntents() {
        val context = RuntimeEnvironment.getApplication()
        val rejected =
            listOf(
                null,
                "",
                "attacker.Activity",
                "android.app.Activity",
                "io.github.aimindseye.tenseven.SableStartActivity",
            )
        rejected.forEach { target -> assertNull(lockPreviewReturnIntent(context, target)) }
    }
}
