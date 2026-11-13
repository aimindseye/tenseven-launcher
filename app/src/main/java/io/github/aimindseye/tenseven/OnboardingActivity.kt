package io.github.aimindseye.tenseven

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.core.content.edit
import androidx.core.net.toUri
import io.github.aimindseye.tenseven.conversations.ConversationAccess
import io.github.aimindseye.tenseven.design.DesignText
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.model.ExtraApps
import io.github.aimindseye.tenseven.model.ExtraGroup
import io.github.aimindseye.tenseven.model.StartText
import io.github.aimindseye.tenseven.onboarding.OnboardingPlan
import io.github.aimindseye.tenseven.onboarding.Setup2Plan
import io.github.aimindseye.tenseven.onboarding.Setup2Stage
import io.github.aimindseye.tenseven.onboarding.SetupState
import io.github.aimindseye.tenseven.onboarding.SetupStatus
import io.github.aimindseye.tenseven.onboarding.SetupStep
import io.github.aimindseye.tenseven.onboarding.SpecialAccessAttemptStore
import io.github.aimindseye.tenseven.platform.ExtraAppsControl
import io.github.aimindseye.tenseven.platform.StartStateRepository
import io.github.aimindseye.tenseven.ui.FrameSettingsSection

/**
 * First-run setup, and "Setup and permissions" in the menu: the home role first, then each optional
 * permission with the reason for it, one at a time. Every optional step can be skipped. Nothing is asked
 * silently: each button opens the system dialog or the Settings page for exactly that one thing.
 * Opened with [EXTRA_STEP] it shows only that step (for example from the Hub's "Allow notification access").
 */
