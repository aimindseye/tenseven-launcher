package io.github.aimindseye.tenseven

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import io.github.aimindseye.tenseven.design.DesignText
import io.github.aimindseye.tenseven.design.applySableSystemBars
import io.github.aimindseye.tenseven.design.resolveSableDarkAppearance
import io.github.aimindseye.tenseven.design.writeGlobalSableAppearance
import io.github.aimindseye.tenseven.model.Bb7Detour
import io.github.aimindseye.tenseven.model.HomeStyle
import io.github.aimindseye.tenseven.model.StartText
import io.github.aimindseye.tenseven.model.StartText.t
import io.github.aimindseye.tenseven.onboarding.SetupStep
import io.github.aimindseye.tenseven.platform.LauncherAppsRepository
import io.github.aimindseye.tenseven.platform.LetterShortcutRepository
import io.github.aimindseye.tenseven.platform.LiveSurfaceRepository
import io.github.aimindseye.tenseven.platform.LockScreenAccessibilityService
import io.github.aimindseye.tenseven.platform.SpeedDialRepository
import io.github.aimindseye.tenseven.platform.StartStateRepository
import io.github.aimindseye.tenseven.platform.WidgetHostController
import io.github.aimindseye.tenseven.platform.WidgetTarget
import io.github.aimindseye.tenseven.ui.Bb7HomeScreen
import io.github.aimindseye.tenseven.ui.SableStartScreen

/**
 * Standalone SableOS HOME.
 *
 * Launcher3QuickStep is not an Activity superclass and does not own this view
 * hierarchy. It remains installed only as Android's Quickstep/Recents service.
 */
class SableStartActivity : ComponentActivity() {
    private lateinit var launcherAppsRepository: LauncherAppsRepository
    private val letterShortcutRepository by lazy { LetterShortcutRepository(this) }
    private val speedDialRepository by lazy { SpeedDialRepository(this, letterShortcutRepository) }
    private lateinit var liveSurfaceRepository: LiveSurfaceRepository
    private lateinit var startStateRepository: StartStateRepository
    private lateinit var activeFrames: io.github.aimindseye.tenseven.platform.ActiveFramesHost

