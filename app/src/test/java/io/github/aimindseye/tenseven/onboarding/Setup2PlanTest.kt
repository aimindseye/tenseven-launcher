package io.github.aimindseye.tenseven.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Setup2PlanTest {
    @Test fun sevenStagesCoverLegacyChoicesOnceWithoutLosingDeepLinks() {
        assertEquals(7, Setup2Plan.stages.size)
        val mapped = Setup2Plan.stages.flatMap(Setup2Plan::steps)
        assertEquals(SetupStep.entries.toSet(), mapped.toSet())
        assertEquals(mapped.size, mapped.distinct().size)
    }

    @Test fun optionalAppPresetsContainOnlyCurrentlyBundledComponents() {
        assertTrue(Setup2Plan.full().containsAll(Setup2Plan.essentials))
        assertEquals(Setup2Plan.full(), Setup2Plan.normalizedCustom(Setup2Plan.full()))
        assertFalse("calendar" in Setup2Plan.full())
        assertFalse("phone" in Setup2Plan.full())
    }

    @Test fun reviewCannotSilentlyRequestPermissionsOrModifySettings() {
        assertTrue(Setup2Plan.steps(Setup2Stage.REVIEW).isEmpty())
        assertEquals(listOf(SetupStep.HOME), Setup2Plan.steps(Setup2Stage.WELCOME))
    }
}