class OnboardingActivity : ComponentActivity() {
    private val setup = mutableStateOf<SetupState?>(null)
    private val attemptedSpecialAccess = mutableStateOf<Set<SetupStep>>(emptySet())
    private lateinit var startState: StartStateRepository
    private lateinit var steps: List<SetupStep>
    private val homeRoleResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }
    private val permissionResult = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fresh-install and reopened Setup use the same seven-section Settings
        // as Home > Launcher Settings. Feature-specific step links still work.
        if (intent?.getStringExtra(EXTRA_STEP).isNullOrBlank()) {
            val opened =
                runCatching {
                    startActivity(
                        Intent(this, LauncherSettingsActivity::class.java)
                            .putExtra("tenseven.settings.category", "permissions"),
                    )
                    true
                }.getOrDefault(false)
            if (opened) {
                StartStateRepository(this).saveOnboardingDone()
                finish()
                return
            }
        }
        StartText.t.bindTo(this)
        DesignText.t.bindTo(this)
        startState = StartStateRepository(this)
        attemptedSpecialAccess.value = SpecialAccessAttemptStore.attempted(this)
        val state = SetupStatus.read(this)
        setup.value = state
        val only =
            intent
                ?.getStringExtra(EXTRA_STEP)
                ?.let { name ->
                    SetupStep.entries.firstOrNull { it.name == name }
                }?.takeUnless {
                    !state.notificationListenerAvailable && it in setOf(SetupStep.NOTIFICATIONS, SetupStep.LOCK)
                }
        steps = if (only != null) listOf(only) else OnboardingPlan.steps(state)
        // Reopening Setup follows the same choices, retaining the existing selections.
        installSetupContent(state, only)
    }

    private fun installSetupContent(
        state: SetupState,
        only: SetupStep?,
    ) {
        val installer = SetupStatus.installer(this)
        val restrictedHelp: (SetupStep) -> Boolean = { step ->
            OnboardingPlan.needsRestrictedSettingsHelp(
                step,
                Build.VERSION.SDK_INT,
                installer,
            )
        }
        setContent {
            LauncherTheme(window = window) {
                ProvideHomeStyle(startState) {
                    if (only == null) {
                        FullSetupContent(state, restrictedHelp)
                    } else {
                        SingleStepSetupContent(state, restrictedHelp)
                    }
                }
            }
        }
    }

    @Suppress("FunctionNaming")
    @Composable
    private fun FullSetupContent(
        state: SetupState,
        restrictedHelp: (SetupStep) -> Boolean,
    ) {
        SetupWizard2(
            state = setup.value ?: state,
            settings = startState,
            access =
                SetupWizardAccess(
                    showRestrictedHelp = restrictedHelp,
                    attemptedSpecialAccess = attemptedSpecialAccess.value,
                    onAction = ::perform,
                    onManage = ::manage,
                    onOpenAppInfo = ::openAppInfo,
                ),
            extras =
                SetupWizardExtras(
                    enabledExtras = ExtraAppsControl.enabledIds(this),
                    onApplyExtras = ::applyExtras,
                    onOpenLauncherSettings = {
                        openSettings(Intent(this, LauncherSettingsActivity::class.java))
                    },
                    onFinish = { finish() },
                ),
        )
    }

    @Suppress("FunctionNaming")
    @Composable
    private fun SingleStepSetupContent(
        state: SetupState,
        restrictedHelp: (SetupStep) -> Boolean,
    ) {
        // Preserve one-step permission/deep-link entry points from the existing release.
        SetupScreen(
            steps = steps,
            startOnOverview = false,
            state = setup.value ?: state,
            showRestrictedHelp = restrictedHelp,
            attemptedSpecialAccess = attemptedSpecialAccess.value,
            onAction = ::perform,
            enabledExtras = ExtraAppsControl.enabledIds(this),
            onApplyExtras = ::applyExtras,
            onOpenAppInfo = ::openAppInfo,
            onManage = ::manage,
            onOpenLauncherSettings = {
                openSettings(Intent(this, LauncherSettingsActivity::class.java))
            },
            onFinish = { finish() },
            settings = startState,
        )
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
        // Permission granted is not the same as listener connected. Never rebind an active listener
        // merely because Setup resumed; doing so can briefly empty Hub, Peek and lock counts.
        io.github.aimindseye.tenseven.platform.NotificationConnectionRecovery
            .onLauncherResume(this)
    }

    private fun perform(step: SetupStep) {
        if (step.isSpecialAccess) {
            attemptedSpecialAccess.value = attemptedSpecialAccess.value + step
            SpecialAccessAttemptStore.markAttempted(this, step)
        }
        when (step) {
            SetupStep.HOME -> {
                requestHomeRole()
            }

            SetupStep.CONTACTS -> {
                permissionResult.launch(Manifest.permission.READ_CONTACTS)
            }

            SetupStep.PHOTOS -> {
                permissionResult.launch(SetupStatus.photosPermission())
            }

            SetupStep.NOTIFICATIONS -> {
                openSettings(ConversationAccess.settingsIntent())
            }

            SetupStep.USAGE -> {
                openSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).setData(Uri.parse("package:$packageName")))
            }

            SetupStep.EXTRAS -> {
                // Extras are configured by the Setup picker.
            }

            // chosen with checkboxes on the step itself
            SetupStep.STYLE, SetupStep.LOOK, SetupStep.GRID, SetupStep.WIDGETS, SetupStep.HUB, SetupStep.LOCK -> {
                // These controls are rendered and persisted on their own Setup steps.
            } // controls on the step itself
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
        val old = ExtraAppsControl.enabledIds(this)
        val roles = getSystemService(RoleManager::class.java)
        // Never disable an activity while Android still holds its default-Phone role.
        if ("tenseven_phone" in old && "tenseven_phone" !in ids &&
            roles?.isRoleHeld(RoleManager.ROLE_DIALER) == true
        ) {
            android.widget.Toast
                .makeText(
                    this,
                    "Choose another default Phone app in Android Settings before disabling TenSeven Phone",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
            refresh()
            return
        }
        ExtraAppsControl.apply(this, ids)
        refresh()
        // This only follows a user-triggered Apps & Features Save, never an install or resume.
        val roleNotYetOffered =
            "tenseven_phone" in ids &&
                !getSharedPreferences("d5_phone_role_offer", MODE_PRIVATE).getBoolean("offered", false)
        if (roleNotYetOffered && roles?.let {
                it.isRoleAvailable(RoleManager.ROLE_DIALER) && !it.isRoleHeld(RoleManager.ROLE_DIALER)
            } == true
        ) {
            // A user-triggered Save can offer once, even on upgrades that already enabled Phone.
            // After decline, use the explicit Launcher Settings button instead of nagging.
            getSharedPreferences("d5_phone_role_offer", MODE_PRIVATE)
                .edit {
                    putBoolean("offered", true)
                }
            window.decorView.post {
                runCatching { startActivity(Intent(this, D5PhoneRoleRequestActivity::class.java)) }
                    .onFailure {
                        android.widget.Toast
                            .makeText(
                                this,
                                "TenSeven Phone was enabled. Choose it in Android Default Apps when ready.",
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                    }
            }
        }
    }

    private fun requestHomeRole() {
        val roles = getSystemService(RoleManager::class.java)
        val canAsk = roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)
        if (canAsk) {
            runCatching { homeRoleResult.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME)) }
        } else {
            openSettings(Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }

    private fun openAppInfo() {
        openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
    }

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
    attemptedSpecialAccess: Set<SetupStep>,
    onAction: (SetupStep) -> Unit,
    enabledExtras: Set<String>,
    onApplyExtras: (Set<String>) -> Unit,
    onOpenAppInfo: () -> Unit,
    onManage: (SetupStep) -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onFinish: () -> Unit,
    settings: StartStateRepository,
) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    var overview by rememberSaveable { mutableStateOf(startOnOverview) }
    val firstButton = remember { FocusRequester() }
    var picked by remember { mutableStateOf(enabledExtras) }
    BackHandler(enabled = !overview && index > 0) { index -= 1 }
    ProvideHomeStyle(settings) {
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
                SetupWizardRouter(
                    model = SetupWizardRouteModel(steps, state, picked, index, overview),
                    ui = SetupWizardRouteUi(firstButton, settings, attemptedSpecialAccess, showRestrictedHelp),
                    actions =
                        SetupWizardRouteActions(
                            onPick = { picked = it },
                            onApply = { onApplyExtras(picked) },
                            onAction = onAction,
                            onManage = onManage,
                            onOpenInfo = onOpenAppInfo,
                        ),
                    navigation =
                        SetupWizardRouteNavigation(
                            onLauncherSettings = onOpenLauncherSettings,
                            onFinish = onFinish,
                            onNext = { if (index + 1 >= steps.size) onFinish() else index += 1 },
                        ),
                )
            }
        }
    }
    LaunchedEffect(index) { runCatching { firstButton.requestFocus() } }
}

