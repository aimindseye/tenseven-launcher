package io.github.aimindseye.tenseven.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPlanTest {
    private val nothing = SetupState(false, false, false, false, false)
    private val everything = SetupState(true, true, true, true, true)

    @Test
    fun stepsComeInTheOrderHomeFirst() {
        assertEquals(
            listOf(SetupStep.HOME, SetupStep.CONTACTS, SetupStep.PHOTOS, SetupStep.NOTIFICATIONS, SetupStep.USAGE),
            OnboardingPlan.pending(nothing),
        )
    }

    @Test
    fun grantedStepsAreLeftOut() {
        val state = nothing.copy(homeRoleHeld = true, photosGranted = true)
        assertEquals(
            listOf(SetupStep.CONTACTS, SetupStep.NOTIFICATIONS, SetupStep.USAGE),
            OnboardingPlan.pending(state),
        )
        assertTrue(OnboardingPlan.pending(everything).isEmpty())
    }

    @Test
    fun onlyHomeIsRequired() {
        assertEquals(listOf(SetupStep.HOME), SetupStep.entries.filter { it.required })
    }

    @Test
    fun restrictedSettingsHelpIsForSpecialAccessOnAndroid13PlusOutsideThePlayStore() {
        val help = { step: SetupStep, sdk: Int, installer: String? -> OnboardingPlan.needsRestrictedSettingsHelp(step, sdk, installer) }
        assertTrue(help(SetupStep.NOTIFICATIONS, 33, null))
        assertTrue(help(SetupStep.USAGE, 35, "com.google.android.packageinstaller"))
        assertFalse(help(SetupStep.NOTIFICATIONS, 32, null))
        assertFalse(help(SetupStep.NOTIFICATIONS, 34, "com.android.vending"))
        assertFalse(help(SetupStep.CONTACTS, 34, null))
        assertFalse(help(SetupStep.HOME, 34, null))
    }
}
