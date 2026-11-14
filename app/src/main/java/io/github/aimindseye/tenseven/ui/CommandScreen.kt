package io.github.aimindseye.tenseven.ui

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aimindseye.tenseven.BackupActivity
import io.github.aimindseye.tenseven.HomeStyleActivity
import io.github.aimindseye.tenseven.LauncherSettingsActivity
import io.github.aimindseye.tenseven.OnboardingActivity
import io.github.aimindseye.tenseven.R
import io.github.aimindseye.tenseven.design.sableFocusRing
import io.github.aimindseye.tenseven.model.AppEntry
import io.github.aimindseye.tenseven.model.AppGrid
import io.github.aimindseye.tenseven.model.AppProfiles
import io.github.aimindseye.tenseven.model.AppUsage
import io.github.aimindseye.tenseven.model.AssignCommand
import io.github.aimindseye.tenseven.model.AssignRequest
import io.github.aimindseye.tenseven.model.CommandBestMatch
import io.github.aimindseye.tenseven.model.CommandGroups
import io.github.aimindseye.tenseven.model.CommandQuery
import io.github.aimindseye.tenseven.model.CommandRanking
import io.github.aimindseye.tenseven.model.CommandScope
import io.github.aimindseye.tenseven.model.HubFilter
import io.github.aimindseye.tenseven.model.QuickActionKind
import io.github.aimindseye.tenseven.model.QuickActions
import io.github.aimindseye.tenseven.model.QuickBar
import io.github.aimindseye.tenseven.model.StartText.t
import io.github.aimindseye.tenseven.model.inLauncherUser
import io.github.aimindseye.tenseven.model.stableKey
import io.github.aimindseye.tenseven.onboarding.SetupStep
import io.github.aimindseye.tenseven.platform.AppUsageRepository
import io.github.aimindseye.tenseven.platform.LetterShortcutRepository
import io.github.aimindseye.tenseven.privacy.AllAppsKeyAction
import io.github.aimindseye.tenseven.privacy.AllAppsKeyPolicy
import io.github.aimindseye.tenseven.privacy.PrivacySummaryPolicy
import kotlinx.coroutines.launch
import java.util.Locale

private const val SEARCH_RECENT_APP_LIMIT = 6
private const val SEARCH_MATCHED_APP_LIMIT = 12
private const val SEARCH_PRIVACY_MATCH_LIMIT = 8

