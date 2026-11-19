package io.github.aimindseye.tenseven.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The original BB10 Hub's choice of message type, using only Android-public compose intents. */
@Composable
internal fun HubComposeDialog(
    onEmail: () -> Unit,
    onMessages: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New message", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                TextButton(onClick = onEmail, modifier = Modifier.fillMaxWidth()) {
                    Text("Email")
                }
                TextButton(onClick = onMessages, modifier = Modifier.fillMaxWidth()) {
                    Text("Message (SMS)")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