private class SetupWizardRouteModel(
    val steps: List<SetupStep>,
    val state: SetupState,
    val picked: Set<String>,
    val index: Int,
    val overview: Boolean,
)

private class SetupWizardRouteUi(
    val focus: FocusRequester,
    val settings: StartStateRepository,
    val attempted: Set<SetupStep>,
    val showRestrictedHelp: (SetupStep) -> Boolean,
)

private class SetupWizardRouteActions(
    val onPick: (Set<String>) -> Unit,
    val onApply: () -> Unit,
    val onAction: (SetupStep) -> Unit,
    val onManage: (SetupStep) -> Unit,
    val onOpenInfo: () -> Unit,
)

private class SetupWizardRouteNavigation(
    val onLauncherSettings: () -> Unit,
    val onFinish: () -> Unit,
    val onNext: () -> Unit,
)

@Suppress("FunctionNaming")
@Composable
private fun SetupWizardRouter(
    model: SetupWizardRouteModel,
    ui: SetupWizardRouteUi,
    actions: SetupWizardRouteActions,
    navigation: SetupWizardRouteNavigation,
) {
    val step = model.steps.getOrNull(model.index)
    if (model.overview || step == null) {
        SetupOverview(
            state = model.state,
            showRestrictedHelp = ui.showRestrictedHelp,
            attemptedSpecialAccess = ui.attempted,
            onOpenAppInfo = actions.onOpenInfo,
            picked = model.picked,
            onPickedChange = actions.onPick,
            firstButton = ui.focus,
            onAction = actions.onAction,
            onManage = actions.onManage,
            onApplyExtras = actions.onApply,
            onOpenLauncherSettings = navigation.onLauncherSettings,
            onFinish = navigation.onFinish,
            settings = ui.settings,
        )
    } else {
        val done = model.state.isDone(step)
        SetupWizardStep(
            display = SetupWizardDisplay(step, done, model.index, model.steps.size, model.picked),
            bindings = SetupWizardBindings(ui.settings, model.state, ui.focus),
            showRestrictedHelp = ui.showRestrictedHelp(step),
            attemptedSpecialAccess = step in ui.attempted,
            events =
                SetupWizardEvents(
                    onPick = actions.onPick,
                    onApply = actions.onApply,
                    onPrimary = { if (done) actions.onManage(step) else actions.onAction(step) },
                    onNext = navigation.onNext,
                    onOpenAppInfo = actions.onOpenInfo,
                ),
        )
    }
}

