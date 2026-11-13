package io.github.aimindseye.tenseven.companions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionSetupPlanTest {
    @Test
    fun minimumAndFullPresetsHaveExpectedAppIds() {
        assertEquals(
            setOf("tasks", "notes", "calculator"),
            CompanionSetupPlan.choosePreset(CompanionPreset.MINIMUM, emptySet()),
        )
        assertEquals(
            CompanionCatalog.fullPreset,
            CompanionSetupPlan.choosePreset(CompanionPreset.FULL, emptySet()),
        )
    }

    @Test
    fun customNeverAddsUnknownIds() {
        val selected = CompanionSetupPlan.toggle(emptySet(), "unknown", true)
        assertTrue(selected.isEmpty())
        assertEquals(setOf("phone"), CompanionSetupPlan.toggle(selected, "phone", true))
        assertFalse("phone" in CompanionSetupPlan.toggle(setOf("phone"), "phone", false))
    }

    @Test
    fun selectionRoundTripsWithoutUnknownValues() {
        val encoded = CompanionSetupPlan.encode(listOf("games", "phone", "phone", "unknown"))
        assertEquals("games,phone", encoded)
        assertEquals(setOf("games", "phone"), CompanionSetupPlan.decode(encoded))
        assertEquals(emptySet<String>(), CompanionSetupPlan.decode(null))
    }

    @Test
    fun existingBundledAppSelectionIsNotModified() {
        val requested = setOf("phone", "notes")
        CompanionSetupPlan.choosePreset(CompanionPreset.FULL, requested)
        assertEquals(setOf("phone", "notes"), requested)
    }

    @Test
    fun installationIsFailClosedForEverySelectedCompanion() {
        CompanionCatalog.all.forEach { spec ->
            assertEquals(
                CompanionInstallGate.RELEASE_IDENTITY_UNAPPROVED,
                CompanionInstallPolicy.installationGate(spec.id, CompanionCatalog.fullPreset),
            )
        }
        assertEquals(
            CompanionInstallGate.NOT_SELECTED,
            CompanionInstallPolicy.installationGate("phone", emptySet()),
        )
    }
}
