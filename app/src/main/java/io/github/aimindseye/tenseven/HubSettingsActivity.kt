package io.github.aimindseye.tenseven

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import io.github.aimindseye.tenseven.conversations.ConversationHub
import io.github.aimindseye.tenseven.design.DesignText
import io.github.aimindseye.tenseven.model.HubApps
import io.github.aimindseye.tenseven.model.StartText
import io.github.aimindseye.tenseven.onboarding.SetupStep
import io.github.aimindseye.tenseven.onboarding.SpecialAccessAttemptStore
import io.github.aimindseye.tenseven.platform.Capabilities
import io.github.aimindseye.tenseven.platform.HubCalendarRepository
import io.github.aimindseye.tenseven.platform.HubCalendarSource
import io.github.aimindseye.tenseven.platform.LauncherAppsRepository
import io.github.aimindseye.tenseven.platform.LockLayerState
import io.github.aimindseye.tenseven.platform.StartStateRepository
import io.github.aimindseye.tenseven.ui.HubSourceSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hub settings: notification access, "Clear the Hub now", which apps feed the Hub, and whether message text may
 * appear on the lock layer and the Hub peek. The Hub keeps its conversations in memory only; nothing here stores
 * message text.
 */
class HubSettingsActivity : ComponentActivity() {
    companion object {
        const val EXTRA_FROM_LAUNCHER_SETTINGS =
            "io.github.aimindseye.tenseven.extra.HUB_FROM_LAUNCHER_SETTINGS"
    }

    private var resumeTick by mutableStateOf(0)

    override fun onResume() {
        super.onResume()
        io.github.aimindseye.tenseven.platform.NotificationConnectionRecovery
            .onLauncherResume(this)
        resumeTick++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StartText.t.bindTo(this)
        DesignText.t.bindTo(this)
        val state = StartStateRepository(this)
        val repository = LauncherAppsRepository(this)
        val calendars = HubCalendarRepository(this)
        val fromLauncherSettings = intent.getBooleanExtra(EXTRA_FROM_LAUNCHER_SETTINGS, false)
        setContent {
            LauncherTheme(window = window) {
                val enabled = remember { HubSourceSettings(this) }
                var showText by remember { mutableStateOf(state.loadHubPeekText()) }
                var calendarPeek by remember { mutableStateOf(state.loadHubCalendarPeek()) }
                var selectedCalendars by remember { mutableStateOf(state.loadHubSelectedCalendarIds()) }
                var calendarSources by remember { mutableStateOf(emptyList<HubCalendarSource>()) }
                val calendarPermitted = resumeTick >= 0 && calendars.hasPermission()
                LaunchedEffect(resumeTick, calendarPeek) {
                    calendarSources =
                        if (calendarPeek && calendarPermitted) {
                            withContext(Dispatchers.IO) { calendars.calendars() }
                        } else {
                            emptyList()
                        }
                }
                var cleared by remember { mutableStateOf(false) }
                var rows by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
                LaunchedEffect(Unit) {
                    rows =
                        withContext(Dispatchers.IO) {
                            HubApps.rows(repository.loadApps().map { it.component.packageName to it.label })
                        }
                }
                HubSettings(
                    access =
                        HubAccessSettings(
                            accessGranted =
                                resumeTick >= 0 &&
                                    io.github.aimindseye.tenseven.platform.PlatformCapabilityProbe
                                        .detect(this)
                                        .hubNotifications ==
                                    io.github.aimindseye.tenseven.platform.CapabilityStatus.Ready,
                            attemptedNotificationAccess =
                                resumeTick >= 0 &&
                                    SetupStep.NOTIFICATIONS in SpecialAccessAttemptStore.attempted(this),
                            listenerAvailable =
                                io.github.aimindseye.tenseven.platform.PlatformCapabilityProbe
                                    .detect(this)
                                    .hubNotifications !=
                                    io.github.aimindseye.tenseven.platform.CapabilityStatus.Unavailable,
                            onAccess = {
                                SpecialAccessAttemptStore.markAttempted(this, SetupStep.NOTIFICATIONS)
                                runCatching { startActivity(ConversationAccess.settingsIntent()) }
                            },
                            onRetryListener = {
                                io.github.aimindseye.tenseven.platform.NotificationConnectionRecovery
                                    .retryFromSettings(this)
                            },
                        ),
                    calendar =
                        HubCalendarSettings(
                            calendarPeek = calendarPeek,
                            calendarPermitted = calendarPermitted,
                            calendarSources = calendarSources,
                            selectedCalendars = selectedCalendars,
                            onCalendarPermission = {
                                requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 7301)
                            },
                            onCalendarToggle = { id ->
                                val existing = selectedCalendars ?: calendarSources.map { it.id }.toSet()
                                selectedCalendars = if (id in existing) existing - id else existing + id
                                state.saveHubSelectedCalendarIds(selectedCalendars)
                            },
                            onCalendarPeek = {
                                calendarPeek = it
                                state.saveHubCalendarPeek(it)
                            },
                        ),
                    content =
                        HubContentSettings(
                            showText = showText,
                            cleared = cleared,
                            onShowText = {
                                showText = it
                                state.saveHubPeekText(it)
                            },
                            onClear = {
                                ConversationHub.clearNow()
                                cleared = true
                            },
                            navigation = HubSettingsNavigation(
                                showLauncherBack = fromLauncherSettings,
                                onBack = { finish() },
                            ),
                        ),
                    rows = rows,
                    enabled = enabled,
                )
            }
        }
    }
}

