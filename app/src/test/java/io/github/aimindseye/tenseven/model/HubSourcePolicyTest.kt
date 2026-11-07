package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HubSourcePolicyTest {
    @Test fun automaticIncludesNewSourcesWithoutAnAssignment() {
        val policy = HubSourcePolicy()
        assertTrue(policy.includes("newly.posting.app"))
        assertFalse(policy.includes(""))
        assertEquals(emptySet<String>(), policy.resolve(emptyList()))
    }

    @Test fun optionalHiddenSourcesStayOutOfAutomaticDelivery() {
        val policy = HubSourcePolicy(hidden = setOf("hidden.app"))
        assertEquals(setOf("mail.app"), policy.resolve(listOf("mail.app", "hidden.app")))
    }

    @Test fun selectedModeKeepsItsSavedListAndDoesNotAutoEnableOtherApps() {
        val policy = HubSourcePolicy(automatic = false, selected = setOf("chosen.app"))
        assertTrue(policy.includes("chosen.app"))
        assertFalse(policy.includes("other.app"))
        assertFalse(HubSourcePolicy(automatic = false).includes("other.app"))
        assertEquals(policy.selected, policy.copy(automatic = true).selected)
    }
}
