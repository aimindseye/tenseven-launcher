package io.github.aimindseye.tenseven

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.aimindseye.tenseven.design.DesignText
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.model.StartText
import io.github.aimindseye.tenseven.onboarding.SetupState
import io.github.aimindseye.tenseven.platform.StartStateRepository
import io.github.aimindseye.tenseven.ui.FrameSettingsSection
import kotlinx.coroutines.launch

/** Named presentation/protocol values; numeric values unchanged from the reviewed source. */
private const val LAUNCHER_SETTINGS_ACTIVITY_SETTINGS2_READABIL_MAX_90 = 90

/**
 * Launcher Settings: one screen for the choices people reach for most, laid out like the settings of a
 * BlackBerry-style launcher: the home screen style as two cards (BlackBerry 7 or 10), then Home, Widgets,
 * Device and Privacy. Every change is saved at once; the home screens read it when they come forward.
 */
private fun settingsCategory(candidate: String?): String =
    candidate?.takeIf {
        it in
            setOf(
                "permissions",
                "appearance_home",
                "themes",
                "lock",
                "frames",
                "apps",
                "review",
            )
    } ?: "root"

class LauncherSettingsActivity : ComponentActivity() {
    private val resumeGeneration = androidx.compose.runtime.mutableIntStateOf(0)

    // Kept on the Activity, outside the resume-generation composition key.
    // Android permission screens may pause/resume Settings without resetting its page.
    private val settingsPage = androidx.compose.runtime.mutableStateOf("root")

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("tenseven.settings.saved_category", settingsPage.value)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        settingsPage.value = settingsCategory(intent.getStringExtra("tenseven.settings.category"))
    }

    override fun onResume() {
        super.onResume()
        resumeGeneration.intValue += 1
        io.github.aimindseye.tenseven.platform.NotificationConnectionRecovery
            .onLauncherResume(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsPage.value =
            settingsCategory(
                savedInstanceState?.getString("tenseven.settings.saved_category")
                    ?: intent?.getStringExtra("tenseven.settings.category"),
            )
        StartText.t.bindTo(this)
        DesignText.t.bindTo(this)
        val state = StartStateRepository(this)
        setContent {
            LauncherTheme(window = window) {
                androidx.compose.runtime.key(resumeGeneration.intValue) {
                    LauncherSettings(
                        state = state,
                        settingsDestination = settingsPage,
                        actions =
                            LauncherSettingsActions(
                                onLanguage = {
                                    val intent =
                                        Intent(Settings.ACTION_APP_LOCALE_SETTINGS).setData(android.net.Uri.parse("package:$packageName"))
                                    runCatching { startActivity(intent) }
                                },
                                onBackup = { runCatching { startActivity(Intent(this, BackupActivity::class.java)) } },
                                onSetup = { settingsPage.value = "permissions" },
                                onAndroidSettings = { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } },
                            ),
                    )
                }
            }
        }
    }
}

private data class LauncherSettingsActions(
    val onLanguage: () -> Unit,
    val onBackup: () -> Unit,
    val onSetup: () -> Unit,
    val onAndroidSettings: () -> Unit,
)

