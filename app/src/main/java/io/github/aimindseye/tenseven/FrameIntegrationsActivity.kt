package io.github.aimindseye.tenseven

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

/** Optional media and notification summaries for BB10 after notification access is enabled. */
class FrameIntegrationsActivity : ComponentActivity() {
    private val choices by lazy { FrameIntegrationPreferences(this) }

    override fun onResume() {
        super.onResume()
        val bb10 = StartStateRepository(this).loadHomeStyle() == io.github.aimindseye.tenseven.model.HomeStyle.Bb10
        if (!io.github.aimindseye.tenseven.frames.FrameAvailability.setup(
                bb10,
                io.github.aimindseye.tenseven.conversations.ConversationAccess
                    .isGranted(this),
            )
        ) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LauncherTheme(window = window) { FrameIntegrationsSurface { Content() } } }
    }

    @Composable
    private fun Content() {
        var excerpts by remember { mutableStateOf(choices.excerpts) }
        var local by remember { mutableStateOf(choices.local) }
        var sources by remember { mutableStateOf(choices.mediaPackages) }
        val apps by produceState(emptyList<io.github.aimindseye.tenseven.model.AppEntry>()) {
            value =
                withContext(Dispatchers.IO) {
                    LauncherAppsRepository(this@FrameIntegrationsActivity)
                        .loadApps()
                        .filter { it.user == android.os.Process.myUserHandle() }
                        .distinctBy { it.component.packageName }
                        .sortedBy { it.label }
                }
        }
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Active Frames · App integrations", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Only apps you open get frames. The last eight are kept; opening a ninth replaces the oldest. Summaries enrich those frames and never add cards on their own.",
            )
            TextButton(onClick = {
                startActivity(Intent(this@FrameIntegrationsActivity, HubSettingsActivity::class.java))
            }) { Text("Hub notification and Calendar access") }
            Text("Media, podcasts, audiobooks and FM Radio", style = MaterialTheme.typography.titleMedium)
            Text(
                "Media-session summaries work with notification access. For FM Radio or a player without metadata, opt in to its notification text below. Station/frequency appears only if the app includes it. These selections do not enable Hub message counts.",
            )
            var selectingSources by remember { mutableStateOf(false) }
            Button(onClick = { selectingSources = true }) { Text("Choose radio/player apps (${sources.size} selected)") }
            if (selectingSources) {
                AlertDialog(
                    onDismissRequest = { selectingSources = false },
                    title = { Text("Radio / player notification sources") },
                    text = {
                        androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 360.dp)) {
                            items(apps.size) { index ->
                                val app = apps[index]
                                val pkg = app.component.packageName
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        sources = if (pkg in sources) sources - pkg else sources + pkg
                                        choices.mediaPackages = sources
                                        io.github.aimindseye.tenseven.conversations.ConversationListenerService
                                            .refreshConnected()
                                    },
                                ) {
                                    Checkbox(checked = pkg in sources, onCheckedChange = null)
                                    Column(Modifier.weight(1f).padding(8.dp)) {
                                        Text(app.label)
                                        Text(pkg, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { selectingSources = false }) { Text("Done") } },
                )
            }
            Text("Mail and messaging", style = MaterialTheme.typography.titleMedium)
            SwitchRow(
                "Show notification excerpts",
                "Use only apps enabled in Hub. Read and snoozed conversations are excluded; no notification text is saved.",
                excerpts,
            ) {
                excerpts = it
                choices.excerpts = it
            }
            Text("TenSeven apps", style = MaterialTheme.typography.titleMedium)
            SwitchRow(
                "Notes, Tasks/Remind and Calculator",
                "Show the latest saved note, next open task and last successful calculation. No notification access needed.",
                local,
            ) {
                local = it
                choices.local = it
            }
            Button(onClick = { finish() }) { Text("Done") }
        }
    }
}

@Composable
internal fun FrameIntegrationsSurface(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize().testTag("frame-integrations-surface"), color = MaterialTheme.colorScheme.background, content = content)
}
