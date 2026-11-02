package io.github.aimindseye.tenseven

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.conversations.ConversationAccess
import io.github.aimindseye.tenseven.platform.Capabilities
import io.github.aimindseye.tenseven.conversations.ConversationHub
import io.github.aimindseye.tenseven.design.DesignText
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.model.HubApps
import io.github.aimindseye.tenseven.model.StartText
import io.github.aimindseye.tenseven.platform.LauncherAppsRepository
import io.github.aimindseye.tenseven.platform.StartStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hub settings: notification access, "Clear the Hub now", which apps feed the Hub, and whether message text may
 * appear on the lock layer and the Hub peek. The Hub keeps its conversations in memory only; nothing here stores
 * message text.
 */
class HubSettingsActivity : ComponentActivity() {
    private var resumeTick by mutableStateOf(0)

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StartText.t.bindTo(this)
        DesignText.t.bindTo(this)
        val state = StartStateRepository(this)
        val repository = LauncherAppsRepository(this)
        setContent {
            LauncherTheme(window = window) {
                var excluded by remember { mutableStateOf(state.loadHubExcludedApps()) }
                var showText by remember { mutableStateOf(state.loadHubPeekText()) }
                var cleared by remember { mutableStateOf(false) }
                var rows by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
                LaunchedEffect(Unit) {
                    rows =
                        withContext(Dispatchers.IO) {
                            HubApps.rows(repository.loadApps().map { it.component.packageName to it.label })
                        }
                }
                HubSettings(
                    accessGranted = resumeTick >= 0 && Capabilities.hasConversationService(this) && ConversationAccess.isGranted(this),
                    listenerAvailable = Capabilities.hasConversationService(this),
                    showText = showText,
                    cleared = cleared,
                    rows = rows,
                    excluded = excluded,
                    onAccess = { runCatching { startActivity(ConversationAccess.settingsIntent()) } },
                    onShowText = {
                        showText = it
                        state.saveHubPeekText(it)
                    },
                    onClear = {
                        ConversationHub.clearNow()
                        cleared = true
                    },
                    onToggle = { pkg ->
                        excluded = HubApps.toggle(excluded, pkg)
                        state.saveHubExcludedApps(excluded)
                        ConversationHub.dropPackages(excluded)
                        if (HubApps.isIncluded(excluded, pkg)) ConversationAccess.requestReconnect(this)
                    },
                )
            }
        }
    }
}

@Composable
private fun HubSettings(
    accessGranted: Boolean,
    listenerAvailable: Boolean,
    showText: Boolean,
    cleared: Boolean,
    rows: List<Pair<String, String>>,
    excluded: Set<String>,
    onAccess: () -> Unit,
    onShowText: (Boolean) -> Unit,
    onClear: () -> Unit,
    onToggle: (String) -> Unit,
) {
    val first = remember { FocusRequester() }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.hub_settings), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.hub_settings_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HubRow(
                title = stringResource(R.string.hub_access),
                detail =
                    stringResource(
                        when {
                            !listenerAvailable -> R.string.hub_access_lite
                            accessGranted -> R.string.hub_access_on
                            else -> R.string.hub_access_off
                        },
                    ),
                modifier = Modifier.focusRequester(first),
                onClick = { if (listenerAvailable) onAccess() },
            )
            HubRow(
                title = stringResource(R.string.hub_clear),
                detail = stringResource(if (cleared) R.string.hub_cleared else R.string.hub_clear_detail),
                onClick = onClear,
            )
            HubSwitch(stringResource(R.string.hub_show_text), stringResource(R.string.hub_show_text_detail), showText, onShowText)
            Text(stringResource(R.string.hub_apps), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text(
                stringResource(R.string.hub_apps_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            rows.forEach { (pkg, label) ->
                HubSwitch(label, "", HubApps.isIncluded(excluded, pkg)) { onToggle(pkg) }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun HubRow(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().sableFocusRing(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HubSwitch(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Surface(
        onClick = { onChange(!checked) },
        modifier = Modifier.fillMaxWidth().sableFocusRing(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (detail.isNotEmpty()) {
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}
