package io.github.aimindseye.tenseven.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** Touch fallback for secondary Home destinations on Q25; no hardcoded physical key codes. */
@Composable
internal fun CompactHomeMenu(
    onDismiss: () -> Unit,
    actions: List<Pair<String, () -> Unit>>,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().background(Bb10Background)
                .border(1.dp, Bb10Line).padding(14.dp),
        ) {
            Text("Home menu", style = MaterialTheme.typography.titleLarge, color = Bb10Text)
            Text("All actions remain touch-accessible", color = Bb10Muted,
                style = MaterialTheme.typography.bodySmall)
            actions.forEach { (name, action) ->
                Text(
                    name,
                    Modifier.fillMaxWidth().clickable {
                        onDismiss()
                        action()
                    }.semantics { contentDescription = "Open $name" }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    color = Bb10Text,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
