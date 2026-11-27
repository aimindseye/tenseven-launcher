package io.github.aimindseye.tenseven.platform

import io.github.aimindseye.tenseven.extras.notes.core.Note
import io.github.aimindseye.tenseven.extras.notes.core.NoteStore
import io.github.aimindseye.tenseven.extras.notes.core.NoteText
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
        val note = Note(NoteStore.newId(), "# Existing title\n- [ ] Keep this task\nUnicode café", CREATED, UPDATED)
        val store = NoteStore(folder.root)
        store.save(note)
        val before =
            folder.root
                .listFiles()
                .orEmpty()
                .associate { it.name to it.readBytes().toList() }
        val summary = LegacyNoteSummaries.latest(folder.root)
        assertEquals(NoteText.title(note.body), summary?.title)
        assertEquals(NoteText.preview(note.body), summary?.preview)
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
