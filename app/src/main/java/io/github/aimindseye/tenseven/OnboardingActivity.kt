package io.github.aimindseye.tenseven

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.aimindseye.tenseven.conversations.ConversationAccess
import io.github.aimindseye.tenseven.design.DesignText
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.model.ExtraApps
import io.github.aimindseye.tenseven.model.ExtraGroup
import io.github.aimindseye.tenseven.model.StartText
import io.github.aimindseye.tenseven.onboarding.OnboardingPlan
import io.github.aimindseye.tenseven.onboarding.SetupState
import io.github.aimindseye.tenseven.onboarding.SetupStatus
import io.github.aimindseye.tenseven.onboarding.SetupStep
import io.github.aimindseye.tenseven.platform.ExtraAppsControl
import io.github.aimindseye.tenseven.platform.StartStateRepository

/**
 * First-run setup, and "Setup and permissions" in the menu: the home role first, then each optional
 * permission with the reason for it, one at a time. Every optional step can be skipped. Nothing is asked
 * silently: each button opens the system dialog or the Settings page for exactly that one thing.
 * Opened with [EXTRA_STEP] it shows only that step (for example from the Hub's "Allow notification access").
 */
class OnboardingActivity : ComponentActivity() {
    private val setup = mutableStateOf<SetupState?>(null)
    private lateinit var startState: StartStateRepository
    private lateinit var steps: List<SetupStep>
    private val homeRoleResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }
    private val permissionResult = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StartText.t.bindTo(this)
        DesignText.t.bindTo(this)
        startState = StartStateRepository(this)
        val state = SetupStatus.read(this)
        setup.value = state
        val only = intent?.getStringExtra(EXTRA_STEP)?.let { name -> SetupStep.entries.firstOrNull { it.name == name } }
        steps = if (only != null) listOf(only) else OnboardingPlan.pending(state)
        // Opened again after the first run (Launcher Settings, the BlackBerry 7 menu): show every choice to change, not a wizard.
        val overview = only == null && startState.loadOnboardingDone()
        val installer = SetupStatus.installer(this)
        setContent {
            LauncherTheme(window = window) {
                SetupScreen(
                    steps = steps,
                    startOnOverview = overview,
                    state = setup.value ?: state,
                    showRestrictedHelp = { step -> OnboardingPlan.needsRestrictedSettingsHelp(step, Build.VERSION.SDK_INT, installer) },
                    onAction = ::perform,
                    enabledExtras = ExtraAppsControl.enabledIds(this),
                    onApplyExtras = ::applyExtras,
                    onOpenAppInfo = ::openAppInfo,
                    onManage = ::manage,
                    onOpenLauncherSettings = { openSettings(Intent(this, LauncherSettingsActivity::class.java)) },
                    onFinish = { finish() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    /** Back, Done and Skip all end here, so the setup opens once on first run and never nags. */
    override fun finish() {
        if (::startState.isInitialized) startState.saveOnboardingDone()
        super.finish()
    }

    private fun refresh() {
        val state = SetupStatus.read(this)
        setup.value = state
        if (state.notificationAccess) ConversationAccess.requestReconnect(this)
    }

    private fun perform(step: SetupStep) {
        when (step) {
            SetupStep.HOME -> requestHomeRole()
            SetupStep.CONTACTS -> permissionResult.launch(Manifest.permission.READ_CONTACTS)
            SetupStep.PHOTOS -> permissionResult.launch(SetupStatus.photosPermission())
            SetupStep.NOTIFICATIONS -> openSettings(ConversationAccess.settingsIntent())
            SetupStep.USAGE ->
                openSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).setData(Uri.parse("package:$packageName")))
            SetupStep.EXTRAS -> Unit // chosen with checkboxes on the step itself
        }
    }

    /** Changing a choice already made: where Android lets it be switched off or changed. */
    private fun manage(step: SetupStep) {
        when (step) {
            SetupStep.HOME -> openSettings(Intent(Settings.ACTION_HOME_SETTINGS))
            SetupStep.CONTACTS, SetupStep.PHOTOS -> openAppInfo()
            else -> perform(step)
        }
    }

    private fun applyExtras(ids: Set<String>) {
        ExtraAppsControl.apply(this, ids)
        refresh()
    }

    private fun requestHomeRole() {
        val roles = getSystemService(RoleManager::class.java)
        val canAsk = roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)
        if (canAsk && roles != null) {
            runCatching { homeRoleResult.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME)) }
        } else {
            openSettings(Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }

    private fun openAppInfo() =
        openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))

    private fun openSettings(intent: Intent) {
        runCatching { startActivity(intent) }
    }

    companion object {
        /** A [SetupStep] name: show only that step. */
        const val EXTRA_STEP = "io.github.aimindseye.tenseven.extra.SETUP_STEP"
    }
}

