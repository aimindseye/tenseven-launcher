// Derived from SableOS (github.com/aimindseye/zinwaq25-sableos @ 31420f599c2f, sable-src/apps/r8/android/notes/src/main),
// Apache License 2.0. Changed: package and imports renamed for Tenseven; native Rust calls replaced by Kotlin where noted.
package io.github.aimindseye.tenseven.extras.notes.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.aimindseye.tenseven.extras.notes.core.Note
import io.github.aimindseye.tenseven.extras.notes.core.Tasks

sealed interface Screen {
    data object List : Screen

    data class Editor(
        val id: String,
    ) : Screen

    data object Tasks : Screen
}

class NotesUiState {
    var notes by mutableStateOf(emptyList<Note>())
    var loaded by mutableStateOf(false)
    var screen by mutableStateOf<Screen>(Screen.List)
    var query by mutableStateOf("")
    var searching by mutableStateOf(false)
    var showDone by mutableStateOf(false)
    var confirmDelete by mutableStateOf<Note?>(null)
    var notice by mutableStateOf<String?>(null)

    /** The list row or task that has keyboard focus, for L, D and O. */
    var focusedId: String? = null
    var focusedTask: Tasks.Task? = null
}

/** Everything the screens can ask for; the controller implements it. */
data class NotesActions(
    val newNote: () -> Unit,
    val open: (String) -> Unit,
    val back: () -> Boolean,
    val edit: (String, String) -> Unit,
    val cycleLabel: (String) -> Unit,
    val delete: (Note) -> Unit,
    val askDelete: (Note?) -> Unit,
    val share: (Note) -> Unit,
    val export: (Note) -> Unit,
    val import: () -> Unit,
    val showTasks: () -> Unit,
    val toggleTask: (Tasks.Task) -> Unit,
    val setQuery: (String) -> Unit,
    val setSearching: (Boolean) -> Unit,
    val setShowDone: (Boolean) -> Unit,
    val focusNote: (String?) -> Unit,
    val focusTask: (Tasks.Task?) -> Unit,
    val dismissNotice: () -> Unit,
)
