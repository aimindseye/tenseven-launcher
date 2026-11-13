package io.github.aimindseye.tenseven.features.phone

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneContactsContentTest {
    private val alex = TenSevenPhoneData.Person(1L, "Alex Rivera", "+1 (555) 0100", false)
    private val aisha = TenSevenPhoneData.Person(2L, "Aisha Khan", "555-0142", true)
    private val people = listOf(alex, aisha, alex)

    @Test
    fun emptyQueryPreservesProviderOrderAndDuplicates() {
        assertEquals(people, matchingPhoneContacts("", people))
    }

    @Test
    fun matchesNamesIgnoringCaseWithoutTreatingNonDigitsAsAnEmptyNumberQuery() {
        assertEquals(listOf(alex, alex), matchingPhoneContacts("aLeX", people))
        assertEquals(emptyList<TenSevenPhoneData.Person>(), matchingPhoneContacts("unknown", people))
        assertEquals(emptyList<TenSevenPhoneData.Person>(), matchingPhoneContacts("---", people))
    }

    @Test
    fun matchesFormattedDigitSubstringsAndKeepsDuplicateEntries() {
        assertEquals(people, matchingPhoneContacts("5-55", people))
        assertEquals(listOf(aisha), matchingPhoneContacts("0142", people))
    }
}