@Composable
internal fun SearchLaunchButton(
    accent: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .sableFocusRing()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(4.dp)
                    .height(42.dp)
                    .background(accent),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = t("search_command"),
                fontSize = 20.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = t("apps_settings_sable_actions"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SearchScreen(
    apps: List<AppEntry>,
    recentKeys: List<String>,
    letterShortcuts: LetterShortcutRepository?,
    initialQuery: String,
    privateSpaceLocked: Boolean?,
    onTogglePrivateSpace: () -> Unit,
    workProfilePaused: Boolean?,
    onToggleWorkProfile: () -> Unit,
    onBack: () -> Unit,
    onLaunchApp: (AppEntry) -> Unit,
    secondInstanceOf: (AppEntry) -> AppEntry?,
    onOpenAppInfo: (AppEntry) -> Unit,
    onOpenPeek: (AppEntry) -> Unit,
    onAllApps: () -> Unit,
    onAppearance: () -> Unit,
    onFrames: (() -> Unit)?,
    onGrid: () -> Unit,
    onHub: (HubFilter) -> Unit,
    onEditHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartIntent: (Intent) -> Unit,
) {
    // Preserve the original independent remember lifetimes: only query resets with its seed.
    val queryState =
        remember(initialQuery) {
            mutableStateOf(TextFieldValue(initialQuery, TextRange(initialQuery.length)))
        }
    val chipState = remember { mutableStateOf<CommandScope?>(null) }
    val actionsState = remember { mutableStateOf<String?>(null) }
    val ui = SearchUiState(queryState, chipState, actionsState)
    val context = LocalContext.current
    val appUsage = remember(apps, recentKeys) { AppUsageRepository(context).load() }
    val input = SearchInput(apps, recentKeys, letterShortcuts, appUsage, privateSpaceLocked)
    val routes =
        SearchRoutes(
            page = SearchPageRoutes(onAllApps, onAppearance, onFrames, onGrid, onHub, onEditHome),
            system = SearchSystemRoutes(onOpenSettings, onStartIntent, onToggleWorkProfile),
            workPaused = workProfilePaused,
        )
    val actions = SearchResultActions(onLaunchApp, secondInstanceOf, onOpenAppInfo, onOpenPeek)
    val model = rememberSearchModel(ui, input, routes, context)
    SearchScreenLayout(ui, model, actions, onBack, onTogglePrivateSpace)
}

/** Search-only state survives recomposition, without changing the seed's remember key. */
private class SearchUiState(
    queryState: MutableState<TextFieldValue>,
    chipState: MutableState<CommandScope?>,
    actionsState: MutableState<String?>,
) {
    var query by queryState
    var chip by chipState
    var actionsFor by actionsState

    fun moveChip(
        scope: CommandScope,
        delta: Int,
    ) {
        chip = CommandGroups.next(chip ?: scope, delta)
        actionsFor = null
    }

    fun toggleActions(key: String) {
        actionsFor = if (actionsFor == key) null else key
    }
}

private data class SearchInput(
    val apps: List<AppEntry>,
    val recentKeys: List<String>,
    val shortcuts: LetterShortcutRepository?,
    val appUsage: AppUsage,
    val privateSpaceLocked: Boolean?,
)

private data class SearchPageRoutes(
    val allApps: () -> Unit,
    val appearance: () -> Unit,
    val frames: (() -> Unit)?,
    val grid: () -> Unit,
    val hub: (HubFilter) -> Unit,
    val editHome: () -> Unit,
)

private data class SearchSystemRoutes(
    val settings: () -> Unit,
    val startIntent: (Intent) -> Unit,
    val toggleWork: () -> Unit,
)

private data class SearchRoutes(
    val page: SearchPageRoutes,
    val system: SearchSystemRoutes,
    val workPaused: Boolean?,
)

private data class SearchResultActions(
    val launch: (AppEntry) -> Unit,
    val secondInstance: (AppEntry) -> AppEntry?,
    val appInfo: (AppEntry) -> Unit,
    val peek: (AppEntry) -> Unit,
)

private data class SearchAppMatches(
    val apps: List<AppEntry>,
    val privacy: List<AppEntry>,
)

private data class SearchCommandGroups(
    val quickLabel: String,
    val filtered: List<SearchCommand>,
    val settings: List<SearchCommand>,
    val people: List<SearchCommand>,
    val hub: List<SearchCommand>,
    val quick: List<SearchCommand>,
)

private data class SearchBest(
    val app: AppEntry?,
    val command: SearchCommand?,
    val firstOther: SearchCommand?,
)

private data class SearchModel(
    val parsed: CommandQuery,
    val profileApps: List<AppEntry>?,
    val matches: SearchAppMatches,
    val commands: SearchCommandGroups,
    val best: SearchBest,
    val lockedPrivate: Boolean,
)

private fun searchAppMatches(
    apps: List<AppEntry>,
    recentKeys: List<String>,
    parsed: CommandQuery,
    appUsage: AppUsage,
    profileApps: List<AppEntry>?,
): SearchAppMatches {
    val term = parsed.term
    return when {
        profileApps != null -> {
            SearchAppMatches(
                CommandRanking.apps(profileApps, { it.label }, { null }, term),
                emptyList(),
            )
        }

        !parsed.includes(CommandScope.Apps) -> {
            SearchAppMatches(emptyList(), emptyList())
        }

        else -> {
            val byName =
                CommandRanking
                    .apps(
                        items = apps,
                        label = { it.label },
                        recentRank = { app -> recentKeys.indexOf(app.stableKey()).takeIf { it >= 0 } },
                        term = term,
                        frequency = { app -> appUsage.score(app.stableKey()) },
                    ).take(
                        if (term.isEmpty()) SEARCH_RECENT_APP_LIMIT else SEARCH_MATCHED_APP_LIMIT,
                    )
            val byPrivacy =
                if (term.isEmpty()) {
                    emptyList()
                } else {
                    apps
                        .filter { it !in byName && PrivacySummaryPolicy.matchesPrivacyQuery(it.privacy, term) }
                        .take(SEARCH_PRIVACY_MATCH_LIMIT)
                }
            SearchAppMatches(byName, byPrivacy)
        }
    }
}

private fun searchPrimaryCommands(routes: SearchPageRoutes): List<SearchCommand> =
    listOfNotNull(
        SearchCommand(t("all_apps"), t("a_to_z_with_privacy"), "A", t("start"), routes.allApps),
        routes.frames?.let { SearchCommand(t("active_frames"), t("open_apps_home"), "▦", t("start"), it) },
        SearchCommand(t("app_grid"), t("every_app_in_your_order"), "▤", t("start"), routes.grid),
        SearchCommand(
            t("hub_page"),
            t("messages_mail_calls_contacts_and"),
            "◇",
            t("start"),
            { routes.hub(HubFilter.All) },
        ),
        SearchCommand(t("edit_home"), t("order_the_app_grid_the"), "▭", t("start"), routes.editHome),
        SearchCommand(t("appearance"), t("theme_and_sable_accent"), "◐", t("start"), routes.appearance),
    )

private fun searchSystemCommands(
    context: Context,
    workPaused: Boolean?,
    routes: SearchSystemRoutes,
): List<SearchCommand> =
    listOfNotNull(
        if (workPaused != null) {
            SearchCommand(
                context.getString(if (workPaused) R.string.work_resume else R.string.work_pause),
                context.getString(R.string.work_hint),
                "▣",
                context.getString(R.string.work_source),
                routes.toggleWork,
            )
        } else {
            null
        },
        SearchCommand(
            context.getString(R.string.launcher_settings),
            context.getString(R.string.launcher_settings_hint),
            "⚙",
            t("start"),
            { routes.startIntent(Intent(context, LauncherSettingsActivity::class.java)) },
        ),
        SearchCommand(
            t("home_screen_style_2"),
            context.getString(R.string.style_command_hint),
            "▭",
            t("start"),
            { routes.startIntent(Intent(context, HomeStyleActivity::class.java)) },
        ),
        SearchCommand(
            context.getString(R.string.setup_menu),
            context.getString(R.string.setup_command_hint),
            "✓",
            t("start"),
            { routes.startIntent(Intent(context, OnboardingActivity::class.java)) },
        ),
        SearchCommand(
            context.getString(R.string.setup_extras_title),
            context.getString(R.string.extras_command_hint),
            "✚",
            t("start"),
            {
                routes.startIntent(
                    Intent(context, OnboardingActivity::class.java)
                        .putExtra(OnboardingActivity.EXTRA_STEP, SetupStep.EXTRAS.name),
                )
            },
        ),
        SearchCommand(
            context.getString(R.string.backup_menu),
            context.getString(R.string.backup_command_hint),
            "⇅",
            t("start"),
            { routes.startIntent(Intent(context, BackupActivity::class.java)) },
        ),
        SearchCommand(t("system_settings"), t("open_android_settings"), "⚙", t("settings"), routes.settings),
    )

private fun searchProfileApps(
    apps: List<AppEntry>,
    scope: CommandScope,
): List<AppEntry>? =
    when (scope) {
        CommandScope.Private -> apps.filter { it.profileLabel == AppProfiles.PRIVATE }
        CommandScope.Dual -> apps.filter { it.profileLabel == AppProfiles.DUAL }
        CommandScope.Work -> apps.filter { it.profileLabel == AppProfiles.WORK }
        else -> null
    }

private fun searchFilteredCommands(
    parsed: CommandQuery,
    commands: List<SearchCommand>,
): List<SearchCommand> {
    if (!parsed.includes(CommandScope.Actions) || parsed.profileScope) return emptyList()
    val locale = Locale.getDefault()
    val term = parsed.term.lowercase(locale)
    return commands.filter { item ->
        term.isEmpty() || item.title.lowercase(locale).contains(term) || item.subtitle.lowercase(locale).contains(term)
    }
}

private fun searchCommandGroups(
    context: Context,
    parsed: CommandQuery,
    routes: SearchRoutes,
    input: SearchInput,
    raw: String,
): SearchCommandGroups {
    val commands = searchPrimaryCommands(routes.page) + searchSystemCommands(context, routes.workPaused, routes.system)
    return SearchCommandGroups(
        quickLabel = context.getString(R.string.quick_group),
        filtered = searchFilteredCommands(parsed, commands),
        settings =
            if (parsed.profileScope) {
                emptyList()
            } else {
                settingsResults(
                    parsed,
                    routes.system.startIntent,
                    routes.system.settings,
                )
            },
        people =
            if (parsed.profileScope) {
                emptyList()
            } else {
                peopleResults(
                    parsed,
                    routes.system.startIntent,
                    routes.page.hub,
                )
            },
        hub = if (parsed.profileScope) emptyList() else hubResults(parsed, routes.page.hub),
        quick =
            quickActionRows(context, raw, routes.system.startIntent) +
                assignRows(context, raw, input.apps, input.appUsage, input.shortcuts),
    )
}

private fun searchFirstOther(
    groups: SearchCommandGroups,
    term: String,
): SearchCommand? =
    (
        groups.quick + groups.filtered.takeIf { term.isNotEmpty() }.orEmpty() +
            groups.settings + groups.people + groups.hub
    ).firstOrNull()

private fun searchBestMatch(
    matches: SearchAppMatches,
    groups: SearchCommandGroups,
    term: String,
): SearchBest {
    val firstOther = searchFirstOther(groups, term)
    val bestIsApp = CommandBestMatch.appWins(matches.apps.firstOrNull()?.label, firstOther?.title, term)
    return SearchBest(
        matches.apps.firstOrNull()?.takeIf { bestIsApp == true },
        firstOther?.takeIf { bestIsApp == false },
        firstOther,
    )
}

@Composable
private fun rememberSearchModel(
    ui: SearchUiState,
    input: SearchInput,
    routes: SearchRoutes,
    context: Context,
): SearchModel {
    val parsed = CommandGroups.apply(CommandQuery.parse(ui.query.text), ui.chip)
    val profileApps = searchProfileApps(input.apps, parsed.scope)
    val matches =
        remember(input.apps, input.recentKeys, parsed, input.appUsage) {
            searchAppMatches(input.apps, input.recentKeys, parsed, input.appUsage, profileApps)
        }
    val results = searchCommandGroups(context, parsed, routes, input, ui.query.text)
    return SearchModel(
        parsed = parsed,
        profileApps = profileApps,
        matches = matches,
        commands = results,
        best = searchBestMatch(matches, results, parsed.term),
        lockedPrivate =
            input.privateSpaceLocked == true &&
                (parsed.scope == CommandScope.Private || parsed.term.isNotEmpty()),
    )
}

@Suppress("FunctionNaming") // Compose UI declarations are intentionally PascalCase.
@Composable
private fun SearchScreenLayout(
    ui: SearchUiState,
    model: SearchModel,
    actions: SearchResultActions,
    onBack: () -> Unit,
    onTogglePrivateSpace: () -> Unit,
) {
    val moveChip: (Int) -> Unit = { delta -> ui.moveChip(model.parsed.scope, delta) }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
        MetroHeader(title = "search", onBack = onBack)
        Spacer(Modifier.height(10.dp))
        SableSearchField(
            value = ui.query,
            onValueChange = {
                ui.query = it
                ui.actionsFor = null
            },
            onSubmit = {
                model.best.app?.let(actions.launch)
                    ?: model.best.command
                        ?.onClick
                        ?.invoke()
                    ?: model.matches.apps
                        .firstOrNull()
                        ?.let(actions.launch)
                    ?: model.best.firstOther
                        ?.onClick
                        ?.invoke()
            },
            onGroupDelta = moveChip,
        )
        Spacer(Modifier.height(8.dp))
        CommandChips(
            selected = if (model.parsed.profileScope) null else model.parsed.scope,
            onSelect = { ui.chip = it },
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier =
                Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    val delta = AppGrid.arrowDelta(native.keyCode)?.takeIf { it == -1 || it == 1 }
                    if (delta == null || native.metaState != 0) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown) moveChip(delta)
                    true
                },
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            addSearchResults(model, ui, actions, onTogglePrivateSpace)
        }
    }
}