@Composable
private fun LauncherSettings(
    state: StartStateRepository,
    actions: LauncherSettingsActions,
    settingsDestination: androidx.compose.runtime.MutableState<String>,
) {
    var destination by settingsDestination
    val context = androidx.compose.ui.platform.LocalContext.current
    // Mark Hub Settings as part of the seven-screen navigation flow; preserve
    // the existing LauncherSettingsActivity for Back/Next return via onNewIntent.
    val openHubFlow: () -> Unit = {
        runCatching {
            context.startActivity(
                Intent(context, HubSettingsActivity::class.java)
                    .putExtra("tenseven.settings.flow", true),
            )
        }
    }
    androidx.activity.compose.BackHandler(enabled = destination != "root") {
        when (
            val previous =
                io.github.aimindseye.tenseven.model.Settings2Flow
                    .previous(destination)
        ) {
            "hub" -> openHubFlow()
            null -> destination = "root"
            else -> destination = previous
        }
    }
    val first = remember { FocusRequester() }
    val pageScroll = rememberScrollState()
    LaunchedEffect(destination) { pageScroll.scrollTo(0) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            // Content scrolls, navigation never scrolls off the bottom (Titan 2/Q25).
            Column(
                modifier = Modifier.weight(1f).verticalScroll(pageScroll).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (destination == "root") {
                    launcherRootCategories({ destination = it }, openHubFlow)
                } else {
                    launcherPageBody(state, first, context, destination, { destination = it }, openHubFlow, actions)
                }
            } // Scrollable settings content ends; navigation stays in the outer Column.
            launcherSettingsFooter(context, state, destination, { destination = it }, openHubFlow)
        }
    }
    LaunchedEffect(destination) {
        if (destination == "appearance_home") runCatching { first.requestFocus() }
    }
}

private val launcherRootCategories: @Composable ((String) -> Unit, () -> Unit) -> Unit = { select, openHubFlow ->
    Text("TenSeven Launcher", style = MaterialTheme.typography.titleLarge)
    Text("Launcher Settings", style = MaterialTheme.typography.titleMedium)
    Settings2Category(
        "permissions",
        "1. Permissions",
        "Contacts, photos, recent apps and notifications",
    ) { select(it) }
    Settings2Category(
        "appearance_home",
        "2. Appearance & Home",
        "Home style, status, icons, theme and wallpaper",
    ) {
        select(it)
    }
    Settings2Category(
        "hub",
        "3. Hub Settings",
        "Compose, notifications, sources and diagnostics",
    ) { openHubFlow() }
    Settings2Category(
        "lock",
        "4. Lockscreen Settings",
        "Notifications, Peek and privacy",
    ) { select(it) }
    Settings2Category(
        "frames",
        "5. Active Frames Settings",
        "Media, app summaries and controls",
    ) { select(it) }
    Settings2Category(
        "apps",
        "6. Apps & Features",
        "Optional TenSeven apps and integrations",
    ) { select(it) }
    Settings2Category(
        "review",
        "7. Review & Finish",
        "Confirm settings and return to Home",
    ) { select(it) }
}

private val launcherPageBody: @Composable (
    StartStateRepository,
    FocusRequester,
    android.content.Context,
    String,
    (String) -> Unit,
    () -> Unit,
    LauncherSettingsActions,
) -> Unit = { state, first, context, destination, select, openHubFlow, actions ->
    if (destination == "themes") {
        androidx.compose.material3.TextButton(onClick = { select("appearance_home") }) {
            Text("← Appearance & Home")
        }
    } else {
        Settings2BackLink { select("root") }
    }
    Text(
        when (destination) {
            "permissions" -> "Permissions"
            "appearance_home" -> "Appearance & Home"
            "themes" -> "Other Themes, Wallpapers"
            "lock" -> "Lockscreen Settings"
            "frames" -> "Active Frames Settings"
            "apps" -> "Apps & Features"
            else -> "Review & Finish"
        },
        style = MaterialTheme.typography.titleLarge,
    )
    ProvideHomeStyle(state) {
        when (destination) {
            "permissions" -> {
                Settings2Permissions(context)
            }

            "appearance_home" -> {
                launcherAppearancePage(state, first, select)
            }

            "themes" -> {
                Section("Additional themes")
                ThemeSection(state)
                Section("Wallpapers")
                WallpaperSection(state)
            }

            "lock" -> {
                LockLayerSection(state)
            }

            "frames" -> {
                launcherFramesPage(state, context)
            }

            "apps" -> {
                launcherAppsPage(state)
            }

            "review" -> {
                launcherReviewPage(state, context, select, openHubFlow, actions)
            }
        }
    }
}

