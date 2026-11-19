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
            notes.save(note)
            tasks.save(task)
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
            notes.delete(note.id)
            tasks.delete(task.id)
            prefs.local = prior
            calc.edit().putString("last_result", oldCalc).commit()
        }
    }

    @Test fun removedDocumentLinksAreForgottenWithoutClearingOtherIntegrationChoices() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("frame_integrations_device", Context.MODE_PRIVATE)
        val oldExcerpts = prefs.getBoolean("notification_excerpts", false)
        try {
            prefs
                .edit()
                .putString("document_word", "content://removed.example/word")
                .putString("document_excel", "content://removed.example/excel")
                .putString("document_powerpoint", "content://removed.example/powerpoint")
                .putBoolean("notification_excerpts", true)
                .commit()
            val choices = FrameIntegrationPreferences(context)
            assertFalse(prefs.contains("document_word"))
            assertFalse(prefs.contains("document_excel"))
            assertFalse(prefs.contains("document_powerpoint"))
            assertTrue(choices.excerpts)
            FrameIntegrationPreferences(context) // migration is idempotent
        } finally {
            prefs.edit().putBoolean("notification_excerpts", oldExcerpts).commit()
        }
    }
}
