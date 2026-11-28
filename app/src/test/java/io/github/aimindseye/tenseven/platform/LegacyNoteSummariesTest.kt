package io.github.aimindseye.tenseven.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LegacyNoteSummariesTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun summariesReadExistingNotesWithoutRewritingData() {
        folder.root
            .resolve("existing.note")
            .writeText(
                "sable-note 1\ncreated $CREATED\nupdated $UPDATED\n\n" +
                    "# Existing title\n- [ ] Keep this task\nUnicode café",
            )
        val before =
            folder.root
                .listFiles()
                .orEmpty()
                .associate { it.name to it.readBytes().toList() }
        val summary = LegacyNoteSummaries.latest(folder.root)
        assertEquals("Existing title", summary?.title)
        assertEquals("Keep this task · Unicode café", summary?.preview)
        assertEquals(UPDATED, summary?.updatedAt)
        val after =
            folder.root
                .listFiles()
                .orEmpty()
                .associate { it.name to it.readBytes().toList() }
        assertEquals(before, after)
    }

    @Test
    fun missingOrMalformedDataNeverCreatesOrDeletesFiles() {
        val missing = folder.root.resolve("missing")
        assertNull(LegacyNoteSummaries.latest(missing))
        assertEquals(false, missing.exists())
        folder.root.resolve("bad.note").writeText("not a note")
        assertNull(LegacyNoteSummaries.latest(folder.root))
        assertEquals("not a note", folder.root.resolve("bad.note").readText())
    }

    private companion object {
        const val CREATED = 100L
        const val UPDATED = 200L
    }
}