@Composable
private fun SetupScreen(
    steps: List<SetupStep>,
    startOnOverview: Boolean,
    state: SetupState,
    showRestrictedHelp: (SetupStep) -> Boolean,
    onAction: (SetupStep) -> Unit,
    enabledExtras: Set<String>,
    onApplyExtras: (Set<String>) -> Unit,
    onOpenAppInfo: () -> Unit,
    onManage: (SetupStep) -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onFinish: () -> Unit,
) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    var overview by rememberSaveable { mutableStateOf(startOnOverview) }
    val firstButton = remember { FocusRequester() }
    var picked by remember { mutableStateOf(enabledExtras) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleLarge)
            val step = steps.getOrNull(index)
            if (overview || step == null) {
                SetupOverview(
                    state = state,
                    picked = picked,
                    onPickedChange = { picked = it },
                    firstButton = firstButton,
                    onAction = onAction,
                    onManage = onManage,
                    onApplyExtras = { onApplyExtras(picked) },
                    onOpenLauncherSettings = onOpenLauncherSettings,
                    onFinish = onFinish,
                )
            } else {
                val done = state.isDone(step)
                val last = index + 1 >= steps.size
                Text(
                    stringResource(R.string.setup_progress, index + 1, steps.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StepCard(step = step)
                if (showRestrictedHelp(step) && !done) {
                    RestrictedSettingsCard(onOpenAppInfo = onOpenAppInfo)
                }
                if (step == SetupStep.EXTRAS) {
                    ExtrasPicker(picked = picked, onChange = { picked = it })
                    Button(
                        onClick = { onApplyExtras(picked) },
                        modifier = Modifier.fillMaxWidth().focusRequester(firstButton).sableFocusRing(),
                    ) {
                        Text(stringResource(R.string.setup_extras_action))
                    }
                } else {
                    Button(
                        onClick = { onAction(step) },
                        enabled = !done,
                        modifier = Modifier.fillMaxWidth().focusRequester(firstButton).sableFocusRing(),
                    ) {
                        Text(if (done) stringResource(R.string.setup_allowed) else stepAction(step))
                    }
                }
                OutlinedButton(
                    onClick = { if (last) onFinish() else index += 1 },
                    modifier = Modifier.fillMaxWidth().sableFocusRing(),
                ) {
                    Text(
                        when {
                            last -> stringResource(R.string.setup_done)
                            done || (step == SetupStep.EXTRAS && picked.isNotEmpty()) -> stringResource(R.string.setup_next)
                            else -> stringResource(R.string.setup_skip)
                        },
                    )
                }
            }
        }
    }
    LaunchedEffect(index) { runCatching { firstButton.requestFocus() } }
}

/**
 * Every setup choice on one screen, for changing any of them later: each shows whether it is on, with a button to
 * turn it on, or to change it where Android keeps the switch. Extra apps are ticked or unticked here and saved.
 */