private val launcherAppearancePage: @Composable (
    StartStateRepository,
    FocusRequester,
    (String) -> Unit,
) -> Unit = { state, first, select ->
    Section("Home style")
    HomeStyleSection(state, first)
    Section("Home status")
    Settings2UnifiedHomeStatus(state)
    Section("Home readability")
    Settings2ReadabilityOptions(state)
    Section("Icons & layout")
    IconGridSection(state)
    IconPresentationSection(state)
    if (homeStyleState(state).value == io.github.aimindseye.tenseven.model.HomeStyle.Bb7) {
        Section("BlackBerry 7 options")
        Settings2Bb7Options(state)
    }
    Section("Theme & wallpapers")
    Settings2AppearanceChoices(state) { select("themes") }
}

private val launcherFramesPage: @Composable (
    StartStateRepository,
    android.content.Context,
) -> Unit = { state, context ->
    val bb10 =
        homeStyleState(state).value ==
            io.github.aimindseye.tenseven.model
                .HomeStyle.Bb10
    val access =
        io.github.aimindseye.tenseven.conversations.ConversationAccess
            .isGranted(context)
    if (!bb10) {
        Text(
            "Active Frames appears on BlackBerry 10 Home. Settings remain " +
                "saved when BlackBerry 7 is selected.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (!access) {
        Card(
            "Enable Notification access",
            "Required for notification-derived media and message summaries",
            false,
        ) {
            runCatching {
                context.startActivity(
                    io.github.aimindseye.tenseven.conversations.ConversationAccess
                        .settingsIntent(),
                )
            }
        }
    }
    if (bb10 && access) {
        FrameSettingsSection(state)
    } else {
        var frameOptions by remember { mutableStateOf(state.loadFrameSettings()) }
        io.github.aimindseye.tenseven.ui.FrameSettingsPanel(
            frameOptions,
            io.github.aimindseye.tenseven.ui
                .rememberPlatformCapabilities(),
        ) { updated ->
            frameOptions = updated.normalized()
            state.saveFrameSettings(frameOptions)
        }
        Card(
            "Configure app summaries",
            "Radio/player sources and optional notification excerpts",
            false,
        ) {
            context.startActivity(Intent(context, FrameIntegrationsActivity::class.java))
        }
    }
}

private val launcherAppsPage: @Composable (StartStateRepository) -> Unit = { state ->
    OptionalAppsSettings2()
    Section("Other features")
    var typeToSearch by remember { mutableStateOf(state.loadTypeToSearch()) }
    SwitchRow(
        "Type to search",
        "Start app search with the physical keyboard",
        typeToSearch,
    ) {
        typeToSearch = it
        state.saveTypeToSearch(it)
    }
    DeviceCompatibilityLink()
}

private val launcherReviewPage: @Composable (
    StartStateRepository,
    android.content.Context,
    (String) -> Unit,
    () -> Unit,
    LauncherSettingsActions,
) -> Unit = { state, context, select, openHubFlow, actions ->
    val access =
        io.github.aimindseye.tenseven.onboarding.SetupStatus
            .read(context)
    Card(
        "Home style",
        state.loadHomeStyle().let {
            if (it ==
                io.github.aimindseye.tenseven.model.HomeStyle.Bb7
            ) {
                "BlackBerry 7"
            } else {
                "BlackBerry 10"
            }
        },
        false,
    ) {
        select("appearance_home")
    }
    Card(
        "Home status",
        state.loadHomeStatusMode().name.let {
            if (it ==
                "Text"
            ) {
                "Icons"
            } else {
                it
            }
        },
        false,
    ) { select("appearance_home") }
    val overlayAllowedForReview = android.provider.Settings.canDrawOverlays(context)
    val weatherLocationForReview =
        context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    Card(
        "Permissions",
        listOf(
            access.contactsGranted,
            access.photosGranted,
            access.usageAccess,
            access.notificationAccess,
            overlayAllowedForReview,
            weatherLocationForReview,
        ).count { it }.toString() +
            " of 6 enabled",
        false,
    ) { select("permissions") }
    Card("Hub", "Compose and notification source choices", false, onClick = openHubFlow)
    Card("Active Frames", "Saved media and recent-app settings", false) {
        select("frames")
    }
    Card("Finish & Return Home", "Save choices and open TenSeven Home", true) {
        state.saveOnboardingDone()
        context.startActivity(
            Intent(context, SableStartActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent
                            .FLAG_ACTIVITY_SINGLE_TOP,
                ),
        )
        (context as? android.app.Activity)?.finish()
    }
    Section("Advanced")
    DeviceSettingsLinks(actions.onLanguage, actions.onBackup, actions.onSetup, actions.onAndroidSettings)
    D5PhoneRoleSetting()
}

private val launcherSettingsFooter: @Composable (
    android.content.Context,
    StartStateRepository,
    String,
    (String) -> Unit,
    () -> Unit,
) -> Unit = { context, state, destination, select, openHubFlow ->
    val flow = io.github.aimindseye.tenseven.model.Settings2Flow
    Settings2FlowFooter(
        backText = "Back",
        nextText = if (destination == "review") "Finish" else "Next",
        onBack = {
            when (val previous = flow.previous(destination)) {
                null -> (context as? android.app.Activity)?.finish()
                "hub" -> openHubFlow()
                else -> select(previous)
            }
        },
        onNext = {
            when (val next = flow.next(destination)) {
                "hub" -> {
                    openHubFlow()
                }

                null -> {
                    state.saveOnboardingDone()
                    context.startActivity(
                        Intent(context, SableStartActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    )
                    (context as? android.app.Activity)?.finish()
                }

                else -> {
                    select(next)
                }
            }
        },
    )
}

/** Consistent, sticky Settings navigation, including Hub's standalone settings activity. */
@Suppress("FunctionNaming")
@Composable
internal fun Settings2FlowFooter(
    backText: String,
    nextText: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        androidx.compose.material3.OutlinedButton(
            onClick = onBack,
            modifier = Modifier.weight(1f),
        ) { Text(backText) }
        androidx.compose.material3.Button(
            onClick = onNext,
            modifier = Modifier.weight(1f),
        ) { Text(nextText) }
    }
}

/** Same return link for the seven internal pages and the standalone Hub Settings activity. */
@Suppress("FunctionNaming")
@Composable
internal fun Settings2BackLink(onBack: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onBack) {
        Text("← All settings", color = MaterialTheme.colorScheme.primary)
    }
}