    private val screen =
        io.github.aimindseye.tenseven.ui
            .LauncherHomeState()
    private val widgetController by lazy {
        WidgetHostController(this, startStateRepository) { target, ids ->
            if (target == WidgetTarget.HOME) screen.homeWidgetIds.value = ids else screen.widgetIds.value = ids
        }
    }
    private val permissionResults =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            screen.liveRefreshGeneration.intValue += 1
        }
    private var opaqueWindowBackground: Drawable? = null

    private val runtime by lazy {
        io.github.aimindseye.tenseven.platform.LauncherRuntime(
            this,
            launcherAppsRepository,
            onInventory = { inventory ->
                screen.appsState.value = inventory.apps
                screen.privateSpaceLockedState.value = inventory.privateSpaceLocked
                screen.workPausedState.value = inventory.workPaused
            },
            onRefresh = {
                screen.liveRefreshGeneration.intValue += 1
                if (::activeFrames.isInitialized) activeFrames.coordinator.refresh()
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StartText.t.bindTo(this)
        DesignText.t.bindTo(this)
        // The BlackBerry 7 home is see-through over the wallpaper. A window that starts opaque keeps its pixels
        // when it later turns transparent, so old BlackBerry 10 screens showed through: always ask for alpha.
        window.setFormat(PixelFormat.TRANSLUCENT)

        launcherAppsRepository = LauncherAppsRepository(this)
        liveSurfaceRepository = LiveSurfaceRepository(this)
        startStateRepository = StartStateRepository(this)
        screen.reload(startStateRepository, widgetController)
        activeFrames =
            io.github.aimindseye.tenseven.platform
                .ActiveFramesHost(this)
        opaqueWindowBackground = window.decorView.background

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (screen.canNavigateBack) {
                        screen.backGeneration.intValue += 1
                    }
                    // HOME root deliberately consumes Back.
                }
            },
        )

        // A recreated activity keeps its own place instead of reopening the requested screen.
        if (savedInstanceState == null) {
            // Once, on first run: the home role and each optional permission, with the reason for it.
            if (!startStateRepository.loadOnboardingDone()) {
                runCatching { startActivity(Intent(this, OnboardingActivity::class.java)) }
            }
            screen.startRequest.value =
                io.github.aimindseye.tenseven.runtime
                    .LauncherRequest(screenFor(intent), 0)
            if (intent?.action == ACTION_SHOW_HUB) requestHub(intent)
            if (intent?.action in BB10_ONLY_ACTIONS) {
                startBb10Detour(returnOnHome = intent?.action != ACTION_SHOW_ACTIVE_FRAMES)
            }
        }

        setContent {
            val appearance = rememberLauncherAppearance()

            SideEffect {
                applySableSystemBars(
                    window = window,
                    darkTheme =
                        resolveSableDarkAppearance(
                            context = this@SableStartActivity,
                            appearance = appearance,
                        ),
                )
            }

            val bb7 = screen.homeStyle.value == HomeStyle.Bb7 && !screen.bb10Detour.value
            SideEffect { showWallpaper(screen.homeWallpaper.value) }
            if (bb7) {
                Bb7HomeScreen(
                    apps = screen.appsState.value,
                    favoriteKeys = screen.bb7Favorites.value,
                    homeRows = screen.bb7HomeRows.intValue,
                    liveSurfaceRepository = liveSurfaceRepository,
                    appearance = appearance,
                    refreshGeneration = screen.liveRefreshGeneration.intValue,
                    homeGeneration = screen.homeGeneration.intValue,
                    onLaunchApp = { entry ->
                        if (
                            entry.component.className == SableStartActivity::class.java.name ||
                            entry.component.className == "io.github.aimindseye.tenseven.LauncherEntry"
                        ) {
                            screen.homeGeneration.intValue += 1
                            true
                        } else {
                            launcherAppsRepository.launch(entry)
                        }
                    },
                    onOpenAppInfo = { entry -> launcherAppsRepository.openAppDetails(entry) },
                    onOpenHub = {
                        screen.hubRequestFilter.value = null
                        screen.hubGeneration.intValue += 1
                        startBb10Detour(returnOnHome = true)
                    },
                    onFavoritesChanged = { keys ->
                        screen.bb7Favorites.value = keys
                        startStateRepository.saveHomeApps(keys)
                    },
                    onHomeRowsChanged = { rows ->
                        screen.bb7HomeRows.intValue = rows
                        startStateRepository.saveBb7HomeRows(rows)
                    },
                    desktopKeys = screen.bb7Desktop.value,
                    onDesktopChanged = { keys ->
                        screen.bb7Desktop.value = keys
                        startStateRepository.saveBb7Desktop(keys)
                    },
                    onOpenMenu = ::showBb7Menu,
                    onOpenCommand = ::openCommandFromBb7,
                    iconSize = screen.bb7IconSize.value,
                    gridColumns = screen.gridColumns.intValue,
                    showLabels = screen.gridLabels.value,
                    widgets = screen.homeWidgets.value,
                    onDoubleTapEmpty = doubleTapLockCallback(),
                    panels = screen.bb7Panels.value,
                    scrimLevel = screen.bb7Scrim.intValue,
                    onPanelsChanged = { panels ->
                        screen.bb7Panels.value = panels
                        startStateRepository.saveBb7Panels(panels)
                    },
                    onScrimChanged = { level ->
                        screen.bb7Scrim.intValue = level
                        startStateRepository.saveBb7Scrim(level)
                    },
                    onOpenWallpaper = {
                        runCatching {
                            startActivity(
                                Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), getString(R.string.bb7_wallpaper)),
                            )
                        }
                    },
                )
                return@setContent
            }

            val request = screen.startRequest.value
            key(request.serial, screen.widgetsPageEnabled.value) {
                SableStartScreen(
                    apps = screen.appsState.value,
                    speedDial = speedDialRepository,
                    letterShortcuts = letterShortcutRepository,
                    privateSpaceLocked = screen.privateSpaceLockedState.value,
                    onTogglePrivateSpace = ::togglePrivateSpace,
                    workProfilePaused = screen.workPausedState.value,
                    onDoubleTapEmpty = doubleTapLockCallback(),
                    onToggleWorkProfile = ::toggleWorkProfile,
                    liveSurfaceRepository = liveSurfaceRepository,
                    appearance = appearance,
                    refreshGeneration = screen.liveRefreshGeneration.intValue,
                    homeGeneration = screen.homeGeneration.intValue,
                    backGeneration = screen.backGeneration.intValue,
                    initialScreen = request.screen,
                    blankHomePage = screen.homeStyle.value == HomeStyle.Bb7,
                    onBackToHomePage = {
                        // Back from the Hub to a BlackBerry 7 home: show it now instead of after the slide.
                        if (screen.bb10Detour.value && screen.bb7Detour.endsAt(false)) endBb10Detour()
                    },
                    onCanNavigateBackChanged = { value ->
                        screen.canNavigateBack = value
                        screen.bb7Detour = screen.bb7Detour.onCanNavigateBack(value)
                        if (screen.bb10Detour.value && screen.bb7Detour.endsAt(value)) endBb10Detour()
                    },
                    initialStartTileKeys = startStateRepository.loadStartTileKeys(),
                    initialHomeApps = startStateRepository.loadHomeApps(),
                    initialGridOrder = startStateRepository.loadGridOrder(),
                    onGridOrderChanged = startStateRepository::saveGridOrder,
                    initialQuickBar = startStateRepository.loadQuickBar(),
                    onQuickBarChanged = startStateRepository::saveQuickBar,
                    initialGlanceWidgets = startStateRepository.loadGlanceWidgets(),
                    typeToSearch = screen.typeToSearch.value,
                    widgetsPage = screen.widgetsPageEnabled.value,
                    widgetsController = widgetController,
                    widgetIds = screen.widgetIds.value,
                    homeWidgetIds = screen.homeWidgetIds.value,
                    wallpaperBehind = screen.homeWallpaper.value,
                    widgetsGeneration = screen.widgetsGeneration.intValue,
                    showIconLabels = screen.gridLabels.value,
                    gridColumnsSetting = screen.gridColumns.intValue,
                    gridIconSize = screen.bb7IconSize.value,
                    onGlanceWidgetsChanged = startStateRepository::saveGlanceWidgets,
                    initialHiddenHomeSections = startStateRepository.loadHiddenHomeSections(),
                    onHiddenHomeSectionsChanged = startStateRepository::saveHiddenHomeSections,
                    framesState = activeFrames.state.value,
                    frameSnapshot = activeFrames::snapshot,
                    onResumeFrame = activeFrames.coordinator::open,
                    onCloseFrame = activeFrames.coordinator::close,
                    onAllowUsageAccess = {
                        runCatching {
                            startActivity(
                                Intent(this@SableStartActivity, OnboardingActivity::class.java)
                                    .putExtra(OnboardingActivity.EXTRA_STEP, SetupStep.USAGE.name),
                            )
                        }
                    },
                    onAppearanceChanged = { next ->
                        startStateRepository.saveAppearance(next.mode.stableValue, next.accent.stableValue)
                        writeGlobalSableAppearance(this@SableStartActivity, next)
                    },
                    onResetAppearance = {
                        startStateRepository.saveAppearance(null, null)
                        writeGlobalSableAppearance(
                            this@SableStartActivity,
                            io.github.aimindseye.tenseven.design
                                .SableAppearance(),
                        )
                    },
                    onLaunchApp = { entry ->
                        launcherAppsRepository.launch(entry)
                    },
                    onOpenSystemSettings = ::openSystemSettings,
                    onStartIntent = { intent ->
                        runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                    onOpenAppInfo = { entry ->
                        launcherAppsRepository.openAppDetails(entry)
                    },
                    onOpenNotificationSettings = { entry ->
                        launcherAppsRepository.openNotificationSettings(entry)
                    },
                    onUninstallApp = { entry ->
                        launcherAppsRepository.requestUninstall(entry)
                    },
                    onRequestLivePermissions = ::requestLivePermissions,
                    onRefreshLive = {
                        screen.liveRefreshGeneration.intValue += 1
                    },
                    hubGeneration = screen.hubGeneration.intValue,
                    hubRequestFilter = screen.hubRequestFilter.value,
                    onRequestContactsPermission = ::requestContactsPermission,
                )
            }
        }
    }

    override fun onStart() {
        io.github.aimindseye.tenseven.platform.ScreenCaptureState.homeVisible = true
        super.onStart()
        runtime.start()
        widgetController.startListening()
        scheduleInventoryRefresh()
    }

    override fun onResume() {
        super.onResume()
        // Android may leave a sideloaded listener disconnected after an update even if access
        // remains granted. Recover without prompting or changing the user's source choices.
        io.github.aimindseye.tenseven.platform.NotificationConnectionRecovery
            .onLauncherResume(this)
        val priorStyle = screen.homeStyle.value
        screen.reload(startStateRepository, widgetController)
        val style = screen.homeStyle.value
        if (style == HomeStyle.Bb7) stopService(Intent(this, io.github.aimindseye.tenseven.platform.ScreenCaptureService::class.java))
        if (style != priorStyle) {
            endBb10Detour()
        }
        scheduleInventoryRefresh()
        activeFrames.coordinator.onResume()
        screen.liveRefreshGeneration.intValue += 1
    }

    // Widget binding and setup report back through the framework's request codes.
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        widgetController.onActivityResult(requestCode, resultCode, data)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        when (intent.action) {
            ACTION_EDIT_QUICK_BAR, ACTION_EDIT_HOME -> {
                // The Screen is recomposed fresh for the request: its Home/Back effects act only on
                // counts above zero, so they restart from zero rather than undo the request at once.
                screen.homeGeneration.intValue = 0
                screen.backGeneration.intValue = 0
                screen.hubGeneration.intValue = 0
                screen.startRequest.value =
                    io.github.aimindseye.tenseven.runtime
                        .LauncherRequest(screenFor(intent), screen.startRequest.value.serial + 1)
                startBb10Detour(returnOnHome = true)
            }

            // Home, and the Recents key (Active Frames are the BB10 Recents): close anything open
            // over the pages and show Active Frames.
            Intent.ACTION_MAIN, ACTION_SHOW_ACTIVE_FRAMES -> {
                screen.homeGeneration.intValue += 1
                activeFrames.coordinator.onResume()
                // On the BlackBerry 7 home, Home goes back to it; Recents shows the Active Frames.
                if (intent.action == ACTION_SHOW_ACTIVE_FRAMES &&
                    io.github.aimindseye.tenseven.frames.FrameAvailability
                        .frames(screen.homeStyle.value == HomeStyle.Bb10)
                ) {
                    startBb10Detour(returnOnHome = false)
                } else {
                    endBb10Detour()
                }
            }

            // The one Hub is Start's Hub page (the Sable Hub app has no launcher entry).
            ACTION_SHOW_HUB -> {
                requestHub(intent)
                startBb10Detour(returnOnHome = true)
            }
        }
    }

    override fun onPause() {
        activeFrames.coordinator.onStop()
        // Leaving from a BlackBerry 10 screen (Hub, Recents, Command) would leave that screen as the last frame, and
        // it flashes while Home animates back in. Go back to the BlackBerry 7 home as the window leaves.
        val leavingBb7Detour = screen.homeStyle.value == HomeStyle.Bb7 && screen.bb10Detour.value
        if (leavingBb7Detour && !isChangingConfigurations) endBb10Detour()
        super.onPause()
    }

    override fun onStop() {
        io.github.aimindseye.tenseven.platform.ScreenCaptureState.homeVisible = false
        activeFrames.coordinator.onStop()
        widgetController.stopListening()
        runtime.stop()
        super.onStop()
    }

    override fun onDestroy() {
        runtime.close()
        activeFrames.destroy()
        super.onDestroy()
    }

    private fun scheduleInventoryRefresh() = runtime.refresh()

    /** Lock or unlock the private space; the profile broadcasts refresh All Apps. */
    private fun togglePrivateSpace() {
        val locked = screen.privateSpaceLockedState.value ?: return
        runtime.setPrivateLocked(!locked)
    }

    /** The lock action for a double tap on empty Home space; null while the setting or the service is off. */
    private fun doubleTapLockCallback(): (() -> Unit)? =
        if (screen.doubleTapLockState.value) {
            { LockScreenAccessibilityService.lockScreen() }
        } else {
            null
        }

    /** Pause or resume work apps; the profile broadcasts refresh All Apps. */
    private fun toggleWorkProfile() {
        val paused = screen.workPausedState.value ?: return
        runtime.setWorkPaused(!paused)
    }

    /** The BlackBerry 7 home draws on the wallpaper; the BlackBerry 10 screen paints its own. */
    private fun showWallpaper(show: Boolean) {
        if (show) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(opaqueWindowBackground)
        }
    }

    /**
     * Menu on the BlackBerry 7 home, a full menu as BlackBerry 7 asks: the highlighted icon's
     * items ([contextual]) first, then the home's own, then Close.
     */
    private fun showBb7Menu(contextual: List<Pair<String, () -> Unit>>) {
        // D4r4: the BB7 screen already supplies exactly four Home actions.
        // Do not append legacy global actions; icon-specific actions remain contextual.
        val items = contextual
        // BB7 menu is a translucent on-wallpaper surface, never a system-white dialog.
        // Views retain native D-pad/trackpad focus and accessibility click actions.
        val density = resources.displayMetrics.density

        fun dp(value: Int) = (value * density).toInt()
        val layout =
            android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                setBackgroundColor(Color.argb(215, 22, 31, 47))
            }
        var dialog: AlertDialog? = null
        items.forEach { (title, action) ->
            layout.addView(
                android.widget.TextView(this).apply {
                    text = title
                    setTextColor(Color.WHITE)
                    textSize = 16f
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(11), dp(12), dp(11))
                    isFocusable = true
                    isClickable = true
                    background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
                    setOnClickListener {
                        dialog?.dismiss()
                        action()
                    }
                    setOnKeyListener { _, keyCode, event ->
                        if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                            (
                                keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                                    keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                            )
                        ) {
                            performClick()
                            true
                        } else {
                            false
                        }
                    }
                },
            )
        }
        val scroll =
            android.widget.ScrollView(this).apply {
                isFillViewport = false
                addView(layout)
            }
        val menuDialog = AlertDialog.Builder(this).setView(scroll).create()
        dialog = menuDialog
        menuDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        menuDialog.show()
        menuDialog.window?.setBackgroundDrawable(ColorDrawable(Color.argb(225, 22, 31, 47)))
        menuDialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * MENU_WIDTH_FRACTION).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        layout.getChildAt(0)?.requestFocus()
    }

    private fun openSystemSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun requestLivePermissions() {
        permissionResults.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES))
    }

    private fun requestContactsPermission() {
        permissionResults.launch(arrayOf(Manifest.permission.READ_CONTACTS))
    }

    /** Calendar access is requested only from the user's explicit Hub/Calendar action. */
    fun requestCalendarPermission() {
        permissionResults.launch(arrayOf(Manifest.permission.READ_CALENDAR))
    }

    /**
     * Command (search, actions, settings, `assign`...) from the BlackBerry 7 home: shown on the BlackBerry 10
     * screen like Hub and Edit Home, and Back returns to the BlackBerry 7 home.
     */
    private fun openCommandFromBb7() {
        screen.homeGeneration.intValue = 0
        screen.backGeneration.intValue = 0
        screen.hubGeneration.intValue = 0
        screen.startRequest.value =
            io.github.aimindseye.tenseven.runtime
                .LauncherRequest(SCREEN_SEARCH, screen.startRequest.value.serial + 1)
        startBb10Detour(returnOnHome = true)
    }

    private fun startBb10Detour(returnOnHome: Boolean) {
        screen.bb10Detour.value = true
        screen.bb7Detour = Bb7Detour(returnOnHome = returnOnHome)
    }

    private fun endBb10Detour() {
        screen.bb10Detour.value = false
        screen.bb7Detour = Bb7Detour()
    }

    private fun requestHub(intent: Intent) {
        screen.hubRequestFilter.value = intent.getStringExtra(EXTRA_HUB_FILTER)
        screen.hubGeneration.intValue += 1
    }

    companion object {
        private const val MENU_WIDTH_FRACTION = 0.88f

        /** Show the Hub page; [EXTRA_HUB_FILTER] picks the tab ("all", "messages", "calls", "contacts"). */
        const val ACTION_SHOW_HUB = "org.sableos.start.action.SHOW_HUB"
        const val EXTRA_HUB_FILTER = "org.sableos.start.extra.HUB_FILTER"

        /** Settings > Sable > Quick bar: Edit Home (the quick bar is edited there with the Home apps). */
        const val ACTION_EDIT_QUICK_BAR = "org.sableos.start.action.EDIT_QUICK_BAR"

        /** Settings > Sable Start > Home layout: Edit Home. */
        const val ACTION_EDIT_HOME = "org.sableos.start.action.EDIT_HOME"

        /** The Recents key or button (sent by Launcher3QuickStep): Active Frames, the BB10 Recents. */
        const val ACTION_SHOW_ACTIVE_FRAMES = "org.sableos.start.action.SHOW_ACTIVE_FRAMES"
        const val SCREEN_START = "start"
        const val SCREEN_EDIT_HOME = "home"
        const val SCREEN_SEARCH = "search"

        /** Requests the BlackBerry 7 home shows on the BlackBerry 10 screen. */
        val BB10_ONLY_ACTIONS =
            setOf(ACTION_EDIT_HOME, ACTION_EDIT_QUICK_BAR, ACTION_SHOW_HUB)

        fun screenFor(intent: Intent?): String =
            when (intent?.action) {
                ACTION_EDIT_QUICK_BAR, ACTION_EDIT_HOME -> SCREEN_EDIT_HOME
                else -> SCREEN_START
            }
    }
}