private data class HubAccessSettings(
    val accessGranted: Boolean,
    val attemptedNotificationAccess: Boolean,
    val listenerAvailable: Boolean,
    val onAccess: () -> Unit,
    val onRetryListener: () -> Unit,
)

private data class HubCalendarSettings(
    val calendarPeek: Boolean,
    val calendarPermitted: Boolean,
    val calendarSources: List<HubCalendarSource>,
    val selectedCalendars: Set<Long>?,
    val onCalendarPermission: () -> Unit,
    val onCalendarToggle: (Long) -> Unit,
    val onCalendarPeek: (Boolean) -> Unit,
)

private data class HubSettingsNavigation(
    val showLauncherBack: Boolean,
    val onBack: () -> Unit,
)

private data class HubContentSettings(
    val showText: Boolean,
    val cleared: Boolean,
    val onShowText: (Boolean) -> Unit,
    val onClear: () -> Unit,
    val navigation: HubSettingsNavigation,
)

@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
private fun HubSettings(
    access: HubAccessSettings,
    calendar: HubCalendarSettings,
    content: HubContentSettings,
    rows: List<Pair<String, String>>,
    enabled: HubSourceSettings,
) {
    val first = remember { FocusRequester() }
    val auditContext = androidx.compose.ui.platform.LocalContext.current
    val hubActivity = auditContext as? android.app.Activity
    val fromFlow = hubActivity?.intent?.getBooleanExtra("tenseven.settings.flow", false) == true
    fun navigateTo(page: String) {
        auditContext.startActivity(
            Intent(auditContext, LauncherSettingsActivity::class.java)
                .putExtra("tenseven.settings.category", page)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        hubActivity?.finish()
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            val launcherSettingsContext = androidx.compose.ui.platform.LocalContext.current
            Settings2BackLink {
                if (content.navigation.showLauncherBack) {
                    // Return to the already open Settings activity; do not start a second copy.
                    content.navigation.onBack()
                } else {
                    launcherSettingsContext.startActivity(
                        Intent(launcherSettingsContext, LauncherSettingsActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    )
                    (launcherSettingsContext as? android.app.Activity)?.finish()
                }
            }
            Text(stringResource(R.string.hub_settings), style = MaterialTheme.typography.titleLarge)
            val hubComposeContext = androidx.compose.ui.platform.LocalContext.current
            val hubComposeRepo = remember(hubComposeContext) { StartStateRepository(hubComposeContext) }
            var composeMode by remember { mutableStateOf(hubComposeRepo.loadHubComposeMode()) }
            Text("Hub Compose action", style = MaterialTheme.typography.titleMedium)
            Text(
                "Choose what Compose opens. Ask every time is closest to the original BB10 Hub.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            io.github.aimindseye.tenseven.model.HubComposeMode.entries.forEach { choice ->
                HubRow(
                    title = (if (choice == composeMode) "✓ " else "") + choice.label,
                    detail = choice.description,
                    onClick = {
                        composeMode = choice
                        hubComposeRepo.saveHubComposeMode(choice)
                    },
                )
            }
            Text(
                stringResource(R.string.hub_settings_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!access.accessGranted && access.listenerAvailable) {
                if (access.attemptedNotificationAccess) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    HubRow(
                        title = "If Android blocked notification access",
                        detail =
                            "After an access request is blocked, App info may show ⋮ → Allow restricted settings. " +
                                "Choose it only if you trust this APK, return and retry notification access. " +
                                "This menu may be absent before the first blocked request.",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            }
                        },
                    )
                } else {
                    Text(
                        "First try Notification access below. If Android blocks it, return here for App info instructions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HubRow(
                title = stringResource(R.string.hub_access),
                detail =
                    stringResource(
                        when {
                            !access.listenerAvailable -> {
                                R.string.hub_access_lite
                            }

                            access.accessGranted &&
                                !io.github.aimindseye.tenseven.conversations.NotificationConnection.connected -> {
                                R.string.hub_access_disconnected
                            }

                            access.accessGranted -> {
                                R.string.hub_access_on
                            }

                            else -> {
                                R.string.hub_access_off
                            }
                        },
                    ),
                modifier = Modifier.focusRequester(first),
                onClick = { if (access.listenerAvailable) access.onAccess() },
            )
            HubListenerDiagnostics(access, enabled)
            HubRow(
                title = "Copy notification diagnostics",
                detail = "Per-app counts and exclusion reasons only. No messages or notification keys are copied.",
                onClick = {
                    val clipboard =
                        auditContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as? android.content.ClipboardManager
                    clipboard?.setPrimaryClip(
                        android.content.ClipData.newPlainText(
                            "TenSeven notification diagnostics",
                            io.github.aimindseye.tenseven.conversations.HubNotificationAudit
                                .report(),
                        ),
                    )
                },
            )
            HubRow(
                title = stringResource(R.string.hub_clear),
                detail = stringResource(if (content.cleared) R.string.hub_cleared else R.string.hub_clear_detail),
                onClick = content.onClear,
            )
            HubSwitch(
                stringResource(R.string.hub_show_text),
                stringResource(R.string.hub_show_text_detail),
                content.showText,
                content.onShowText,
            )
            HubSwitch(
                stringResource(R.string.hub_calendar_peek),
                stringResource(R.string.hub_calendar_peek_detail),
                calendar.calendarPeek,
                calendar.onCalendarPeek,
            )
            if (calendar.calendarPeek) {
                if (!calendar.calendarPermitted) {
                    HubRow(
                        "Allow Calendar access",
                        "Required to show events from calendars synchronized with Android",
                        onClick = calendar.onCalendarPermission,
                    )
                } else {
                    Text("Calendar sources", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Select the calendars whose events appear in Hub. Selection stays on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    calendar.calendarSources.forEach { source ->
                        HubSwitch(
                            source.name,
                            source.account,
                            calendar.selectedCalendars == null || source.id in calendar.selectedCalendars,
                        ) {
                            calendar.onCalendarToggle(source.id)
                        }
                    }
                    if (calendar.calendarSources.isEmpty()) {
                        Text(
                            "No visible Android calendars found",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            HubSourceOptions(rows, enabled, enabled::toggle)
            }
            Settings2FlowFooter(
                backText = "Back",
                nextText = "Next",
                onBack = { if (fromFlow) navigateTo("appearance_home") else hubActivity?.finish() },
                onNext = { navigateTo("lock") },
            )
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
    Card(title = title, detail = detail, active = false, modifier = modifier, onClick = onClick)
}

@Composable
private fun HubSwitch(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SwitchRow(title, detail, checked, onChange)
}

@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
private fun HubSourceOptions(rows: List<Pair<String, String>>, sources: HubSourceSettings, onToggle: (String) -> Unit) {
    var filters by remember { mutableStateOf(false) }
    Text(stringResource(R.string.hub_apps), style = MaterialTheme.typography.titleMedium)
    HubSwitch(
        "Automatic sources",
        "Use notifications delivered by Android. No app assignment is required.",
        sources.policy.automatic,
    ) {
        sources.setAutomatic(it)
    }
    Text(stringResource(R.string.hub_apps_detail), style = MaterialTheme.typography.bodyMedium)
    Text(
        if (sources.policy.automatic) {
            "All Android-delivered sources, except your filters"
        } else {
            stringResource(R.string.hub_apps_enabled_count, rows.count { sources.policy.includes(it.first) })
        },
        style = MaterialTheme.typography.labelMedium,
    )
    HubRow("App filters (optional)", if (filters) "Hide filters" else "Show filters") { filters = !filters }
    if (filters) {
        rows.forEach { (pkg, label) -> HubSwitch(label, "", sources.policy.includes(pkg)) { onToggle(pkg) } }
    }
}

@Suppress("FunctionNaming") // Compose UI entry points use PascalCase.
@Composable
private fun HubListenerDiagnostics(access: HubAccessSettings, enabled: HubSourceSettings) {
    val connected = io.github.aimindseye.tenseven.conversations.NotificationConnection.connected
    val activeCount = io.github.aimindseye.tenseven.conversations.NotificationConnection.activeCount
    val conversationsCount = ConversationHub.notifications().size
    val lockCount = LockLayerState.counts.values.sum()
    val sourceMode = if (enabled.policy.automatic) "automatic" else "selected"
    if (access.accessGranted && connected) {
        Text("Notification listener · Connected", style = MaterialTheme.typography.titleMedium)
        Text(
            if (!enabled.policy.automatic && enabled.policy.selected.isEmpty()) {
                "No apps selected. Enable notification sources below."
            } else {
                "$activeCount active device notifications · $conversationsCount Hub entries · " +
                    "$lockCount lock-screen counts · $sourceMode sources."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        HubRow(
            title = "Notification listener diagnostics",
            detail =
                when {
                    !access.listenerAvailable -> {
                        "Listener service unavailable in this APK."
                    }

                    !access.accessGranted -> {
                        "Android notification access is off. Open Notification access above."
                    }

                    else -> {
                        "Access granted, listener disconnected. Tap here to reconnect."
                    }
                },
            onClick = { if (access.accessGranted) access.onRetryListener() else access.onAccess() },
        )
    }
}