/**
 * Every setup choice on one screen, for changing any of them later: each shows whether it is on, with a button to
 * turn it on, or to change it where Android keeps the switch. Extra apps are ticked or unticked here and saved.
 */
@Composable
private fun SetupOverview(
    state: SetupState,
    showRestrictedHelp: (SetupStep) -> Boolean,
    attemptedSpecialAccess: Set<SetupStep>,
    onOpenAppInfo: () -> Unit,
    picked: Set<String>,
    onPickedChange: (Set<String>) -> Unit,
    firstButton: FocusRequester,
    onAction: (SetupStep) -> Unit,
    onManage: (SetupStep) -> Unit,
    onApplyExtras: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onFinish: () -> Unit,
    settings: StartStateRepository,
) {
    Text(
        stringResource(R.string.setup_all_done_detail),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // Reopened setup defaults to overview: guidance must be visible here too,
    // not only in the initial wizard's Notification or Usage step.
    if (OnboardingPlan.needsRestrictedSettingsOverviewHelp(
            state,
            showRestrictedHelp,
            attemptedSpecialAccess,
        )
    ) {
        RestrictedSettingsCard(onOpenAppInfo = onOpenAppInfo)
    } else if (listOf(SetupStep.NOTIFICATIONS, SetupStep.USAGE)
            .any { !state.isDone(it) && showRestrictedHelp(it) }
    ) {
        Text(
            stringResource(R.string.setup_restricted_first_request),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val rowEvents =
        SetupOverviewRowEvents(
            onPick = onPickedChange,
            onApplyExtras = onApplyExtras,
            onAction = onAction,
            onManage = onManage,
            firstButton = firstButton,
        )
    SetupStep.entries.forEachIndexed { position, step ->
        SetupOverviewRowItem(
            SetupOverviewRow(step, position, state.isDone(step), picked, settings),
            rowEvents,
            state,
        )
    }
    OutlinedButton(onClick = onOpenLauncherSettings, modifier = Modifier.fillMaxWidth().sableFocusRing()) {
        Text(stringResource(R.string.launcher_settings))
    }
    Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().sableFocusRing()) {
        Text(stringResource(R.string.setup_done))
    }
}

private class SetupWizardDisplay(
    val step: SetupStep,
    val done: Boolean,
    val index: Int,
    val total: Int,
    val picked: Set<String>,
) {
    val last: Boolean get() = index + 1 >= total
}

private class SetupWizardBindings(
    val settings: StartStateRepository,
    val state: SetupState,
    val focus: FocusRequester,
)

private class SetupWizardEvents(
    val onPick: (Set<String>) -> Unit,
    val onApply: () -> Unit,
    val onPrimary: () -> Unit,
    val onNext: () -> Unit,
    val onOpenAppInfo: () -> Unit,
)

@Suppress("FunctionNaming")
@Composable
private fun SetupWizardStep(
    display: SetupWizardDisplay,
    bindings: SetupWizardBindings,
    showRestrictedHelp: Boolean,
    attemptedSpecialAccess: Boolean,
    events: SetupWizardEvents,
) {
    val step = display.step
    val settings = bindings.settings
    val state = bindings.state
    val firstButton = bindings.focus
    Text(
        stringResource(R.string.setup_progress, display.index + 1, display.total),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    StepCard(step = step)
    if (showRestrictedHelp && !display.done) {
        if (attemptedSpecialAccess) {
            RestrictedSettingsCard(onOpenAppInfo = events.onOpenAppInfo)
        } else {
            Text(
                stringResource(R.string.setup_restricted_first_request),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (step == SetupStep.EXTRAS) {
        ExtrasPicker(picked = display.picked, onChange = events.onPick)
        Button(
            onClick = events.onApply,
            modifier = Modifier.fillMaxWidth().focusRequester(firstButton).sableFocusRing(),
        ) { Text(stringResource(R.string.setup_extras_action)) }
    } else if (step.isChoice) {
        ChoiceSection(step, settings)
    } else {
        SetupWizardPrimaryAction(display, firstButton, events.onPrimary)
    }
    if (step == SetupStep.NOTIFICATIONS && display.done) {
        FrameSettingsSection(settings, state.notificationAccess)
    }
    SetupWizardNext(display, events.onNext)
}

@Suppress("FunctionNaming")
@Composable
private fun SetupWizardPrimaryAction(
    display: SetupWizardDisplay,
    focus: FocusRequester,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().focusRequester(focus).sableFocusRing(),
    ) { Text(if (display.done) stringResource(R.string.setup_change) else stepAction(display.step)) }
}

@Suppress("FunctionNaming")
@Composable
private fun SetupWizardNext(
    display: SetupWizardDisplay,
    onNext: () -> Unit,
) {
    OutlinedButton(onClick = onNext, modifier = Modifier.fillMaxWidth().sableFocusRing()) {
        Text(
            when {
                display.last -> {
                    stringResource(R.string.setup_done)
                }

                display.done || (display.step == SetupStep.EXTRAS && display.picked.isNotEmpty()) -> {
                    stringResource(R.string.setup_next)
                }

                else -> {
                    stringResource(R.string.setup_skip)
                }
            },
        )
    }
}

private class SetupOverviewRow(
    val step: SetupStep,
    val position: Int,
    val done: Boolean,
    val picked: Set<String>,
    val settings: StartStateRepository,
)

private class SetupOverviewRowEvents(
    val onPick: (Set<String>) -> Unit,
    val onApplyExtras: () -> Unit,
    val onAction: (SetupStep) -> Unit,
    val onManage: (SetupStep) -> Unit,
    val firstButton: FocusRequester,
)

@Suppress("FunctionNaming")
@Composable
private fun SetupOverviewRowItem(
    row: SetupOverviewRow,
    events: SetupOverviewRowEvents,
    state: SetupState,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stepLabel(row.step), style = MaterialTheme.typography.titleMedium)
            if (row.step.isChoice) {
                ChoiceSection(row.step, row.settings)
                return@Column
            }
            SetupOverviewRowStatus(row)
            SetupOverviewRowAction(row, events)
            if (row.step == SetupStep.NOTIFICATIONS && row.done) {
                FrameSettingsSection(row.settings, state.notificationAccess)
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun SetupOverviewRowStatus(row: SetupOverviewRow) {
    Text(
        when {
            row.step == SetupStep.EXTRAS -> {
                stringResource(R.string.setup_extras_status, row.picked.size)
            }

            row.step == SetupStep.NOTIFICATIONS && row.done &&
                !io.github.aimindseye.tenseven.conversations.NotificationConnection.connected -> {
                stringResource(R.string.setup_notifications_connected_missing)
            }

            row.done -> {
                stringResource(R.string.setup_allowed)
            }

            else -> {
                stringResource(R.string.setup_not_set)
            }
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (row.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Suppress("FunctionNaming")
@Composable
private fun SetupOverviewRowAction(
    row: SetupOverviewRow,
    events: SetupOverviewRowEvents,
) {
    val modifier =
        Modifier.fillMaxWidth().sableFocusRing().then(
            if (row.position == 0) Modifier.focusRequester(events.firstButton) else Modifier,
        )
    if (row.step == SetupStep.EXTRAS) {
        ExtrasPicker(picked = row.picked, onChange = events.onPick)
        Button(onClick = events.onApplyExtras, modifier = modifier) {
            Text(stringResource(R.string.setup_extras_action))
        }
    } else {
        OutlinedButton(
            onClick = { if (row.done) events.onManage(row.step) else events.onAction(row.step) },
            modifier = modifier,
        ) { Text(if (row.done) stringResource(R.string.setup_change) else stepAction(row.step)) }
    }
}

/** The controls of a choice step, the same ones Launcher Settings shows. */
@Composable
private fun ChoiceSection(
    step: SetupStep,
    state: StartStateRepository,
) {
    when (step) {
        SetupStep.STYLE -> {
            HomeStyleSection(state)
            HomeDisplaySection(state)
        }

        SetupStep.LOOK -> {
            ThemeSection(state)
            WallpaperSection(state)
        }

        SetupStep.GRID -> {
            IconPresentationSection(state)
            IconGridSection(state)
        }

        SetupStep.WIDGETS -> {
            WidgetsSection(state)
        }

        SetupStep.HUB -> {
            HubSection()
        }

        SetupStep.LOCK -> {
            LockLayerSection(state)
        }

        else -> {
            // No additional choice screen for this step.
        }
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
            SetupStep.STYLE -> R.string.setup_label_style
            SetupStep.LOOK -> R.string.setup_label_look
            SetupStep.GRID -> R.string.setup_label_grid
            SetupStep.WIDGETS -> R.string.setup_label_widgets
            SetupStep.HUB -> R.string.setup_label_hub
            SetupStep.LOCK -> R.string.setup_label_lock
        },
    )

@Composable
private fun StepCard(step: SetupStep) {
    val (title, reason) =
        when (step) {
            SetupStep.HOME -> {
                stringResource(R.string.setup_home_title) to stringResource(R.string.setup_home_reason)
            }

            SetupStep.CONTACTS -> {
                stringResource(R.string.setup_contacts_title) to stringResource(R.string.setup_contacts_reason)
            }

            SetupStep.PHOTOS -> {
                stringResource(R.string.setup_photos_title) to stringResource(R.string.setup_photos_reason)
            }

            SetupStep.NOTIFICATIONS -> {
                stringResource(R.string.setup_notifications_title) to stringResource(R.string.setup_notifications_reason)
            }

            SetupStep.USAGE -> {
                stringResource(R.string.setup_usage_title) to stringResource(R.string.setup_usage_reason)
            }

            SetupStep.EXTRAS -> {
                stringResource(R.string.setup_extras_title) to stringResource(R.string.setup_extras_reason)
            }

            SetupStep.STYLE -> {
                stringResource(R.string.setup_style_title) to stringResource(R.string.setup_style_reason)
            }

            SetupStep.LOOK -> {
                stringResource(R.string.setup_look_title) to stringResource(R.string.setup_look_reason)
            }

            SetupStep.GRID -> {
                stringResource(R.string.setup_grid_title) to stringResource(R.string.setup_grid_reason)
            }

            SetupStep.WIDGETS -> {
                stringResource(R.string.setup_widgets_title) to stringResource(R.string.setup_widgets_reason)
            }

            SetupStep.HUB -> {
                stringResource(R.string.setup_hub_title) to stringResource(R.string.setup_hub_reason)
            }

            SetupStep.LOCK -> {
                stringResource(R.string.setup_lock_title) to stringResource(R.string.setup_lock_reason)
            }
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
            else -> R.string.setup_next
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

@Suppress("FunctionNaming")
@Composable
private fun SetupStageWelcome(
    state: SetupState,
    access: SetupWizardAccess,
) {
    Text("Choose TenSeven as your Home app. Every other permission is optional.")
    StepCard(SetupStep.HOME)
    OutlinedButton(
        onClick = { if (state.homeRoleHeld) access.onManage(SetupStep.HOME) else access.onAction(SetupStep.HOME) },
        modifier = Modifier.fillMaxWidth().sableFocusRing(),
    ) { Text(if (state.homeRoleHeld) "Change default Home app" else "Set TenSeven as Home") }
}

@Suppress("FunctionNaming")
@Composable
private fun SetupStageHome(settings: StartStateRepository) {
    Text("Select the BB10 or BB7 home. Their saved layout settings remain separate.")
    ChoiceSection(SetupStep.STYLE, settings)
    ChoiceSection(SetupStep.GRID, settings)
    ChoiceSection(SetupStep.WIDGETS, settings)
}

@Suppress("FunctionNaming")
@Composable
private fun SetupStageAppearance(settings: StartStateRepository) {
    Text(
        "Use the default BlackBerry look or choose phone colors. " +
            "Additional themes, wallpapers and icon packs remain optional.",
    )
    Settings2AppearanceChoices(settings)
}

@Suppress("FunctionNaming")
@Composable
private fun SetupStageApps(
    picked: Set<String>,
    selectApps: (Set<String>) -> Unit,
) {
    Text("Choose a preset or enable individual bundled tools and games. Changes save immediately.")
    OutlinedButton(
        onClick = { selectApps(Setup2Plan.essentials) },
        modifier = Modifier.fillMaxWidth().sableFocusRing(),
    ) {
        Text("Essentials · Calculator, Tasks, Notes")
    }
    OutlinedButton(
        onClick = { selectApps(Setup2Plan.full()) },
        modifier = Modifier.fillMaxWidth().sableFocusRing(),
    ) {
        Text("Full · All currently bundled extras")
    }
    Text("Custom · Select individual apps below.", style = MaterialTheme.typography.titleSmall)
    ExtrasPicker(picked = picked, onChange = selectApps)
    Text(
        "Optional Calendar and Phone apps will be configurable when installed. " +
            "They are not installed by this D1 build.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Suppress("FunctionNaming")
@Composable
private fun SetupStageHubLock(settings: StartStateRepository) {
    Text(
        "Hub follows Android-delivered notifications, including silent sources. " +
            "Notification access is requested in the next step.",
    )
    ChoiceSection(SetupStep.HUB, settings)
    ChoiceSection(SetupStep.LOCK, settings)
    Text(
        "BB10 Active Frames: media-session details work with Notification access. " +
            "FM radio/player notification sources and messaging excerpts require opt-in " +
            "under Launcher Settings → Active Frames → Configure app summaries.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Suppress("FunctionNaming")
@Composable
private fun SetupStagePermissions(
    state: SetupState,
    access: SetupWizardAccess,
) {
    Text(
        "Android asks you to grant each optional permission. " +
            "TenSeven cannot grant restricted access automatically.",
    )
    for (step in Setup2Plan.steps(Setup2Stage.PERMISSIONS)) {
        if (!state.notificationListenerAvailable && step == SetupStep.NOTIFICATIONS) continue
        StepCard(step)
        val done = state.isDone(step)
        OutlinedButton(
            onClick = { if (done) access.onManage(step) else access.onAction(step) },
            modifier = Modifier.fillMaxWidth().sableFocusRing(),
        ) { Text(if (done) "Granted · manage" else stepAction(step)) }
        if (!done && access.showRestrictedHelp(step)) {
            if (step in access.attemptedSpecialAccess) {
                RestrictedSettingsCard(onOpenAppInfo = access.onOpenAppInfo)
            } else {
                Text(
                    stringResource(R.string.setup_restricted_first_request),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun SetupStageReview(
    state: SetupState,
    pickedCount: Int,
    onOpenLauncherSettings: () -> Unit,
) {
    Text("Your selections are saved. You can return to any category in Launcher Settings.")
    Text("Default Home: ${if (state.homeRoleHeld) "ready" else "not selected"}")
    Text(
        "Notification access: ${if (state.notificationAccess) "granted" else "optional · not granted"}",
    )
    Text("Usage access: ${if (state.usageAccess) "granted" else "optional · not granted"}")
    Text("Contacts access: ${if (state.contactsGranted) "granted" else "optional · not granted"}")
    Text("Selected bundled apps: $pickedCount")
    Text(
        "Active Frames: choose BB10 Home and grant Notification access to configure " +
            "radio/player notification sources and optional message excerpts. " +
            "Saved choices survive upgrades.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
        onClick = onOpenLauncherSettings,
        modifier = Modifier.fillMaxWidth().sableFocusRing(),
    ) {
        Text("Open Launcher Settings")
    }
}

/** Seven-stage setup reusing the very same persisted controls as Launcher Settings. */
private class SetupWizardAccess(
    val showRestrictedHelp: (SetupStep) -> Boolean,
    val attemptedSpecialAccess: Set<SetupStep>,
    val onAction: (SetupStep) -> Unit,
    val onManage: (SetupStep) -> Unit,
    val onOpenAppInfo: () -> Unit,
)

private class SetupWizardExtras(
    val enabledExtras: Set<String>,
    val onApplyExtras: (Set<String>) -> Unit,
    val onOpenLauncherSettings: () -> Unit,
    val onFinish: () -> Unit,
)

/** The setup stage retains callbacks without widening its Compose signature. */
@Suppress("FunctionNaming")
@Composable
private fun SetupWizard2(
    state: SetupState,
    settings: StartStateRepository,
    access: SetupWizardAccess,
    extras: SetupWizardExtras,
) {
    val enabledExtras = extras.enabledExtras
    val onApplyExtras = extras.onApplyExtras
    val onOpenLauncherSettings = extras.onOpenLauncherSettings
    val onFinish = extras.onFinish
    var index by rememberSaveable { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf(enabledExtras) }
    val stages = Setup2Plan.stages
    val stage = stages[index.coerceIn(0, stages.lastIndex)]
    val scroll = rememberScrollState()
    val selectApps: (Set<String>) -> Unit = { ids ->
        val normalized = Setup2Plan.normalizedCustom(ids)
        picked = normalized
        onApplyExtras(normalized)
    }

    BackHandler(enabled = index > 0) { index -= 1 }
    LaunchedEffect(index) { scroll.scrollTo(0) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(scroll)
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SetupWizardHeader(index, stages.size, stage.title)

            when (stage) {
                Setup2Stage.WELCOME -> SetupStageWelcome(state, access)
                Setup2Stage.HOME -> SetupStageHome(settings)
                Setup2Stage.APPEARANCE -> SetupStageAppearance(settings)
                Setup2Stage.APPS -> SetupStageApps(picked, selectApps)
                Setup2Stage.HUB_LOCK -> SetupStageHubLock(settings)
                Setup2Stage.PERMISSIONS -> SetupStagePermissions(state, access)
                Setup2Stage.REVIEW -> SetupStageReview(state, picked.size, onOpenLauncherSettings)
            }

            SetupWizardNavigation(
                index = index,
                last = stages.lastIndex,
                onBack = { index -= 1 },
                onNext = { index += 1 },
                onFinish = onFinish,
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun SetupWizardHeader(
    index: Int,
    total: Int,
    title: String,
) {
    Text("TenSeven 2.0 Setup", style = MaterialTheme.typography.titleLarge)
    Text(
        "Step ${index + 1} of $total · $title",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Suppress("FunctionNaming")
@Composable
private fun SetupWizardNavigation(
    index: Int,
    last: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (index > 0) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).sableFocusRing()) {
                Text("Back")
            }
        }
        OutlinedButton(
            onClick = { if (index == last) onFinish() else onNext() },
            modifier = Modifier.weight(1f).sableFocusRing(),
        ) {
            Text(if (index == last) "Finish" else "Next")
        }
    }
}
