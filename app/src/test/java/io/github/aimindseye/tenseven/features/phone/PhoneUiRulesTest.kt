package io.github.aimindseye.tenseven.features.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneUiRulesTest {
    @Test fun dialerMatchesSubstringsWithoutDuplicateContact() {
        val pool = listOf(
            PhoneUiRules.Match(1, "Alex Rivera", "+1 (555) 0100"),
            PhoneUiRules.Match(1, "Alex Rivera", "555-0199"),
            PhoneUiRules.Match(2, "Aisha Khan", "555-0142"),
            PhoneUiRules.Match(3, "Bree", "333-9292"),
        )
        assertEquals(listOf(1L, 2L), PhoneUiRules.dialMatches("555", pool, 2).map { it.contactId })
        assertTrue(PhoneUiRules.dialMatches("5", pool, 2).isEmpty())
        assertEquals(1, PhoneUiRules.dialMatches("555", pool, 1).size)
        assertTrue(PhoneUiRules.dialMatches("555", pool, 0).isEmpty())
    }

    @Test fun profileKeepsCallControlInAvailableViewport() {
        val titan = PhoneUiRules.bounds(576f, 530f)
        val q25 = PhoneUiRules.bounds(411f, 425f)
        assertFalse(titan.compact)
        assertEquals(2, titan.maxMatches)
        assertTrue(q25.compact)
        assertEquals(1, q25.maxMatches)
        assertTrue(q25.primaryCallHeightDp > 0)
    }

    @Test fun contactNavigationHandlesInitialsAndNonLetters() {
        assertEquals("AK", PhoneUiRules.initials("Aisha Khan"))
        assertEquals("?", PhoneUiRules.initials("   "))
        assertEquals("#", PhoneUiRules.firstLetter("4Test"))
        assertEquals("A", PhoneUiRules.firstLetter("alex"))
    }
}
