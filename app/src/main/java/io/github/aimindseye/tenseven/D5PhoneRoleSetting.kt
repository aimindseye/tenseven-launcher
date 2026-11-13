package io.github.aimindseye.tenseven

import android.app.role.RoleManager
import android.content.Intent
import android.widget.Toast
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
import io.github.aimindseye.tenseven.platform.ExtraAppsControl

/** Explicit role request from Launcher Settings, separate from initial Apps & Features setup. */
@Suppress("FunctionNaming")
@Composable
internal fun D5PhoneRoleSetting() {
    val context = LocalContext.current
    val enabled = "tenseven_phone" in ExtraAppsControl.enabledIds(context)
    val roles = context.getSystemService(RoleManager::class.java)
    val available = roles?.isRoleAvailable(RoleManager.ROLE_DIALER) == true
    val held = roles?.isRoleHeld(RoleManager.ROLE_DIALER) == true
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Default Phone app", style = MaterialTheme.typography.titleMedium)
        Text(
            when {
                held -> "TenSeven Phone handles calls on this device."
                !enabled -> "Enable TenSeven Phone under Apps & Features first."
                !available -> "Android's default Phone role is not available."
                else -> "TenSeven Phone is enabled but is not your default Phone app."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (enabled && available && !held) {
            OutlinedButton(onClick = {
                context.startActivity(Intent(context, D5PhoneRoleRequestActivity::class.java))
            }) { Text("Make TenSeven Phone default") }
        }
        if (enabled) {
            Text(
                "In the Android dialog choose 'TenSeven Launcher'. This is the same installed APK that " +
                    "contains TenSeven Phone; no separate app will be listed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
