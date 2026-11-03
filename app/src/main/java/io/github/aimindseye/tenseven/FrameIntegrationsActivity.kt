package io.github.aimindseye.tenseven

import androidx.core.net.toUri
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.platform.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Opt-ins and Android's file picker; no account login, broad storage grant or hidden app API. */
class FrameIntegrationsActivity : ComponentActivity() {
    private val choices by lazy { FrameIntegrationPreferences(this) }
    private var picking = "word"
    private var revision by mutableIntStateOf(0)
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && uri.scheme == "content") {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                forget(picking, uri.toString())
                choices.document(picking, uri.toString())
                revision++
            }.onFailure { Toast.makeText(this, "File access could not be retained. Please choose another file.", Toast.LENGTH_LONG).show() }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("picking", picking)
        super.onSaveInstanceState(outState)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        picking = savedInstanceState?.getString("picking") ?: "word"
        setContent { LauncherTheme(window = window) {
            Surface(Modifier.fillMaxSize().testTag("frame-integrations-surface"), color = MaterialTheme.colorScheme.background) { Content() }
        } }
        if (savedInstanceState == null) intent.getStringExtra("choose_document")?.takeIf { it in OfficeFrameDocuments.KINDS }?.let { kind ->
            picking = kind
            picker.launch(arrayOf(OfficeFrameDocuments.MIME.getValue(kind)))
        }
    }
    private fun forget(kind: String, replacement: String? = null) {
        val prior = choices.document(kind)
        choices.document(kind, null)
        if (prior != null && prior != replacement && OfficeFrameDocuments.KINDS.none { choices.document(it) == prior })
            runCatching { contentResolver.releasePersistableUriPermission(prior.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    @Composable
    private fun Content() {
        var excerpts by remember { mutableStateOf(choices.excerpts) }
        var local by remember { mutableStateOf(choices.local) }
        var sources by remember { mutableStateOf(choices.mediaPackages) }
        val apps by produceState(emptyList<io.github.aimindseye.tenseven.model.AppEntry>()) {
            value = withContext(Dispatchers.IO) { LauncherAppsRepository(this@FrameIntegrationsActivity).loadApps()
                .filter { it.user == android.os.Process.myUserHandle() }.distinctBy { it.component.packageName }.sortedBy { it.label } }
        }
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Active Frames · App integrations", style = MaterialTheme.typography.headlineSmall)
            Text("Only apps you open get frames. The last eight are kept; opening a ninth replaces the oldest. Summaries enrich those frames and never add cards on their own.")
            TextButton(onClick = { startActivity(Intent(this@FrameIntegrationsActivity, HubSettingsActivity::class.java)) }) { Text("Hub notification and Calendar access") }
            Text("Media, podcasts, audiobooks and FM Radio", style = MaterialTheme.typography.titleMedium)
            Text("Media-session summaries work with notification access. For FM Radio or a player without metadata, opt in to its notification text below. Station/frequency appears only if the app includes it. These selections do not enable Hub message counts.")
            var selectingSources by remember { mutableStateOf(false) }
            Button(onClick = { selectingSources = true }) { Text("Choose radio/player apps (${sources.size} selected)") }
            if (selectingSources) AlertDialog(onDismissRequest = { selectingSources = false },
                title = { Text("Radio / player notification sources") },
                text = {
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(apps.size) { index ->
                            val app = apps[index]
                            val pkg = app.component.packageName
                            Row(Modifier.fillMaxWidth().clickable {
                                sources = if (pkg in sources) sources - pkg else sources + pkg
                                choices.mediaPackages = sources
                                io.github.aimindseye.tenseven.conversations.ConversationListenerService.refreshConnected()
                            }) {
                                Checkbox(checked = pkg in sources, onCheckedChange = null)
                                Column(Modifier.weight(1f).padding(8.dp)) {
                                    Text(app.label); Text(pkg, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }, confirmButton = { TextButton(onClick = { selectingSources = false }) { Text("Done") } })
            Text("Mail and messaging", style = MaterialTheme.typography.titleMedium)
            SwitchRow("Show notification excerpts", "Use only apps enabled in Hub. Read and snoozed conversations are excluded; no notification text is saved.", excerpts) {
                excerpts = it; choices.excerpts = it
            }
            Text("Word, Excel and PowerPoint", style = MaterialTheme.typography.titleMedium)
            Text("Select one file for each app. DOCX text, XLSX workbook text and PPTX first-slide text are read locally. Other formats show file information only. Saved file changes refresh within 30 seconds; unsaved editor changes cannot be read. No Microsoft account connection is used.")
            key(revision) {
                OfficeFrameDocuments.KINDS.forEach { kind ->
                    Text(kind.replaceFirstChar(Char::uppercase) + if (choices.document(kind) == null) " · no file selected" else " · file selected")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { picking = kind; picker.launch(arrayOf(OfficeFrameDocuments.MIME.getValue(kind), "application/msword", "application/vnd.ms-excel", "application/vnd.ms-powerpoint")) }) { Text("Choose file") }
                        TextButton(onClick = { forget(kind); revision++ }) { Text("Remove") }
                    }
                }
            }
            Text("TenSeven apps", style = MaterialTheme.typography.titleMedium)
            SwitchRow("Notes, Tasks/Remind and Calculator", "Show the latest saved note, next open task and last successful calculation. No notification access needed.", local) {
                local = it; choices.local = it
            }
            Button(onClick = { finish() }) { Text("Done") }
        }
    }
}
