package io.github.aimindseye.tenseven.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HubStreamSelectionTest {
    private data class Row(val name: String, val packages: List<String>)

    @Test fun multipleMailClientsRemainSeparateSelectableSources() {
        val rows = listOf(Row("Gmail", listOf("com.google.android.gm")),
            Row("Outlook", listOf("com.microsoft.office.outlook")), Row("Signal", listOf("org.thoughtcrime.securesms")))
        assertEquals(3, HubStreamSelection.apply(rows, null) { it.packages }.size)
        assertEquals(listOf("Outlook"), HubStreamSelection.apply(rows, "com.microsoft.office.outlook") { it.packages }.map { it.name })
        assertEquals(listOf("Gmail"), HubStreamSelection.apply(rows, "com.google.android.gm") { it.packages }.map { it.name })
    }

    @Test fun groupedConversationAppearsUnderBothSourceFilters() {
        val rows = listOf(Row("Person", listOf("com.google.android.gm", "com.microsoft.office.outlook")))
        assertEquals(rows, HubStreamSelection.apply(rows, "com.google.android.gm") { it.packages })
        assertEquals(rows, HubStreamSelection.apply(rows, "com.microsoft.office.outlook") { it.packages })
        assertEquals(emptyList<Row>(), HubStreamSelection.apply(rows, "other") { it.packages })
    }

    @Test fun sourceLabelsDeduplicateAndSortByName() {
        assertEquals(listOf("a" to "Gmail", "b" to "Outlook"),
            HubStreamSelection.options(listOf("b" to "Outlook", "a" to "Gmail", "a" to "Gmail")))
    }
}