@Suppress("FunctionNaming")
@Composable
private fun Settings2Permissions(context: android.content.Context) {
    var permissionRefresh by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val access =
        remember(permissionRefresh) {
            io.github.aimindseye.tenseven.onboarding.SetupStatus
                .read(context)
        }
    Text(
        "Each permission is optional. Select a row to review Android's access request and its impact.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
    Settings2HomeRolePermission(context, access)
    Settings2PeoplePermissions(context, access) { permissionRefresh++ }
    Settings2SpecialPermissions(context, access)
    Settings2LocationPermission(context)
    Text(
        "Android controls these permissions. Restrictions and profile privacy remain enforced.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Suppress("FunctionNaming")
@Composable
private fun Settings2UnifiedHomeStatus(state: StartStateRepository) {
    val style = homeStyleState(state).value
    var mode by remember { mutableStateOf(state.loadHomeStatusMode()) }
    var metrics by remember { mutableStateOf(state.loadHomeDials()) }
    Text(
        "One status configuration shared by BlackBerry 7 and BlackBerry 10.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        io.github.aimindseye.tenseven.model.HomeStatusMode.entries.forEach { choice ->
            StyleCard(if (choice.name == "Text") "Icons" else choice.name, "", choice == mode, Modifier.weight(1f)) {
                mode = choice
                state.saveHomeStatusMode(choice)
            }
        }
    }
    if (mode != io.github.aimindseye.tenseven.model.HomeStatusMode.Hidden) {
        Section("Status size")
        var statusSize by remember { mutableStateOf(state.loadHomeStatusSize()) }
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            io.github.aimindseye.tenseven.model.HomeStatusSize.entries.forEach { choice ->
                StyleCard(choice.title, choice.description, choice == statusSize, Modifier.weight(1f)) {
                    statusSize = choice
                    state.saveHomeStatusSize(choice)
                }
            }
        }
        Section("Status items")
        val choices =
            listOf(
                "cpu" to "CPU",
                "ram" to "RAM",
                "battery" to "Battery",
                "wifi" to "Wi-Fi",
                "mobile" to "Mobile",
                "cell" to "Cell",
                "storage" to "Storage",
            )
        choices.forEach { (key, label) ->
            SwitchRow(
                label,
                "Shown in ${
                    if (mode.name == "Text") "icons" else mode.name.lowercase()
                } mode when Android supplies a reading",
                key in metrics,
            ) { selected ->
                metrics = if (selected) metrics + key else metrics - key
                state.saveHomeDials(metrics)
            }
        }
    }
}

