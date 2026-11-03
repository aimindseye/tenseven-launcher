package io.github.aimindseye.tenseven

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.aimindseye.tenseven.extras.notes.core.Note
import io.github.aimindseye.tenseven.extras.notes.core.NoteStore
import io.github.aimindseye.tenseven.platform.*
import io.github.aimindseye.tenseven.tasks.TaskRecord
import io.github.aimindseye.tenseven.tasks.TaskStore
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AppFrameIntegrationsTest {
    @Test fun officeXmlUsesRealAndroidParserAndRejectsDocumentEntities() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val documents = OfficeFrameDocuments(context)
        assertEquals("Hello World", documents.xmlText("<w:document xmlns:w='urn:test'><w:t>Hello</w:t><w:t>World</w:t></w:document>"))
        assertEquals("1 25", documents.xmlText("<worksheet><v>1</v><v>25</v></worksheet>"))
        assertEquals("", documents.xmlText("<r><unrelated>Hidden</unrelated></r>"))
        assertEquals(240, documents.xmlText("<r><t>" + "a".repeat(2000) + "</t></r>").length)
        assertTrue(runCatching { documents.xmlText("<!DOCTYPE r [<!ENTITY x SYSTEM 'file:///data/local/tmp/private'>]><r><t>&x;</t></r>") }.isFailure)
        assertTrue(runCatching { documents.xmlText("<r><t>broken") }.isFailure)
    }

    @Test fun localSummariesNeedNoNotificationAccessAndDisappearWhenDisabled() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = FrameIntegrationPreferences(context)
        val prior = prefs.local
        val notes = NoteStore(File(context.filesDir, "notes"))
        val now = System.currentTimeMillis()
        val note = Note(NoteStore.newId(), "Summary fixture\nLocal note preview", now, now)
        val tasks = TaskStore(File(context.filesDir, "tasks-v1"))
        val task = TaskRecord(title = "Summary task fixture", dueAtEpochMs = now + 60000)
        val calc = context.getSharedPreferences("calculator_frame", Context.MODE_PRIVATE)
        val oldCalc = calc.getString("last_result", null)
        try {
            notes.save(note); tasks.save(task)
            calc.edit().putString("last_result", "2 + 3 = 5").commit()
            prefs.local = true
            val repository = AppFrameSummaryRepository(context)
            val values = repository.snapshot(now)
            assertTrue(values.any { it.key == "local:notes" && it.detail.contains("Summary fixture") })
            assertTrue(values.any { it.key == "local:tasks" && it.detail.contains("Summary task fixture") })
            assertTrue(values.any { it.key == "local:calculator" && it.detail == "2 + 3 = 5" })
            prefs.local = false
            assertFalse(repository.snapshot(now).any { it.key.startsWith("local:") })
        } finally {
            notes.delete(note.id); tasks.delete(task.id); prefs.local = prior
            calc.edit().putString("last_result", oldCalc).commit()
        }
    }
}
