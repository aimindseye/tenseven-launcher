package io.github.aimindseye.tenseven.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import io.github.aimindseye.tenseven.HubSettingsActivity
import io.github.aimindseye.tenseven.LauncherSettingsActivity
import io.github.aimindseye.tenseven.SableStartActivity
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.model.AppProfiles
import io.github.aimindseye.tenseven.model.HubFilter
import io.github.aimindseye.tenseven.model.QuickBar
import io.github.aimindseye.tenseven.model.StartPages
import io.github.aimindseye.tenseven.model.inLauncherUser
import io.github.aimindseye.tenseven.model.stableKey
import io.github.aimindseye.tenseven.platform.LetterShortcutRepository

internal class StartNavigation(
    private val state: StartNavigationState,
    private val animatePage: (Int) -> Unit,
    private val context: Context,
    private val onStartIntent: (Intent) -> Unit,
) {
    fun showPage(page: Int) {
        state.destination = SableStartDestination.Start
        animatePage(page)
    }

    fun showHub(filter: HubFilter) {
        state.hubFilter = filter
        showPage(StartPages.HUB)
    }

    fun openCommand() = openSearch(SableStartDestination.Start)

    fun openPeek(
        entry: AppEntry,
        from: SableStartDestination,
    ) {
        state.selectedApp = entry
        state.returnDestination = from
        state.destination = SableStartDestination.Peek
    }

    fun openSearch(
        from: SableStartDestination,
        seed: String = "",
    ) {
        state.searchSeed = seed
        state.returnDestination = from
        state.destination = SableStartDestination.Search
    }

    fun navigateBack() {
        state.destination = backDestination(state.destination, state.returnDestination)
    }

    fun openHubSettings() {
        onStartIntent(Intent(context, HubSettingsActivity::class.java))
    }

    fun openLauncherSettings() {
        onStartIntent(Intent(context, LauncherSettingsActivity::class.java))
    }

    fun openAppearance() {
        state.returnDestination = SableStartDestination.Start
        state.destination = SableStartDestination.Appearance
    }

    fun dial(digits: String) {
        onStartIntent(Intent(Intent.ACTION_DIAL, ("tel:" + Uri.encode(digits)).toUri()))
    }
}

internal class StartApplicationActions(
    private val apps: List<AppEntry>,
    private val state: StartLaunchState,
    private val navigation: StartNavigation,
    private val onLaunchApp: (AppEntry) -> Boolean,
) {
    fun launchApp(entry: AppEntry) {
        if (entry.component.className == SableStartActivity::class.java.name ||
            entry.component.className == "io.github.aimindseye.tenseven.LauncherEntry"
        ) {
            navigation.showPage(StartPages.HOME)
            return
        }
        if (onLaunchApp(entry)) {
            val key = entry.stableKey()
            state.recentKeys = (listOf(key) + state.recentKeys.filterNot { it == key }).take(MAX_SESSION_RECENTS)
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

    fun launchSecond(entry: AppEntry) = launchApp(secondInstance(entry) ?: entry)
}

internal data class StartHomeBindings(
    val onGridOrderChanged: (List<String>?) -> Unit,
    val onQuickBarChanged: (List<String>?) -> Unit,
    val onGlanceWidgetsChanged: (List<String>?) -> Unit,
    val onHiddenHomeSectionsChanged: (Set<String>?) -> Unit,
    val onStartIntent: (Intent) -> Unit,
    val letterShortcuts: LetterShortcutRepository?,
)

internal class StartHomeActions(
    private val inputs: StartHomeBindings,
    private val session: StartSessionState,
    private val quickSlots: List<QuickSlot>,
    private val navigation: StartNavigation,
    private val applications: StartApplicationActions,
    private val onHomeEdited: () -> Unit,
) {
    private val home: StartHomeEditState get() = session.home
    private val state: StartNavigationState get() = session.navigation

    fun setGridOrder(next: List<String>) {
        home.savedGridOrder = next
        inputs.onGridOrderChanged(next)
    }

    fun activateSlot(slot: QuickSlot) =
        activateQuickSlot(
            slot,
            QuickSlotActions(
                command = navigation::openCommand,
                apps = { state.destination = SableStartDestination.Apps },
                hub = { navigation.showHub(HubFilter.All) },
                camera = { inputs.onStartIntent(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) },
                launch = applications::launchApp,
            ),
        )

    fun saveEdit(edit: HomeEdit) {
        setGridOrder(edit.gridOrder)
        home.quickBarTokens = QuickBar.normalize(edit.quickBar)
        inputs.onQuickBarChanged(if (home.quickBarTokens == QuickBar.DEFAULT) null else home.quickBarTokens)
        home.glanceWidgets = edit.glanceWidgets
        inputs.onGlanceWidgetsChanged(edit.glanceWidgets)
        home.hiddenSections = edit.hiddenSections
        inputs.onHiddenHomeSectionsChanged(edit.hiddenSections)
        state.destination = SableStartDestination.Start
        onHomeEdited()
    }

    fun replaceSlot(
        app: AppEntry,
        index: Int,
    ) {
        val next = QuickBar.replace(home.quickBarTokens, index, QuickBar.appToken(app.stableKey()))
        if (next != home.quickBarTokens) {
            home.quickBarTokens = next
            inputs.onQuickBarChanged(next)
        }
        state.destination = SableStartDestination.Peek
    }

    fun toggleBaseBar(
        app: AppEntry,
        barIndex: Int,
    ) {
        val key = app.stableKey()
        val slotToken = quickSlots.getOrNull(barIndex)?.token
        if (slotToken == null && QuickBar.needsSlotPicker(home.quickBarTokens)) {
            state.destination = SableStartDestination.SlotPicker
        } else {
            val next = QuickBar.toggleApp(home.quickBarTokens, slotToken, key)
            if (next != home.quickBarTokens) {
                home.quickBarTokens = next
                inputs.onQuickBarChanged(next)
            }
        }
    }

    fun setLetterShortcut(
        app: AppEntry,
        letter: Char?,
    ) {
        if (letter == null) {
            inputs.letterShortcuts?.remove(app.component)
        } else {
            inputs.letterShortcuts?.assign(letter, app.component)
        }
        session.launches.shortcutVersion += 1
    }
}
