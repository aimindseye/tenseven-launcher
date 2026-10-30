// Derived from SableOS (github.com/aimindseye/zinwaq25-sableos @ 31420f599c2f, sable-src/apps/r8/android/notes/src/main),
// Apache License 2.0. Changed: package and imports renamed for Tenseven; native Rust calls replaced by Kotlin where noted.
package io.github.aimindseye.tenseven.extras.notes

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.aimindseye.tenseven.design.SableGlobalTheme
import io.github.aimindseye.tenseven.extras.notes.ui.NotesApp

/**
 * Sable Notes: local notes and checklists, typed on the keyboard. Notes stay in app storage; share,
 * export and import happen only when asked. Text shared from another app opens as a new note.
 */
class MainActivity : ComponentActivity() {
    private lateinit var controller: NotesController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        io.github.aimindseye.tenseven.design.DesignText.t.bindTo(this)
        enableEdgeToEdge()
        controller = NotesController(this)
        controller.load()
        setContent {
            SableGlobalTheme(window = window) {
                NotesApp(controller.state, controller.actions)
            }
        }
        if (savedInstanceState == null) controller.handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        controller.handleIntent(intent)
    }

    /** List and Tasks keys arrive here only when no focused view used them, so typing always wins. */
    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean = (event.repeatCount == 0 && controller.onKey(keyCode, event)) || super.onKeyDown(keyCode, event)

    override fun onPause() {
        controller.flushAll()
        super.onPause()
    }
}