private fun LazyListScope.addSearchResults(
    model: SearchModel,
    ui: SearchUiState,
    actions: SearchResultActions,
    onTogglePrivateSpace: () -> Unit,
) {
    if (model.lockedPrivate) {
        item(key = "locked") {
            PrivateLockedCard(onUnlock = onTogglePrivateSpace, full = model.parsed.scope == CommandScope.Private)
            Spacer(Modifier.height(12.dp))
        }
    }
    if (model.best.app != null || model.best.command != null) {
        item(key = "best") {
            SearchBestMatch {
                val bestApp = model.best.app
                if (bestApp != null) {
                    SearchAppResult(bestApp, ui, actions)
                } else {
                    model.best.command?.let { SearchCommandResult(it, ui) }
                }
            }
        }
    }
    addSearchAppItems(model, ui, actions)
    addSearchCommandItems(model, ui)
    item {
        Text(
            text = t("results_groups_enter_opens_shift"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun LazyListScope.addSearchAppItems(
    model: SearchModel,
    ui: SearchUiState,
    actions: SearchResultActions,
) {
    val apps = model.matches.apps.filterNot { it == model.best.app }
    if (apps.isNotEmpty()) {
        item {
            SectionLabel(
                when {
                    model.profileApps != null -> {
                        CommandGroups.label(model.parsed.scope).lowercase(Locale.getDefault()) + " apps"
                    }

                    model.parsed.term.isEmpty() -> {
                        t("recent_apps")
                    }

                    else -> {
                        "apps"
                    }
                },
            )
        }
        items(apps, key = { "a:" + it.stableKey() }) { app -> SearchAppResult(app, ui, actions) }
        item { Spacer(Modifier.height(12.dp)) }
    }
    if (model.matches.privacy.isNotEmpty()) {
        item { SectionLabel(t("permission_matches")) }
        items(model.matches.privacy, key = { "p:" + it.stableKey() }) { app -> SearchAppResult(app, ui, actions) }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

private fun LazyListScope.addSearchCommandItems(
    model: SearchModel,
    ui: SearchUiState,
) {
    val groups = model.commands
    listOf(
        groups.quickLabel to groups.quick,
        "people" to groups.people,
        "settings" to groups.settings,
        "hub" to groups.hub,
        (if (model.parsed.term.isEmpty()) t("quick_actions") else "actions") to groups.filtered,
    ).forEach { (label, rows) ->
        val shown = rows.filterNot { it == model.best.command }
        if (shown.isNotEmpty()) {
            item { SectionLabel(label) }
            items(shown, key = { "$label:" + it.source + it.title }) { command -> SearchCommandResult(command, ui) }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
    val noMatches =
        listOf(
            model.matches.apps,
            model.matches.privacy,
            groups.filtered,
            groups.settings,
            groups.quick,
            groups.people,
            groups.hub,
        ).all { it.isEmpty() }
    if (noMatches && !model.lockedPrivate) {
        item { EmptyState(t("no_local_results_match_prefixes", model.parsed.term)) }
    }
}

private fun searchAppActions(
    app: AppEntry,
    actions: SearchResultActions,
): List<Pair<String, () -> Unit>> =
    buildList {
        add("open" to { actions.launch(app) })
        actions.secondInstance(app)?.let { other ->
            val label = if (other.profileLabel == AppProfiles.DUAL) t("open_dual_copy") else t("open_personal_app")
            add(label to { actions.launch(other) })
        }
        add(t("app_info_2") to { actions.appInfo(app) })
        add(t("more_actions") to { actions.peek(app) })
    }

@Suppress("FunctionNaming") // Compose UI declarations are intentionally PascalCase.
@Composable
private fun SearchAppResult(app: AppEntry, ui: SearchUiState, actions: SearchResultActions) {
    val key = "app:" + app.stableKey()
    AppRow(
        app = app,
        onClick = { actions.launch(app) },
        onContext = { ui.toggleActions(key) },
        modifier =
            Modifier.onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                val decision = AllAppsKeyPolicy.decide(native.keyCode, 0, native.metaState, native.repeatCount > 0)
                val action: (() -> Unit)? =
                    when (decision.action) {
                        AllAppsKeyAction.OpenSecond -> {
                            { actions.launch(actions.secondInstance(app) ?: app) }
                        }

                        AllAppsKeyAction.Actions -> {
                            { ui.toggleActions(key) }
                        }

                        else -> {
                            null
                        }
                    }
                if (action != null && event.type == KeyEventType.KeyDown) action()
                action != null
            },
    )
    if (ui.actionsFor == key) EditOptions(searchAppActions(app, actions))
}

@Suppress("FunctionNaming") // Compose UI declarations are intentionally PascalCase.
@Composable
private fun SearchCommandResult(command: SearchCommand, ui: SearchUiState) {
    val key = "cmd:" + command.source + ":" + command.title
    SearchCommandRow(command = command, onActions = { ui.toggleActions(key) })
    if (ui.actionsFor == key) EditOptions(command.actions.ifEmpty { listOf("open" to command.onClick) })
}

/** The group chips over the results; the current scope is filled. */
@Composable
internal fun CommandChips(
    selected: CommandScope?,
    onSelect: (CommandScope) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CommandGroups.CHIPS.forEach { scope ->
            val on = scope == selected
            Text(
                text = CommandGroups.label(scope),
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                        .clickable { onSelect(scope) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

/**
 * Command's locked state (sable-command-titan2 "4. Locked / Private"): the
 * Private Space is locked, so its apps are neither listed nor searched.
 */
@Composable
internal fun PrivateLockedCard(
    onUnlock: () -> Unit,
    full: Boolean,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .padding(12.dp),
        horizontalAlignment = if (full) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        if (full) Text("🔒", fontSize = 28.sp)
        Text(
            text = if (full) t("content_hidden") else t("private_space_locked"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = t("unlock_to_show_and_search"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = t("unlock"),
            modifier =
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                    .sableFocusRing()
                    .clickable(onClick = onUnlock)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

internal fun settingsResults(
    parsed: CommandQuery,
    onStartIntent: (Intent) -> Unit,
    onOpenSettings: () -> Unit,
): List<SearchCommand> {
    if (!parsed.includes(CommandScope.Settings)) return emptyList()
    val limit = if (parsed.scope == CommandScope.Settings) 12 else 4
    return CommandRanking.settings(parsed.term, limit = limit).map { dest ->
        val open = { onStartIntent(Intent(dest.action)) }
        SearchCommand(
            title = dest.title,
            subtitle = t("open_settings", dest.title),
            mark = "⚙",
            source = t("settings"),
            onClick = open,
            actions = listOf(t("open_2", dest.title) to open, t("open_settings_home") to onOpenSettings),
        )
    }
}

/**
 * People: dial a typed number, or hand the name to Contacts' own search
 * (Start holds no contacts permission and keeps no copy).
 */
internal fun peopleResults(
    parsed: CommandQuery,
    onStartIntent: (Intent) -> Unit,
    onHub: (HubFilter) -> Unit,
): List<SearchCommand> {
    val term = parsed.term
    if (term.isEmpty() || !parsed.includes(CommandScope.People)) return emptyList()
    val dial =
        CommandQuery.dialable(term)?.let { number ->
            val call = { onStartIntent(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
            SearchCommand(
                title = t("call", number),
                subtitle = t("opens_the_dialer_with_the"),
                mark = "☎",
                source = t("phone"),
                onClick = call,
                actions =
                    listOf(
                        "call" to call,
                        t("send_a_message") to { onStartIntent(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))) },
                        t("add_to_contacts") to {
                            onStartIntent(
                                Intent(Intent.ACTION_INSERT)
                                    .setType("vnd.android.cursor.dir/contact")
                                    .putExtra("phone", number),
                            )
                        },
                    ),
            )
        }
    val find = {
        onStartIntent(
            Intent(Intent.ACTION_SEARCH)
                .setPackage(CONTACTS_PACKAGE)
                .putExtra(SearchManager.QUERY, term),
        )
    }
    val contacts =
        SearchCommand(
            title = t("find_in_contacts", term),
            subtitle = t("search_people_by_name_number"),
            mark = "@",
            source = t("contacts"),
            onClick = find,
            actions = listOf(t("find_in_contacts_2") to find, t("hub_page_contacts") to { onHub(HubFilter.Contacts) }),
        ).takeIf { parsed.scope == CommandScope.People || term.length >= 2 }
    return listOfNotNull(dial, contacts)
}

/**
 * Hub results open Start's own Hub page on a tab (Start never shows message, mail or call
 * content). Shown for "/hub", or when the term names Hub or one of its tabs.
 */
internal fun hubResults(
    parsed: CommandQuery,
    onHub: (HubFilter) -> Unit,
): List<SearchCommand> {
    if (!parsed.includes(CommandScope.Hub)) return emptyList()
    val term = parsed.term.lowercase(Locale.ROOT)
    val tab = HubFilter.entries.firstOrNull { term.isNotEmpty() && it.label.lowercase(Locale.ROOT).startsWith(term) }
    val named = term.isNotEmpty() && ("hub".startsWith(term) || tab != null)
    if (parsed.scope != CommandScope.Hub && !named) return emptyList()
    val target = tab ?: HubFilter.All
    return listOf(
        SearchCommand(
            title = if (target == HubFilter.All) t("open_hub") else t("hub_2", target.label),
            subtitle = t("messages_calls_contacts_and_notifications"),
            mark = "◉",
            source = t("hub"),
            onClick = { onHub(target) },
            actions = HubFilter.entries.map { filter -> t("hub_2", filter.label) to { onHub(filter) } },
        ),
    )
}

@Composable
internal fun SableSearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSubmit: () -> Unit,
    onGroupDelta: (Int) -> Unit,
) {
    val fieldFocus = rememberSearchFieldFocus()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.searchAccentModifier())
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(fieldFocus)
                        .sableSearchKeyHandling(value, onValueChange, onSubmit, onGroupDelta)
                        .padding(vertical = 8.dp),
                singleLine = true,
                textStyle =
                    MaterialTheme.typography.titleLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                decorationBox = { innerTextField ->
                    if (value.text.isEmpty()) {
                        Text(
                            text = t("search_apps_sable_actions"),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                },
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
    }
}

@Composable
private fun Modifier.searchAccentModifier(): Modifier =
    width(4.dp)
        .height(46.dp)
        .background(MaterialTheme.colorScheme.primary)

@Composable
private fun rememberSearchFieldFocus(): FocusRequester {
    // The field takes focus on arrival so the keyboard types straight into it.
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { fieldFocus.requestFocus() }
    }
    return fieldFocus
}

private fun Modifier.sableSearchKeyHandling(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSubmit: () -> Unit,
    onGroupDelta: (Int) -> Unit,
): Modifier =
    onPreviewKeyEvent { event ->
        val native = event.nativeKeyEvent
        val groupDirection = searchGroupDirection(value, native.keyCode, native.metaState)
        when {
            event.type != KeyEventType.KeyDown -> {
                false
            }

            // Holding Backspace clears the whole query.
            native.keyCode == KEYCODE_DEL && native.repeatCount > 0 -> {
                if (value.text.isNotEmpty()) onValueChange(TextFieldValue(""))
                true
            }

            native.keyCode == AllAppsKeyPolicy.KEYCODE_ENTER && native.repeatCount == 0 -> {
                onSubmit()
                true
            }

            // Left/right at the query boundaries navigates between groups.
            groupDirection != 0 -> {
                onGroupDelta(groupDirection)
                true
            }

            else -> {
                false
            }
        }
    }

private fun searchGroupDirection(
    value: TextFieldValue,
    keyCode: Int,
    metaState: Int,
): Int {
    if (metaState != 0 || !value.selection.collapsed) return 0
    return when {
        keyCode == KEYCODE_DPAD_LEFT && value.selection.start == 0 -> -1
        keyCode == KEYCODE_DPAD_RIGHT && value.selection.end == value.text.length -> 1
        else -> 0
    }
}

@Composable
internal fun SearchCommandRow(
    command: SearchCommand,
    onActions: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .sableFocusRing()
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    val alt = native.metaState and (QuickBar.META_ALT_ON or QuickBar.META_FUNCTION_ON) != 0
                    val menu = native.keyCode == AllAppsKeyPolicy.KEYCODE_MENU || (native.keyCode == AllAppsKeyPolicy.KEYCODE_ENTER && alt)
                    if (menu && event.type == KeyEventType.KeyDown && native.repeatCount == 0) onActions()
                    menu
                }.combinedClickable(onClick = command.onClick, onLongClick = onActions)
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = command.mark,
            modifier = Modifier.width(38.dp),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = command.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = command.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = command.source,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

internal fun quickActionRows(
    context: Context,
    raw: String,
    onStartIntent: (Intent) -> Unit,
): List<SearchCommand> {
    val source = context.getString(R.string.quick_source)
    QuickActions.parse(raw)?.let { action ->
        val (title, intent) =
            when (action.kind) {
                QuickActionKind.TODO -> {
                    context.getString(R.string.quick_todo, action.text) to
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, action.text),
                            context.getString(R.string.quick_send_to_app),
                        )
                }

                QuickActionKind.NOTE -> {
                    context.getString(R.string.quick_note, action.text) to
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, action.text),
                            context.getString(R.string.quick_send_to_app),
                        )
                }

                QuickActionKind.EVENT -> {
                    context.getString(R.string.quick_event, action.text) to
                        Intent(Intent.ACTION_INSERT)
                            .setData(CalendarContract.Events.CONTENT_URI)
                            .putExtra(CalendarContract.Events.TITLE, action.text)
                }
            }
        return listOf(
            SearchCommand(title, context.getString(R.string.quick_hint), "+", source, { onStartIntent(intent) }),
        )
    }
    QuickActions.normalizedWebUrl(raw)?.let { url ->
        return listOf(
            SearchCommand(
                context.getString(R.string.quick_open_url, url),
                context.getString(R.string.quick_hint_url),
                "↗",
                source,
                { onStartIntent(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
            ),
        )
    }
    return emptyList()
}

/** `assign k camera` lists the apps that K could open; choosing one holds it on K. `unassign k` frees the letter. */
private fun clearAssignRows(
    context: Context,
    apps: List<AppEntry>,
    shortcuts: LetterShortcutRepository,
    request: AssignRequest.Clear,
): List<SearchCommand> {
    val current = shortcuts.load()
    val source = context.getString(R.string.assign_source)
    val letter = request.letter.uppercaseChar()

    val held = current.componentFor(request.letter)?.let(android.content.ComponentName::unflattenFromString)
    val label = held?.let { c -> apps.firstOrNull { it.component == c }?.label ?: c.packageName }
    return if (held == null || label == null) {
        listOf(SearchCommand(context.getString(R.string.assign_nothing, letter), "", "$letter", source, {}))
    } else {
        listOf(
            SearchCommand(
                context.getString(R.string.assign_clear, letter, label),
                context.getString(R.string.assign_hint, letter),
                "$letter",
                source,
                {
                    shortcuts.remove(held)
                    Toast.makeText(context, context.getString(R.string.assign_cleared, letter), Toast.LENGTH_SHORT).show()
                },
            ),
        )
    }
}

private fun assignmentReplacementName(
    holder: String?,
    app: AppEntry,
    apps: List<AppEntry>,
): String? =
    holder
        ?.takeIf { it != app.component.flattenToString() }
        ?.let(android.content.ComponentName::unflattenFromString)
        ?.let { component -> apps.firstOrNull { it.component == component }?.label ?: component.packageName }

internal fun assignRows(
    context: Context,
    raw: String,
    apps: List<AppEntry>,
    usage: AppUsage,
    letterShortcuts: LetterShortcutRepository?,
): List<SearchCommand> {
    val request = AssignCommand.parse(raw)
    val shortcuts = letterShortcuts
    if (request == null || shortcuts == null) return emptyList()
    val source = context.getString(R.string.assign_source)
    val letter = request.letter.uppercaseChar()
    val current = shortcuts.load()
    return when (request) {
        is AssignRequest.Clear -> {
            clearAssignRows(context, apps, shortcuts, request)
        }

        is AssignRequest.Assign -> {
            CommandRanking
                .apps(
                    items = apps.filter { it.inLauncherUser },
                    label = { it.label },
                    recentRank = { null },
                    term = request.appTerm,
                    frequency = { usage.score(it.stableKey()) },
                ).take(ASSIGN_ROWS)
                .map { app ->
                    val holder = current.componentFor(request.letter)
                    val replaces = assignmentReplacementName(holder, app, apps)
                    SearchCommand(
                        context.getString(R.string.assign_row, letter, app.label),
                        if (replaces !=
                            null
                        ) {
                            context.getString(R.string.assign_replaces, replaces)
                        } else {
                            context.getString(R.string.assign_hint, letter)
                        },
                        "$letter",
                        source,
                        {
                            shortcuts.assign(request.letter, app.component)
                            Toast.makeText(context, context.getString(R.string.assign_done, letter, app.label), Toast.LENGTH_SHORT).show()
                        },
                    )
                }
        }
    }
}

// Compose lint requires PascalCase for Unit renderers; detekt does not recognize that contract.
@Suppress("FunctionNaming")
@Composable
private fun SearchBestMatch(content: @Composable () -> Unit) {
    SectionLabel(t("best_match"))
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp),
    ) {
        Column { content() }
    }
    Spacer(Modifier.height(12.dp))
}