/** Weather is off until a city is provided. No GPS, location or personal history. */
@Suppress("FunctionNaming")
@Composable
private fun Settings2HomeRolePermission(
    context: android.content.Context,
    access: SetupState,
) {
    if (!access.homeRoleHeld) {
        Card(
            "Default Home · Not selected",
            "Choose TenSeven as your Home app for the full launcher experience. You can do this later.",
            false,
        ) {
            context.startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS))
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun Settings2PeoplePermissions(
    context: android.content.Context,
    access: SetupState,
    onRefresh: () -> Unit,
) {
    // Runtime permission results refresh this page rather than opening old Setup.
    val contactsRequest =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts
                .RequestPermission(),
        ) { onRefresh() }
    val photosRequest =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts
                .RequestPermission(),
        ) { onRefresh() }
    val openAppInfo = {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData("package:${context.packageName}".toUri()),
        )
    }
    val granted = { permission: String ->
        context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    Card(
        "Contacts · " + if (access.contactsGranted) "Allowed" else "Not allowed",
        "Names and contact actions in Hub and Phone. Without access: generic sender details and no contact lookup.",
        false,
    ) {
        if (granted(android.Manifest.permission.READ_CONTACTS)) {
            openAppInfo()
        } else {
            contactsRequest.launch(android.Manifest.permission.READ_CONTACTS)
        }
    }
    Card(
        "Photos · " + if (access.photosGranted) "Allowed" else "Not allowed",
        "Optional access to images for wallpaper and media features. Without access: built-in " +
            "backgrounds and Home continue to work.",
        false,
    ) {
        val permission =
            io.github.aimindseye.tenseven.onboarding.SetupStatus
                .photosPermission()
        if (granted(permission)) openAppInfo() else photosRequest.launch(permission)
    }
}

