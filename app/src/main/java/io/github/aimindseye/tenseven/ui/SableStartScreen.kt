package io.github.aimindseye.tenseven.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.repeatOnLifecycle
import io.github.aimindseye.tenseven.HubSettingsActivity
import io.github.aimindseye.tenseven.LauncherSettingsActivity
import io.github.aimindseye.tenseven.conversations.ConversationAccess
import io.github.aimindseye.tenseven.design.SableAppearance
import io.github.aimindseye.tenseven.design.SableFocusMemory
import io.github.aimindseye.tenseven.design.SableRefreshableSurface
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.model.AppGrid
import io.github.aimindseye.tenseven.model.AppProfiles
import io.github.aimindseye.tenseven.model.Bb7IconSize
import io.github.aimindseye.tenseven.model.GlanceWidgets
import io.github.aimindseye.tenseven.model.GridColumns
import io.github.aimindseye.tenseven.model.HomeApps
import io.github.aimindseye.tenseven.model.HomeLayout
import io.github.aimindseye.tenseven.model.HubFilter
import io.github.aimindseye.tenseven.model.QuickBar
import io.github.aimindseye.tenseven.model.StartPages
import io.github.aimindseye.tenseven.model.inLauncherUser
import io.github.aimindseye.tenseven.model.stableKey
import io.github.aimindseye.tenseven.platform.LetterShortcutRepository
import io.github.aimindseye.tenseven.platform.LiveSurfaceRepository
import io.github.aimindseye.tenseven.platform.SpeedDialRepository
import io.github.aimindseye.tenseven.platform.WidgetHostController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SableStartScreen(
    apps: List<AppEntry>,
    liveSurfaceRepository: LiveSurfaceRepository,
    appearance: SableAppearance = SableAppearance(),
    refreshGeneration: Int = 0,
    homeGeneration: Int = 0,
    backGeneration: Int = 0,
    initialScreen: String = "start",
    onCanNavigateBackChanged: (Boolean) -> Unit = {},
    /** Back from the Hub or an app page is about to slide to the home page. */
    onBackToHomePage: () -> Unit = {},
    /** A visit from the BlackBerry 7 home: its Home page stays empty so the BlackBerry 10 cards never flash. */
    blankHomePage: Boolean = false,
    /** Start tiles chosen before Home became an ordered list; seeds the grid order with [initialHomeApps]. */
    initialStartTileKeys: Set<String>? = null,
    /** The three-page Home apps; they lead the app grid until the user orders it. */
    initialHomeApps: List<String>? = null,
    initialGridOrder: List<String>? = null,
    onGridOrderChanged: (List<String>?) -> Unit = {},
    initialQuickBar: List<String>? = null,
    onQuickBarChanged: (List<String>?) -> Unit = {},
    initialGlanceWidgets: List<String>? = null,
    /** Launcher Settings: whether typing on Home starts a search. */
    typeToSearch: Boolean = true,
    widgetsPage: Boolean = false,
    widgetsController: WidgetHostController? = null,
    widgetIds: List<Int> = emptyList(),
    homeWidgetIds: List<Int> = emptyList(),
    wallpaperBehind: Boolean = false,
    widgetsGeneration: Int = 0,
    showIconLabels: Boolean = true,
    gridColumnsSetting: Int = GridColumns.AUTO,
    gridIconSize: Bb7IconSize = Bb7IconSize.DEFAULT,
    onGlanceWidgetsChanged: (List<String>?) -> Unit = {},
    initialHiddenHomeSections: Set<String>? = null,
    onHiddenHomeSectionsChanged: (Set<String>?) -> Unit = {},
    framesState: io.github.aimindseye.tenseven.frames.FramesState = io.github.aimindseye.tenseven.frames.FramesState(),
    frameSnapshot: (io.github.aimindseye.tenseven.frames.FrameItem, Long) -> Bitmap? = { _, _ -> null },
    onResumeFrame: (io.github.aimindseye.tenseven.frames.FrameItem) -> Unit = {},
    onCloseFrame: (io.github.aimindseye.tenseven.frames.FrameItem) -> Unit = {},
    onAllowUsageAccess: () -> Unit = {},
    onAppearanceChanged: (SableAppearance) -> Unit = {},
    onResetAppearance: () -> Unit = {},
    onLaunchApp: (AppEntry) -> Boolean,
    onOpenSystemSettings: () -> Unit,
    onOpenAppInfo: (AppEntry) -> Unit,
    onRequestLivePermissions: () -> Unit,
    onRefreshLive: () -> Unit,
    onOpenNotificationSettings: (AppEntry) -> Unit = onOpenAppInfo,
    onUninstallApp: (AppEntry) -> Unit = {},
    onStartIntent: (Intent) -> Unit = {},
    /** null: no private space on this device; true: locked; false: unlocked. */
    privateSpaceLocked: Boolean? = null,
    onTogglePrivateSpace: () -> Unit = {},
    workProfilePaused: Boolean? = null,
    onToggleWorkProfile: () -> Unit = {},
    onDoubleTapEmpty: (() -> Unit)? = null,
    /** Counts up for each "show the Hub page" request (org.sableos.start.action.SHOW_HUB). */
    hubGeneration: Int = 0,
    /** The tab that request asked for ([HubFilter] by name), or null for All. */
    hubRequestFilter: String? = null,
    /** The Contacts tab asks for READ_CONTACTS; the activity bumps [refreshGeneration] after. */
    onRequestContactsPermission: () -> Unit = {},
    /** Phone's speed dial: holding an assigned letter on Home calls it (null: no speed dial). */
    speedDial: SpeedDialRepository? = null,
    /** The apps held on a letter on Home; Peek ("More actions") sets and clears them. */
    letterShortcuts: LetterShortcutRepository? = null,
) {
    var shortcutVersion by remember { mutableStateOf(0) }
    val speedDialLetters by
        produceState(emptySet<Char>(), refreshGeneration, speedDial, shortcutVersion) {
            value = speedDial?.assignedLetters() ?: emptySet()
        }
    var hubPeek by remember { mutableStateOf(false) }
    var destination by remember(initialScreen) {
        mutableStateOf(destinationFor(initialScreen))
    }
    var returnDestination by remember { mutableStateOf(SableStartDestination.Start) }
    var selectedApp by remember { mutableStateOf<AppEntry?>(null) }
    var selectedSlot by remember { mutableStateOf<QuickSlot?>(null) }
    var savedGridOrder by remember(initialGridOrder) { mutableStateOf(initialGridOrder) }
    var recentKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    // Text typed on Start before Command/Search opened (type-to-launch).
    var searchSeed by remember { mutableStateOf("") }
    var quickBarTokens by remember(initialQuickBar) {
        mutableStateOf(QuickBar.normalize(initialQuickBar))
    }
    var glanceWidgets by remember(initialGlanceWidgets) {
        mutableStateOf(GlanceWidgets.normalize(initialGlanceWidgets))
    }
    var hiddenSections by remember(initialHiddenHomeSections) {
        mutableStateOf(HomeLayout.normalizeHidden(initialHiddenHomeSections))
    }
    var hubFilter by remember { mutableStateOf(HubFilter.All) }
    var editBackGeneration by remember { mutableStateOf(0) }
    // Survives Peek / App info / Search round trips (FOCUS_RESTORATION=REQUIRED).
    val focusMemory = remember { SableFocusMemory() }
    var liveSnapshot by remember {
        mutableStateOf(liveSurfaceRepository.initialSnapshot())
    }
    var liveRefreshing by remember {
        mutableStateOf(false)
    }

    val liveLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(refreshGeneration, homeGeneration, liveLifecycle) {
        liveLifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) {
                liveRefreshing = true
                try { liveSnapshot = liveSurfaceRepository.snapshot() }
                finally { liveRefreshing = false }
                kotlinx.coroutines.delay(5000)
            }
        }
    }

    val appByKey = apps.associateBy { it.stableKey() }
    // The grid holds every app except Private Space ones, which stay in All Apps behind its lock.
    val gridCandidates = apps.filterNot { it.profileLabel == PROFILE_PRIVATE_LABEL }
    val homeContext = LocalContext.current
    val defaultHomeKeys = remember(apps) { defaultHomeApps(apps, homeContext).map { it.stableKey() } }
    val gridKeys =
        AppGrid.resolve(
            saved = savedGridOrder,
            preferredFirst = HomeApps.resolve(initialHomeApps, initialStartTileKeys, defaultHomeKeys),
            installed = gridCandidates.map { it.stableKey() },
        )
    val gridApps = gridKeys.mapNotNull(appByKey::get)
    val gridLabels = gridApps.mapTo(HashSet()) { it.label }
    val quickSlots =
        quickBarTokens.mapNotNull { token ->
            resolveQuickSlot(token, apps, appByKey)
        }
    val gridColumns = AppGrid.columnsFor(gridColumnsSetting, startGridWidthDp())
    val gridIconScale = AppGrid.iconScale(gridIconSize)
    val gridRows = AppGrid.rowsFor(startGridHeightDp(), (GRID_CELL_MIN_DP * gridIconScale).toInt())
    val gridPerPage = AppGrid.perPage(gridRows, gridColumns)
    val gridPages = AppGrid.pageCount(gridApps.size, gridPerPage)
    // The Widgets page, when kept on, is one page left of Hub: every pager index is the page's own number plus lead.
    val lead = if (widgetsPage) 1 else 0
    val pageCount = StartPages.count(gridPages) + lead

    val startPager =
        rememberPagerState(
            initialPage = StartPages.forScreen(initialScreen).let { if (it < 0 && lead == 0) StartPages.HOME else it } + lead,
        ) { pageCount }
    val currentPage = startPager.currentPage - lead
    val settledPage = startPager.settledPage - lead
    val pagerScope = rememberCoroutineScope()

    fun movePage(delta: Int) {
        val target = (startPager.currentPage + delta).coerceIn(0, pageCount - 1)
        pagerScope.launch { startPager.animateScrollToPage(target) }
    }

    fun showPage(page: Int) {
        destination = SableStartDestination.Start
        pagerScope.launch { startPager.animateScrollToPage(page + lead) }
    }

    fun launchApp(entry: AppEntry) {
        if (onLaunchApp(entry)) {
            val key = entry.stableKey()
            recentKeys =
                (listOf(key) + recentKeys.filterNot { it == key })
                    .take(MAX_SESSION_RECENTS)
        }
    }

    fun openNotificationApp(packageName: String) {
        apps.firstOrNull { it.inLauncherUser && it.component.packageName == packageName }?.let(::launchApp)
    }

    fun secondInstance(entry: AppEntry): AppEntry? =
        AppProfiles.secondInstance(
            app = entry,
            apps = apps,
            componentOf = { it.component.flattenToString() },
            profileOf = { it.profileLabel },
        )

    /** Shift+Enter: the Dual copy (or the personal app of a Dual copy); the app itself when it has none. */
    fun launchSecond(entry: AppEntry) = launchApp(secondInstance(entry) ?: entry)

    fun setGridOrder(next: List<String>) {
        savedGridOrder = next
        onGridOrderChanged(next)
    }

    fun openPeek(
        entry: AppEntry,
        from: SableStartDestination,
    ) {
        selectedApp = entry
        returnDestination = from
        destination = SableStartDestination.Peek
    }

    fun openSearch(
        from: SableStartDestination,
        seed: String = "",
    ) {
        searchSeed = seed
        returnDestination = from
        destination = SableStartDestination.Search
    }

    fun navigateBack() {
        destination = backDestination(destination, returnDestination)
    }

    val settingsContext = LocalContext.current

    /** The Hub's settings: the notification access step of Setup. */
    fun openHubSettings() {
        onStartIntent(Intent(settingsContext, HubSettingsActivity::class.java))
    }

    fun showHub(filter: HubFilter) {
        hubFilter = filter
        showPage(StartPages.HUB)
    }

    fun dial(digits: String) {
        onStartIntent(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(digits))))
    }

    fun activateSlot(slot: QuickSlot) {
        when (slot.token) {
            QuickBar.COMMAND -> openSearch(SableStartDestination.Start)
            QuickBar.ALL_APPS -> destination = SableStartDestination.Apps
            // The Hub slot is Start's own Hub page; the Sable Hub app has no screen to open.
            QuickBar.HUB -> showHub(HubFilter.All)
            else -> slot.app?.let(::launchApp)
        }
    }

    fun resumeFrame(frame: io.github.aimindseye.tenseven.frames.FrameItem) {
        onResumeFrame(frame)
    }

    LaunchedEffect(homeGeneration) {
        if (homeGeneration > 0) {
            destination = SableStartDestination.Start
            returnDestination = SableStartDestination.Start
            selectedApp = null
            startPager.animateScrollToPage(StartPages.HOME + lead)
        }
    }

    LaunchedEffect(hubGeneration) {
        if (hubGeneration > 0) {
            destination = SableStartDestination.Start
            selectedApp = null
            hubFilter = HubFilter.fromName(hubRequestFilter) ?: HubFilter.All
            startPager.animateScrollToPage(StartPages.HUB + lead)
        }
    }

    LaunchedEffect(widgetsGeneration) {
        if (widgetsGeneration > 0 && lead > 0) {
            destination = SableStartDestination.Start
            selectedApp = null
            startPager.animateScrollToPage(StartPages.WIDGETS + lead)
        }
    }

    LaunchedEffect(backGeneration) {
        if (backGeneration > 0) {
            when {
                hubPeek -> hubPeek = false
                // Edit Home decides: cancel the item being moved, else leave without saving.
                destination == SableStartDestination.EditHome -> editBackGeneration += 1
                destination != SableStartDestination.Start -> navigateBack()
                // Back on Hub or an app grid page returns to Active Frames.
                currentPage != StartPages.HOME -> {
                    onBackToHomePage()
                    startPager.animateScrollToPage(StartPages.HOME + lead)
                }
            }
        }
    }

    LaunchedEffect(destination, currentPage, hubPeek) {
        onCanNavigateBackChanged(
            destination != SableStartDestination.Start || currentPage != StartPages.HOME || hubPeek,
        )
    }

    LaunchedEffect(settledPage) {
        if (settledPage == StartPages.HUB) onRefreshLive()
    }

    SableThemeWithWidgets(appearance, widgetsController, homeWidgetIds, if (wallpaperBehind) HOME_BACKDROP_ALPHA else 1f) {
        val seeThrough = wallpaperBehind && destination == SableStartDestination.Start
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (seeThrough) Color.Transparent else MaterialTheme.colorScheme.background,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .pointerInput(onDoubleTapEmpty, destination) {
                            // Home only, and only empty space: taps on tiles and rows are consumed by them first.
                            if (onDoubleTapEmpty != null && destination == SableStartDestination.Start) {
                                detectTapGestures(onDoubleTap = { onDoubleTapEmpty() })
                            }
                        },
            ) {
                when (destination) {
                    SableStartDestination.Start ->
                        Bb10ThemeWithPeek(
                            appearance = appearance,
                            peekOpen = hubPeek,
                            edgeEnabled = currentPage == StartPages.HOME,
                            onPeekChange = { hubPeek = it },
                            onOpenHub = { pagerScope.launch { startPager.animateScrollToPage(StartPages.HUB + lead) } },
                        ) {
                            StartStrip(
                                state = startPager,
                                pageCount = pageCount,
                                pageLead = lead,
                                quickSlots = quickSlots,
                                onMovePage = ::movePage,
                                onShowPage = { page -> pagerScope.launch { startPager.animateScrollToPage(page) } },
                                onQuickSlot = ::activateSlot,
                                onSlotOptions = { slot ->
                                    selectedSlot = slot
                                    destination = SableStartDestination.SlotOptions
                                },
                                onHubPeek = { hubPeek = true },
                                onDial = { char -> dial(char.toString()) },
                                onTypeToSearch = { seed -> if (typeToSearch) openSearch(SableStartDestination.Start, seed) },
                                speedDialLetters = speedDialLetters,
                                onSpeedDial = { letter -> speedDial?.call(letter) },
                                onCommand = { openSearch(SableStartDestination.Start) },
                                onEditHome = { destination = SableStartDestination.EditHome },
                            ) { pagerIndex ->
                                val page = pagerIndex - lead
                                when {
                                    page == StartPages.WIDGETS ->
                                        WidgetsPage(
                                            controller = widgetsController,
                                            ids = widgetIds,
                                            builtIn =
                                                if (glanceWidgets.isEmpty()) {
                                                    null
                                                } else {
                                                    {
                                                        BuiltInWidgets(
                                                            widgets = glanceWidgets,
                                                            snapshot = liveSnapshot,
                                                            apps = apps,
                                                            refreshGeneration = refreshGeneration,
                                                            onLaunchApp = ::launchApp,
                                                            onStartIntent = onStartIntent,
                                                            onRequestPermissions = onRequestLivePermissions,
                                                            onShowHub = { showHub(HubFilter.All) },
                                                        )
                                                    }
                                                },
                                        )
                                    page == StartPages.HUB ->
                                        SableRefreshableSurface(
                                            isRefreshing = liveRefreshing,
                                            onRefresh = onRefreshLive,
                                        ) {
                                            HubPage(
                                                snapshot = liveSnapshot,
                                                apps = apps,
                                                filter = hubFilter,
                                                active = settledPage == StartPages.HUB,
                                                onFilter = { hubFilter = it },
                                                onLaunchApp = ::launchApp,
                                                onOpenHubSettings = ::openHubSettings,
                                                onStartIntent = onStartIntent,
                                                refreshGeneration = refreshGeneration,
                                                onRequestContactsPermission = onRequestContactsPermission,
                                            )
                                        }
                                    page == StartPages.HOME && blankHomePage -> Spacer(Modifier.fillMaxSize())
                                    page == StartPages.HOME ->
                                        ActiveFramesPage(
                                            state = framesState,
                                            apps = apps,
                                            hiddenSections = hiddenSections,
                                            onOpenSettings = {
                                                onStartIntent(Intent(homeContext, LauncherSettingsActivity::class.java))
                                            },
                                            onSearch = { openSearch(SableStartDestination.Start) },
                                            snapshot = liveSnapshot,
                                            refreshGeneration = refreshGeneration,
                                            frameSnapshot = frameSnapshot,
                                            active = settledPage == StartPages.HOME,
                                            onResume = ::resumeFrame,
                                            onClose = onCloseFrame,
                                            onShowHub = { showHub(HubFilter.All) },
                                            onOpenNotificationApp = ::openNotificationApp,
                                            onAllowUsageAccess = onAllowUsageAccess,
                                        )
                                    else -> {
                                        val index = page - StartPages.FIRST_GRID
                                        AppGridPage(
                                            apps = AppGrid.page(gridApps, index, gridPerPage),
                                            rows = gridRows,
                                            columns = gridColumns,
                                            iconScale = gridIconScale,
                                            gridLabels = gridLabels,
                                            active = settledPage == page,
                                            onLaunchApp = ::launchApp,
                                            onLaunchSecond = ::launchSecond,
                                            onOpenPeek = { app -> openPeek(app, SableStartDestination.Start) },
                                            onEditHome = { destination = SableStartDestination.EditHome },
                                            showLabels = showIconLabels,
                                        )
                                    }
                                }
                            }
                        }

                    SableStartDestination.SlotOptions -> {
                        val slot = selectedSlot
                        if (slot == null) {
                            destination = SableStartDestination.Start
                        } else {
                            SlotOptionsScreen(
                                slot = slot,
                                apps = apps,
                                onBack = ::navigateBack,
                                onOpen = {
                                    destination = SableStartDestination.Start
                                    activateSlot(slot)
                                },
                                onHubFilter = ::showHub,
                                onOpenHubSettings = {
                                    destination = SableStartDestination.Start
                                    openHubSettings()
                                },
                                onStartIntent = { intent ->
                                    destination = SableStartDestination.Start
                                    onStartIntent(intent)
                                },
                                onLaunchApp = { app ->
                                    destination = SableStartDestination.Start
                                    launchApp(app)
                                },
                                onAppInfo = { app -> onOpenAppInfo(app) },
                                onEditHome = { destination = SableStartDestination.EditHome },
                            )
                        }
                    }

                    SableStartDestination.SlotPicker -> {
                        val app = selectedApp
                        if (app == null) {
                            destination = SableStartDestination.Start
                        } else {
                            SlotPickerScreen(
                                app = app,
                                tokens = quickBarTokens,
                                apps = apps,
                                appByKey = appByKey,
                                onBack = { destination = SableStartDestination.Peek },
                                onReplace = { index ->
                                    val next = QuickBar.replace(quickBarTokens, index, QuickBar.appToken(app.stableKey()))
                                    if (next != quickBarTokens) {
                                        quickBarTokens = next
                                        onQuickBarChanged(next)
                                    }
                                    destination = SableStartDestination.Peek
                                },
                            )
                        }
                    }

                    SableStartDestination.EditHome ->
                        EditHomeScreen(
                            showFrameOptions = !blankHomePage && ConversationAccess.isGranted(homeContext),
                            apps = apps,
                            initial =
                                HomeEdit(
                                    gridOrder = gridKeys,
                                    quickBar = quickBarTokens,
                                    glanceWidgets = glanceWidgets,
                                    hiddenSections = hiddenSections,
                                ),
                            defaults =
                                HomeEdit(
                                    gridOrder =
                                        AppGrid.resolve(
                                            saved = null,
                                            preferredFirst = defaultHomeKeys,
                                            installed = gridCandidates.map { it.stableKey() },
                                        ),
                                    quickBar = QuickBar.DEFAULT,
                                    glanceWidgets = GlanceWidgets.DEFAULT,
                                    hiddenSections = HomeLayout.DEFAULT_HIDDEN,
                                ),
                            backGeneration = editBackGeneration,
                            onCancel = { destination = SableStartDestination.Start },
                            onDone = { edit ->
                                setGridOrder(edit.gridOrder)
                                quickBarTokens = QuickBar.normalize(edit.quickBar)
                                onQuickBarChanged(if (quickBarTokens == QuickBar.DEFAULT) null else quickBarTokens)
                                glanceWidgets = edit.glanceWidgets
                                onGlanceWidgetsChanged(edit.glanceWidgets)
                                hiddenSections = edit.hiddenSections
                                onHiddenHomeSectionsChanged(edit.hiddenSections)
                                destination = SableStartDestination.Start
                                pagerScope.launch { startPager.scrollToPage(StartPages.HOME + lead) }
                            },
                            onAppearance = {
                                returnDestination = SableStartDestination.Start
                                destination = SableStartDestination.Appearance
                            },
                        )

                    SableStartDestination.Apps ->
                        AppsScreen(
                            apps = apps,
                            focusMemory = focusMemory,
                            onBack = ::navigateBack,
                            onSearch = {
                                openSearch(SableStartDestination.Apps)
                            },
                            onLaunchApp = ::launchApp,
                            onLaunchSecond = ::launchSecond,
                            onOpenPeek = { app ->
                                openPeek(app, SableStartDestination.Apps)
                            },
                            onOpenAppInfo = onOpenAppInfo,
                            privateSpaceLocked = privateSpaceLocked,
                            onTogglePrivateSpace = onTogglePrivateSpace,
                        )

                    SableStartDestination.Search ->
                        SearchScreen(
                            apps = apps,
                            recentKeys = recentKeys,
                            letterShortcuts = letterShortcuts,
                            initialQuery = searchSeed,
                            privateSpaceLocked = privateSpaceLocked,
                            onTogglePrivateSpace = onTogglePrivateSpace,
                            workProfilePaused = workProfilePaused,
                            onToggleWorkProfile = onToggleWorkProfile,
                            onBack = ::navigateBack,
                            onLaunchApp = ::launchApp,
                            secondInstanceOf = ::secondInstance,
                            onOpenAppInfo = onOpenAppInfo,
                            onOpenPeek = { app ->
                                openPeek(app, SableStartDestination.Search)
                            },
                            onAllApps = {
                                destination = SableStartDestination.Apps
                            },
                            onAppearance = {
                                returnDestination = SableStartDestination.Start
                                destination = SableStartDestination.Appearance
                            },
                            onFrames = if (blankHomePage) null else ({ showPage(StartPages.HOME) }),
                            onGrid = { showPage(StartPages.FIRST_GRID) },
                            onHub = ::showHub,
                            onEditHome = {
                                destination = SableStartDestination.EditHome
                            },
                            onOpenSettings = onOpenSystemSettings,
                            onStartIntent = onStartIntent,
                        )

                    SableStartDestination.Appearance ->
                        AppearanceScreen(
                            appearance = appearance,
                            onBack = ::navigateBack,
                            onAppearanceChanged = onAppearanceChanged,
                            onReset = onResetAppearance,
                        )

                    SableStartDestination.Peek -> {
                        val app = selectedApp
                        if (app == null) {
                            destination = returnDestination
                        } else {
                            val key = app.stableKey()
                            val barIndex = quickSlots.indexOfFirst { it.app?.stableKey() == key }
                            SablePeekScreen(
                                app = app,
                                inBaseBar = QuickBar.appState(app.inLauncherUser, barIndex >= 0),
                                hasSecondInstance = secondInstance(app) != null,
                                onToggleBaseBar = {
                                    val slotToken = quickSlots.getOrNull(barIndex)?.token
                                    if (slotToken == null && QuickBar.needsSlotPicker(quickBarTokens)) {
                                        destination = SableStartDestination.SlotPicker
                                    } else {
                                        val next = QuickBar.toggleApp(quickBarTokens, slotToken, key)
                                        if (next != quickBarTokens) {
                                            quickBarTokens = next
                                            onQuickBarChanged(next)
                                        }
                                    }
                                },
                                onClose = ::navigateBack,
                                onOpen = { launchApp(app) },
                                onOpenSecond = { launchSecond(app) },
                                onOpenAppInfo = { onOpenAppInfo(app) },
                                onOpenNotificationSettings = { onOpenNotificationSettings(app) },
                                onUninstall = { onUninstallApp(app) },
                                letterShortcut =
                                    remember(app, shortcutVersion) {
                                        letterShortcuts?.load()?.letterOf(app.component.flattenToString())
                                    },
                                shortcutsAvailable = letterShortcuts != null && app.inLauncherUser,
                                onSetLetterShortcut = { letter ->
                                    if (letter == null) {
                                        letterShortcuts?.remove(app.component)
                                    } else {
                                        letterShortcuts?.assign(letter, app.component)
                                    }
                                    shortcutVersion += 1
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The BB10 look over the Start pages, in the user's Dark theme and Sable accent: the
 * focus fill and rings are the accent ([startPalette]).
 */
