package io.github.aimindseye.tenseven.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPlanTest {
    private val nothing = SetupState(false, false, false, false, false, false)
    private val everything = SetupState(true, true, true, true, true, true)

    @Test
    fun stepsComeInTheOrderHomeFirst() {
        assertEquals(
            listOf(
                SetupStep.HOME,
                SetupStep.STYLE,
                SetupStep.CONTACTS,
                SetupStep.PHOTOS,
                SetupStep.NOTIFICATIONS,
                SetupStep.USAGE,
                SetupStep.LOOK,
                SetupStep.GRID,
                SetupStep.WIDGETS,
                SetupStep.HUB,
                SetupStep.LOCK,
                SetupStep.EXTRAS,
            ),
            OnboardingPlan.pending(nothing),
        )
    }

    @Test
    fun grantedStepsAreLeftOut() {
        val state = nothing.copy(homeRoleHeld = true, photosGranted = true)
        val pending = OnboardingPlan.pending(state)
        assertFalse(SetupStep.HOME in pending)
        assertFalse(SetupStep.PHOTOS in pending)
        assertTrue(SetupStep.CONTACTS in pending && SetupStep.NOTIFICATIONS in pending && SetupStep.USAGE in pending)
        // Only the choice steps remain when every permission is granted and extras are chosen.
        assertEquals(SetupStep.entries.filter { it.isChoice }, OnboardingPlan.pending(everything))
    }

    @Test
    fun extrasAreOffUntilChosen() {
        assertTrue(SetupStep.EXTRAS in OnboardingPlan.pending(nothing))
        assertFalse(SetupStep.EXTRAS in OnboardingPlan.pending(nothing.copy(extrasChosen = true)))
        assertFalse(SetupStep.EXTRAS.isSpecialAccess)
    }

    @Test
    fun everyLauncherSettingsGroupHasAChoiceStep() {
        assertEquals(
            setOf(SetupStep.STYLE, SetupStep.LOOK, SetupStep.GRID, SetupStep.WIDGETS, SetupStep.HUB, SetupStep.LOCK),
            SetupStep.entries.filter { it.isChoice }.toSet(),
        )
        assertTrue(SetupStep.entries.filter { it.isChoice }.none { it.required || it.isSpecialAccess })
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