@Composable
private fun SetupOverview(
    state: SetupState,
    picked: Set<String>,
    onPickedChange: (Set<String>) -> Unit,
    firstButton: FocusRequester,
    onAction: (SetupStep) -> Unit,
    onManage: (SetupStep) -> Unit,
    onApplyExtras: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onFinish: () -> Unit,
) {
    Text(
        stringResource(R.string.setup_all_done_detail),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SetupStep.entries.forEachIndexed { position, step ->
        val done = state.isDone(step)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stepLabel(step), style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        step == SetupStep.EXTRAS -> stringResource(R.string.setup_extras_status, picked.size)
                        done -> stringResource(R.string.setup_allowed)
                        else -> stringResource(R.string.setup_not_set)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (step == SetupStep.EXTRAS) {
                    ExtrasPicker(picked = picked, onChange = onPickedChange)
                    Button(
                        onClick = onApplyExtras,
                        modifier = Modifier.fillMaxWidth().sableFocusRing().then(if (position == 0) Modifier.focusRequester(firstButton) else Modifier),
                    ) {
                        Text(stringResource(R.string.setup_extras_action))
                    }
                } else {
                    OutlinedButton(
                        onClick = { if (done) onManage(step) else onAction(step) },
                        modifier = Modifier.fillMaxWidth().sableFocusRing().then(if (position == 0) Modifier.focusRequester(firstButton) else Modifier),
                    ) {
                        Text(if (done) stringResource(R.string.setup_change) else stepAction(step))
                    }
                }
            }
        }
    }
    OutlinedButton(onClick = onOpenLauncherSettings, modifier = Modifier.fillMaxWidth().sableFocusRing()) {
        Text(stringResource(R.string.launcher_settings))
    }
    Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().sableFocusRing()) {
        Text(stringResource(R.string.setup_done))
    }
}

@Composable
private fun stepLabel(step: SetupStep): String =
    stringResource(
        when (step) {
            SetupStep.HOME -> R.string.setup_label_home
            SetupStep.CONTACTS -> R.string.setup_label_contacts
            SetupStep.PHOTOS -> R.string.setup_label_photos
            SetupStep.NOTIFICATIONS -> R.string.setup_label_notifications
            SetupStep.USAGE -> R.string.setup_label_usage
            SetupStep.EXTRAS -> R.string.setup_label_extras
        },
    )

@Composable
private fun StepCard(step: SetupStep) {
    val (title, reason) =
        when (step) {
            SetupStep.HOME -> stringResource(R.string.setup_home_title) to stringResource(R.string.setup_home_reason)
            SetupStep.CONTACTS -> stringResource(R.string.setup_contacts_title) to stringResource(R.string.setup_contacts_reason)
            SetupStep.PHOTOS -> stringResource(R.string.setup_photos_title) to stringResource(R.string.setup_photos_reason)
            SetupStep.NOTIFICATIONS ->
                stringResource(R.string.setup_notifications_title) to stringResource(R.string.setup_notifications_reason)
            SetupStep.USAGE -> stringResource(R.string.setup_usage_title) to stringResource(R.string.setup_usage_reason)
            SetupStep.EXTRAS -> stringResource(R.string.setup_extras_title) to stringResource(R.string.setup_extras_reason)
        }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (step.required) stringResource(R.string.setup_required) else stringResource(R.string.setup_optional),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RestrictedSettingsCard(onOpenAppInfo: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.setup_restricted_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.setup_restricted_reason), style = MaterialTheme.typography.bodyMedium)
            Row {
                OutlinedButton(onClick = onOpenAppInfo, modifier = Modifier.sableFocusRing()) {
                    Text(stringResource(R.string.setup_restricted_action))
                }
            }
        }
    }
}

@Composable
private fun stepAction(step: SetupStep): String =
    stringResource(
        when (step) {
            SetupStep.HOME -> R.string.setup_home_action
            SetupStep.CONTACTS -> R.string.setup_contacts_action
            SetupStep.PHOTOS -> R.string.setup_photos_action
            SetupStep.NOTIFICATIONS -> R.string.setup_notifications_action
            SetupStep.USAGE -> R.string.setup_usage_action
            SetupStep.EXTRAS -> R.string.setup_extras_action
        },
    )

@Composable
private fun ExtrasPicker(
    picked: Set<String>,
    onChange: (Set<String>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(ExtraGroup.Tools to R.string.setup_extras_tools, ExtraGroup.Games to R.string.setup_extras_games).forEach { (group, heading) ->
            Text(stringResource(heading), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            ExtraApps.ofGroup(group).forEach { app ->
                val on = app.id in picked
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .sableFocusRing()
                            .toggleable(
                                value = on,
                                role = Role.Checkbox,
                                onValueChange = { onChange(if (it) picked + app.id else picked - app.id) },
                            ).padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = on, onCheckedChange = null)
                    Column {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge)
                        Text(app.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