@Suppress("FunctionNaming")
@Composable
private fun Settings2SpecialPermissions(
    context: android.content.Context,
    access: SetupState,
) {
    Card(
        "Recent apps / Usage · " + if (access.usageAccess) "Allowed" else "Not allowed",
        "Used for recent-app activity and Active Frames on BlackBerry 10. Without access: recent-app " +
            "frames may be limited.",
        false,
    ) {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .setData("package:${context.packageName}".toUri()),
        )
    }
    Card(
        "Notifications · " + if (access.notificationAccess) "Allowed" else "Not allowed",
        "Needed by Hub, notification Peek, lock notification counts and media summaries. Without " +
            "access: notification-driven features are unavailable.",
        false,
    ) {
        context.startActivity(
            io.github.aimindseye.tenseven.conversations.ConversationAccess
                .settingsIntent(),
        )
    }
    // Special Android access, not a runtime permission; never silently grant it.
    val overlayAllowed = android.provider.Settings.canDrawOverlays(context)
    Card(
        "Display over other apps · " + if (overlayAllowed) "Allowed" else "Not allowed",
        "Required only for the optional TenSeven lock screen to appear over other apps. Without it, " +
            "Home and Hub still work.",
        false,
    ) {
        runCatching {
            context.startActivity(
                Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:${context.packageName}".toUri(),
                ),
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun Settings2LocationPermission(context: android.content.Context) {
    val locationPermission = android.Manifest.permission.ACCESS_COARSE_LOCATION
    var locationGranted by remember(context) {
        mutableStateOf(
            context.checkSelfPermission(locationPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val requestLocation =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts
                .RequestPermission(),
        ) { granted -> locationGranted = granted }
    Card(
        "Weather location · " + if (locationGranted) "Allowed" else "Not allowed",
        "Approximate location only, while TenSeven is open. Used for optional Home weather. If " +
            "declined, enter a city manually; Home and Hub still work.",
        false,
    ) {
        if (!locationGranted) {
            requestLocation.launch(locationPermission)
        } else {
            context.startActivity(
                android.content
                    .Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData("package:${context.packageName}".toUri()),
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun Settings2ReadabilityOptions(state: StartStateRepository) {
    var size by remember { mutableStateOf(state.loadLauncherTextSize()) }
    Text("Launcher text size", style = MaterialTheme.typography.titleMedium)
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        io.github.aimindseye.tenseven.model.LauncherTextSize.entries.forEach { choice ->
            StyleCard(choice.name, "", choice == size, Modifier.weight(1f)) {
                size = choice
                state.saveLauncherTextSize(choice)
            }
        }
    }
    Text(
        "Small / Default / Large scales reading text only; dock icons and system status stay fixed.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Settings2WeatherOptions(state)
}

@Suppress("FunctionNaming")
@Composable
private fun Settings2WeatherOptions(state: StartStateRepository) {
    var weather by remember { mutableStateOf(state.loadHomeWeatherEnabled()) }
    SwitchRow("Show weather on Home", "Optional current conditions; location access is not required", weather) {
        weather = it
        state.saveHomeWeatherEnabled(it)
    }
    if (weather) {
        var useDevice by remember { mutableStateOf(state.loadHomeWeatherUseDeviceLocation()) }
        SwitchRow(
            "Use approximate device location",
            "Optional. Grant Weather location under Permissions; otherwise the manual city is used.",
            useDevice,
        ) {
            useDevice = it
            state.saveHomeWeatherUseDeviceLocation(it)
        }
        Settings2WeatherCityEditor(state)
    }
}

@Suppress("FunctionNaming")
@Composable
private fun Settings2WeatherCityEditor(state: StartStateRepository) {
    var city by remember { mutableStateOf(state.loadHomeWeatherCity()) }
    androidx.compose.material3.OutlinedTextField(
        value = city,
        onValueChange = { next ->
            city = next.take(LAUNCHER_SETTINGS_ACTIVITY_SETTINGS2_READABIL_MAX_90)
            state.saveHomeWeatherCity(city)
        },
        label = { Text("Weather city (e.g. Paris, France)") },
        supportingText = {
            Text(
                "Enter City, State/Country for an exact match. " +
                    "The Home header shows Open-Meteo, not the city.",
            )
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Settings2WeatherCheck(city)
}

@Suppress("FunctionNaming")
@Composable
private fun Settings2WeatherCheck(city: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val client =
        remember(context) {
            io.github.aimindseye.tenseven.platform
                .HomeWeatherClient(context)
        }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    androidx.compose.material3.TextButton(
        enabled = !checking && city.trim().length >= 2,
        onClick = {
            checking = true
            feedback = ""
            scope.launch {
                val locale = java.util.Locale.getDefault()
                val result = client.current(city.trim(), locale.country.equals("US", ignoreCase = true))
                feedback =
                    if (result != null) {
                        "Current conditions available · Open-Meteo"
                    } else {
                        "City not found or Internet unavailable. Try City, State/Country."
                    }
                checking = false
            }
        },
    ) { Text(if (checking) "Checking city…" else "Check city weather") }
    if (feedback.isNotBlank()) {
        Text(
            feedback,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        "Weather data: Open-Meteo (CC BY 4.0). Internet is used only when enabled.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Suppress("FunctionNaming")
@Composable
private fun Settings2Bb7Options(state: StartStateRepository) {
    Text(
        "Favorites tray rows and icon labels are configured under Icons & layout.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text("Tray darkness", style = MaterialTheme.typography.titleMedium)
    var darkness by remember { mutableIntStateOf(state.loadBb7Scrim()) }
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Lighter" to -1, "Darker" to 1).forEach { (label, step) ->
            Card(label, "", false, Modifier.weight(1f)) {
                darkness =
                    io.github.aimindseye.tenseven.model.Bb7Scrim
                        .step(darkness, step)
                state.saveBb7Scrim(darkness)
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun Settings2Category(
    id: String,
    title: String,
    detail: String,
    onSelect: (String) -> Unit,
) {
    val paths = settings2CategoryIconPaths(id)
    Surface(
        onClick = { onSelect(id) },
        modifier = Modifier.fillMaxWidth().sableFocusRing(),
        color = MaterialTheme.colorScheme.background,
        shape = MaterialTheme.shapes.extraSmall,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 13.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            io.github.aimindseye.tenseven.ui.LineIcon(
                paths = paths,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
            androidx.compose.foundation.layout
                .Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "›",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

private fun settings2CategoryIconPaths(id: String): List<String> =
    when (id) {
        "permissions" -> {
            listOf("M12 2l8 4v6c0 5-3 8-8 10-5-2-8-5-8-10v-6z", "M9 12l2 2 4-4")
        }

        "appearance_home" -> {
            listOf(
                "M12 3a9 9 0 1 0 0 18 3 3 0 0 0 3-3c0-2-2-2-2-4 0-1 2-2 2-4a3 3 0 0 0-3-3",
                "M7 9h.01M9 5h.01M6 15h.01",
            )
        }

        "hub" -> {
            listOf("M3 5h18v14h-18z", "M3 6l9 8 9-8")
        }

        "lock" -> {
            listOf("M5 11h14v10h-14z", "M8 11v-4a4 4 0 0 1 8 0v4")
        }

        "frames" -> {
            listOf("M3 5h12v10h-12z", "M9 9h12v10h-12z")
        }

        "apps" -> {
            listOf("M3 3h7v7h-7z", "M14 3h7v7h-7z", "M3 14h7v7h-7z", "M14 14h7v7h-7z")
        }

        else -> {
            listOf("M12 2a10 10 0 1 0 0 20 10 10 0 1 0 0-20", "M7 12l3 3 7-7")
        }
    }

/** A single persisted feature source, shared with the existing Setup extras picker. */
@Suppress("FunctionNaming")
@Composable
private fun OptionalAppsSettings2() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val control = io.github.aimindseye.tenseven.platform.ExtraAppsControl
    val apps = io.github.aimindseye.tenseven.model.ExtraApps
    val minimum = io.github.aimindseye.tenseven.onboarding.Setup2Plan.essentials
    val full =
        io.github.aimindseye.tenseven.onboarding.Setup2Plan
            .full()
    var enabled by remember(context) { mutableStateOf(control.enabledIds(context)) }

    val selection =
        when (enabled) {
            minimum -> "Minimum"
            full -> "Full"
            else -> "Custom"
        }
    Section("Bundled app selection")
    Text(
        "Minimum enables Calculator, Tasks and Notes. Full enables all bundled tools and games. Custom " +
            "uses the switches below.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Card("Minimum apps · Essentials", "Calc Plus, Tasks and Notes", selection == "Minimum") {
        control.apply(context, minimum)
        enabled = control.enabledIds(context)
    }
    Card("Full apps", "All currently bundled utilities and games", selection == "Full") {
        control.apply(context, full)
        enabled = control.enabledIds(context)
    }
    Text(
        "Selection: $selection · ${enabled.size} of ${full.size} apps enabled",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Section("Custom app selection")
    for (group in io.github.aimindseye.tenseven.model.ExtraGroup.entries) {
        Section(if (group == io.github.aimindseye.tenseven.model.ExtraGroup.Tools) "Tools" else "Games")
        for (app in apps.ofGroup(group)) {
            SwitchRow(app.label, app.summary, app.id in enabled) { checked ->
                control.setEnabled(context, app, checked)
                enabled = control.enabledIds(context)
            }
        }
    }
    Text(
        "These options show or hide bundled app entries. They do not switch APK editions, download " +
            "apps, or change Android default apps.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Suppress("FunctionNaming")
@Composable
internal fun Settings2AppearanceChoices(
    state: StartStateRepository,
    onOtherThemes: (() -> Unit)? = null,
) {
    // Capture Compose context while composing, never inside a click callback.
    val themeLaunchContext = androidx.compose.ui.platform.LocalContext.current
    var customSelected by remember {
        mutableStateOf(
            state.loadAppearanceMode() != null || state.loadAppearanceAccent() != null ||
                state.loadActiveCustomThemeId() != null,
        )
    }
    androidx.compose.runtime.DisposableEffect(state) {
        val unsubscribe =
            state.onAppearanceChanged {
                customSelected = state.loadAppearanceMode() != null ||
                    state.loadAppearanceAccent() != null || state.loadActiveCustomThemeId() != null
            }
        onDispose { unsubscribe() }
    }
    Card(
        "BlackBerry 10 theme",
        "Black background, white text and blue selection. Preserves saved wallpaper images.",
        !customSelected,
    ) {
        state.saveAppearance(null, null)
        customSelected = false
    }
    Card(
        "Other Themes, Wallpapers",
        "Open optional themes, Android colors and saved wallpapers",
        customSelected,
    ) {
        // The Home Settings caller supplies a subpage callback. Legacy single-step
        // Setup callers do not; route them to the same unified Theme page.
        if (onOtherThemes != null) {
            onOtherThemes()
        } else {
            themeLaunchContext.startActivity(
                android.content
                    .Intent(
                        themeLaunchContext,
                        LauncherSettingsActivity::class.java,
                    ).putExtra("tenseven.settings.category", "themes"),
            )
        }
    }
    if (!customSelected) {
        var showBehind by remember { mutableStateOf(state.loadHomeWallpaper()) }
        SwitchRow(
            "Show wallpaper behind Home",
            "Off by default on new installs; use BlackBerry black background.",
            showBehind,
        ) {
            showBehind = it
            state.saveHomeWallpaper(it)
        }
    }
}

// Unit composables follow the naming required by Compose lint.
@Suppress("FunctionNaming")
@Composable
private fun DeviceCompatibilityLink() {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card("Device compatibility", "Window, keyboard profile and key-event probe", false, onClick = {
        context.startActivity(Intent(context, DeviceCompatibilityActivity::class.java))
    })
}

// Compose Unit renderers require PascalCase; this exception is limited to this renderer.
@Suppress("FunctionNaming")
@Composable
private fun DeviceSettingsLinks(
    onLanguage: () -> Unit,
    onBackup: () -> Unit,
    onSetup: () -> Unit,
    onAndroidSettings: () -> Unit,
) {
    DeviceCompatibilityLink()
    Card(stringResource(R.string.ls_language), stringResource(R.string.ls_language_detail), false, onClick = onLanguage)
    Card(stringResource(R.string.ls_backup), stringResource(R.string.ls_backup_detail), false, onClick = onBackup)
    Card(stringResource(R.string.ls_setup), stringResource(R.string.ls_setup_detail), false, onClick = onSetup)
    Card(
        stringResource(R.string.ls_android_settings),
        stringResource(R.string.ls_android_settings_detail),
        false,
        onClick = onAndroidSettings,
    )
}
