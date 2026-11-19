package io.github.aimindseye.tenseven

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Explicit role request from Launcher Settings, separate from initial Apps & Features setup. */
@Composable
internal fun D5PhoneRoleSetting() {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Default Phone app", style = MaterialTheme.typography.titleMedium)
        Text("Phone is an optional independent app. Android manages your default Phone selection.")
        OutlinedButton(onClick = {
            context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }) { Text("Open Android Default Apps") }
    }
}
