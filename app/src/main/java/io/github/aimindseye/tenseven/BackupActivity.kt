package io.github.aimindseye.tenseven

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.design.DesignText
import io.github.aimindseye.tenseven.design.SableGlobalTheme
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.model.BackupValue
import io.github.aimindseye.tenseven.model.LauncherBackup
import io.github.aimindseye.tenseven.model.StartText
import io.github.aimindseye.tenseven.platform.StartStateRepository

/**
 * Backup and restore of launcher settings (home layout, quick bar, favorites, icon pack, home style) as one
 * JSON file the user keeps, written and read through the system file picker: Tenseven asks for no storage
 * permission. A file is checked before anything changes; restoring asks first, then restarts the launcher so
 * every screen reads the restored settings.
 */
class BackupActivity : ComponentActivity() {
    private lateinit var startState: StartStateRepository
    private var message by mutableStateOf<String?>(null)
    private var pending by mutableStateOf<Map<String, BackupValue>?>(null)

    private val createFile =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(::export) }
    private val openFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::readBackup) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StartText.t.bindTo(this)
        DesignText.t.bindTo(this)
        startState = StartStateRepository(this)
        setContent {
            SableGlobalTheme(window = window) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier =
                            Modifier
                                .safeDrawingPadding()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.backup_body), style = MaterialTheme.typography.bodyMedium)
                        val restoring = pending
                        if (restoring == null) {
                            Button(
                                onClick = { createFile.launch(getString(R.string.backup_file_name)) },
                                modifier = Modifier.fillMaxWidth().sableFocusRing(),
                            ) { Text(stringResource(R.string.backup_export)) }
                            OutlinedButton(
                                onClick = { runCatching { openFile.launch(arrayOf("application/json", "text/plain", "*/*")) } },
                                modifier = Modifier.fillMaxWidth().sableFocusRing(),
                            ) { Text(stringResource(R.string.backup_import)) }
                        } else {
                            Text(
                                stringResource(R.string.backup_confirm, restoring.size),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Button(
                                onClick = { restore(restoring) },
                                modifier = Modifier.fillMaxWidth().sableFocusRing(),
                            ) { Text(stringResource(R.string.backup_restore_now)) }
                            OutlinedButton(
                                onClick = { pending = null },
                                modifier = Modifier.fillMaxWidth().sableFocusRing(),
                            ) { Text(stringResource(R.string.backup_cancel)) }
                        }
                        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                        OutlinedButton(
                            onClick = { finish() },
                            modifier = Modifier.fillMaxWidth().sableFocusRing(),
                        ) { Text(stringResource(R.string.backup_done)) }
                    }
                }
            }
        }
    }

    private fun export(uri: Uri) {
        val json = LauncherBackup.encode(startState.exportSettings())
        val written =
            runCatching {
                contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(json) } ?: error("no stream")
            }
        message =
            if (written.isSuccess) getString(R.string.backup_exported) else getString(R.string.backup_export_failed)
    }

    private fun readBackup(uri: Uri) {
        val decoded =
            runCatching {
                val text =
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        val buffer = CharArray(8_192)
                        val out = StringBuilder()
                        while (true) {
                            val count = reader.read(buffer)
                            if (count < 0) break
                            require(out.length + count <= LauncherBackup.MAX_CHARACTERS) { "too large" }
                            out.append(buffer, 0, count)
                        }
                        out.toString()
                    } ?: error("no stream")
                LauncherBackup.decode(text).getOrThrow()
            }
        val settings = decoded.getOrNull()
        if (settings == null) {
            pending = null
            message = getString(R.string.backup_invalid)
        } else {
            message = null
            pending = settings
        }
    }

    private fun restore(settings: Map<String, BackupValue>) {
        startState.restoreSettings(settings)
        pending = null
        message = getString(R.string.backup_restored)
        // Every screen reads these settings when it starts, so start the launcher fresh.
        runCatching {
            startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, RESTART_DELAY_MS)
    }

    private companion object {
        const val RESTART_DELAY_MS = 400L
    }
}
